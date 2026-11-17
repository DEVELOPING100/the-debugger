package ca.unb.labline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.unb.labline.model.Category;
import ca.unb.labline.model.Course;
import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.RequestStatus;
import ca.unb.labline.model.Role;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.HelpRequestRepository;
import ca.unb.labline.repository.LabSessionRepository;
import ca.unb.labline.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** SCRUM-7 unit tests. All repositories and the AuditLogger are mocks. */
@ExtendWith(MockitoExtension.class)
class TaQueueServiceTest {

    private static final Long SESSION_ID = 20L;
    private static final Long REQUEST_ID = 100L;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private LabSessionRepository labSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogger auditLogger;

    private TaQueueService service;
    private User ta;
    private User otherTa;
    private User student;
    private LabSession session;
    private HelpRequest request;

    @BeforeEach
    void setUp() {
        service = new TaQueueService(helpRequestRepository, labSessionRepository,
                userRepository, auditLogger);

        User instructor = withId(new User("test.prof@unb.ca", "Test Prof", "HASH"), 9L);
        instructor.changeRole(Role.INSTRUCTOR);
        ta = withId(new User("test.ta@unb.ca", "Test TA", "HASH"), 5L);
        ta.changeRole(Role.TA);
        otherTa = withId(new User("other.ta@unb.ca", "Other TA", "HASH"), 6L);
        otherTa.changeRole(Role.TA);
        student = withId(new User("test.student@unb.ca", "Test Student", "HASH"), 1L);

        Course course = withId(new Course("CS2043", "Software Engineering I", instructor), 10L);
        session = withId(new LabSession(course, "Lab A", "ITC 315",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), "TEST01"), SESSION_ID);
        session.assignTa(ta);
        session.assignTa(otherTa);
        Category debugging = withId(new Category(course, "Debugging"), 30L);

