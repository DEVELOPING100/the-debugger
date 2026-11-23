package ca.unb.labline.service;

/** The user is not allowed to do this TA action on this request or session (403). */
public class TaActionNotAllowedException extends RuntimeException {

    public TaActionNotAllowedException(String message) {
        super(message);
    }
}
