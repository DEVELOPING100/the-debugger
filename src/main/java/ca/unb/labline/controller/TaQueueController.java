package ca.unb.labline.controller;

import ca.unb.labline.controller.dto.HelpRequestResponse;
import ca.unb.labline.controller.dto.StatusChangeBody;
import ca.unb.labline.service.TaQueueService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCRUM-7: a TA works through a session's queue.
 * Under /api/ta, so SecurityConfig only lets TAs and instructors in.
 */
@RestController
@RequestMapping("/api/ta")
public class TaQueueController {

    private final TaQueueService taQueueService;

    public TaQueueController(TaQueueService taQueueService) {
        this.taQueueService = taQueueService;
    }

    @GetMapping("/sessions/{sessionId}/queue")
    public List<HelpRequestResponse> queue(@PathVariable Long sessionId,
            Authentication authentication) {
        return taQueueService.viewQueue(sessionId, authentication.getName()).stream()
                .map(HelpRequestResponse::from)
                .toList();
    }

    @PostMapping("/sessions/{sessionId}/claim-next")
    public HelpRequestResponse claimNext(@PathVariable Long sessionId,
            Authentication authentication) {
        return HelpRequestResponse.from(
                taQueueService.claimNext(sessionId, authentication.getName()));
    }

    @PostMapping("/requests/{id}/resolve")
    public HelpRequestResponse resolve(@PathVariable Long id, Authentication authentication) {
        return HelpRequestResponse.from(taQueueService.resolve(id, authentication.getName()));
    }

    @PostMapping("/requests/{id}/defer")
    public HelpRequestResponse defer(@PathVariable Long id,
            @Valid @RequestBody(required = false) StatusChangeBody body,
            Authentication authentication) {
        return HelpRequestResponse.from(
                taQueueService.defer(id, authentication.getName(), reasonOf(body)));
    }

    @PostMapping("/requests/{id}/return")
    public HelpRequestResponse returnToQueue(@PathVariable Long id,
            @Valid @RequestBody(required = false) StatusChangeBody body,
            Authentication authentication) {
        return HelpRequestResponse.from(
                taQueueService.returnToQueue(id, authentication.getName(), reasonOf(body)));
    }

    private static String reasonOf(StatusChangeBody body) {
        return body == null ? null : body.reason();
    }
}
