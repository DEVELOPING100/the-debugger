package ca.unb.labline.service;

import ca.unb.labline.model.Role;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.UserRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SCRUM-1: creating accounts and managing roles.
 * Every new account is a STUDENT; only an administrator changes roles.
 */
@Service
public class AccountService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String allowedDomain;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value("${labline.auth.allowed-email-domain}") String allowedDomain) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.allowedDomain = allowedDomain.toLowerCase(Locale.ROOT);
    }

    @Transactional
    public User register(String email, String name, String rawPassword) {
        String cleanEmail = normalizeEmail(email);
        requireUniversityEmail(cleanEmail);
        requireName(name);
        requireLongEnoughPassword(rawPassword);
        requireUnusedEmail(cleanEmail);

        User newUser = new User(cleanEmail, name.trim(), passwordEncoder.encode(rawPassword));
        return userRepository.save(newUser);
    }

    @Transactional
    public User changeRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("No user with id " + userId));
        user.changeRole(newRole);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UserNotFoundException("No account for " + email));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new RegistrationException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void requireUniversityEmail(String email) {
        if (!email.endsWith("@" + allowedDomain)) {
            throw new RegistrationException("Please sign up with your @" + allowedDomain + " email");
        }
    }

    private void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new RegistrationException("Name is required");
        }
    }

    private void requireLongEnoughPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new RegistrationException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private void requireUnusedEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new RegistrationException("An account with this email already exists");
        }
    }
}
