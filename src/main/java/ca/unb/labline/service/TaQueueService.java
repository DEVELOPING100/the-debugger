package ca.unb.labline.service;

import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.RequestStatus;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.HelpRequestRepository;
import ca.unb.labline.repository.LabSessionRepository;
import ca.unb.labline.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A TA's actions on the queue of a lab session (SCRUM-7).
 * Allowed status changes:
 *   WAITING  -> CLAIMED   (claim the next request, oldest first)
 *   CLAIMED  -> RESOLVED  (only the TA who claimed it)
 *   CLAIMED  -> DEFERRED  (only the TA who claimed it)
 *   CLAIMED or DEFERRED -> WAITING  (return to queue; keeps its original place)
 *   WAITING, or CLAIMED by this TA -> RESOLVED  (one broadcast answer to a cluster, SCRUM-11)
 * Every status change is written to the audit log through AuditLogger.
 */
@Service
public class TaQueueService {

    private static final Set<RequestStatus> OPEN_STATUSES =
            Set.of(RequestStatus.WAITING, RequestStatus.CLAIMED, RequestStatus.DEFERRED);

    private static final String CLAIM_REASON = "Claimed by TA";
    private static final String RESOLVE_REASON = "Resolved by TA";
    private static final String DEFAULT_DEFER_REASON = "Deferred by TA";
    private static final String DEFAULT_RETURN_REASON = "Returned to queue by TA";
    private static final String BROADCAST_PREFIX = "Broadcast answer: ";

    /** The answer is kept as the audit reason (VARCHAR 255), so it must fit with the prefix. */
    public static final int MAX_ANSWER_LENGTH = 230;

    private final HelpRequestRepository helpRequestRepository;
    private final LabSessionRepository labSessionRepository;
    private final UserRepository userRepository;
    private final AuditLogger auditLogger;

    public TaQueueService(HelpRequestRepository helpRequestRepository,
            LabSessionRepository labSessionRepository, UserRepository userRepository,
            AuditLogger auditLogger) {
        this.helpRequestRepository = helpRequestRepository;
        this.labSessionRepository = labSessionRepository;
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
    }

    /** The TA queue view: every open request, first come first served. */
    @Transactional(readOnly = true)
    public List<HelpRequest> viewQueue(Long sessionId, String taEmail) {
        User ta = findUser(taEmail);
        requireStaffOf(findSession(sessionId), ta);
        return helpRequestRepository
                .findBySessionIdAndStatusInOrderByTimeJoinedAsc(sessionId, OPEN_STATUSES);
    }

    @Transactional
    public HelpRequest claimNext(Long sessionId, String taEmail) {
        User ta = findUser(taEmail);
        requireStaffOf(findSession(sessionId), ta);
        HelpRequest next = helpRequestRepository
                .findFirstBySessionIdAndStatusOrderByTimeJoinedAsc(sessionId, RequestStatus.WAITING)
                .orElseThrow(() -> new RequestNotFoundException("No students are waiting"));

        next.markClaimed(ta);
        return changeStatus(next, RequestStatus.CLAIMED, ta, CLAIM_REASON);
    }

    @Transactional
    public HelpRequest resolve(Long requestId, String taEmail) {
        User ta = findUser(taEmail);
        HelpRequest request = findRequest(requestId);
        requireStatus(request, RequestStatus.CLAIMED);
        requireClaimer(request, ta);

        request.markResolved();
        return changeStatus(request, RequestStatus.RESOLVED, ta, RESOLVE_REASON);
    }

    @Transactional
    public HelpRequest defer(Long requestId, String taEmail, String reason) {
        User ta = findUser(taEmail);
        HelpRequest request = findRequest(requestId);
        requireStatus(request, RequestStatus.CLAIMED);
        requireClaimer(request, ta);

        return changeStatus(request, RequestStatus.DEFERRED, ta,
                reasonOrDefault(reason, DEFAULT_DEFER_REASON));
    }

    /** Any TA of the session may return a request, since the claimer may have left. */
    @Transactional
    public HelpRequest returnToQueue(Long requestId, String taEmail, String reason) {
        User ta = findUser(taEmail);
        HelpRequest request = findRequest(requestId);
        requireStatus(request, RequestStatus.CLAIMED, RequestStatus.DEFERRED);
        requireStaffOf(request.getSession(), ta);

        request.releaseClaim();
        return changeStatus(request, RequestStatus.WAITING, ta,
                reasonOrDefault(reason, DEFAULT_RETURN_REASON));
    }

