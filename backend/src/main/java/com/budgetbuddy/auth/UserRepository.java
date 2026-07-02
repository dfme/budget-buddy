package com.budgetbuddy.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository-Zugriff auf {@link User} (auth-internes Interface, kein modulübergreifender Zugriff). */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Sucht einen User anhand der E-Mail (Login, BE-AUTH-03). */
    Optional<User> findByEmail(String email);

    /** Prüft, ob bereits ein User mit dieser E-Mail existiert (Duplikat-Check bei Registrierung). */
    boolean existsByEmail(String email);
}
