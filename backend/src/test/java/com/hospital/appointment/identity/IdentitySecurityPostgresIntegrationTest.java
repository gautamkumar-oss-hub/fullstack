package com.hospital.appointment.identity;

import com.hospital.appointment.identity.application.AccountService;
import com.hospital.appointment.identity.application.AuthService;
import com.hospital.appointment.identity.application.IdentityEmailSender;
import com.hospital.appointment.identity.domain.Role;
import com.hospital.appointment.identity.domain.UserAccount;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"application.identity.bootstrap.enabled=false", "application.identity.email.provider=test"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
@Import(IdentitySecurityPostgresIntegrationTest.EmailConfiguration.class)
class IdentitySecurityPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.1-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accounts;
    @Autowired AuthService auth;
    @Autowired TestEmailSender email;
    private String csrf;

    @BeforeEach
    void reset() throws Exception {
        jdbc.update("DELETE FROM identity_security_audit");
        jdbc.update("DELETE FROM identity_challenge");
        jdbc.update("DELETE FROM identity_refresh_session");
        jdbc.update("DELETE FROM identity_account");
        email.codes.clear();
        email.verificationCodes.clear();
        email.recoveryCodes.clear();
        email.mfaCodes.clear();
        csrf = json.readTree(mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("csrfToken").asText();
    }

    @Test
    void privilegedLoginRequiresRealMfaAndNeverReturnsDevelopmentCode() throws Exception {
        UserAccount admin = accounts.create("admin@example.test", "Admin-password-2026", Role.ADMIN,
                "Test", "Admin", null, true);
        MvcResult login = mutation(post("/api/v1/auth/login"), Map.of("email", admin.email(), "password", "Admin-password-2026"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist()).andExpect(jsonPath("$.developmentCode").doesNotExist()).andReturn();
        assertThat(login.getResponse().getCookies()).noneMatch(cookie -> AuthService.REFRESH_COOKIE.equals(cookie.getName()));
        String code = email.codes.get(admin.id());
        assertThat(code).isNotEqualTo("123456").matches("[0-9]{6}");
        JsonNode tokens = tokens(mutation(post("/api/v1/auth/mfa/verify"), Map.of("email", admin.email(), "code", code))
                .andExpect(status().isOk()).andExpect(jsonPath("$.account.role").value("ADMIN")).andReturn());
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", bearer(tokens))).andExpect(status().isOk());
        mutation(post("/api/v1/auth/mfa/verify"), Map.of("email", admin.email(), "code", "000000")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_security_audit WHERE event_type = 'mfa_failed' "
                + "AND account_id = ? AND details ->> 'reason' = 'invalid_challenge'", Integer.class, admin.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_security_audit WHERE details::text LIKE '%accessToken%'", Integer.class)).isZero();
    }

    @Test
    void wrongChallengeAttemptsPersistAndBoundCodeGuessing() throws Exception {
        String emailAddress = "attempts@example.test";
        mutation(post("/api/v1/auth/register"), Map.of("email", emailAddress, "password", "Patient-password-2026",
                "firstName", "Test", "lastName", "Patient")).andExpect(status().isAccepted());
        UUID accountId = jdbc.queryForObject("SELECT id FROM identity_account WHERE normalized_email = ?", UUID.class, emailAddress);
        for (int attempt = 1; attempt <= 5; attempt++) {
            mutation(post("/api/v1/auth/verification/confirm"), Map.of("email", emailAddress, "code", "000000"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_challenge"));
        }
        assertThat(jdbc.queryForObject("SELECT attempts FROM identity_challenge WHERE account_id = ?", Integer.class, accountId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_security_audit WHERE event_type = 'verification_failed' "
                + "AND account_id = ? AND details ->> 'challengeType' = 'EMAIL_VERIFICATION'", Integer.class, accountId)).isEqualTo(5);
        mutation(post("/api/v1/auth/verification/confirm"), Map.of("email", emailAddress, "code", email.codes.get(accountId)))
                .andExpect(status().isBadRequest());
        mutation(post("/api/v1/auth/login"), Map.of("email", emailAddress, "password", "Patient-password-2026"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forcedStaffPasswordChangeIsRestrictedAndRevokesTheOldSession() throws Exception {
        UserAccount staff = accounts.create("staff@example.test", "Initial-password-2026", Role.STAFF,
                "Test", "Staff", null, true);
        JsonNode initial = privilegedLogin(staff);
        assertThat(initial.at("/account/forcePasswordChange").asBoolean()).isTrue();
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", bearer(initial))).andExpect(status().isUnauthorized());
        Cookie refresh = refreshCookie(initial);
        mutation(post("/api/v1/auth/change-password").header("Authorization", bearer(initial)),
                Map.of("currentPassword", "Initial-password-2026", "newPassword", "Changed-password-2026")).andExpect(status().isOk());
        mutation(post("/api/v1/auth/refresh").cookie(refresh), null).andExpect(status().isUnauthorized());
        JsonNode changed = privilegedLogin(staff, "Changed-password-2026");
        assertThat(changed.at("/account/forcePasswordChange").asBoolean()).isFalse();
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", bearer(changed))).andExpect(status().isOk());
    }

    @Test
    void recoveryRevokesAccessAndRefreshAndDoesNotEnumerateOrExposeResendThrottle() throws Exception {
        JsonNode patient = registerPatient("recovery@example.test");
        UUID accountId = UUID.fromString(patient.get("account").get("id").asText());
        MvcResult first = mutation(post("/api/v1/auth/recovery/request"), Map.of("email", "recovery@example.test"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.developmentCode").doesNotExist()).andReturn();
        MvcResult repeat = mutation(post("/api/v1/auth/recovery/request"), Map.of("email", "recovery@example.test"))
                .andExpect(status().isAccepted()).andReturn();
        MvcResult unknown = mutation(post("/api/v1/auth/recovery/request"), Map.of("email", "unknown@example.test"))
                .andExpect(status().isAccepted()).andReturn();
        assertThat(repeat.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString())
                .isEqualTo(unknown.getResponse().getContentAsString());
        mutation(post("/api/v1/auth/recovery/confirm"), Map.of("email", "recovery@example.test", "code", email.codes.get(accountId),
                "newPassword", "Recovered-password-2026")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", bearer(patient))).andExpect(status().isUnauthorized());
        mutation(post("/api/v1/auth/refresh").cookie(refreshCookie(patient)), null).andExpect(status().isUnauthorized());
        mutation(post("/api/v1/auth/login"), Map.of("email", "recovery@example.test", "password", "Patient-password-2026"))
                .andExpect(status().isUnauthorized());
        mutation(post("/api/v1/auth/login"), Map.of("email", "recovery@example.test", "password", "Recovered-password-2026"))
                .andExpect(status().isOk());
    }

    @Test
    void adminManagementRevokesChangedAuthorizationAndRejectsStaleVersionsAndUnauthorizedCallers() throws Exception {
        JsonNode patient = registerPatient("managed@example.test");
        UUID patientId = UUID.fromString(patient.get("account").get("id").asText());
        UserAccount admin = accounts.create("manager@example.test", "Manager-password-2026", Role.ADMIN,
                "Test", "Manager", null, true);
        JsonNode administrator = privilegedLogin(admin);
        mutation(post("/api/v1/accounts").header("Authorization", bearer(patient)), Map.of("email", "forbidden@example.test",
                "password", "Forbidden-password-2026", "role", "PATIENT", "firstName", "No", "lastName", "Access"))
                .andExpect(status().isForbidden());
        mutation(post("/api/v1/accounts").header("Authorization", bearer(administrator)), Map.of("email", "managed-staff@example.test",
                "password", "Managed-password-2026", "role", "STAFF", "firstName", "Managed", "lastName", "Staff"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.forcePasswordChange").value(true));
        mutation(patch("/api/v1/accounts/" + patientId).header("Authorization", bearer(administrator)), Map.of("role", "PATIENT",
                "status", "SUSPENDED", "firstName", "Managed", "lastName", "Patient", "version", patient.at("/account/version").asLong())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", bearer(patient))).andExpect(status().isUnauthorized());
        mutation(post("/api/v1/auth/refresh").cookie(refreshCookie(patient)), null).andExpect(status().isUnauthorized());
        mutation(patch("/api/v1/accounts/" + patientId).header("Authorization", bearer(administrator)), Map.of("role", "PATIENT",
                "status", "ACTIVE", "firstName", "Managed", "lastName", "Patient", "version", 0)).andExpect(status().isConflict());
        mutation(patch("/api/v1/accounts/" + admin.id()).header("Authorization", bearer(administrator)), Map.of("role", "ADMIN",
                "status", "ACTIVE", "firstName", "Test", "lastName", "Manager", "version", 0)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/accounts/patients/" + patientId).header("Authorization", bearer(administrator)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/accounts/patients/" + patientId)).andExpect(status().isUnauthorized());

        UserAccount staff = accounts.create("role-change@example.test", "Staff-password-2026", Role.STAFF,
                "Role", "Change", null, true);
        JsonNode staffTokens = privilegedLogin(staff, "Staff-password-2026");
        mutation(patch("/api/v1/accounts/" + staff.id()).header("Authorization", bearer(administrator)), Map.of("role", "PATIENT",
                "status", "ACTIVE", "firstName", "Role", "lastName", "Change", "version", 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.forcePasswordChange").value(false));
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", bearer(staffTokens))).andExpect(status().isUnauthorized());
        mutation(post("/api/v1/auth/refresh").cookie(refreshCookie(staffTokens)), null).andExpect(status().isUnauthorized());
        Map<String, Object> authorizationAudit = jdbc.queryForMap("SELECT account_id, details FROM identity_security_audit "
                + "WHERE event_type = 'admin_authorization_changed' AND account_id = ?", staff.id());
        assertThat(authorizationAudit.get("account_id")).isEqualTo(staff.id());
        JsonNode details = json.readTree(authorizationAudit.get("details").toString());
        assertThat(details.get("actorAccountId").asString()).isEqualTo(admin.id().toString());
        assertThat(details.get("targetAccountId").asString()).isEqualTo(staff.id().toString());
        assertThat(details.get("previousRole").asString()).isEqualTo("STAFF");
        assertThat(details.get("newRole").asString()).isEqualTo("PATIENT");
        List<String> auditDetails = jdbc.queryForList("SELECT details::text FROM identity_security_audit", String.class);
        assertThat(String.join("", auditDetails)).doesNotContain("password", "token", "secret", "code", "Bearer");
    }

    @Test
    void outstandingVerificationCannotActivateSuspendedAccountOrConsumeItsCode() {
        UserAccount patient = accounts.create("suspended-verification@example.test", "Patient-password-2026", Role.PATIENT,
                "Test", "Patient", null, false);
        auth.requestVerification(patient.email(), "test");
        String code = email.verificationCodes.get(patient.id());
        jdbc.update("UPDATE identity_account SET status = 'SUSPENDED' WHERE id = ?", patient.id());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> auth.verifyEmail(patient.email(), code, "test"))
                .isInstanceOf(com.hospital.appointment.identity.application.IdentityException.class)
                .extracting("status").isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);

        Map<String, Object> state = jdbc.queryForMap("SELECT status, email_verified FROM identity_account WHERE id = ?", patient.id());
        assertThat(state).containsEntry("status", "SUSPENDED").containsEntry("email_verified", false);
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM identity_challenge WHERE account_id = ? AND challenge_type = 'EMAIL_VERIFICATION'",
                java.sql.Timestamp.class, patient.id())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_security_audit WHERE event_type = 'verification_failed' "
                + "AND account_id = ? AND details ->> 'reason' = 'account_suspended'", Integer.class, patient.id())).isEqualTo(1);
        assertThat(auditText()).doesNotContain(code);
    }

    @Test
    void recoveryMayCompletePendingVerificationButCannotClearSuspensionOrConsumeOutstandingCode() {
        UserAccount pending = accounts.create("pending-recovery@example.test", "Old-password-2026", Role.PATIENT,
                "Pending", "Patient", null, false);
        auth.requestRecovery(pending.email(), "test");
        auth.resetPassword(pending.email(), email.recoveryCodes.get(pending.id()), "New-password-2026", "test");
        Map<String, Object> recovered = jdbc.queryForMap("SELECT status, email_verified FROM identity_account WHERE id = ?", pending.id());
        assertThat(recovered).containsEntry("status", "ACTIVE").containsEntry("email_verified", true);
        assertThat(passwordLoginSucceeds(pending.email(), "New-password-2026")).isTrue();

        UserAccount suspended = accounts.create("suspended-recovery@example.test", "Old-password-2026", Role.PATIENT,
                "Suspended", "Patient", null, false);
        auth.requestRecovery(suspended.email(), "test");
        String code = email.recoveryCodes.get(suspended.id());
        String oldHash = jdbc.queryForObject("SELECT password_hash FROM identity_account WHERE id = ?", String.class, suspended.id());
        jdbc.update("UPDATE identity_account SET status = 'SUSPENDED' WHERE id = ?", suspended.id());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> auth.resetPassword(suspended.email(), code, "New-password-2026", "test"))
                .isInstanceOf(com.hospital.appointment.identity.application.IdentityException.class)
                .extracting("status").isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);

        Map<String, Object> state = jdbc.queryForMap("SELECT status, email_verified, password_hash FROM identity_account WHERE id = ?", suspended.id());
        assertThat(state).containsEntry("status", "SUSPENDED").containsEntry("email_verified", false).containsEntry("password_hash", oldHash);
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM identity_challenge WHERE account_id = ? AND challenge_type = 'PASSWORD_RECOVERY'",
                java.sql.Timestamp.class, suspended.id())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_security_audit WHERE event_type = 'recovery_failed' "
                + "AND account_id = ? AND details ->> 'reason' = 'account_suspended'", Integer.class, suspended.id())).isEqualTo(1);
        assertThat(auditText()).doesNotContain(code, "New-password-2026", oldHash);
    }

    @Test
    void administratorPasswordReplacementUsesSharedPolicyAndAuditsOnlyContext() throws Exception {
        UserAccount admin = accounts.create("policy-admin@example.test", "Admin-password-2026", Role.ADMIN,
                "Policy", "Admin", null, true);
        JsonNode administrator = privilegedLogin(admin, "Admin-password-2026");
        String createdResponse = mutation(post("/api/v1/accounts").header("Authorization", bearer(administrator)),
                Map.of("email", "policy-patient@example.test", "password", "Initial-password-2026", "role", "PATIENT",
                        "firstName", "Policy", "lastName", "Patient")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID target = UUID.fromString(json.readTree(createdResponse).get("id").asText());

        for (String replacement : List.of("letters-only-password", "123456789012")) {
            mutation(patch("/api/v1/accounts/" + target).header("Authorization", bearer(administrator)),
                    Map.of("role", "PATIENT", "status", "ACTIVE", "firstName", "Policy", "lastName", "Patient",
                            "newPassword", replacement, "version", 0)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.newPassword").exists());
        }
        assertThat(jdbc.queryForObject("SELECT version FROM identity_account WHERE id = ?", Long.class, target)).isZero();
        mutation(patch("/api/v1/accounts/" + target).header("Authorization", bearer(administrator)),
                Map.of("role", "PATIENT", "status", "ACTIVE", "firstName", "Policy", "lastName", "Patient",
                        "newPassword", "Replacement-password-2026", "version", 0)).andExpect(status().isOk());

        Map<String, Object> createdAudit = jdbc.queryForMap("SELECT account_id, details FROM identity_security_audit "
                + "WHERE event_type = 'admin_account_created' AND account_id = ?", target);
        assertThat(createdAudit.get("account_id")).isEqualTo(target);
        assertThat(json.readTree(createdAudit.get("details").toString()).get("actorAccountId").asText()).isEqualTo(admin.id().toString());
        for (String event : List.of("admin_account_updated", "admin_password_replaced")) {
            Map<String, Object> eventAudit = jdbc.queryForMap("SELECT account_id, details FROM identity_security_audit "
                    + "WHERE event_type = ? AND account_id = ?", event, target);
            assertThat(eventAudit.get("account_id")).isEqualTo(target);
            JsonNode details = json.readTree(eventAudit.get("details").toString());
            assertThat(details.get("actorAccountId").asText()).isEqualTo(admin.id().toString());
            assertThat(details.get("targetAccountId").asText()).isEqualTo(target.toString());
        }
        assertThat(auditText()).doesNotContain("Initial-password-2026", "Replacement-password-2026", "letters-only-password",
                "123456789012", "passwordHash", "secretHash");
    }

    private boolean passwordLoginSucceeds(String emailAddress, String password) {
        try {
            return auth.login(emailAddress, password, "test").session() != null;
        } catch (com.hospital.appointment.identity.application.IdentityException exception) {
            return false;
        }
    }

    private String auditText() {
        return String.join("", jdbc.queryForList("SELECT event_type || ' ' || details::text FROM identity_security_audit", String.class));
    }

    private JsonNode registerPatient(String emailAddress) throws Exception {
        mutation(post("/api/v1/auth/register"), Map.of("email", emailAddress, "password", "Patient-password-2026",
                "firstName", "Test", "lastName", "Patient")).andExpect(status().isAccepted());
        UUID id = jdbc.queryForObject("SELECT id FROM identity_account WHERE normalized_email = ?", UUID.class, emailAddress);
        mutation(post("/api/v1/auth/verification/confirm"), Map.of("email", emailAddress, "code", email.codes.get(id)))
                .andExpect(status().isOk());
        return tokens(mutation(post("/api/v1/auth/login"), Map.of("email", emailAddress, "password", "Patient-password-2026"))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode privilegedLogin(UserAccount account) throws Exception {
        return privilegedLogin(account, account.email().equals("staff@example.test") ? "Initial-password-2026"
                : account.email().equals("admin@example.test") ? "Admin-password-2026" : "Manager-password-2026");
    }

    private JsonNode privilegedLogin(UserAccount account, String password) throws Exception {
        mutation(post("/api/v1/auth/login"), Map.of("email", account.email(), "password", password)).andExpect(status().isAccepted());
        return tokens(mutation(post("/api/v1/auth/mfa/verify"), Map.of("email", account.email(), "code", email.codes.get(account.id())))
                .andExpect(status().isOk()).andReturn());
    }

    private org.springframework.test.web.servlet.ResultActions mutation(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                                                         Object body) throws Exception {
        request.cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf).header("Origin", "http://localhost:5173");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private JsonNode tokens(MvcResult result) throws Exception {
        ObjectNode value = (ObjectNode) json.readTree(result.getResponse().getContentAsString());
        value.put("testRefreshCookie", result.getResponse().getCookie(AuthService.REFRESH_COOKIE).getValue());
        return value;
    }

    private static Cookie refreshCookie(JsonNode tokens) {
        return new Cookie(AuthService.REFRESH_COOKIE, tokens.get("testRefreshCookie").asText());
    }

    private static String bearer(JsonNode tokens) {
        return "Bearer " + tokens.get("accessToken").asText();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EmailConfiguration {
        @Bean
        @Primary
        TestEmailSender testIdentityEmailSender() {
            return new TestEmailSender();
        }
    }

    static class TestEmailSender implements IdentityEmailSender {
        final Map<UUID, String> codes = new ConcurrentHashMap<>();
        final Map<UUID, String> verificationCodes = new ConcurrentHashMap<>();
        final Map<UUID, String> recoveryCodes = new ConcurrentHashMap<>();
        final Map<UUID, String> mfaCodes = new ConcurrentHashMap<>();
        private int counter = 100000;

        @Override public void sendVerification(UserAccount account, String code) {
            codes.put(account.id(), code);
            verificationCodes.put(account.id(), code);
        }
        @Override public void sendRecovery(UserAccount account, String code) {
            codes.put(account.id(), code);
            recoveryCodes.put(account.id(), code);
        }
        @Override public void sendMfa(UserAccount account, String code) {
            assertThat(code).isNotEqualTo("000000");
            assertThat(counter++).isLessThan(900000);
            codes.put(account.id(), code);
            mfaCodes.put(account.id(), code);
        }
    }
}
