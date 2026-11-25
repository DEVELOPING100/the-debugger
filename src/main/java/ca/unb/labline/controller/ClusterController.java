package ca.unb.labline.controller;

import ca.unb.labline.controller.dto.BroadcastBody;
import ca.unb.labline.controller.dto.ClusterResponse;
import ca.unb.labline.controller.dto.HelpRequestResponse;
import ca.unb.labline.service.QuestionClusterService;
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
 * SCRUM-10 and SCRUM-11: a TA sees similar questions grouped and answers a group at once.
 * Under /api/ta, so SecurityConfig only lets TAs and instructors in.
 */
@RestController
@RequestMapping("/api/ta/sessions/{sessionId}")
public class ClusterController {

    private final QuestionClusterService questionClusterService;
    private final TaQueueService taQueueService;

    public ClusterController(QuestionClusterService questionClusterService,
            TaQueueService taQueueService) {
        this.questionClusterService = questionClusterService;
        this.taQueueService = taQueueService;
    }

    @GetMapping("/clusters")
    public List<ClusterResponse> clusters(@PathVariable Long sessionId,
            Authentication authentication) {
        return questionClusterService.clustersFor(sessionId, authentication.getName()).stream()
                .map(ClusterResponse::from)
                .toList();
    }

    @PostMapping("/broadcast")
    public List<HelpRequestResponse> broadcast(@PathVariable Long sessionId,
            @Valid @RequestBody BroadcastBody body, Authentication authentication) {
        return taQueueService.broadcastAnswer(sessionId, body.requestIds(), body.answer(),
                        authentication.getName()).stream()
                .map(HelpRequestResponse::from)
                .toList();
    }
}
