package ca.unb.labline.controller.dto;

import ca.unb.labline.model.Role;
import ca.unb.labline.model.User;

/** What the API returns about an account. Never includes the password hash. */
public record UserResponse(Long id, String email, String name, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
