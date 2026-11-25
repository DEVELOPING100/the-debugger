package ca.unb.labline.service;

/** The request is no longer WAITING, so it cannot change (409). */
public class RequestNotEditableException extends RuntimeException {

    public RequestNotEditableException(String message) {
        super(message);
    }
}
