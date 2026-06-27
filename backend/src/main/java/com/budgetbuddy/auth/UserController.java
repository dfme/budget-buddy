package com.budgetbuddy.auth;

import com.budgetbuddy.auth.dto.UpdateIncomeRequest;
import com.budgetbuddy.auth.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/users/me")
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
            description = "Setzt monthlyIncome. Der Betrag muss grösser als 0 sein.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Einkommen aktualisiert"),
        @ApiResponse(responseCode = "400", description = "betrag fehlt oder ist <= 0", content = {}),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public UserProfileResponse updateIncome(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody UpdateIncomeRequest request) {
        return userService.updateIncome(userId, request.betrag());
    }
}
