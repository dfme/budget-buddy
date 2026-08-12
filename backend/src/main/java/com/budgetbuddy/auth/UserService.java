package com.budgetbuddy.auth;

import com.budgetbuddy.auth.dto.UserProfileResponse;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profil-Lese- und Einkommens-Update-Logik für das auth-Modul (BE-AUTH-02).
 *
 * <p>Implementiert zusätzlich den {@link UserIncomePort}, über den das {@code budget}-Modul das
 * Einkommen für die Fixkosten-Warnung liest (BE-FC-02) — ohne Zugriff auf {@link UserRepository}
 * oder die {@link User}-Entity über die Modulgrenze hinweg.
 */
@Service
public class UserService implements UserIncomePort {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
     * @throws UserNotFoundException wenn kein User mit dieser ID existiert.
     */
    @Transactional
    public UserProfileResponse updateIncome(long userId, BigDecimal betrag) {
        User user = findUser(userId);
        user.setMonthlyIncome(betrag);
        return toResponse(user);
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

    private User findUser(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private UserProfileResponse toResponse(User user) {
        return UserProfileResponse.from(user);
    }
}
