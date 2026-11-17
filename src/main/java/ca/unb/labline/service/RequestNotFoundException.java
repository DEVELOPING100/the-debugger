package ca.unb.labline.service;

/** No help request with that id (404). */
public class RequestNotFoundException extends RuntimeException {

    public RequestNotFoundException(String message) {
        super(message);
    }
}
