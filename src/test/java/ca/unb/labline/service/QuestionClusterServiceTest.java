package ca.unb.labline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.unb.labline.model.Category;
import ca.unb.labline.model.Course;
import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.RequestStatus;
import ca.unb.labline.model.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** SCRUM-10 unit tests. The queue and the grouper are mocks. */
@ExtendWith(MockitoExtension.class)
class QuestionClusterServiceTest {

    private static final Long SESSION_ID = 20L;
    private static final String TA_EMAIL = "test.ta@unb.ca";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 14, 0);

    @Mock
    private TaQueueService taQueueService;

    @Mock
    private QuestionGrouper questionGrouper;

    @Test
    void onlyWaitingQuestionsAreGrouped() {
        HelpRequest waiting1 = request(1L, NOW);
        HelpRequest claimed = request(2L, NOW.plusMinutes(1));
        claimed.changeStatus(RequestStatus.CLAIMED);
        HelpRequest waiting2 = request(3L, NOW.plusMinutes(2));
        when(taQueueService.viewQueue(SESSION_ID, TA_EMAIL))
                .thenReturn(List.of(waiting1, claimed, waiting2));
        QuestionCluster cluster = new QuestionCluster("null object", List.of(waiting1, waiting2));
        when(questionGrouper.group(List.of(waiting1, waiting2))).thenReturn(List.of(cluster));

        List<QuestionCluster> clusters = service().clustersFor(SESSION_ID, TA_EMAIL);

        assertEquals(List.of(cluster), clusters);
    }

    @Test
    void clusterWithLongestWaitingStudentComesFirst() {
        HelpRequest oldest = request(1L, NOW);
        HelpRequest newer = request(2L, NOW.plusMinutes(5));
        HelpRequest newest = request(3L, NOW.plusMinutes(9));
        when(taQueueService.viewQueue(SESSION_ID, TA_EMAIL)).thenReturn(List.of(oldest, newer, newest));
        QuestionCluster newCluster = new QuestionCluster("loop", List.of(newer, newest));
        QuestionCluster oldCluster = QuestionCluster.single(oldest);
        when(questionGrouper.group(List.of(oldest, newer, newest)))
                .thenReturn(List.of(newCluster, oldCluster));

        List<QuestionCluster> clusters = service().clustersFor(SESSION_ID, TA_EMAIL);

        assertEquals(List.of(oldCluster, newCluster), clusters);
    }

    @Test
    void nonStaffCannotSeeClustersAndNothingIsGrouped() {
        when(taQueueService.viewQueue(SESSION_ID, TA_EMAIL))
                .thenThrow(new TaActionNotAllowedException("You are not a TA for this lab session"));

        assertThrows(TaActionNotAllowedException.class,
                () -> service().clustersFor(SESSION_ID, TA_EMAIL));
        verifyNoInteractions(questionGrouper);
    }

    // ---- helpers ----

    private QuestionClusterService service() {
        return new QuestionClusterService(taQueueService, questionGrouper);
    }

    private static HelpRequest request(Long id, LocalDateTime joined) {
        User instructor = new User("test.prof@unb.ca", "Test Prof", "HASH");
        User student = new User("student" + id + "@unb.ca", "Student " + id, "HASH");
        Course course = new Course("CS2043", "Software Engineering I", instructor);
        LabSession session = new LabSession(course, "Lab A", "ITC 315",
                NOW, NOW.plusHours(2), "TEST01");
        HelpRequest request = new HelpRequest(session, student, new Category(course, "Debugging"),
                "Question " + id, "Row 1");
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "timeJoined", joined);
        return request;
    }
}
