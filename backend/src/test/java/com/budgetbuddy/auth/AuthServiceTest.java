package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerHashesPasswordAndPersistsUser() {
        when(userRepository.existsByEmail("lara@example.ch")).thenReturn(false);
        when(passwordEncoder.encode("geheim123")).thenReturn("bcrypt-hash");
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.register("lara@example.ch", "geheim123");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("lara@example.ch");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        // Klartext-Passwort darf nie als Hash landen.
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("geheim123");
    }

    @Test
    void registerWithDuplicateEmailThrowsAndDoesNotSave() {
        when(userRepository.existsByEmail("lara@example.ch")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("lara@example.ch", "geheim123"))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginWithCorrectCredentialsReturnsUser() {
        User user = new User("lara@example.ch", "bcrypt-hash");
        when(userRepository.findByEmail("lara@example.ch")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("geheim123", "bcrypt-hash")).thenReturn(true);

        User result = authService.login("lara@example.ch", "geheim123");

        assertThat(result).isSameAs(user);
    }

    @Test
    void loginWithWrongPasswordThrows() {
        User user = new User("lara@example.ch", "bcrypt-hash");
        when(userRepository.findByEmail("lara@example.ch")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("falsch", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("lara@example.ch", "falsch"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginWithUnknownEmailThrows() {
        when(userRepository.findByEmail("nobody@example.ch")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@example.ch", "geheim123"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
