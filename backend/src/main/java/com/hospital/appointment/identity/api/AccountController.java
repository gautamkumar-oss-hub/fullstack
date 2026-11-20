package com.hospital.appointment.identity.api;

import com.hospital.appointment.identity.application.AccountService;
import com.hospital.appointment.identity.application.AccountView;
import com.hospital.appointment.identity.application.AuthenticatedAccount;
import com.hospital.appointment.identity.infrastructure.AccountStateFilter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@SecurityRequirement(name = "bearerAuth")
class AccountController {
    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/me")
    IdentityApi.AccountResponse me(HttpServletRequest request) {
        AuthenticatedAccount principal = (AuthenticatedAccount) request.getAttribute(AccountStateFilter.ACCOUNT_ATTRIBUTE);
        return response(accounts.view(principal.accountId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    IdentityApi.AccountResponse create(@Valid @RequestBody IdentityApi.ManagedAccountRequest request, HttpServletRequest http) {
        return response(accounts.createManagedAccount(principal(http).accountId(), request.email(), request.password(), request.role(),
                request.firstName(), request.lastName(), request.phone(), source(http)));
    }

    @PatchMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    IdentityApi.AccountResponse update(@PathVariable UUID accountId, @Valid @RequestBody IdentityApi.UpdateAccountRequest request, HttpServletRequest http) {
        return response(accounts.updateManagedAccount(principal(http).accountId(), accountId, request.role(), request.status(),
                request.firstName(), request.lastName(), request.phone(), request.newPassword(), request.version(), source(http)));
    }

    @GetMapping("/patients/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF') or (hasRole('PATIENT') and #patientId.toString() == authentication.name)")
    IdentityApi.AccountResponse patient(@PathVariable UUID patientId) {
        AccountView account = accounts.view(patientId);
        if (account.role() != com.hospital.appointment.identity.domain.Role.PATIENT) {
            throw new com.hospital.appointment.identity.application.IdentityException(HttpStatus.NOT_FOUND, "account_not_found", "The account was not found.");
        }
        return response(account);
    }

    private static IdentityApi.AccountResponse response(AccountView view) {
        return new IdentityApi.AccountResponse(view.id(), view.email(), view.firstName(), view.lastName(), view.role(), view.status(),
                view.emailVerified(), view.forcePasswordChange(), view.authzVersion(), view.version());
    }

    private static AuthenticatedAccount principal(HttpServletRequest request) {
        return (AuthenticatedAccount) request.getAttribute(AccountStateFilter.ACCOUNT_ATTRIBUTE);
    }

    private static String source(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null ? "unknown" : address.substring(0, Math.min(address.length(), 100));
    }
}
