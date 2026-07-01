package com.budgetbuddy.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registrierungs- und Login-Logik des auth-Moduls (BE-AUTH-03, US-01).
 *
 * <p>Passwörter werden ausschliesslich als bcrypt-Hash gespeichert (ADR-7). Der eigentliche
 * JWT-/Cookie-Aufbau liegt bewusst nicht hier, sondern im Controller ({@link JwtService},
 * {@link JwtCookieFactory}) — dieser Service kennt nur User-Persistenz und Credential-Prüfung.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Legt einen neuen User an (E-Mail + bcrypt-Hash) und liefert die persistierte Entity.
     *
     * @throws EmailAlreadyExistsException wenn die E-Mail bereits vergeben ist (→ 409).
     */
    @Transactional
    public User register(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        User user = new User(email, passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    /**
     * Prüft die Credentials und liefert bei Erfolg den User.
     *
     * @throws InvalidCredentialsException bei unbekannter E-Mail oder falschem Passwort (→ 401);
     *     bewusst nicht unterscheidbar (User-Enumeration-Schutz).
     */
    @Transactional(readOnly = true)
    public User login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }
}
