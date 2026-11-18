package ca.unb.labline.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * One lab session of a course, with its own queue and join code.
 * Maps to lab_sessions; the TAs assigned to it are stored in session_tas.
 */
@Entity
@Table(name = "lab_sessions")
public class LabSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String room;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "join_code", nullable = false, unique = true, length = 10)
    private String joinCode;

    @Column(name = "queue_open", nullable = false)
    private boolean queueOpen;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "session_tas",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "ta_id"))
    private Set<User> tas = new HashSet<>();

    /** Required by JPA; not for use in application code. */
    protected LabSession() {
    }

    /** Creates a session with its queue closed. */
    public LabSession(Course course, String name, String room,
            LocalDateTime startTime, LocalDateTime endTime, String joinCode) {
        this.course = course;
        this.name = name;
        this.room = room;
        this.startTime = startTime;
        this.endTime = endTime;
        this.joinCode = joinCode;
        this.queueOpen = false;
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public String getName() {
        return name;
    }

    public String getRoom() {
        return room;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getJoinCode() {
        return joinCode;
    }

    public boolean isQueueOpen() {
        return queueOpen;
    }

    public void openQueue() {
        this.queueOpen = true;
    }

    public void closeQueue() {
        this.queueOpen = false;
    }

    public Set<User> getTas() {
        return tas;
    }

    public void assignTa(User ta) {
        tas.add(ta);
    }

    @Override
    public String toString() {
        return "LabSession{id=" + id + ", name=" + name + ", joinCode=" + joinCode + "}";
    }
}
