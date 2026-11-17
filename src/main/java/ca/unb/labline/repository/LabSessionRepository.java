package ca.unb.labline.repository;

import ca.unb.labline.model.LabSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabSessionRepository extends JpaRepository<LabSession, Long> {

    Optional<LabSession> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);
}
