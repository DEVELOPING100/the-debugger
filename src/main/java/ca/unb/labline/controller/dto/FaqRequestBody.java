
package ca.unb.labline.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FaqRequestBody(
        @NotBlank @Size(max = 300) String question,
        @NotBlank @Size(max = 1000) String answer) {
}