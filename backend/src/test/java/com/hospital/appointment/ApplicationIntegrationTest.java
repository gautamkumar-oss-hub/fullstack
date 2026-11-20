package com.hospital.appointment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@org.springframework.test.context.ActiveProfiles({"dev", "identity-dev"})
class ApplicationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.1-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("springdoc.api-docs.enabled", () -> true);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void migratesRealPostgresAndUsesUtc() {
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        String sessionZone = jdbcTemplate.queryForObject("SHOW TIME ZONE", String.class);
        Integer extensions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);

        assertThat(migrations).isEqualTo(3);
        assertThat(sessionZone).isEqualToIgnoringCase("UTC");
        assertThat(extensions).isEqualTo(1);
    }

    @Test
    void exposesHealthAndTypedPublicConfiguration() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicTimeZone").value("UTC"))
                .andExpect(jsonPath("$.bookingHorizonDays").value(90));
    }

    @Test
    void exposesOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.info.title").value("Hospital Appointment API"))
                .andExpect(jsonPath("$.info.version").value("0.1.0"))
                .andExpect(jsonPath("$.paths['/api/v1/system/info'].get.responses['200'].content['application/json']").exists())
                .andExpect(jsonPath("$.components.schemas.SystemInfo.required").isArray());
    }

    @Test
    void openApiDescribesRuntimeResponsesSecurityAndRequiredFields() throws Exception {
        JsonNode contract = json.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(contract.at("/paths/~1api~1v1~1auth~1register/post/responses/202/content/application~1json").isObject()).isTrue();
        assertThat(contract.at("/paths/~1api~1v1~1auth~1recovery~1request/post/responses/202/content/application~1json").isObject()).isTrue();
        assertThat(contract.at("/paths/~1api~1v1~1auth~1login/post/responses/200/content/application~1json/schema/$ref").asString())
                .isEqualTo("#/components/schemas/LoginResponse");
        assertThat(contract.at("/paths/~1api~1v1~1auth~1login/post/responses/202/content/application~1json/schema/$ref").asString())
                .isEqualTo("#/components/schemas/MfaRequiredResponse");
        JsonNode logoutResponses = contract.at("/paths/~1api~1v1~1auth~1logout/post/responses");
        assertThat(logoutResponses.has("204")).isTrue();
        assertThat(logoutResponses.has("200")).isFalse();
        assertThat(logoutResponses.at("/204").has("content")).isFalse();

        JsonNode csrf = contract.at("/paths/~1api~1v1~1auth~1csrf/get");
        assertThat(csrf.has("parameters")).isFalse();
        assertThat(contract.at("/components/schemas/CsrfToken").isMissingNode()).isTrue();
        assertThat(contract.at("/components/schemas/CsrfResponse/required"))
                .containsExactlyInAnyOrder(json.valueToTree("csrfToken"));

        JsonNode bearer = contract.at("/components/securitySchemes/bearerAuth");
        assertThat(bearer.get("type").asString()).isEqualTo("http");
        assertThat(bearer.get("scheme").asString()).isEqualTo("bearer");
        assertThat(bearer.get("bearerFormat").asString()).isEqualTo("JWT");
        for (String operation : List.of("/paths/~1api~1v1~1accounts/post/security/0/bearerAuth",
                "/paths/~1api~1v1~1accounts~1me/get/security/0/bearerAuth",
                "/paths/~1api~1v1~1auth~1change-password/post/security/0/bearerAuth")) {
            assertThat(contract.at(operation).isArray()).as(operation).isTrue();
        }
        assertThat(contract.at("/paths/~1api~1v1~1auth~1login/post").has("security")).isFalse();
        assertThat(contract.at("/paths/~1api~1v1~1system~1info/get").has("security")).isFalse();

        for (String schema : List.of("ManagedAccountRequest", "UpdateAccountRequest")) {
            JsonNode roles = contract.at("/components/schemas/" + schema + "/properties/role/enum");
            assertThat(roles).contains(json.valueToTree("STAFF"), json.valueToTree("PATIENT"));
            assertThat(roles).doesNotContain(json.valueToTree("ADMIN"));
        }
        assertThat(contract.at("/components/schemas/AccountResponse/required")).containsExactlyInAnyOrder(
                json.valueToTree("id"), json.valueToTree("email"), json.valueToTree("firstName"), json.valueToTree("lastName"),
                json.valueToTree("role"), json.valueToTree("status"), json.valueToTree("emailVerified"),
                json.valueToTree("forcePasswordChange"), json.valueToTree("authzVersion"), json.valueToTree("version"));
        for (String schema : List.of("TokenResponse", "LoginResponse")) {
            assertThat(contract.at("/components/schemas/" + schema + "/required")).containsExactlyInAnyOrder(
                    json.valueToTree("accessToken"), json.valueToTree("tokenType"), json.valueToTree("expiresIn"), json.valueToTree("account"));
        }
        assertThat(contract.at("/components/schemas/MfaRequiredResponse/required"))
                .containsExactlyInAnyOrder(json.valueToTree("mfaRequired"));
        assertThat(contract.at("/components/schemas/MessageResponse/required"))
                .containsExactlyInAnyOrder(json.valueToTree("message"));

        String passwordPattern = "^(?=.*[A-Za-z])(?=.*[0-9]).{12,128}$";
        for (String pointer : List.of("/components/schemas/UpdateAccountRequest/properties/newPassword",
                "/components/schemas/ManagedAccountRequest/properties/password",
                "/components/schemas/RegisterRequest/properties/password",
                "/components/schemas/RecoveryRequest/properties/newPassword",
                "/components/schemas/ChangePasswordRequest/properties/newPassword")) {
            JsonNode password = contract.at(pointer);
            assertThat(password.get("pattern").asString()).as(pointer).isEqualTo(passwordPattern);
            assertThat(password.get("minLength").asInt()).isEqualTo(12);
            assertThat(password.get("maxLength").asInt()).isEqualTo(128);
        }
    }

    @Test
    void servesTheCommittedOpenApiContract() throws Exception {
        String live = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Path contract = Path.of(System.getProperty("user.dir")).resolveSibling("docs/openapi.json");
        assertThat(contract).isRegularFile();
        assertThat(withoutServerUrl(json.readTree(live)))
                .as("Update docs/openapi.json with frontend's sync:api command after an intentional API change")
                .isEqualTo(withoutServerUrl(json.readTree(Files.readString(contract))));
    }

    private JsonNode withoutServerUrl(JsonNode document) {
        ((ObjectNode) document).remove("servers");
        return document;
    }
}
