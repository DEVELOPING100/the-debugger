package ca.unb.labline.service;

import ca.unb.labline.model.RequestStatus;

/**
 * A student's open request as shown on their page.
 * position is their place in line while WAITING (1 = next), 0 while a TA is
 * helping them (CLAIMED), and null while DEFERRED.
 */
public record MyRequestStatus(Long requestId, RequestStatus status, Long categoryId,
        String categoryName, String description, String seat, Integer position,
        int waitingCount) {
}
