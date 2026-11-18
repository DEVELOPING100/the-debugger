package ca.unb.labline.controller;

import ca.unb.labline.controller.dto.EditRequestBody;
import ca.unb.labline.controller.dto.HelpRequestResponse;
import ca.unb.labline.service.HelpRequestService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCRUM-24: a logged-in student edits or cancels their own request.
 * Cancel is a status change (kept for the audit trail), not a DELETE.
 */
@RestController
@RequestMapping("/api/requests")
public class HelpRequestController {

    private final HelpRequestService helpRequestService;

    public HelpRequestController(HelpRequestService helpRequestService) {
        this.helpRequestService = helpRequestService;
    }

    @PutMapping("/{id}")
    public HelpRequestResponse edit(@PathVariable Long id,
            @Valid @RequestBody EditRequestBody body, Authentication authentication) {
        return HelpRequestResponse.from(helpRequestService.editRequest(
                id, authentication.getName(), body.categoryId(), body.description(), body.seat()));
    }

    @PostMapping("/{id}/cancel")
    public HelpRequestResponse cancel(@PathVariable Long id, Authentication authentication) {
        return HelpRequestResponse.from(
                helpRequestService.cancelRequest(id, authentication.getName()));
    }
}
