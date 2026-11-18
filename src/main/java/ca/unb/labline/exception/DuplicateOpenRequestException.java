package ca.unb.labline.exception;

/** Thrown when a student tries to join a queue they already have an open request in. */
public class DuplicateOpenRequestException extends RuntimeException {

    public DuplicateOpenRequestException(Long studentId, Long sessionId) {
        super("Student " + studentId + " already has an open request in session " + sessionId);
    }
}
