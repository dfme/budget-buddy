package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.auth.dto.UserProfileResponse;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = newUser(1L, "lara@example.ch", new BigDecimal("4200.00"), true);
    }

    @Test
    void getProfileReturnsMappedFields() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("lara@example.ch");
        assertThat(response.monthlyIncome()).isEqualByComparingTo("4200.00");
        assertThat(response.onboardingCompleted()).isTrue();
    }

    @Test
    void getProfileThrowsWhenUserMissing() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateIncomeSetsAmountAndReturnsUpdatedProfile() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.updateIncome(1L, new BigDecimal("5000.00"));

        assertThat(user.getMonthlyIncome()).isEqualByComparingTo("5000.00");
        assertThat(response.monthlyIncome()).isEqualByComparingTo("5000.00");
    }

    @Test
    void updateIncomeThrowsWhenUserMissing() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateIncome(99L, BigDecimal.ONE))
                .isInstanceOf(UserNotFoundException.class);
    }

    // --- changePassword (BE-AUTH-09) ---

    @Test
    void changePasswordWithCorrectCurrentPasswordReplacesHash() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("altesPasswort1", "irrelevant-for-test")).thenReturn(true);
        when(passwordEncoder.encode("neuesPasswort2")).thenReturn("neuer-bcrypt-hash");

        userService.changePassword(1L, "altesPasswort1", "neuesPasswort2");

        assertThat(user.getPasswordHash()).isEqualTo("neuer-bcrypt-hash");
    }

    @Test
    void changePasswordWithWrongCurrentPasswordThrowsAndLeavesHashUnchanged() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("falsch", "irrelevant-for-test")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(1L, "falsch", "neuesPasswort2"))
                .isInstanceOf(InvalidCurrentPasswordException.class);

        assertThat(user.getPasswordHash()).isEqualTo("irrelevant-for-test");
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void changePasswordThrowsWhenUserMissing() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(99L, "alt", "neuesPasswort2"))
                .isInstanceOf(UserNotFoundException.class);
    }

    // --- UserIncomePort (BE-FC-02): Einkommen über die Modulgrenze ---

    @Test
    void findMonthlyIncomeReturnsTheAmount() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(userService.findMonthlyIncome(1L)).get()
                .isEqualTo(new BigDecimal("4200.00"));
    }

    @Test
    void findMonthlyIncomeIsEmptyWhenIncomeIsNull() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(newUser(1L, "lara@example.ch", null, false)));

        assertThat(userService.findMonthlyIncome(1L)).isEmpty();
    }

    @Test
    void findMonthlyIncomeIsEmptyForUnknownUserInsteadOfThrowing() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        // Bewusst keine UserNotFoundException: für die Fixkosten-Warnung ist «kein Vergleichswert»
        // ein Ergebnis, kein Fehler, der den Aufrufer abbricht.
        assertThat(userService.findMonthlyIncome(99L)).isEmpty();
    }

    // User hat bewusst keine Setter für id/email/passwordHash (Entity-Kapselung); im Unit-Test
    // werden die Felder daher via Reflection gesetzt.
    private static User newUser(long id, String email, BigDecimal income, boolean onboarded) {
        try {
            User u = User.class.getDeclaredConstructor().newInstance();
            setField(u, "id", id);
            setField(u, "email", email);
            setField(u, "passwordHash", "irrelevant-for-test");
            setField(u, "monthlyIncome", income);
            setField(u, "onboardingCompleted", onboarded);
            return u;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(User u, String name, Object value)
            throws ReflectiveOperationException {
        Field field = User.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(u, value);
    }
}
