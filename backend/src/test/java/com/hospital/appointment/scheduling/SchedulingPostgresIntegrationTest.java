package com.hospital.appointment.scheduling;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"application.clinic-time-zone=America/New_York", "application.identity.bootstrap.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
@org.springframework.test.context.ActiveProfiles({"dev", "identity-dev"})
class SchedulingPostgresIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.1-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    String admin;
    String staff;
    String patient;
    String csrf;

    @BeforeEach
    void reset() throws Exception {
        jdbc.update("DELETE FROM doctor_schedule_override");
        jdbc.update("DELETE FROM doctor_weekly_availability");
        jdbc.update("DELETE FROM doctor");
        jdbc.update("DELETE FROM specialization");
        jdbc.update("UPDATE clinic_configuration SET booking_horizon_days=90, proposal_hold_hours=24, patient_change_cutoff_hours=24, version=0");
        jdbc.update("DELETE FROM identity_security_audit");
        jdbc.update("DELETE FROM identity_challenge");
        jdbc.update("DELETE FROM identity_refresh_session");
        jdbc.update("DELETE FROM identity_account");
        admin = account("ADMIN");
        staff = account("STAFF");
        patient = account("PATIENT");
        csrf = csrf();
    }

    @Test
    void authorizationMatrixAppliesToDirectorySchedulesAndConfiguration() throws Exception {
        UUID specialization = createSpecialization(admin, "Authorization");
        UUID doctor = createDoctor(admin, specialization);
        Map<String, Object> specializationBody = Map.of("name", "Other");
        assertPermission(specializationBody, 403, 403, 403, 201);
        assertDoctorPermission(doctor);
        mvc.perform(get("/api/v1/directory/specializations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/directory/specializations").header("Authorization", "Bearer " + patient)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/directory/specializations?activeOnly=false").header("Authorization", "Bearer " + patient)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/doctors/" + doctor + "/availability?from=2026-07-20&to=2026-07-21").header("Authorization", "Bearer " + patient)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/doctors/" + doctor + "/schedule").header("Authorization", "Bearer " + staff)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/configuration").header("Authorization", "Bearer " + staff)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/configuration").header("Authorization", "Bearer " + staff).cookie(new Cookie("XSRF-TOKEN", csrf))
                .header("X-XSRF-TOKEN", csrf).header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("bookingHorizonDays", 30, "proposalHoldHours", 24, "patientChangeCutoffHours", 24, "version", 0))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void directoryVersionsDeactivationHistoryAndAccountSeparationSurvive() throws Exception {
        UUID specialization = createSpecialization(admin, "Cardiology");
        UUID doctor = createDoctor(admin, specialization);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='doctor' AND column_name LIKE '%account%'", Integer.class)).isZero();
        mvc.perform(post("/api/v1/auth/login").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                .header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "doctor@example.test", "password", "A-strong-password-2026"))))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_account WHERE normalized_email='doctor@example.test'", Integer.class)).isZero();
        mvc.perform(delete("/api/v1/directory/specializations/" + specialization + "?version=99").header("Authorization", "Bearer " + admin)
                .cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf).header("Origin", "http://localhost:5173"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("resource_changed"));
        mvc.perform(delete("/api/v1/directory/specializations/" + specialization + "?version=0").header("Authorization", "Bearer " + admin)
                .cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf).header("Origin", "http://localhost:5173"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("duplicate_or_overlap"));
        mvc.perform(delete("/api/v1/directory/doctors/" + doctor + "?version=0").header("Authorization", "Bearer " + admin)
                .cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf).header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(delete("/api/v1/directory/specializations/" + specialization + "?version=0").header("Authorization", "Bearer " + admin)
                .cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf).header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/directory/doctors?activeOnly=false").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].active").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM doctor WHERE id=?", Integer.class, doctor)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_security_audit WHERE event_type='doctor_deactivated'", Integer.class)).isOne();
    }

    @Test
    void validatesScheduleOverlapDstOverridesOptimisticVersionsAndPreview() throws Exception {
        UUID specialization = createSpecialization(admin, "Scheduling");
        UUID doctor = createDoctor(admin, specialization);
        Map<String, Object> weekly = Map.of("weekday", "MONDAY", "startLocal", "09:00:00", "endLocal", "11:00:00", "effectiveFrom", "2026-07-01", "effectiveTo", "2027-01-31", "version", 0);
        mvc.perform(authenticatedPost("/api/v1/doctors/" + doctor + "/schedule", admin, weekly)).andExpect(status().isCreated());
        mvc.perform(authenticatedPost("/api/v1/doctors/" + doctor + "/schedule", admin, weekly)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("duplicate_or_overlap"));
        Map<String, Object> invalid = new java.util.HashMap<>(weekly);
        invalid.put("startLocal", "12:00:00");
        mvc.perform(authenticatedPost("/api/v1/doctors/" + doctor + "/schedule", admin, invalid)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation_failed"));
        Map<String, Object> gap = Map.of("weekday", "SUNDAY", "startLocal", "02:30:00", "endLocal", "03:30:00", "effectiveFrom", "2026-03-08", "effectiveTo", "2026-03-08", "version", 0);
        mvc.perform(authenticatedPost("/api/v1/doctors/" + doctor + "/schedule", admin, gap)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("gap")));
        Map<String, Object> ambiguous = new java.util.HashMap<>(gap);
        ambiguous.put("startLocal", "01:30:00"); ambiguous.put("endLocal", "02:30:00"); ambiguous.put("effectiveFrom", "2026-11-01"); ambiguous.put("effectiveTo", "2026-11-01");
        mvc.perform(authenticatedPost("/api/v1/doctors/" + doctor + "/schedule", admin, ambiguous)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ambiguous")));
        Map<String, Object> leave = Map.of("date", "2026-12-21", "type", "LEAVE", "startLocal", "09:30:00", "endLocal", "10:00:00", "reason", "Synthetic leave", "version", 0);
        mvc.perform(authenticatedPost("/api/v1/doctors/" + doctor + "/overrides", admin, leave)).andExpect(status().isCreated());
        mvc.perform(authenticatedPost("/api/v1/doctors/" + doctor + "/overrides", admin, leave)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/doctors/" + doctor + "/availability?from=2026-12-21&to=2026-12-21").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clinicTimeZone").value("America/New_York")).andExpect(jsonPath("$.slots.length()").value(3))
                .andExpect(jsonPath("$.slots[0].startAt").value("2026-12-21T14:00:00Z"));
        mvc.perform(get("/api/v1/doctors/" + doctor + "/schedule-conflict-preview?from=2026-07-20&to=2026-07-21").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allocationChecksEnforced").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Phase 5")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE '%allocation%'", Integer.class)).isZero();
    }

    @Test
    void realPostgresEnforcesConstraintsAndAuditsOnlySafeConfigurationValues() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conname IN ('doctor_weekly_no_overlap', 'doctor_override_no_overlap')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname IN ('doctor_specialization_active_idx','doctor_weekly_lookup_idx','doctor_override_lookup_idx')", Integer.class)).isEqualTo(3);
        UUID specialization = createSpecialization(admin, "Configuration");
        UUID doctor = createDoctor(admin, specialization);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("UPDATE doctor SET slot_duration_minutes=7 WHERE id=?", doctor)).hasMessageContaining("slot_duration_minutes");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("INSERT INTO doctor (id, specialization_id, first_name, last_name, slot_duration_minutes, active, created_at, updated_at) VALUES (?, ?, 'No', 'Specialty', 30, true, now(), now())", UUID.randomUUID(), UUID.randomUUID())).hasMessageContaining("foreign key");
        mvc.perform(authenticatedPatch("/api/v1/configuration", admin, Map.of("bookingHorizonDays", 30, "proposalHoldHours", 48, "patientChangeCutoffHours", 72, "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(authenticatedPatch("/api/v1/configuration", admin, Map.of("bookingHorizonDays", 45, "proposalHoldHours", 48, "patientChangeCutoffHours", 72, "version", 0)))
                .andExpect(status().isConflict());
        String audit = jdbc.queryForObject("SELECT details::text FROM identity_security_audit WHERE event_type='clinic_configuration_updated'", String.class);
        assertThat(audit).contains("configurationVersion").doesNotContain("password").doesNotContain("token").doesNotContain("30");
    }

    private void assertPermission(Map<String, Object> body, int anonymous, int patientStatus, int staffStatus, int adminStatus) throws Exception {
        mvc.perform(post("/api/v1/directory/specializations").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().is(anonymous));
        mvc.perform(authenticatedPost("/api/v1/directory/specializations", patient, body)).andExpect(status().is(patientStatus));
        mvc.perform(authenticatedPost("/api/v1/directory/specializations", staff, body)).andExpect(status().is(staffStatus));
        mvc.perform(authenticatedPost("/api/v1/directory/specializations", admin, body)).andExpect(status().is(adminStatus));
    }

    private void assertDoctorPermission(UUID doctor) throws Exception {
        Map<String, Object> body = Map.of("weekday", "MONDAY", "startLocal", "09:00:00", "endLocal", "10:00:00", "effectiveFrom", "2026-07-01", "effectiveTo", "2026-07-31", "version", 0);
        mvc.perform(authenticatedPost("/api/v1/doctors/" + doctor + "/schedule", patient, body)).andExpect(status().isForbidden());
        mvc.perform(authenticatedPost("/api/v1/doctors/" + doctor + "/schedule", staff, body)).andExpect(status().isForbidden());
    }

    private UUID createSpecialization(String token, String name) throws Exception {
        MvcResult result = mvc.perform(authenticatedPost("/api/v1/directory/specializations", token, Map.of("name", name))).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createDoctor(String token, UUID specialization) throws Exception {
        MvcResult result = mvc.perform(authenticatedPost("/api/v1/directory/doctors", token, Map.of("specializationId", specialization, "firstName", "Directory", "lastName", "Doctor", "slotDurationMinutes", 30)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedPost(String path, String token, Object body) {
        return post(path).header("Authorization", "Bearer " + token).cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                .header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedPatch(String path, String token, Object body) {
        return patch(path).header("Authorization", "Bearer " + token).cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                .header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private String account(String role) throws Exception {
        String email = role.toLowerCase() + "@example.test";
        String csrf = csrf();
        MvcResult created = mvc.perform(post("/api/v1/auth/register").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                .header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "email", email, "password", "A-strong-password-2026", "firstName", role, "lastName", "Test")))).andExpect(status().isAccepted()).andReturn();
        String code = json.readTree(created.getResponse().getContentAsString()).get("developmentCode").asText();
        mvc.perform(post("/api/v1/auth/verification/confirm").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                .header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "code", code)))).andExpect(status().isOk());
        jdbc.update("UPDATE identity_account SET role=?, force_password_change=false, authz_version=authz_version+1 WHERE normalized_email=?", role, email);
        MvcResult login = mvc.perform(post("/api/v1/auth/login").cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                .header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "password", "A-strong-password-2026"))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String csrf() throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("csrfToken").asText();
    }
}
