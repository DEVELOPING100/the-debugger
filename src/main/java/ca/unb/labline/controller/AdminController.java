package ca.unb.labline.controller;

import ca.unb.labline.controller.dto.RoleChangeRequest;
import ca.unb.labline.controller.dto.UserResponse;
import ca.unb.labline.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-only actions. SecurityConfig limits /api/admin/** to ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AccountService accountService;

    public AdminController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PutMapping("/users/{id}/role")
    public UserResponse changeRole(@PathVariable Long id,
            @Valid @RequestBody RoleChangeRequest request) {
        return UserResponse.from(accountService.changeRole(id, request.role()));
    }
}
