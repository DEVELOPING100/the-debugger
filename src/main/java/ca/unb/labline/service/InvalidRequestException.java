package ca.unb.labline.service;

/** The new details are not valid (400). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
