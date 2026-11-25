package ca.unb.labline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.unb.labline.exception.DuplicateOpenRequestException;
import ca.unb.labline.model.Category;
import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.RequestStatus;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.CategoryRepository;
import ca.unb.labline.repository.HelpRequestRepository;
import ca.unb.labline.repository.LabSessionRepository;
import ca.unb.labline.repository.UserRepository;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HelpRequestServiceTest {

    private static final Long SESSION_ID = 1L;
    private static final Long STUDENT_ID = 2L;
    private static final Long CATEGORY_ID = 3L;
    private static final Long REQUEST_ID = 4L;

    @Mock
    private HelpRequestRepository helpRequestRepository;
    @Mock
    private LabSessionRepository labSessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private LabSession session;
    @Mock
    private User student;
    @Mock
    private Category category;

    @Mock
    private AuditLogger auditLogger;

    private HelpRequestService service;

    @BeforeEach
    void setUp() {
        service = new HelpRequestService(helpRequestRepository, labSessionRepository,
                userRepository, categoryRepository, auditLogger,
                HelpRequestService.MAX_DESCRIPTION_LENGTH);
    }

    // ---------- SCRUM-2: submitRequest ----------

    @Test
    void submitRequest_savesWaitingRequestWhenAllRulesPass() {
        givenOpenSession();
        when(helpRequestRepository.existsBySessionIdAndStudentIdAndStatusIn(
                SESSION_ID, STUDENT_ID, HelpRequestService.OPEN_STATUSES)).thenReturn(false);
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(helpRequestRepository.save(any(HelpRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HelpRequest result = service.submitRequest(
                SESSION_ID, STUDENT_ID, CATEGORY_ID, "  Loop never ends  ", " A12 ");

        assertEquals(RequestStatus.WAITING, result.getStatus());
        assertSame(student, result.getStudent());
        assertSame(session, result.getSession());
        assertEquals("Loop never ends", result.getDescription());
        assertEquals("A12", result.getSeat());
    }

    @Test
    void submitRequest_throwsWhenStudentAlreadyHasOpenRequest() {
        givenOpenSession();
        when(helpRequestRepository.existsBySessionIdAndStudentIdAndStatusIn(
                SESSION_ID, STUDENT_ID, HelpRequestService.OPEN_STATUSES)).thenReturn(true);

        assertThrows(DuplicateOpenRequestException.class, () ->
                service.submitRequest(SESSION_ID, STUDENT_ID, CATEGORY_ID, "Help", "A12"));
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void submitRequest_throwsWhenQueueIsClosed() {
        when(labSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(session.isQueueOpen()).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                service.submitRequest(SESSION_ID, STUDENT_ID, CATEGORY_ID, "Help", "A12"));
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void submitRequest_throwsWhenSessionNotFound() {
        when(labSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () ->
                service.submitRequest(SESSION_ID, STUDENT_ID, CATEGORY_ID, "Help", "A12"));
    }

    @Test
    void submitRequest_throwsWhenDescriptionIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                service.submitRequest(SESSION_ID, STUDENT_ID, CATEGORY_ID, "   ", "A12"));
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void submitRequest_throwsWhenDescriptionIsTooLong() {
        String tooLong = "x".repeat(HelpRequestService.MAX_DESCRIPTION_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () ->
                service.submitRequest(SESSION_ID, STUDENT_ID, CATEGORY_ID, tooLong, "A12"));
    }

    @Test
    void submitRequest_throwsWhenSeatIsMissing() {
        assertThrows(IllegalArgumentException.class, () ->
                service.submitRequest(SESSION_ID, STUDENT_ID, CATEGORY_ID, "Help", null));
    }

    // ---------- SCRUM-14: rateRequest ----------

    @Test
    void rateRequest_savesRatingAndCommentOnResolvedRequest() {
        HelpRequest request = givenRequest(RequestStatus.RESOLVED);
        when(helpRequestRepository.save(request)).thenReturn(request);

        HelpRequest result = service.rateRequest(REQUEST_ID, STUDENT_ID, 4, "  Very helpful ");

        assertEquals(4, result.getRating());
        assertEquals("Very helpful", result.getRatingComment());
    }

    @Test
    void rateRequest_allowsMissingComment() {
        HelpRequest request = givenRequest(RequestStatus.RESOLVED);
        when(helpRequestRepository.save(request)).thenReturn(request);

        HelpRequest result = service.rateRequest(REQUEST_ID, STUDENT_ID, 5, null);

        assertEquals(5, result.getRating());
        assertNull(result.getRatingComment());
    }

    @Test
    void rateRequest_throwsWhenRatingIsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () ->
                service.rateRequest(REQUEST_ID, STUDENT_ID, 0, null));
        assertThrows(IllegalArgumentException.class, () ->
                service.rateRequest(REQUEST_ID, STUDENT_ID, 6, null));
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void rateRequest_throwsWhenCommentIsTooLong() {
        String tooLong = "x".repeat(HelpRequestService.MAX_COMMENT_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () ->
                service.rateRequest(REQUEST_ID, STUDENT_ID, 3, tooLong));
    }

    @Test
    void rateRequest_throwsWhenRequestNotFound() {
        when(helpRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThrows(RequestNotFoundException.class, () ->
                service.rateRequest(REQUEST_ID, STUDENT_ID, 3, null));
    }

    @Test
    void rateRequest_throwsWhenAnotherStudentTriesToRate() {
        givenRequest(RequestStatus.RESOLVED);

        assertThrows(IllegalStateException.class, () ->
                service.rateRequest(REQUEST_ID, 999L, 3, null));
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void rateRequest_throwsWhenRequestIsNotResolved() {
        givenRequest(RequestStatus.WAITING);

        assertThrows(IllegalStateException.class, () ->
                service.rateRequest(REQUEST_ID, STUDENT_ID, 3, null));
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void rateRequest_throwsWhenRequestAlreadyRated() {
        HelpRequest request = givenRequest(RequestStatus.RESOLVED);
        request.rate(2, "ok");

        assertThrows(IllegalStateException.class, () ->
                service.rateRequest(REQUEST_ID, STUDENT_ID, 5, null));
        verify(helpRequestRepository, never()).save(any());
    }

    // ---------- helpers ----------

    private void givenOpenSession() {
        when(labSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(session.isQueueOpen()).thenReturn(true);
    }

    /** Stubs the repository to return a request owned by STUDENT_ID with the given status. */
    private HelpRequest givenRequest(RequestStatus status) {
        HelpRequest request = new HelpRequest(session, student, category, "Help", "A12");
        request.changeStatus(status);
        when(student.getId()).thenReturn(STUDENT_ID);
        when(helpRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        return request;
    }
}
