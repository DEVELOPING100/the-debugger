package ca.unb.labline.service;

import ca.unb.labline.model.Category;
import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.RequestStatus;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.CategoryRepository;
import ca.unb.labline.repository.HelpRequestRepository;
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
