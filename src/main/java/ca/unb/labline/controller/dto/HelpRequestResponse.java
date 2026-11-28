package ca.unb.labline.controller.dto;

import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.RequestStatus;
import ca.unb.labline.model.User;
import java.time.LocalDateTime;

/**
 * What the API returns about a help request.
 * claimedById is the TA helping right now (null if nobody), so the TA page can
 * show Resolve/Defer only on that TA's own requests.
 */
public record HelpRequestResponse(Long id, Long sessionId, Long categoryId,
        String description, String seat, RequestStatus status, LocalDateTime timeJoined,
        Long claimedById) {

    public static HelpRequestResponse from(HelpRequest request) {
        return new HelpRequestResponse(request.getId(), request.getSession().getId(),
                request.getCategory().getId(), request.getDescription(), request.getSeat(),
                request.getStatus(), request.getTimeJoined(), idOf(request.getClaimedBy()));
    }

    private static Long idOf(User user) {
        return user == null ? null : user.getId();
    }
}
