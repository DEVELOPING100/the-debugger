package ca.unb.labline.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A student's place in a session's queue. Maps to the help_requests table.
 * Business rules (who may edit, which status changes are allowed) live in
 * the service layer; this class only holds the data.
 */
@Entity
@Table(name = "help_requests")
public class HelpRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LabSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, length = 20)
    private String seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_by")
    private User claimedBy;

    @Column(name = "time_joined", nullable = false)
    private LocalDateTime timeJoined;

    @Column(name = "time_claimed")
    private LocalDateTime timeClaimed;

    @Column(name = "time_resolved")
    private LocalDateTime timeResolved;

    /** SCRUM-14: the student's 1-5 rating of the help they got (null until rated). */
    @Column(name = "rating")
    private Integer rating;

    /** SCRUM-14: optional comment that goes with the rating. */
    @Column(name = "rating_comment", length = 300)
    private String ratingComment;

    /** Required by JPA; not for use in application code. */
    protected HelpRequest() {
    }

    /** Creates a new request; it starts WAITING and is timestamped now. */
    public HelpRequest(LabSession session, User student, Category category,
            String description, String seat) {
        this.session = session;
        this.student = student;
        this.category = category;
        this.description = description;
        this.seat = seat;
        this.status = RequestStatus.WAITING;
        this.timeJoined = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LabSession getSession() {
        return session;
    }

    public User getStudent() {
        return student;
    }

    public Category getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getSeat() {
        return seat;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public User getClaimedBy() {
        return claimedBy;
    }

    public LocalDateTime getTimeJoined() {
        return timeJoined;
    }

    public LocalDateTime getTimeClaimed() {
        return timeClaimed;
    }

    public LocalDateTime getTimeResolved() {
        return timeResolved;
    }

    public Integer getRating() {
        return rating;
    }

    public String getRatingComment() {
        return ratingComment;
    }

    public boolean isWaiting() {
        return status == RequestStatus.WAITING;
    }

    public boolean isRated() {
        return rating != null;
    }

    public boolean isOwnedBy(User user) {
        return user != null && student.getId() != null
                && Objects.equals(student.getId(), user.getId());
    }

    /** Replaces the editable details (used by SCRUM-24 edit). */
    public void updateDetails(Category newCategory, String newDescription, String newSeat) {
        this.category = newCategory;
        this.description = newDescription;
        this.seat = newSeat;
    }

    /** Sets the new status; the service checks the change is allowed first. */
    public void changeStatus(RequestStatus newStatus) {
        this.status = newStatus;
    }

    public void markClaimed(User ta) {
        this.claimedBy = ta;
        this.timeClaimed = LocalDateTime.now();
    }

    public void markResolved() {
        this.timeResolved = LocalDateTime.now();
    }

    /** Records the student's rating; the service checks the rules first. */
    public void rate(int newRating, String newComment) {
        this.rating = newRating;
        this.ratingComment = newComment;
    }

    @Override
    public String toString() {
        return "HelpRequest{id=" + id + ", status=" + status + ", seat=" + seat + "}";
    }
}
