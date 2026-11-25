package ca.unb.labline.service;

/** Thrown when a sign-up is rejected; the message is shown to the user. */
public class RegistrationException extends RuntimeException {

    public RegistrationException(String message) {
        super(message);
    }
}