        request = withId(new HelpRequest(session, student, debugging,
                "NullPointerException in my loop", "Row 3 Seat 4"), REQUEST_ID);
    }

    // ---- claim next ----

    @Test
    void taClaimsOldestWaitingRequestAndItIsAudited() {
        givenLoggedIn(ta);
        givenSessionExists();
        when(helpRequestRepository.findFirstBySessionIdAndStatusOrderByTimeJoinedAsc(
                SESSION_ID, RequestStatus.WAITING)).thenReturn(Optional.of(request));
        givenSaveReturnsRequest();

        HelpRequest claimed = service.claimNext(SESSION_ID, ta.getEmail());

        assertEquals(RequestStatus.CLAIMED, claimed.getStatus());
        assertEquals(ta, claimed.getClaimedBy());
        assertNotNull(claimed.getTimeClaimed());
        verify(auditLogger).record(request, RequestStatus.WAITING, RequestStatus.CLAIMED,
                ta, "Claimed by TA");
    }

    @Test
    void claimNextOnEmptyQueueIsReportedAndNothingIsAudited() {
        givenLoggedIn(ta);
        givenSessionExists();
        when(helpRequestRepository.findFirstBySessionIdAndStatusOrderByTimeJoinedAsc(
                SESSION_ID, RequestStatus.WAITING)).thenReturn(Optional.empty());

        assertThrows(RequestNotFoundException.class,
                () -> service.claimNext(SESSION_ID, ta.getEmail()));
        verifyNothingAudited();
    }

    @Test
    void studentCannotClaimRequests() {
        givenLoggedIn(student);
        givenSessionExists();

        assertThrows(TaActionNotAllowedException.class,
                () -> service.claimNext(SESSION_ID, student.getEmail()));
        verifyNothingAudited();
    }

    @Test
    void taNotAssignedToSessionCannotClaim() {
        User outsideTa = withId(new User("outside.ta@unb.ca", "Outside TA", "HASH"), 7L);
        outsideTa.changeRole(Role.TA);
        givenLoggedIn(outsideTa);
        givenSessionExists();

        assertThrows(TaActionNotAllowedException.class,
                () -> service.claimNext(SESSION_ID, outsideTa.getEmail()));
        verifyNothingAudited();
    }

    // ---- resolve ----

    @Test
    void claimingTaCanResolveAndItIsAudited() {
        givenClaimedBy(ta);
        givenLoggedIn(ta);
        givenRequestExists();
        givenSaveReturnsRequest();

        HelpRequest resolved = service.resolve(REQUEST_ID, ta.getEmail());

        assertEquals(RequestStatus.RESOLVED, resolved.getStatus());
        assertNotNull(resolved.getTimeResolved());
        verify(auditLogger).record(request, RequestStatus.CLAIMED, RequestStatus.RESOLVED,
                ta, "Resolved by TA");
    }

    @Test
    void anotherTaCannotResolveSomeoneElsesClaim() {
        givenClaimedBy(ta);
        givenLoggedIn(otherTa);
        givenRequestExists();

        assertThrows(TaActionNotAllowedException.class,
                () -> service.resolve(REQUEST_ID, otherTa.getEmail()));
        verifyNothingAudited();
    }

    @Test
    void waitingRequestCannotBeResolvedWithoutClaimingFirst() {
        givenLoggedIn(ta);
        givenRequestExists();

        assertThrows(InvalidStatusChangeException.class,
                () -> service.resolve(REQUEST_ID, ta.getEmail()));
        verifyNothingAudited();
    }

    // ---- defer ----

    @Test
    void claimingTaCanDeferWithAReason() {
        givenClaimedBy(ta);
        givenLoggedIn(ta);
        givenRequestExists();
        givenSaveReturnsRequest();

        HelpRequest deferred = service.defer(REQUEST_ID, ta.getEmail(), "  Student not at seat ");

        assertEquals(RequestStatus.DEFERRED, deferred.getStatus());
        verify(auditLogger).record(request, RequestStatus.CLAIMED, RequestStatus.DEFERRED,
                ta, "Student not at seat");
    }

    @Test
    void deferWithoutReasonUsesDefaultReason() {
        givenClaimedBy(ta);
        givenLoggedIn(ta);
        givenRequestExists();
        givenSaveReturnsRequest();

        service.defer(REQUEST_ID, ta.getEmail(), null);

        verify(auditLogger).record(request, RequestStatus.CLAIMED, RequestStatus.DEFERRED,
                ta, "Deferred by TA");
    }

    // ---- return to queue ----

    @Test
    void deferredRequestReturnsToQueueKeepingItsPlace() {
        givenClaimedBy(ta);
        request.changeStatus(RequestStatus.DEFERRED);
        LocalDateTime originalJoinTime = request.getTimeJoined();
        givenLoggedIn(otherTa);
        givenRequestExists();
        givenSaveReturnsRequest();

        HelpRequest returned = service.returnToQueue(REQUEST_ID, otherTa.getEmail(), null);

        assertEquals(RequestStatus.WAITING, returned.getStatus());
        assertNull(returned.getClaimedBy());
        assertEquals(originalJoinTime, returned.getTimeJoined());
        verify(auditLogger).record(request, RequestStatus.DEFERRED, RequestStatus.WAITING,
                otherTa, "Returned to queue by TA");
    }

    @Test
    void resolvedRequestCannotBeReturnedToQueue() {
        givenClaimedBy(ta);
        request.changeStatus(RequestStatus.RESOLVED);
        givenLoggedIn(ta);
        givenRequestExists();

        assertThrows(InvalidStatusChangeException.class,
                () -> service.returnToQueue(REQUEST_ID, ta.getEmail(), null));
        verifyNothingAudited();
    }

    // ---- queue view ----

    @Test
    void taSeesOpenRequestsOfTheSession() {
        givenLoggedIn(ta);
        givenSessionExists();
        when(helpRequestRepository.findBySessionIdAndStatusInOrderByTimeJoinedAsc(
                eq(SESSION_ID), any())).thenReturn(List.of(request));

        List<HelpRequest> queue = service.viewQueue(SESSION_ID, ta.getEmail());

        assertEquals(List.of(request), queue);
    }

    // ---- helpers ----

    private void givenLoggedIn(User user) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private void givenSessionExists() {
        when(labSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    }

    private void givenRequestExists() {
        when(helpRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    }

    private void givenSaveReturnsRequest() {
        when(helpRequestRepository.save(request)).thenReturn(request);
    }

    private void givenClaimedBy(User claimer) {
        request.markClaimed(claimer);
        request.changeStatus(RequestStatus.CLAIMED);
    }

    private void verifyNothingAudited() {
        verify(auditLogger, never()).record(any(), any(), any(), any(), any());
    }

    /** Entities only get ids from the database, so tests set them directly. */
    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
