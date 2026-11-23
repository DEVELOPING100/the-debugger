package ca.unb.labline.repository;

import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.RequestStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, Long> {

    /** The queue for a session in first-come, first-served order. */
    List<HelpRequest> findBySessionIdAndStatusInOrderByTimeJoinedAsc(
            Long sessionId, Collection<RequestStatus> statuses);

    /** True if the student already has an open request in this session. */
    boolean existsBySessionIdAndStudentIdAndStatusIn(
            Long sessionId, Long studentId, Collection<RequestStatus> statuses);
}
