package ca.unb.labline.service;

import ca.unb.labline.exception.DuplicateOpenRequestException;
import ca.unb.labline.model.Category;
import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.Category;
import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.RequestStatus;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.CategoryRepository;
import ca.unb.labline.repository.HelpRequestRepository;
import ca.unb.labline.repository.LabSessionRepository;
import ca.unb.labline.repository.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** Business rules for help requests (the queue entries students submit). */
@Service
public class HelpRequestService {

    /** A request is "open" until it is resolved or cancelled. */
    static final List<RequestStatus> OPEN_STATUSES =
            List.of(RequestStatus.WAITING, RequestStatus.CLAIMED, RequestStatus.DEFERRED);

    static final int MAX_DESCRIPTION_LENGTH = 300;
    static final int MAX_SEAT_LENGTH = 20;

    private final HelpRequestRepository helpRequestRepository;
    private final LabSessionRepository labSessionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public HelpRequestService(HelpRequestRepository helpRequestRepository,
            LabSessionRepository labSessionRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository) {
        this.helpRequestRepository = helpRequestRepository;
        this.labSessionRepository = labSessionRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * SCRUM-2: a student submits a help request to a session's queue.
     * A student may have only one open request per session.
     */
    public HelpRequest submitRequest(Long sessionId, Long studentId, Long categoryId,
            String description, String seat) {
        requireText(description, MAX_DESCRIPTION_LENGTH, "Description");
        requireText(seat, MAX_SEAT_LENGTH, "Seat");

        LabSession session = findSession(sessionId);
        requireQueueOpen(session);
        requireNoOpenRequest(sessionId, studentId);

        User student = findStudent(studentId);
        Category category = findCategory(categoryId);

        HelpRequest request = new HelpRequest(
                session, student, category, description.trim(), seat.trim());
        return helpRequestRepository.save(request);
    }

    private void requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must be at most " + maxLength + " characters");
        }
    }

    private void requireQueueOpen(LabSession session) {
        if (!session.isQueueOpen()) {
            throw new IllegalStateException("This session's queue is closed");
        }
    }

    private void requireNoOpenRequest(Long sessionId, Long studentId) {
        if (helpRequestRepository.existsBySessionIdAndStudentIdAndStatusIn(
                sessionId, studentId, OPEN_STATUSES)) {
            throw new DuplicateOpenRequestException(studentId, sessionId);
        }
    }

    private LabSession findSession(Long sessionId) {
        return labSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    }

    private User findStudent(Long studentId) {
        return userRepository.findById(studentId)
                .orElseThrow(() -> new NoSuchElementException("Student not found: " + studentId));
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NoSuchElementException("Category not found: " + categoryId));
import ca.unb.labline.repository.UserRepository;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's actions on their own help request.
 * SCRUM-24: edit or cancel, only by the owner and only while WAITING.
 * (SCRUM-2 "submit a request" can be added to this class.)
 */
@Service
public class HelpRequestService {

    private static final int MAX_SEAT_LENGTH = 20;
    private static final String CANCEL_REASON = "Cancelled by student";

    private final HelpRequestRepository helpRequestRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AuditLogger auditLogger;
    private final int maxDescriptionLength;

    public HelpRequestService(HelpRequestRepository helpRequestRepository,
            CategoryRepository categoryRepository, UserRepository userRepository,
            AuditLogger auditLogger,
            @Value("${labline.queue.max-description-length}") int maxDescriptionLength) {
        this.helpRequestRepository = helpRequestRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
        this.maxDescriptionLength = maxDescriptionLength;
    }

    @Transactional
    public HelpRequest editRequest(Long requestId, String studentEmail,
            Long categoryId, String description, String seat) {
        User student = findUser(studentEmail);
        HelpRequest request = findRequest(requestId);
        requireOwner(request, student);
        requireWaiting(request);

        Category category = findCategoryForSameCourse(categoryId, request);
        String cleanDescription = requireValidDescription(description);
        String cleanSeat = requireValidSeat(seat);

        request.updateDetails(category, cleanDescription, cleanSeat);
        return helpRequestRepository.save(request);
    }

    @Transactional
    public HelpRequest cancelRequest(Long requestId, String studentEmail) {
        User student = findUser(studentEmail);
        HelpRequest request = findRequest(requestId);
        requireOwner(request, student);
        requireWaiting(request);

        RequestStatus oldStatus = request.getStatus();
        request.changeStatus(RequestStatus.CANCELLED);
        HelpRequest saved = helpRequestRepository.save(request);
        auditLogger.record(saved, oldStatus, RequestStatus.CANCELLED, student, CANCEL_REASON);
        return saved;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("No account for " + email));
    }

    private HelpRequest findRequest(Long requestId) {
        return helpRequestRepository.findById(requestId)
                .orElseThrow(() -> new RequestNotFoundException(
                        "No help request with id " + requestId));
    }

    private void requireOwner(HelpRequest request, User user) {
        if (!request.isOwnedBy(user)) {
            throw new NotRequestOwnerException("You can only change your own request");
        }
    }

    private void requireWaiting(HelpRequest request) {
        if (!request.isWaiting()) {
            throw new RequestNotEditableException(
                    "This request is " + request.getStatus()
                    + "; it can only be changed while WAITING");
        }
    }

    private Category findCategoryForSameCourse(Long categoryId, HelpRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new InvalidRequestException("Unknown category"));
        Long requestCourseId = request.getSession().getCourse().getId();
        if (!category.isActive()
                || !Objects.equals(category.getCourse().getId(), requestCourseId)) {
            throw new InvalidRequestException("That category is not available for this lab");
        }
        return category;
    }

    private String requireValidDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new InvalidRequestException("Description is required");
        }
        String clean = description.trim();
        if (clean.length() > maxDescriptionLength) {
            throw new InvalidRequestException(
                    "Description must be at most " + maxDescriptionLength + " characters");
        }
        return clean;
    }

    private String requireValidSeat(String seat) {
        if (seat == null || seat.isBlank() || seat.trim().length() > MAX_SEAT_LENGTH) {
            throw new InvalidRequestException(
                    "Seat is required (at most " + MAX_SEAT_LENGTH + " characters)");
        }
        return seat.trim();
    }
}
