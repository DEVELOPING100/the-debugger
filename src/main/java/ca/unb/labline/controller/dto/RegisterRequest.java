package ca.unb.labline.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** JSON body for POST /api/auth/register. */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String name,
        @NotBlank String password) {
}
