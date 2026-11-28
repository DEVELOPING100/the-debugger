package ca.unb.labline.controller;

import ca.unb.labline.controller.dto.FaqRequestBody;
import ca.unb.labline.controller.dto.FaqResponse;
import ca.unb.labline.service.AccountService;
import ca.unb.labline.service.FaqService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCRUM-15: a session's FAQ board. Anyone can read it; a TA assigned to the
 * session adds entries. The TA is always taken from the login, never from the body.
 */
@RestController
public class FaqController {

    private final FaqService faqService;
    private final AccountService accountService;

    public FaqController(FaqService faqService, AccountService accountService) {
        this.faqService = faqService;
        this.accountService = accountService;
    }

    @GetMapping("/api/sessions/{id}/faq")
    public List<FaqResponse> list(@PathVariable Long id) {
        return faqService.getEntries(id).stream().map(FaqResponse::from).toList();
    }

    @PostMapping("/api/ta/sessions/{id}/faq")
    @ResponseStatus(HttpStatus.CREATED)
    public FaqResponse add(@PathVariable Long id, @Valid @RequestBody FaqRequestBody body,
            Authentication authentication) {
        Long taId = accountService.getByEmail(authentication.getName()).getId();
        return FaqResponse.from(faqService.addEntry(id, taId, body.question(), body.answer()));
    }
}