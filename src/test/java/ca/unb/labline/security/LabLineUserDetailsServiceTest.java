package ca.unb.labline.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import ca.unb.labline.model.User;
import ca.unb.labline.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** SCRUM-1 login tests: the role becomes a Spring Security authority. */
@ExtendWith(MockitoExtension.class)
class LabLineUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LabLineUserDetailsService userDetailsService;

    @Test
    void loadsAccountWithRoleAsAuthority() {
        User account = new User("test.student@unb.ca", "Test Student", "HASHED");
        when(userRepository.findByEmail("test.student@unb.ca")).thenReturn(Optional.of(account));

        UserDetails details = userDetailsService.loadUserByUsername("Test.Student@unb.ca");

        assertEquals("test.student@unb.ca", details.getUsername());
        assertEquals("HASHED", details.getPassword());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_STUDENT".equals(authority.getAuthority())));
    }

    @Test
    void unknownEmailCannotLogIn() {
        when(userRepository.findByEmail("nobody@unb.ca")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("nobody@unb.ca"));
    }
}
