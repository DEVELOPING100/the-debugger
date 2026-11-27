package ca.unb.labline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ca.unb.labline.model.Category;
import ca.unb.labline.model.Course;
import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.RequestStatus;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.CategoryRepository;
import ca.unb.labline.repository.HelpRequestRepository;
import ca.unb.labline.repository.LabSessionRepository;
import ca.unb.labline.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** FR4 / FR6 unit tests: joining with a code and seeing my place in line. */
@ExtendWith(MockitoExtension.class)
class StudentQueueServiceTest {

    private static final String ME = "test.student@unb.ca";

    @Mock
    private LabSessionRepository labSessionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StudentQueueService service;

    private User me;
    private LabSession session;
    private Category debugging;

    @BeforeEach
    void setUp() {
        User instructor = withId(new User("test.prof@unb.ca", "Test Prof", "HASH"), 9L);
        me = withId(new User(ME, "Test Student", "HASH"), 1L);
        Course course = withId(new Course("CS2043", "Software Engineering I", instructor), 10L);
        session = withId(new LabSession(course, "Lab A", "ITC 315",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), "TEST01"), 20L);
        session.openQueue();
        debugging = withId(new Category(course, "Debugging"), 30L);
    }

    @Test
    void joinSessionReturnsSessionWithItsActiveCategories() {
        when(labSessionRepository.findByJoinCode("TEST01")).thenReturn(Optional.of(session));
        when(categoryRepository.findByCourseIdAndActiveTrue(10L)).thenReturn(List.of(debugging));

        SessionInfo info = service.joinSession("  test01 ");

        assertEquals(20L, info.id());
        assertEquals("CS2043", info.courseCode());
        assertTrue(info.queueOpen());
        assertEquals("Debugging", info.categories().get(0).name());
    }

    @Test
    void joinSessionRejectsUnknownCode() {
        when(labSessionRepository.findByJoinCode("NOPE")).thenReturn(Optional.empty());

        assertThrows(RequestNotFoundException.class, () -> service.joinSession("NOPE"));
    }

    @Test
    void waitingRequestShowsPlaceInLine() {
        User other = withId(new User("other@unb.ca", "Other", "HASH"), 2L);
        HelpRequest earlier = withId(new HelpRequest(session, other, debugging, "Loop", "A1"), 100L);
        HelpRequest mine = withId(new HelpRequest(session, me, debugging, "NPE", "B2"), 101L);
        givenLoggedIn();
        when(helpRequestRepository.findBySessionIdAndStatusInOrderByTimeJoinedAsc(eq(20L), anyCollection()))
                .thenReturn(List.of(earlier, mine));
        when(helpRequestRepository.findFirstBySessionIdAndStudentIdAndStatusIn(eq(20L), eq(1L), anyCollection()))
                .thenReturn(Optional.of(mine));

        MyRequestStatus status = service.myOpenRequest(20L, ME).orElseThrow();

        assertEquals(2, status.position());
        assertEquals(2, status.waitingCount());
        assertEquals(RequestStatus.WAITING, status.status());
    }

    @Test
    void claimedRequestShowsBeingHelped() {
        HelpRequest mine = withId(new HelpRequest(session, me, debugging, "NPE", "B2"), 101L);
        mine.changeStatus(RequestStatus.CLAIMED);
        givenLoggedIn();
        when(helpRequestRepository.findBySessionIdAndStatusInOrderByTimeJoinedAsc(eq(20L), anyCollection()))
                .thenReturn(List.of());
        when(helpRequestRepository.findFirstBySessionIdAndStudentIdAndStatusIn(eq(20L), eq(1L), anyCollection()))
                .thenReturn(Optional.of(mine));

        MyRequestStatus status = service.myOpenRequest(20L, ME).orElseThrow();

        assertEquals(0, status.position());
    }

    @Test
    void deferredRequestHasNoPlaceInLine() {
        HelpRequest mine = withId(new HelpRequest(session, me, debugging, "NPE", "B2"), 101L);
        mine.changeStatus(RequestStatus.DEFERRED);
        givenLoggedIn();
        when(helpRequestRepository.findBySessionIdAndStatusInOrderByTimeJoinedAsc(eq(20L), anyCollection()))
                .thenReturn(List.of());
        when(helpRequestRepository.findFirstBySessionIdAndStudentIdAndStatusIn(eq(20L), eq(1L), anyCollection()))
                .thenReturn(Optional.of(mine));

        assertNull(service.myOpenRequest(20L, ME).orElseThrow().position());
    }

    @Test
    void noOpenRequestGivesEmptyResult() {
        givenLoggedIn();
        when(helpRequestRepository.findBySessionIdAndStatusInOrderByTimeJoinedAsc(eq(20L), anyCollection()))
                .thenReturn(List.of());
        when(helpRequestRepository.findFirstBySessionIdAndStudentIdAndStatusIn(any(), any(), anyCollection()))
                .thenReturn(Optional.empty());

        assertFalse(service.myOpenRequest(20L, ME).isPresent());
    }

    private void givenLoggedIn() {
        when(userRepository.findByEmail(ME)).thenReturn(Optional.of(me));
    }

    /** Entities only get ids from the database, so tests set them directly. */
    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
