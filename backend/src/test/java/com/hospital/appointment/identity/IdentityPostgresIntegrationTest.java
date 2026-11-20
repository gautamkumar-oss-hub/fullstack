package com.hospital.appointment.identity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hospital.appointment.identity.application.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"application.identity.bootstrap.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
@org.springframework.test.context.ActiveProfiles({"dev", "identity-dev"})
class IdentityPostgresIntegrationTest {
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

    @org.junit.jupiter.api.BeforeEach
    void clearIdentityData() {
        jdbc.update("DELETE FROM identity_security_audit");
        jdbc.update("DELETE FROM identity_challenge");
        jdbc.update("DELETE FROM identity_refresh_session");
        jdbc.update("DELETE FROM identity_account");
    }

    @Test
    void patientRegistrationVerificationLoginAndOwnershipWorkWithRealPostgres() throws Exception {
        String email = "Patient.Test+" + UUID.randomUUID() + "@Example.test";
        String csrf = csrf();
        MvcResult registration = mvc.perform(post("/api/v1/auth/register").cookie(new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf).header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "email", email, "password", "A-strong-password-2026", "firstName", "Test", "lastName", "Patient"))))
                .andExpect(status().isAccepted()).andReturn();
        String code = json.readTree(registration.getResponse().getContentAsString()).get("developmentCode").asText();
        mvc.perform(post("/api/v1/auth/verification/confirm").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                        .header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "code", code))))
                .andExpect(status().isOk());
        MvcResult login = mvc.perform(post("/api/v1/auth/login").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                        .header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email.toLowerCase(), "password", "A-strong-password-2026"))))
                .andExpect(status().isOk()).andExpect(cookie().httpOnly(AuthService.REFRESH_COOKIE, true))
                .andExpect(cookie().sameSite(AuthService.REFRESH_COOKIE, "Strict"))
                .andExpect(cookie().path(AuthService.REFRESH_COOKIE, "/api/v1/auth"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("accessToken"))))
                .andReturn();
        JsonNode tokens = json.readTree(login.getResponse().getContentAsString());
        String accountId = tokens.at("/account/id").asText();
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email.toLowerCase()));
        mvc.perform(get("/api/v1/accounts/patients/" + UUID.randomUUID()).header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isForbidden());
        String normalized = jdbc.queryForObject("SELECT normalized_email FROM identity_account WHERE id = ?", String.class, UUID.fromString(accountId));
        assertThat(normalized).isEqualTo(email.toLowerCase());
        assertThat(tokens.get("accessToken").asText().split("\\.")).hasSize(3);
    }

    @Test
    void rejectsPreLoginCsrfAndInvalidOriginAndRotatesAndRevokesReusedRefreshFamily() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@example.test";
        String csrf = csrf();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "Valid-password-2026", "firstName", "R", "lastName", "User"))))
                .andExpect(status().isForbidden());
        MvcResult registration = mvc.perform(post("/api/v1/auth/register").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                        .header("Origin", "https://attacker.invalid").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "Valid-password-2026", "firstName", "R", "lastName", "User"))))
                .andExpect(status().isForbidden()).andReturn();
        registration = mvc.perform(post("/api/v1/auth/register").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                        .header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "Valid-password-2026", "firstName", "R", "lastName", "User"))))
                .andExpect(status().isAccepted()).andReturn();
        String code = json.readTree(registration.getResponse().getContentAsString()).get("developmentCode").asText();
        mvc.perform(post("/api/v1/auth/verification/confirm").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                .header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "code", code)))).andExpect(status().isOk());
        MvcResult login = mvc.perform(post("/api/v1/auth/login").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                        .header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "Valid-password-2026")))).andExpect(status().isOk()).andReturn();
        Cookie oldRefresh = login.getResponse().getCookie(AuthService.REFRESH_COOKIE);
        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                        .header("Origin", "http://localhost:5173").cookie(oldRefresh)).andExpect(status().isOk()).andReturn();
        Cookie newRefresh = rotated.getResponse().getCookie(AuthService.REFRESH_COOKIE);
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                        .header("Origin", "http://localhost:5173").cookie(oldRefresh)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                        .header("Origin", "http://localhost:5173").cookie(newRefresh)).andExpect(status().isUnauthorized());
        Integer revocations = jdbc.queryForObject("SELECT count(*) FROM identity_security_audit WHERE event_type = 'refresh_reuse_detected'", Integer.class);
        assertThat(revocations).isGreaterThanOrEqualTo(1);
    }

    @Test
    void publicJwksContainsOnlyPublicRsaMaterial() throws Exception {
        MvcResult result = mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();
        assertThat(content).contains("RS256").doesNotContain("\"d\"").doesNotContain("\"p\"").doesNotContain("PRIVATE KEY");
    }

    private String csrf() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("csrfToken").asText();
    }
}
