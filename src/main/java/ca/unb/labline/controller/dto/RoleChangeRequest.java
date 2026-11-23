package ca.unb.labline.controller.dto;

import ca.unb.labline.model.Role;
import jakarta.validation.constraints.NotNull;

/** JSON body for PUT /api/admin/users/{id}/role. */
public record RoleChangeRequest(@NotNull Role role) {
}
