package ca.unb.labline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.unb.labline.model.Role;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** SCRUM-1 unit tests. The repository and encoder are mocks: no database needed. */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(userRepository, passwordEncoder, "unb.ca");
    }

    @Test
    void registerCreatesStudentWithHashedPasswordAndCleanEmail() {
        when(userRepository.existsByEmail("test.student@unb.ca")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User saved = accountService.register("  Test.Student@UNB.ca ", "Test Student", "password123");

        assertEquals("test.student@unb.ca", saved.getEmail());
        assertEquals(Role.STUDENT, saved.getRole());
        assertEquals("HASHED", saved.getPasswordHash());
    }

    @Test
    void registerRejectsNonUniversityEmail() {
        assertThrows(RegistrationException.class,
                () -> accountService.register("someone@gmail.com", "Someone", "password123"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsEmailAlreadyInUse() {
        when(userRepository.existsByEmail("test.student@unb.ca")).thenReturn(true);

        assertThrows(RegistrationException.class,
                () -> accountService.register("test.student@unb.ca", "Test Student", "password123"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsShortPasswordWithoutHashingIt() {
        assertThrows(RegistrationException.class,
                () -> accountService.register("test.student@unb.ca", "Test Student", "short"));

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changeRoleUpdatesTheAccount() {
        User account = new User("test.ta@unb.ca", "Test TA", "HASHED");
        when(userRepository.findById(5L)).thenReturn(Optional.of(account));
        when(userRepository.save(account)).thenReturn(account);

        User updated = accountService.changeRole(5L, Role.TA);

        assertEquals(Role.TA, updated.getRole());
    }

    @Test
    void changeRoleFailsForUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> accountService.changeRole(99L, Role.TA));
    }
}
