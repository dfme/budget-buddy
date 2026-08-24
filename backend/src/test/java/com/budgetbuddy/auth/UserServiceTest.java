package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
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

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

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

    // --- BE-AUTH-08: Rappen und Kapazitätsgrenze ---

    /**
     * Der Defekt aus #148: Vorher kam {@code 4200.004} durch, wurde mit 200 quittiert und von
     * {@code numeric(10,2)} still auf {@code 4200.00} gerundet.
     */
    @Test
    void updateIncomeRejectsMoreThanTwoDecimals() {
        assertThatThrownBy(() -> userService.updateIncome(1L, new BigDecimal("4200.004")))
                .isInstanceOf(InvalidIncomeException.class)
                .hasMessage("Einkommen darf höchstens zwei Nachkommastellen haben.")
                .extracting(e -> ((InvalidIncomeException) e).getField())
                .isEqualTo("betrag");
    }

    /**
     * Die Gegenprobe zur Regel darüber und der Grund, warum {@code @Digits(fraction = 2)} am DTO
     * nicht getragen hätte: {@code 100.000} hat Skala 3, ist aber derselbe Wert wie
     * {@code 100.00}. Wie viele Nullen ein Client anhängt, ist seine Sache.
     */
    @Test
    void updateIncomeAcceptsTrailingZerosBeyondRappen() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateIncome(1L, new BigDecimal("100.000"));

        assertThat(user.getMonthlyIncome()).isEqualByComparingTo("100.00");
        // Skala explizit: isEqualByComparingTo ignoriert sie, und normalisiert wird hier gerade.
        assertThat(user.getMonthlyIncome().scale()).isEqualTo(2);
    }

    @Test
    void updateIncomeAcceptsTheCapacityLimit() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatCode(() -> userService.updateIncome(1L, new BigDecimal("99999999.99")))
                .doesNotThrowAnyException();
        assertThat(user.getMonthlyIncome()).isEqualByComparingTo("99999999.99");
    }

    /** Ein Rappen darüber: vorher ein DB-Fehler, jetzt eine 400-Antwort. */
    @Test
    void updateIncomeRejectsAboveTheCapacityLimit() {
        assertThatThrownBy(() -> userService.updateIncome(1L, new BigDecimal("100000000.00")))
                .isInstanceOf(InvalidIncomeException.class)
                .hasMessage("Einkommen darf 99'999'999.99 nicht überschreiten.");
    }

    @Test
    void updateIncomeRejectsNull() {
        assertThatThrownBy(() -> userService.updateIncome(1L, null))
                .isInstanceOf(InvalidIncomeException.class)
                .hasMessage("Einkommen ist erforderlich.");
    }

    @Test
    void updateIncomeRejectsZeroAndNegative() {
        for (String betrag : new String[] {"0", "0.00", "-10.00"}) {
            assertThatThrownBy(() -> userService.updateIncome(1L, new BigDecimal(betrag)))
                    .as("Betrag %s", betrag)
                    .isInstanceOf(InvalidIncomeException.class)
                    .hasMessage("Einkommen muss grösser als 0 sein.");
        }
    }

    /**
     * Die Prüfung steht <em>vor</em> dem Laden des Users. Ohne diese Reihenfolge wäre ein
     * abgelehnter Betrag zwar auch nicht gespeichert, aber jeder Fehlversuch kostete eine Query —
     * und der Beweis, dass nichts geschrieben wurde, hinge am Verhalten der Entity statt an der
     * Reihenfolge.
     */
    @Test
    void rejectedIncomeNeverTouchesTheRepository() {
        assertThatThrownBy(() -> userService.updateIncome(1L, new BigDecimal("0.001")))
                .isInstanceOf(InvalidIncomeException.class);

        verify(userRepository, never()).findById(anyLong());
        assertThat(user.getMonthlyIncome()).isEqualByComparingTo("4200.00");
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
