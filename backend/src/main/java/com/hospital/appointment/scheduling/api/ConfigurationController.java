package com.hospital.appointment.scheduling.api;

import com.hospital.appointment.scheduling.application.ConfigurationService;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ClinicConfiguration;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/configuration")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Configuration")
class ConfigurationController {
    private final ConfigurationService configurations;

    ConfigurationController(ConfigurationService configurations) {
        this.configurations = configurations;
    }

    @GetMapping
    SchedulingApi.ConfigurationResponse current() {
        return response(configurations.current());
    }

    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    SchedulingApi.ConfigurationResponse update(@Valid @RequestBody SchedulingApi.ConfigurationRequest request, HttpServletRequest http) {
        return response(configurations.update(DirectoryController.actor(http).accountId(), request.bookingHorizonDays(), request.proposalHoldHours(),
                request.patientChangeCutoffHours(), request.version(), DirectoryController.source(http)));
    }

    private static SchedulingApi.ConfigurationResponse response(ClinicConfiguration value) {
        return new SchedulingApi.ConfigurationResponse(value.clinicTimeZone(), value.bookingHorizonDays(), value.proposalHoldHours(),
                value.patientChangeCutoffHours(), value.version());
    }
}
