package com.budgetbuddy.auth;

import com.budgetbuddy.auth.dto.UserProfileResponse;
import com.budgetbuddy.budget.FixedCostCleanupPort;
import com.budgetbuddy.money.ChfAmounts;
import com.budgetbuddy.transaction.TransactionCleanupPort;
import java.math.BigDecimal;
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
 * <p><strong>Die Prüfung von {@code monthlyIncome} steht im Service, nicht am DTO</strong>
 * (BE-AUTH-08). {@code UpdateIncomeRequest} trägt bewusst keine Bean-Validation-Annotationen —
 * dieselbe Aufteilung wie bei {@code FixedCostRequest}/{@code FixedCostService}, und aus denselben
 * zwei Gründen: Annotationen greifen erst, wenn ein Controller {@code @Valid} setzt (der Service
 * wäre also ungeschützt, sobald ihn jemand anders aufruft), und dieselbe Regel an zwei Stellen
 * läuft irgendwann auseinander.
 *
 * <p>Die CHF-Regel selbst liegt seit BE-FC-04 in {@link ChfAmounts} — genau deshalb, weil sie
 * zuvor hier <em>und</em> in {@code FixedCostService} stand. Modul-lokal bleiben die beiden Dinge,
 * die sich unterscheiden sollen: {@link InvalidIncomeException} und ihre feldspezifischen Texte.
 */
@Service
public class UserService implements UserIncomePort {

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
     *     Nachkommastellen tragen und {@link ChfAmounts#MAX} nicht überschreiten.
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
     * <p>Die Regel liefert {@link ChfAmounts} — dieselbe, die {@code FixedCostService} für
     * {@code fixed_costs.betrag} anwendet, inklusive der {@code stripTrailingZeros()}-Feinheit und
     * der Kapazitätsgrenze der {@code DECIMAL(10,2)}-Spalte. Hier bleibt nur, was das auth-Modul
     * ausmacht: {@link InvalidIncomeException} und der Text, der «Einkommen» sagt statt «Betrag».
     */
    private static BigDecimal validateBetrag(BigDecimal betrag) {
        ChfAmounts.check(betrag)
                .ifPresent(
                        violation -> {
                            throw new InvalidIncomeException("betrag", meldung(violation));
                        });
        return ChfAmounts.toRappen(betrag);
    }

    /**
     * Der feldspezifische Text zu einer verletzten Regel.
     *
     * <p>Bewusst nicht in {@link ChfAmounts}: {@code FixedCostService} formuliert dieselben vier
     * Fälle mit «Betrag» statt «Einkommen». US-03 und #148 verlangen feldspezifische Meldungen —
     * geteilt wird die Prüfung, nicht der Text.
     */
    private static String meldung(ChfAmounts.Violation violation) {
        return switch (violation) {
            case FEHLT -> "Einkommen ist erforderlich.";
            case NICHT_POSITIV -> "Einkommen muss grösser als 0 sein.";
            case ZU_VIELE_NACHKOMMASTELLEN ->
                    "Einkommen darf höchstens zwei Nachkommastellen haben.";
            case UEBER_MAXIMUM ->
                    "Einkommen darf " + ChfAmounts.MAX_FORMATTED + " nicht überschreiten.";
        };
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
     * <p>Erhöht zusätzlich {@code tokenVersion} (BE-AUTH-11, #201): jedes zuvor ausgestellte JWT
     * wird damit beim nächsten Request ungültig, inklusive des Cookies der aufrufenden Session —
     * bewusst kein automatischer Cookie-Reissue, der Client muss sich neu einloggen.
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
        user.invalidateTokenVersion();
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
