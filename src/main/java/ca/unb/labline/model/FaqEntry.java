package ca.unb.labline.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * One question-and-answer on a session's FAQ board, written by a TA.
 * Maps to the faq_entries table. Business rules live in FaqService.
 */
@Entity
@Table(name = "faq_entries")
public class FaqEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LabSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, length = 300)
    private String question;

    @Column(nullable = false, length = 1000)
    private String answer;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Required by JPA; not for use in application code. */
    protected FaqEntry() {
    }

    /** Creates an entry for a session's FAQ board, timestamped now. */
    public FaqEntry(LabSession session, User createdBy, String question, String answer) {
        this.session = session;
        this.createdBy = createdBy;
        this.question = question;
        this.answer = answer;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LabSession getSession() {
        return session;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "FaqEntry{id=" + id + ", question=" + question + "}";
    }
}