    /**
     * SCRUM-11: answers a whole cluster at once. The TA sends the ids of the requests
     * they saw in the cluster (clusters are not stored). Every request is checked
     * before any is changed, so either all are resolved or none are.
     */
    @Transactional
    public List<HelpRequest> broadcastAnswer(Long sessionId, List<Long> requestIds,
            String answer, String taEmail) {
        User ta = findUser(taEmail);
        requireStaffOf(findSession(sessionId), ta);
        requireValidAnswer(answer);
        List<HelpRequest> cluster = findAnswerableRequests(sessionId, requestIds, ta);

        String reason = BROADCAST_PREFIX + answer.trim();
        return cluster.stream()
                .map(request -> resolveByBroadcast(request, ta, reason))
                .toList();
    }

    private List<HelpRequest> findAnswerableRequests(Long sessionId, List<Long> requestIds, User ta) {
        if (requestIds == null || requestIds.isEmpty()) {
            throw new InvalidRequestException("Choose at least one request to answer");
        }
        List<HelpRequest> cluster = requestIds.stream().distinct().map(this::findRequest).toList();
        for (HelpRequest request : cluster) {
            if (!Objects.equals(request.getSession().getId(), sessionId)) {
                throw new InvalidRequestException(
                        "Request " + request.getId() + " is not in this lab session");
            }
            if (!isAnswerableBy(request, ta)) {
                throw new InvalidStatusChangeException("Request " + request.getId() + " is "
                        + request.getStatus() + " and cannot get a broadcast answer");
            }
        }
        return cluster;
    }

    /** Waiting requests, or ones this TA already claimed (never another TA's claim). */
    private boolean isAnswerableBy(HelpRequest request, User ta) {
        return request.isWaiting()
                || (request.getStatus() == RequestStatus.CLAIMED && request.isClaimedBy(ta));
    }

    private HelpRequest resolveByBroadcast(HelpRequest request, User ta, String reason) {
        if (request.isWaiting()) {
            request.markClaimed(ta); // records which TA helped
        }
        request.markResolved();
        return changeStatus(request, RequestStatus.RESOLVED, ta, reason);
    }

    private void requireValidAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            throw new InvalidRequestException("The answer cannot be empty");
        }
        if (answer.trim().length() > MAX_ANSWER_LENGTH) {
            throw new InvalidRequestException(
                    "The answer must be at most " + MAX_ANSWER_LENGTH + " characters");
        }
    }

    /** The one place a TA action changes a status, so none can skip the audit log. */
    private HelpRequest changeStatus(HelpRequest request, RequestStatus newStatus,
            User ta, String reason) {
        RequestStatus oldStatus = request.getStatus();
        request.changeStatus(newStatus);
        HelpRequest saved = helpRequestRepository.save(request);
        auditLogger.record(saved, oldStatus, newStatus, ta, reason);
        return saved;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("No account for " + email));
    }

    private LabSession findSession(Long sessionId) {
        return labSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RequestNotFoundException(
                        "No lab session with id " + sessionId));
    }

    private HelpRequest findRequest(Long requestId) {
        return helpRequestRepository.findById(requestId)
                .orElseThrow(() -> new RequestNotFoundException(
                        "No help request with id " + requestId));
    }

    /** Staff = a TA assigned to the session, or the course's instructor. */
    private void requireStaffOf(LabSession session, User user) {
        boolean assignedTa = session.getTas().stream()
                .anyMatch(ta -> Objects.equals(ta.getId(), user.getId()));
        boolean instructor = Objects.equals(
                session.getCourse().getInstructor().getId(), user.getId());
        if (!user.isStaff() || !(assignedTa || instructor)) {
            throw new TaActionNotAllowedException("You are not a TA for this lab session");
        }
    }

    private void requireClaimer(HelpRequest request, User ta) {
        if (!request.isClaimedBy(ta)) {
            throw new TaActionNotAllowedException("Only the TA who claimed this request can do that");
        }
    }

    private void requireStatus(HelpRequest request, RequestStatus... allowed) {
        if (!List.of(allowed).contains(request.getStatus())) {
            throw new InvalidStatusChangeException("This request is " + request.getStatus()
                    + "; expected " + List.of(allowed));
        }
    }

    private String reasonOrDefault(String reason, String defaultReason) {
        return (reason == null || reason.isBlank()) ? defaultReason : reason.trim();
    }
}
