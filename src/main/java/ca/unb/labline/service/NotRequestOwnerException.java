package ca.unb.labline.service;

/** A user tried to change someone else's request (403). */
public class NotRequestOwnerException extends RuntimeException {

    public NotRequestOwnerException(String message) {
        super(message);
    }
}
