package ca.unb.labline.repository;

import ca.unb.labline.model.FaqEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqEntryRepository extends JpaRepository<FaqEntry, Long> {

    /** A session's FAQ board, oldest entry first. */
    List<FaqEntry> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
