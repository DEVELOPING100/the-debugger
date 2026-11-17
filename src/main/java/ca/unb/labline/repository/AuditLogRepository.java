package ca.unb.labline.repository;

import ca.unb.labline.model.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByRequestIdOrderByChangedAtAsc(Long requestId);
}
