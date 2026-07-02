package com.budgetbuddy.auth;

import com.budgetbuddy.auth.dto.UserProfileResponse;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profil-Lese- und Einkommens-Update-Logik für das auth-Modul (BE-AUTH-02). */
@Service
public class UserService {

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

    private User findUser(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private UserProfileResponse toResponse(User user) {
        return UserProfileResponse.from(user);
    }
}
