package com.hospital.appointment.system;

import com.hospital.appointment.config.ApplicationProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System")
class SystemController {

    private final ApplicationProperties properties;
    private final com.hospital.appointment.scheduling.application.ConfigurationRepository configurations;

    SystemController(ApplicationProperties properties, com.hospital.appointment.scheduling.application.ConfigurationRepository configurations) {
        this.properties = properties;
        this.configurations = configurations;
    }

    @GetMapping(value = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Public application and scheduling configuration")
    @ApiResponse(responseCode = "200", content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = SystemInfo.class)))
    SystemInfo getInfo() {
        var configuration = configurations.current();
        return new SystemInfo(
                properties.name(),
                properties.clinicZoneId().getId(),
                configuration.bookingHorizonDays(),
                configuration.proposalHoldHours(),
                configuration.patientChangeCutoffHours());
    }

    record SystemInfo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String applicationName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String clinicTimeZone,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int bookingHorizonDays,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int proposalHoldHours,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int patientChangeCutoffHours) {
    }
}
