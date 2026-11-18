package ca.unb.labline.controller.dto;

import ca.unb.labline.service.TaQueueService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** One answer for every request in a cluster (SCRUM-11). */
public record BroadcastBody(
        @NotEmpty List<Long> requestIds,
        @NotBlank @Size(max = TaQueueService.MAX_ANSWER_LENGTH) String answer) {
}
