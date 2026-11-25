package ca.unb.labline.service;

/** The request's current status does not allow this change (409). */
public class InvalidStatusChangeException extends RuntimeException {

    public InvalidStatusChangeException(String message) {
        super(message);
    }
}
