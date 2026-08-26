package com.budgetbuddy.auth;

import com.budgetbuddy.auth.dto.UserProfileResponse;
import com.budgetbuddy.budget.FixedCostCleanupPort;
import com.budgetbuddy.transaction.TransactionCleanupPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profil-Lese- und Einkommens-Update-Logik für das auth-Modul (BE-AUTH-02).
 *
 * <p>Implementiert zusätzlich den {@link UserIncomePort}, über den das {@code budget}-Modul das
 * Einkommen für die Fixkosten-Warnung liest (BE-FC-02) — ohne Zugriff auf {@link UserRepository}
 * oder die {@link User}-Entity über die Modulgrenze hinweg.
 *
 * <p><strong>Die Regeln für {@code monthlyIncome} stehen hier und nur hier</strong> (BE-AUTH-08).
 * {@code UpdateIncomeRequest} trägt bewusst keine Bean-Validation-Annotationen mehr — dieselbe
 * Aufteilung wie bei {@code FixedCostRequest}/{@code FixedCostService}, und aus denselben zwei
 * Gründen: Annotationen greifen erst, wenn ein Controller {@code @Valid} setzt (der Service wäre
 * also ungeschützt, sobald ihn jemand anders aufruft), und dieselbe Regel an zwei Stellen läuft
 * irgendwann auseinander.
 */
@Service
public class UserService implements UserIncomePort {

    /** Rappen — Zielskala von {@code monthly_income} (ADR-9, {@code DECIMAL(10,2)} in V01). */
    private static final int RAPPEN_SCALE = 2;

    /**
     * Kapazitätsgrenze von {@code users.monthly_income}: {@code DECIMAL(10,2)} fasst maximal
     * {@code 99999999.99} (Flyway {@code V01__create_users_table.sql}). Ein grösserer Wert lief
     * vorher nicht in eine 400-Antwort, sondern in einen DB-Fehler.
     */
    private static final BigDecimal MAX_INCOME = new BigDecimal("99999999.99");

    private final UserRepository userRepository;
    private final TransactionCleanupPort transactionCleanupPort;
    private final FixedCostCleanupPort fixedCostCleanupPort;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            TransactionCleanupPort transactionCleanupPort,
            FixedCostCleanupPort fixedCostCleanupPort,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.transactionCleanupPort = transactionCleanupPort;
        this.fixedCostCleanupPort = fixedCostCleanupPort;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Liefert das Profil des Users.
     *
     * @throws UserNotFoundException wenn kein User mit dieser ID existiert.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(long userId) {
        return toResponse(findUser(userId));
    }

    /**
     * Setzt das monatliche Einkommen und liefert das aktualisierte Profil.
     *
     * <p>Der Betrag wird vor dem Schreiben geprüft (BE-AUTH-08): {@code numeric(10,2)} rundet
     * sonst still, und der User bekäme eine Erfolgsmeldung für einen Betrag, der so nicht
     * gespeichert wurde. Bei einem Feld, das die Safe-to-Spend-Rechnung trägt, ist das die falsche
     * Antwort — richtig ist ein 400 mit der verletzten Regel.
     *
     * @param betrag Monatseinkommen in CHF. Muss {@code > 0} sein, höchstens zwei
     *     Nachkommastellen tragen und {@link #MAX_INCOME} nicht überschreiten.
     * @throws UserNotFoundException wenn kein User mit dieser ID existiert.
     * @throws InvalidIncomeException wenn der Betrag eine der Regeln verletzt. Der User wird dann
     *     nicht geladen und nichts geschrieben — die Prüfung steht vor {@link #findUser(long)}.
     */
    @Transactional
    public UserProfileResponse updateIncome(long userId, BigDecimal betrag) {
        BigDecimal geprueft = validateBetrag(betrag);
        User user = findUser(userId);
        user.setMonthlyIncome(geprueft);
        return toResponse(user);
    }

