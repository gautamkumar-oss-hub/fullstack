package com.hospital.appointment.identity.api;

import com.hospital.appointment.identity.application.AuthService;
import com.hospital.appointment.identity.application.AuthSession;
import com.hospital.appointment.identity.application.AuthenticatedAccount;
import com.hospital.appointment.identity.infrastructure.AccountStateFilter;
import com.hospital.appointment.identity.infrastructure.IdentityProperties;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
    private final AuthService authService;
    private final IdentityProperties properties;

    AuthController(AuthService authService, IdentityProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/register")
    @ApiResponses({
            @ApiResponse(responseCode = "202", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = IdentityApi.MessageResponse.class)))
    })
    ResponseEntity<IdentityApi.MessageResponse> register(@Valid @RequestBody IdentityApi.RegisterRequest request, HttpServletRequest http) {
        AuthService.ChallengeReceipt receipt = authService.register(request.email(), request.password(), request.firstName(), request.lastName(), request.phone(), source(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new IdentityApi.MessageResponse(
                "If the registration can be accepted, a verification code will be sent.", receipt.developmentCode()));
    }

    @PostMapping("/login")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = IdentityApi.LoginResponse.class))),
            @ApiResponse(responseCode = "202", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = IdentityApi.MfaRequiredResponse.class)))
    })
    ResponseEntity<?> login(@Valid @RequestBody IdentityApi.LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request.email(), request.password(), source(http));
        if (result.mfaRequired()) {
            return ResponseEntity.accepted().body(new IdentityApi.MfaRequiredResponse(true, result.developmentCode()));
        }
        AuthSession session = result.session();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), properties.refreshIdleLifetime()).toString());
        return ResponseEntity.ok(new IdentityApi.LoginResponse(session.accessToken(), "Bearer", session.expiresInSeconds(), accountResponse(session)));
    }

    @PostMapping("/mfa/verify")
    ResponseEntity<IdentityApi.TokenResponse> verifyMfa(@Valid @RequestBody IdentityApi.CodeRequest request, HttpServletRequest http, HttpServletResponse response) {
        return tokenResponse(authService.verifyMfa(request.email(), request.code(), source(http)), response);
    }

    @PostMapping("/refresh")
    ResponseEntity<IdentityApi.TokenResponse> refresh(
            @CookieValue(name = AuthService.REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest http, HttpServletResponse response) {
        return tokenResponse(authService.refresh(refreshToken, source(http)), response);
    }

    @PostMapping("/logout")
    @ApiResponses({
            @ApiResponse(responseCode = "204", content = @Content)
    })
    ResponseEntity<Void> logout(@CookieValue(name = AuthService.REFRESH_COOKIE, required = false) String refreshToken,
                                HttpServletRequest http, HttpServletResponse response) {
        authService.logout(refreshToken, source(http));
        expireRefresh(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verification/request")
    IdentityApi.MessageResponse requestVerification(@Valid @RequestBody IdentityApi.EmailRequest request, HttpServletRequest http) {
        AuthService.ChallengeReceipt receipt = authService.requestVerification(request.email(), source(http));
        return new IdentityApi.MessageResponse("If verification is needed, a code will be sent.", receipt.developmentCode());
    }

    @PostMapping("/verification/confirm")
    IdentityApi.MessageResponse verifyEmail(@Valid @RequestBody IdentityApi.CodeRequest request, HttpServletRequest http) {
        authService.verifyEmail(request.email(), request.code(), source(http));
        return new IdentityApi.MessageResponse("Email verification is complete.", null);
    }

    @PostMapping("/recovery/request")
    @ApiResponses({
            @ApiResponse(responseCode = "202", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = IdentityApi.MessageResponse.class)))
    })
    ResponseEntity<IdentityApi.MessageResponse> requestRecovery(@Valid @RequestBody IdentityApi.EmailRequest request, HttpServletRequest http) {
        AuthService.ChallengeReceipt receipt = authService.requestRecovery(request.email(), source(http));
        return ResponseEntity.accepted().body(new IdentityApi.MessageResponse(
                "If the account can be recovered, a recovery code will be sent.", receipt.developmentCode()));
    }

    @PostMapping("/recovery/confirm")
    IdentityApi.MessageResponse recover(@Valid @RequestBody IdentityApi.RecoveryRequest request, HttpServletRequest http) {
        authService.resetPassword(request.email(), request.code(), request.newPassword(), source(http));
        return new IdentityApi.MessageResponse("Password recovery is complete. Sign in with your new password.", null);
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    IdentityApi.MessageResponse changePassword(@Valid @RequestBody IdentityApi.ChangePasswordRequest request, HttpServletRequest http, HttpServletResponse response) {
        AuthenticatedAccount principal = (AuthenticatedAccount) http.getAttribute(AccountStateFilter.ACCOUNT_ATTRIBUTE);
        authService.changePassword(principal.accountId(), request.currentPassword(), request.newPassword(), source(http));
        expireRefresh(response);
        return new IdentityApi.MessageResponse("Password changed. Sign in again.", null);
    }

    @GetMapping("/csrf")
    IdentityApi.CsrfResponse csrf(@Parameter(hidden = true) CsrfToken token) {
        return new IdentityApi.CsrfResponse(token.getToken());
    }

    private ResponseEntity<IdentityApi.TokenResponse> tokenResponse(AuthSession session, HttpServletResponse response) {
        if (session.freshLogin() || !session.account().forcePasswordChange()) {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), properties.refreshIdleLifetime()).toString());
        }
        return ResponseEntity.ok(new IdentityApi.TokenResponse(session.accessToken(), "Bearer", session.expiresInSeconds(), accountResponse(session)));
    }

    private static IdentityApi.AccountResponse accountResponse(AuthSession session) {
        return new IdentityApi.AccountResponse(session.account().id(), session.account().email(), session.account().firstName(),
                session.account().lastName(), session.account().role(), session.account().status(), session.account().emailVerified(),
                session.account().forcePasswordChange(), session.account().authzVersion(), session.account().version());
    }

    private void expireRefresh(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie refreshCookie(String value, Duration lifetime) {
        return ResponseCookie.from(AuthService.REFRESH_COOKIE, value).httpOnly(true).secure(properties.cookieSecure())
                .sameSite("Strict").path("/api/v1/auth").maxAge(lifetime).build();
    }

    private static String source(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null ? "unknown" : address.substring(0, Math.min(address.length(), 100));
    }
}
