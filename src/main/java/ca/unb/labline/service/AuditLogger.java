package ca.unb.labline.service;

import ca.unb.labline.model.AuditLog;
import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.RequestStatus;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.AuditLogRepository;
import org.springframework.stereotype.Component;

/**
 * Records every status change of a help request (FR11).
 * Its only job is writing audit rows (SRP); any service that changes a
 * status (cancel, claim, resolve, defer, return) calls record().
 */
@Component
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;

    public AuditLogger(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public AuditLog record(HelpRequest request, RequestStatus oldStatus,
            RequestStatus newStatus, User changedBy, String reason) {
        return auditLogRepository.save(
                new AuditLog(request, oldStatus, newStatus, changedBy, reason));
    }
}
