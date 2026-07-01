package com.budgetbuddy.auth;

import com.budgetbuddy.auth.dto.LoginRequest;
import com.budgetbuddy.auth.dto.RegisterRequest;
import com.budgetbuddy.auth.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth-Endpoints für Registrierung, Login und Logout (BE-AUTH-03, US-01).
 *
 * <p>Bei Register/Login wird ein signiertes JWT (HS256) als httpOnly-Cookie gesetzt; ab dann
 * authentifiziert der {@link JwtCookieAuthenticationFilter} alle Requests. Logout invalidiert das
 * Cookie via {@code Max-Age=0}. {@code /auth/**} ist in {@code SecurityConfig} öffentlich.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Registrierung, Login und Logout")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final JwtCookieFactory cookieFactory;

    public AuthController(
            AuthService authService, JwtService jwtService, JwtCookieFactory cookieFactory) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookieFactory = cookieFactory;
    }

    @PostMapping("/register")
    @Operation(summary = "Konto erstellen",
            description = "Legt ein Konto an (bcrypt-Hash) und setzt direkt ein JWT-Cookie.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Konto erstellt, JWT-Cookie gesetzt"),
        @ApiResponse(responseCode = "400", description = "E-Mail/Passwort ungültig", content = {}),
        @ApiResponse(responseCode = "409", description = "E-Mail bereits registriert", content = {})
    })
    public ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.email(), request.password());
        return authenticatedResponse(user, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Einloggen",
            description = "Prüft Credentials und setzt bei Erfolg ein JWT-Cookie (SameSite=Strict).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Eingeloggt, JWT-Cookie gesetzt"),
        @ApiResponse(responseCode = "400", description = "E-Mail/Passwort fehlt", content = {}),
        @ApiResponse(responseCode = "401", description = "Ungültige Anmeldedaten", content = {})
    })
    public ResponseEntity<UserProfileResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request.email(), request.password());
        return authenticatedResponse(user, HttpStatus.OK);
    }

    @PostMapping("/logout")
    @Operation(summary = "Ausloggen",
            description = "Invalidiert das JWT-Cookie sofort (Max-Age=0).")
    @ApiResponse(responseCode = "204", description = "JWT-Cookie invalidiert")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    private ResponseEntity<UserProfileResponse> authenticatedResponse(User user, HttpStatus status) {
        ResponseCookie cookie = cookieFactory.create(jwtService.generateToken(user.getId()));
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(UserProfileResponse.from(user));
    }
}
