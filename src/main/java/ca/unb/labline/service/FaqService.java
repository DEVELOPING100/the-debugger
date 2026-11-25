package ca.unb.labline.service;

import ca.unb.labline.model.FaqEntry;
import ca.unb.labline.model.LabSession;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.FaqEntryRepository;
import ca.unb.labline.repository.LabSessionRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SCRUM-15: the FAQ board for a lab session. */
@Service
public class FaqService {

    static final int MAX_QUESTION_LENGTH = 300;
    static final int MAX_ANSWER_LENGTH = 1000;

    private final FaqEntryRepository faqEntryRepository;
    private final LabSessionRepository labSessionRepository;

    public FaqService(FaqEntryRepository faqEntryRepository,
            LabSessionRepository labSessionRepository) {
        this.faqEntryRepository = faqEntryRepository;
        this.labSessionRepository = labSessionRepository;
    }

    /** A TA assigned to the session adds a question and answer to its FAQ board. */
    @Transactional
    public FaqEntry addEntry(Long sessionId, Long taId, String question, String answer) {
        requireText(question, MAX_QUESTION_LENGTH, "Question");
        requireText(answer, MAX_ANSWER_LENGTH, "Answer");

        LabSession session = findSession(sessionId);
        User ta = findAssignedTa(session, taId);

        return faqEntryRepository.save(
                new FaqEntry(session, ta, question.trim(), answer.trim()));
    }

    /** Anyone in the session can read its FAQ board, oldest entry first. */
    @Transactional(readOnly = true)
    public List<FaqEntry> getEntries(Long sessionId) {
        if (!labSessionRepository.existsById(sessionId)) {
            throw new NoSuchElementException("Session not found: " + sessionId);
        }
        return faqEntryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    private void requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must be at most " + maxLength + " characters");
        }
    }

    private LabSession findSession(Long sessionId) {
        return labSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    }

    private User findAssignedTa(LabSession session, Long taId) {
        return session.getTas().stream()
                .filter(ta -> Objects.equals(ta.getId(), taId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Only a TA assigned to this session can add FAQ entries"));
    }
}
