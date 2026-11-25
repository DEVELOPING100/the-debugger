package ca.unb.labline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** SCRUM-24 (edit and cancel) unit tests. All repositories and the AuditLogger are mocks. */
@ExtendWith(MockitoExtension.class)
class HelpRequestEditCancelTest {

    private static final String OWNER_EMAIL = "test.student@unb.ca";
    private static final String OTHER_EMAIL = "other.student@unb.ca";

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private LabSessionRepository labSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogger auditLogger;

    private HelpRequestService service;
    private User owner;
    private User otherStudent;
    private Course course;
    private Category gitCategory;
    private HelpRequest request;

    @BeforeEach
    void setUp() {
        service = new HelpRequestService(helpRequestRepository, labSessionRepository,
                userRepository, categoryRepository, auditLogger, 300);

        User instructor = withId(new User("test.prof@unb.ca", "Test Prof", "HASH"), 9L);
        owner = withId(new User(OWNER_EMAIL, "Test Student", "HASH"), 1L);
        otherStudent = withId(new User(OTHER_EMAIL, "Other Student", "HASH"), 2L);

        course = withId(new Course("CS2043", "Software Engineering I", instructor), 10L);
        LabSession session = withId(new LabSession(course, "Lab A", "ITC 315",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), "TEST01"), 20L);
        Category debugging = withId(new Category(course, "Debugging"), 30L);
        gitCategory = withId(new Category(course, "Git"), 31L);

        request = withId(new HelpRequest(session, owner, debugging,
                "NullPointerException in my loop", "Row 3 Seat 4"), 100L);
    }

    @Test
    void ownerCanEditWaitingRequest() {
        givenLoggedIn(owner);
        when(helpRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(categoryRepository.findById(31L)).thenReturn(Optional.of(gitCategory));
        when(helpRequestRepository.save(request)).thenReturn(request);

        HelpRequest edited = service.editRequest(100L, OWNER_EMAIL, 31L,
                "  Merge conflict in Git  ", "Row 5 Seat 1");

        assertEquals("Git", edited.getCategory().getName());
        assertEquals("Merge conflict in Git", edited.getDescription());
        assertEquals("Row 5 Seat 1", edited.getSeat());
        assertEquals(RequestStatus.WAITING, edited.getStatus());
    }

    @Test
    void anotherStudentCannotEditTheRequest() {
        givenLoggedIn(otherStudent);
        when(helpRequestRepository.findById(100L)).thenReturn(Optional.of(request));

        assertThrows(NotRequestOwnerException.class,
                () -> service.editRequest(100L, OTHER_EMAIL, 31L, "Hijack", "Row 1"));
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void claimedRequestCannotBeEdited() {
        request.changeStatus(RequestStatus.CLAIMED);
        givenLoggedIn(owner);
        when(helpRequestRepository.findById(100L)).thenReturn(Optional.of(request));

        assertThrows(RequestNotEditableException.class,
                () -> service.editRequest(100L, OWNER_EMAIL, 31L, "New text", "Row 1"));
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void editRejectsDescriptionOverLimit() {
        givenLoggedIn(owner);
        when(helpRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(categoryRepository.findById(31L)).thenReturn(Optional.of(gitCategory));

        assertThrows(InvalidRequestException.class,
                () -> service.editRequest(100L, OWNER_EMAIL, 31L, "x".repeat(301), "Row 1"));
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void editRejectsCategoryFromAnotherCourse() {
        User otherProf = withId(new User("other.prof@unb.ca", "Other Prof", "HASH"), 8L);
        Course otherCourse = withId(new Course("CS1073", "Intro", otherProf), 11L);
        Category foreign = withId(new Category(otherCourse, "Loops"), 40L);
        givenLoggedIn(owner);
        when(helpRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(categoryRepository.findById(40L)).thenReturn(Optional.of(foreign));

        assertThrows(InvalidRequestException.class,
                () -> service.editRequest(100L, OWNER_EMAIL, 40L, "New text", "Row 1"));
    }

    @Test
    void ownerCanCancelWaitingRequestAndItIsAudited() {
        givenLoggedIn(owner);
        when(helpRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(helpRequestRepository.save(request)).thenReturn(request);

        HelpRequest cancelled = service.cancelRequest(100L, OWNER_EMAIL);

        assertEquals(RequestStatus.CANCELLED, cancelled.getStatus());
        verify(auditLogger).record(request, RequestStatus.WAITING, RequestStatus.CANCELLED,
                owner, "Cancelled by student");
    }

    @Test
    void resolvedRequestCannotBeCancelledAndNothingIsAudited() {
        request.changeStatus(RequestStatus.RESOLVED);
        givenLoggedIn(owner);
        when(helpRequestRepository.findById(100L)).thenReturn(Optional.of(request));

        assertThrows(RequestNotEditableException.class,
                () -> service.cancelRequest(100L, OWNER_EMAIL));
        verify(auditLogger, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void anotherStudentCannotCancelTheRequest() {
        givenLoggedIn(otherStudent);
        when(helpRequestRepository.findById(100L)).thenReturn(Optional.of(request));

        assertThrows(NotRequestOwnerException.class,
                () -> service.cancelRequest(100L, OTHER_EMAIL));
        verify(auditLogger, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void unknownRequestIsReported() {
        givenLoggedIn(owner);
        when(helpRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RequestNotFoundException.class,
                () -> service.cancelRequest(999L, OWNER_EMAIL));
    }

    private void givenLoggedIn(User user) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    /** Entities only get ids from the database, so tests set them directly. */
    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
