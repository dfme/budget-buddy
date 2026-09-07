package com.budgetbuddy.auth;

import com.budgetbuddy.auth.dto.ChangePasswordRequest;
import com.budgetbuddy.auth.dto.IncomeErrorResponse;
import com.budgetbuddy.auth.dto.UpdateIncomeRequest;
import com.budgetbuddy.auth.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profil-Endpoints des eingeloggten Users (BE-AUTH-02).
 *
 * <p>Beide Endpoints sind durch {@code anyRequest().authenticated()} geschützt; die User-ID kommt
 * als Principal aus dem {@code JwtCookieAuthenticationFilter} (BE-AUTH-01). Ohne gültiges JWT
 * antwortet Spring Security mit 401, bevor der Controller erreicht wird.
 */
@RestController
@RequestMapping("/api/users/me")
@Tag(name = "User", description = "Profil des eingeloggten Users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Eigenes Profil abrufen",
            description = "Liefert Profil inkl. onboardingCompleted und monthlyIncome.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profil zurückgegeben"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public UserProfileResponse getCurrentUser(@AuthenticationPrincipal Long userId) {
        return userService.getProfile(userId);
    }

    @PutMapping("/income")
    @Operation(summary = "Monatliches Einkommen aktualisieren",
            description = "Setzt monthlyIncome. Der Betrag muss grösser als 0 sein, darf höchstens "
                    + "zwei Nachkommastellen tragen und 99'999'999.99 nicht überschreiten — die "
                    + "Kapazität von DECIMAL(10,2). Ein Wert mit mehr Nachkommastellen wird "
                    + "abgelehnt und nicht still gerundet (BE-AUTH-08); wertgleiche Schreibweisen "
                    + "wie 100.000 sind gültig.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Einkommen aktualisiert"),
        @ApiResponse(responseCode = "400",
                description = "betrag fehlt, ist <= 0, hat mehr als zwei Nachkommastellen, "
                        + "überschreitet 99'999'999.99 oder hat den falschen Typ; der Body nennt "
                        + "das Feld und die verletzte Regel",
                content = @Content(schema = @Schema(implementation = IncomeErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public UserProfileResponse updateIncome(
            @AuthenticationPrincipal Long userId, @RequestBody UpdateIncomeRequest request) {
        return userService.updateIncome(userId, request.betrag());
    }

    @PostMapping("/onboarding-complete")
    @Operation(summary = "Onboarding abschliessen",
            description = "Setzt onboardingCompleted auf true, sodass der Fixkosten-Wizard beim "
                    + "nächsten Öffnen der App nicht mehr erscheint (US-03). Idempotent: ein "
                    + "zweiter Aufruf liefert dasselbe Profil und ist kein Fehler.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Onboarding abgeschlossen"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public UserProfileResponse completeOnboarding(@AuthenticationPrincipal Long userId) {
        return userService.completeOnboarding(userId);
    }

    @PutMapping("/password")
    @Operation(summary = "Passwort ändern",
            description = "Prüft aktuellesPasswort gegen den gespeicherten bcrypt-Hash und "
                    + "ersetzt ihn bei Erfolg durch den Hash von neuesPasswort. Invalidiert dabei "
                    + "alle zuvor ausgestellten JWTs, inklusive des Cookies der aufrufenden "
                    + "Session (BE-AUTH-11) — der Client muss sich danach neu einloggen.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Passwort geändert", content = {}),
        @ApiResponse(responseCode = "400",
                description = "aktuellesPasswort falsch oder neuesPasswort unter 8 Zeichen"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public void changePassword(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request.aktuellesPasswort(), request.neuesPasswort());
    }
}
