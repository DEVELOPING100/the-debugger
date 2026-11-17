package ca.unb.labline.service;

import ca.unb.labline.exception.DuplicateOpenRequestException;
import ca.unb.labline.model.Category;
import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.LabSession;
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
    }
}
