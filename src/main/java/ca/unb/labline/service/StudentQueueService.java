package ca.unb.labline.service;

import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.RequestStatus;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.CategoryRepository;
import ca.unb.labline.repository.HelpRequestRepository;
import ca.unb.labline.repository.LabSessionRepository;
import ca.unb.labline.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the student page reads: joining a session by its code (FR4) and
 * seeing my own request with my place in line (FR6).
 * Read-only: it never changes a request.
 */
@Service
public class StudentQueueService {

    private static final List<RequestStatus> OPEN_STATUSES =
            List.of(RequestStatus.WAITING, RequestStatus.CLAIMED, RequestStatus.DEFERRED);
    private static final List<RequestStatus> WAITING_ONLY = List.of(RequestStatus.WAITING);
    private static final int BEING_HELPED = 0;

    private final LabSessionRepository labSessionRepository;
    private final CategoryRepository categoryRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final UserRepository userRepository;

    public StudentQueueService(LabSessionRepository labSessionRepository,
            CategoryRepository categoryRepository, HelpRequestRepository helpRequestRepository,
            UserRepository userRepository) {
        this.labSessionRepository = labSessionRepository;
        this.categoryRepository = categoryRepository;
        this.helpRequestRepository = helpRequestRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public SessionInfo joinSession(String joinCode) {
        LabSession session = labSessionRepository.findByJoinCode(normalizeCode(joinCode))
                .orElseThrow(() -> new RequestNotFoundException(
                        "No lab session with join code " + joinCode));
        Long courseId = session.getCourse().getId();
        List<SessionInfo.CategoryInfo> categories = categoryRepository
                .findByCourseIdAndActiveTrue(courseId).stream()
                .map(category -> new SessionInfo.CategoryInfo(category.getId(), category.getName()))
                .toList();
        return new SessionInfo(session.getId(), session.getName(), session.getRoom(),
                session.getCourse().getCode(), session.isQueueOpen(), categories);
    }

    @Transactional(readOnly = true)
    public Optional<MyRequestStatus> myOpenRequest(Long sessionId, String studentEmail) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new UserNotFoundException("No account for " + studentEmail));
        List<HelpRequest> waitingLine = helpRequestRepository
                .findBySessionIdAndStatusInOrderByTimeJoinedAsc(sessionId, WAITING_ONLY);
        return helpRequestRepository
                .findFirstBySessionIdAndStudentIdAndStatusIn(sessionId, student.getId(), OPEN_STATUSES)
                .map(request -> toStatus(request, waitingLine));
    }

    private MyRequestStatus toStatus(HelpRequest request, List<HelpRequest> waitingLine) {
        return new MyRequestStatus(request.getId(), request.getStatus(),
                request.getCategory().getId(), request.getCategory().getName(),
                request.getDescription(), request.getSeat(),
                positionOf(request, waitingLine), waitingLine.size());
    }

    /** 1 = next in line; 0 = a TA is helping; null = deferred (not in line). */
    private Integer positionOf(HelpRequest request, List<HelpRequest> waitingLine) {
        if (request.getStatus() == RequestStatus.CLAIMED) {
            return BEING_HELPED;
        }
        if (!request.isWaiting()) {
            return null;
        }
        for (int index = 0; index < waitingLine.size(); index++) {
            if (waitingLine.get(index).getId().equals(request.getId())) {
                return index + 1;
            }
        }
        return null;
    }

    private String normalizeCode(String joinCode) {
        return joinCode == null ? "" : joinCode.trim().toUpperCase(Locale.ROOT);
    }
}
