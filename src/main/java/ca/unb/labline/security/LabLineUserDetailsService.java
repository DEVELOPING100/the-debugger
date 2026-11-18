package ca.unb.labline.security;

import ca.unb.labline.model.User;
import ca.unb.labline.repository.UserRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells Spring Security how to find an account at login time.
 * The role becomes an authority such as ROLE_STUDENT.
 */
@Service
public class LabLineUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public LabLineUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String cleanEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User account = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));

        return org.springframework.security.core.userdetails.User
                .withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .roles(account.getRole().name())
                .build();
    }
}
