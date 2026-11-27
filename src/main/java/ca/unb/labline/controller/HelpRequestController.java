package ca.unb.labline.controller;

import ca.unb.labline.controller.dto.EditRequestBody;
import ca.unb.labline.controller.dto.HelpRequestResponse;
import ca.unb.labline.controller.dto.SubmitRequestBody;
import ca.unb.labline.service.AccountService;
import ca.unb.labline.service.HelpRequestService;
import ca.unb.labline.service.MyRequestStatus;
import ca.unb.labline.service.StudentQueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A logged-in student's own help request.
 * SCRUM-2: submit. SCRUM-24: edit or cancel (cancel is a status change, kept
 * for the audit trail, not a DELETE). FR6: see my request and place in line.
 * The student is always taken from the login, never from the request body.
 */
@RestController
@RequestMapping("/api/requests")
public class HelpRequestController {

    private final HelpRequestService helpRequestService;
    private final StudentQueueService studentQueueService;
    private final AccountService accountService;

    public HelpRequestController(HelpRequestService helpRequestService,
            StudentQueueService studentQueueService, AccountService accountService) {
        this.helpRequestService = helpRequestService;
        this.studentQueueService = studentQueueService;
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HelpRequestResponse submit(@Valid @RequestBody SubmitRequestBody body,
            Authentication authentication) {
        Long studentId = accountService.getByEmail(authentication.getName()).getId();
        return HelpRequestResponse.from(helpRequestService.submitRequest(body.sessionId(),
                studentId, body.categoryId(), body.description(), body.seat()));
    }

    /** 200 with my request, or 204 if I have no open request in this session. */
    @GetMapping("/mine")
    public ResponseEntity<MyRequestStatus> mine(@RequestParam Long sessionId,
            Authentication authentication) {
        return studentQueueService.myOpenRequest(sessionId, authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
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
