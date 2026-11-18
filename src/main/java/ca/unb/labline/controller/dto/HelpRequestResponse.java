package ca.unb.labline.controller.dto;

import ca.unb.labline.model.HelpRequest;
import ca.unb.labline.model.RequestStatus;
import java.time.LocalDateTime;

/** What the API returns about a help request. */
public record HelpRequestResponse(Long id, Long sessionId, Long categoryId,
        String description, String seat, RequestStatus status, LocalDateTime timeJoined) {

    public static HelpRequestResponse from(HelpRequest request) {
        return new HelpRequestResponse(request.getId(), request.getSession().getId(),
                request.getCategory().getId(), request.getDescription(), request.getSeat(),
                request.getStatus(), request.getTimeJoined());
    }
}