    /**
     * Prüft das Einkommen und liefert es auf Rappen normalisiert.
     *
     * <p>Dieselbe Regel wie {@code FixedCostService.validateBetrag} für {@code fixed_costs.betrag}
     * — inklusive der {@code stripTrailingZeros()}-Feinheit: {@code 100.00} (Skala 2) und
     * {@code 100.000} (Skala 3) sind derselbe Wert, und wie viele Nullen ein Client anhängt, ist
     * seine Sache. Ein {@code @Digits(fraction = 2)} am DTO könnte das nicht leisten — es zählt
     * {@code scale()} ohne Normalisierung und lehnte {@code 100.000} ab.
     *
     * <p>{@link RoundingMode#UNNECESSARY} beim {@code setScale}: An dieser Stelle steht bereits
     * fest, dass höchstens zwei Nachkommastellen belegt sind. Müsste hier gerundet werden, wäre die
     * Prüfung darüber falsch — dann soll es laut scheitern und nicht still runden. Genau das
     * stille Runden ist der Defekt, den dieser Task behebt.
     */
    private static BigDecimal validateBetrag(BigDecimal betrag) {
        if (betrag == null) {
            throw new InvalidIncomeException("betrag", "Einkommen ist erforderlich.");
        }
        if (betrag.signum() <= 0) {
            throw new InvalidIncomeException("betrag", "Einkommen muss grösser als 0 sein.");
        }
        if (betrag.stripTrailingZeros().scale() > RAPPEN_SCALE) {
            throw new InvalidIncomeException(
                    "betrag", "Einkommen darf höchstens zwei Nachkommastellen haben.");
        }
        if (betrag.compareTo(MAX_INCOME) > 0) {
            throw new InvalidIncomeException(
                    "betrag", "Einkommen darf 99'999'999.99 nicht überschreiten.");
        }
        return betrag.setScale(RAPPEN_SCALE, RoundingMode.UNNECESSARY);
    }

    /**
     * Markiert das Onboarding als abgeschlossen und liefert das aktualisierte Profil (US-03).
     *
     * <p>Idempotent: ein zweiter Aufruf ist kein Fehler, sondern liefert dasselbe Profil. Der
     * Wizard darf mehrfach abgeschlossen werden, ohne dass der Client den Zustand vorher prüfen
     * muss.
     *
     * @throws UserNotFoundException wenn kein User mit dieser ID existiert.
     */
    @Transactional
    public UserProfileResponse completeOnboarding(long userId) {
        User user = findUser(userId);
        user.completeOnboarding();
        return toResponse(user);
    }

    /**
     * Prüft das aktuelle Passwort und ersetzt bei Erfolg den gespeicherten Hash (BE-AUTH-09).
     *
     * @throws UserNotFoundException wenn kein User mit dieser ID existiert.
     * @throws InvalidCurrentPasswordException wenn {@code currentPassword} nicht mit dem
     *     gespeicherten Hash übereinstimmt — die Änderung findet dann nicht statt.
     */
    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Wirft bewusst <em>keine</em> {@link UserNotFoundException} bei unbekannter ID: der Port
     * dient der Fixkosten-Warnung, und dort ist «kein Vergleichswert vorhanden» das Ergebnis —
     * nicht ein Fehler, der den Aufrufer abbricht.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> findMonthlyIncome(long userId) {
        return userRepository.findById(userId).map(User::getMonthlyIncome);
    }

    /**
     * Löscht den User und alle abhängigen Daten (US-02, DB-07).
     *
     * <p>{@code transactions}, {@code import_jobs} und {@code fixed_costs} tragen alle eine
     * Fremdschlüssel auf {@code users} ohne {@code ON DELETE} — der User wird deshalb erst
     * gelöscht, <em>nachdem</em> beide Cleanup-Ports ihre Tabellen geräumt haben, sonst schlägt
     * die letzte Zeile am Constraint fehl. Bewusst kein {@code ON DELETE CASCADE}: die Löschung
     * bleibt eine sichtbare, einzeln testbare Operation im Code statt einer stillen
     * DB-Nebenwirkung (siehe {@code V05__create_import_jobs_table.sql}).
     *
     * @throws UserNotFoundException wenn kein User mit dieser ID existiert.
     */
    @Transactional
    public void deleteUser(long userId) {
        User user = findUser(userId);
        transactionCleanupPort.deleteAllForUser(userId);
        fixedCostCleanupPort.deleteAllForUser(userId);
        userRepository.delete(user);
    }

    private User findUser(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private UserProfileResponse toResponse(User user) {
        return UserProfileResponse.from(user);
    }
}
