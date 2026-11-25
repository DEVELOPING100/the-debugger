package ca.unb.labline.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** JSON body for PUT /api/requests/{id}. Lengths are checked in the service. */
public record EditRequestBody(
        @NotNull Long categoryId,
        @NotBlank String description,
        @NotBlank String seat) {
}
