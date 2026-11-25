package ca.unb.labline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.unb.labline.model.FaqEntry;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.FaqEntryRepository;
import ca.unb.labline.repository.LabSessionRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    private static final Long SESSION_ID = 1L;
    private static final Long TA_ID = 2L;

    @Mock
    private FaqEntryRepository faqEntryRepository;
    @Mock
    private LabSessionRepository labSessionRepository;

    @Mock
    private LabSession session;
    @Mock
    private User ta;
    @Mock
    private User otherTa;

    @InjectMocks
    private FaqService service;

    // ---------- addEntry ----------

    @Test
    void addEntry_savesTrimmedEntryForAssignedTa() {
        when(labSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(session.getTas()).thenReturn(Set.of(ta));
        when(ta.getId()).thenReturn(TA_ID);
        when(faqEntryRepository.save(any(FaqEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FaqEntry result = service.addEntry(
                SESSION_ID, TA_ID, "  How do I submit?  ", " Use the Submit button. ");

        assertEquals("How do I submit?", result.getQuestion());
        assertEquals("Use the Submit button.", result.getAnswer());
        assertSame(session, result.getSession());
        assertSame(ta, result.getCreatedBy());
    }

    @Test
    void addEntry_throwsWhenQuestionIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                service.addEntry(SESSION_ID, TA_ID, "   ", "An answer"));
        verify(faqEntryRepository, never()).save(any());
    }

    @Test
    void addEntry_throwsWhenAnswerIsMissing() {
        assertThrows(IllegalArgumentException.class, () ->
                service.addEntry(SESSION_ID, TA_ID, "A question", null));
        verify(faqEntryRepository, never()).save(any());
    }

    @Test
    void addEntry_throwsWhenQuestionIsTooLong() {
        String tooLong = "x".repeat(FaqService.MAX_QUESTION_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () ->
                service.addEntry(SESSION_ID, TA_ID, tooLong, "An answer"));
    }

    @Test
    void addEntry_throwsWhenAnswerIsTooLong() {
        String tooLong = "x".repeat(FaqService.MAX_ANSWER_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () ->
                service.addEntry(SESSION_ID, TA_ID, "A question", tooLong));
    }

    @Test
    void addEntry_throwsWhenSessionNotFound() {
        when(labSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () ->
                service.addEntry(SESSION_ID, TA_ID, "A question", "An answer"));
    }

    @Test
    void addEntry_throwsWhenUserIsNotATaOfTheSession() {
        when(labSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(session.getTas()).thenReturn(Set.of(otherTa));
        when(otherTa.getId()).thenReturn(99L);

        assertThrows(IllegalStateException.class, () ->
                service.addEntry(SESSION_ID, TA_ID, "A question", "An answer"));
        verify(faqEntryRepository, never()).save(any());
    }

    // ---------- getEntries ----------

    @Test
    void getEntries_returnsTheSessionsBoard() {
        List<FaqEntry> board = List.of(new FaqEntry(session, ta, "Q", "A"));
        when(labSessionRepository.existsById(SESSION_ID)).thenReturn(true);
        when(faqEntryRepository.findBySessionIdOrderByCreatedAtAsc(SESSION_ID)).thenReturn(board);

        assertEquals(board, service.getEntries(SESSION_ID));
    }

    @Test
    void getEntries_throwsWhenSessionNotFound() {
        when(labSessionRepository.existsById(SESSION_ID)).thenReturn(false);

        assertThrows(NoSuchElementException.class, () -> service.getEntries(SESSION_ID));
        verify(faqEntryRepository, never()).findBySessionIdOrderByCreatedAtAsc(any());
    }
}
