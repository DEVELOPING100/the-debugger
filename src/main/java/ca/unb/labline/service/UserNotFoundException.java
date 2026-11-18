package ca.unb.labline.service;

/** Thrown when an account id or email does not exist. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
