package ca.unb.labline.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** JSON body for POST /api/requests. The student comes from the login, never from here. */
public record SubmitRequestBody(
        @NotNull Long sessionId,
        @NotNull Long categoryId,
        @NotBlank String description,
        @NotBlank String seat) {
}
