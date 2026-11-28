package ca.unb.labline.controller;

import ca.unb.labline.exception.DuplicateOpenRequestException;
import ca.unb.labline.service.InvalidRequestException;
import ca.unb.labline.service.InvalidStatusChangeException;
import ca.unb.labline.service.NotRequestOwnerException;
import ca.unb.labline.service.RegistrationException;
import ca.unb.labline.service.RequestNotEditableException;
import ca.unb.labline.service.RequestNotFoundException;
import ca.unb.labline.service.TaActionNotAllowedException;
import ca.unb.labline.service.UserNotFoundException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns exceptions into clear JSON errors like {"error": "..."}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({RegistrationException.class, InvalidRequestException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleBadInput(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({UserNotFoundException.class, RequestNotFoundException.class,
            NoSuchElementException.class})
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({NotRequestOwnerException.class, TaActionNotAllowedException.class})
    public ResponseEntity<Map<String, String>> handleForbidden(RuntimeException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler({RequestNotEditableException.class, InvalidStatusChangeException.class,
            DuplicateOpenRequestException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleConflict(RuntimeException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalid(MethodArgumentNotValidException e) {
        String field = e.getBindingResult().getFieldError() == null
                ? "request" : e.getBindingResult().getFieldError().getField();
        return error(HttpStatus.BAD_REQUEST, "Invalid or missing " + field);
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
