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
import org.hibernate.annotations.Immutable;

/**
 * One recorded status change of a help request. Maps to audit_log.
 * Append-only (NFR5): there are no setters, and @Immutable stops
 * Hibernate from ever updating a saved row.
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private HelpRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 20)
    private RequestStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private RequestStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(length = 255)
    private String reason;

    /** Required by JPA; not for use in application code. */
    protected AuditLog() {
    }

    public AuditLog(HelpRequest request, RequestStatus oldStatus,
            RequestStatus newStatus, User changedBy, String reason) {
        this.request = request;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.reason = reason;
        this.changedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public HelpRequest getRequest() {
        return request;
    }

    public RequestStatus getOldStatus() {
        return oldStatus;
    }

    public RequestStatus getNewStatus() {
        return newStatus;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "AuditLog{request=" + (request == null ? null : request.getId())
                + ", " + oldStatus + " -> " + newStatus + "}";
    }
}
