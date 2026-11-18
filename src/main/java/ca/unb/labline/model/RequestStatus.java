package ca.unb.labline.model;

/**
 * The life cycle of a help request. Stored as text in help_requests.status.
 */
public enum RequestStatus {
    WAITING,
    CLAIMED,
    DEFERRED,
    RESOLVED,
    CANCELLED
}
