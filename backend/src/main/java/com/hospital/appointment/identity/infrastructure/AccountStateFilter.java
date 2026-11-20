package com.hospital.appointment.identity.infrastructure;

import com.hospital.appointment.identity.application.AccountRepository;
import com.hospital.appointment.identity.application.AuthenticatedAccount;
import com.hospital.appointment.identity.domain.UserAccount;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class AccountStateFilter extends OncePerRequestFilter {
    public static final String ACCOUNT_ATTRIBUTE = AccountStateFilter.class.getName() + ".account";
    private final AccountRepository accounts;

    AccountStateFilter(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            try {
                UUID accountId = UUID.fromString(token.getName());
                UserAccount account = accounts.findById(accountId).orElseThrow(IllegalArgumentException::new);
                Number version = token.getToken().getClaim("authz_version");
                if (!account.canAuthenticate() || account.forcePasswordChange() && !request.getRequestURI().equals("/api/v1/auth/change-password")
                        || !account.role().name().equals(token.getToken().getClaimAsString("role"))
                        || version == null || version.longValue() != account.authzVersion()) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                            + "\"detail\":\"Your session is no longer active.\",\"code\":\"session_revoked\"}");
                    return;
                }
                request.setAttribute(ACCOUNT_ATTRIBUTE, new AuthenticatedAccount(account.id(), account.role(), account.authzVersion()));
            } catch (IllegalArgumentException exception) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                        + "\"detail\":\"Your session is no longer active.\",\"code\":\"session_revoked\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
