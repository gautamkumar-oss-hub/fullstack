package com.hospital.appointment.scheduling.api;

import com.hospital.appointment.identity.application.AuthenticatedAccount;
import com.hospital.appointment.identity.infrastructure.AccountStateFilter;
import com.hospital.appointment.scheduling.application.DirectoryService;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.Doctor;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.Specialization;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/directory")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Directory")
class DirectoryController {
    private final DirectoryService directory;

    DirectoryController(DirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping("/specializations")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF') or #activeOnly")
    List<SchedulingApi.SpecializationResponse> specializations(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return directory.specializations(activeOnly).stream().map(DirectoryController::specialization).toList();
    }

    @PostMapping("/specializations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    SchedulingApi.SpecializationResponse createSpecialization(@Valid @RequestBody SchedulingApi.SpecializationRequest request, HttpServletRequest http) {
        return specialization(directory.createSpecialization(actor(http).accountId(), request.name(), source(http)));
    }

    @PatchMapping("/specializations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    SchedulingApi.SpecializationResponse updateSpecialization(@PathVariable UUID id, @Valid @RequestBody SchedulingApi.SpecializationRequest request,
                                                              @RequestParam long version, HttpServletRequest http) {
        return specialization(directory.updateSpecialization(actor(http).accountId(), id, request.name(), version, source(http)));
    }

    @DeleteMapping("/specializations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    SchedulingApi.SpecializationResponse deactivateSpecialization(@PathVariable UUID id, @RequestParam long version, HttpServletRequest http) {
        return specialization(directory.deactivateSpecialization(actor(http).accountId(), id, version, source(http)));
    }

    @GetMapping("/doctors")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF') or #activeOnly")
    List<SchedulingApi.DoctorResponse> doctors(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return directory.doctors(activeOnly).stream().map(DirectoryController::doctor).toList();
    }

    @PostMapping("/doctors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    SchedulingApi.DoctorResponse createDoctor(@Valid @RequestBody SchedulingApi.DoctorRequest request, HttpServletRequest http) {
        return doctor(directory.createDoctor(actor(http).accountId(), request.specializationId(), request.firstName(), request.lastName(), request.slotDurationMinutes(), source(http)));
    }

    @PatchMapping("/doctors/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    SchedulingApi.DoctorResponse updateDoctor(@PathVariable UUID id, @Valid @RequestBody SchedulingApi.DoctorRequest request,
                                              @RequestParam long version, HttpServletRequest http) {
        return doctor(directory.updateDoctor(actor(http).accountId(), id, request.specializationId(), request.firstName(), request.lastName(), request.slotDurationMinutes(), version, source(http)));
    }

    @DeleteMapping("/doctors/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    SchedulingApi.DoctorResponse deactivateDoctor(@PathVariable UUID id, @RequestParam long version, HttpServletRequest http) {
        return doctor(directory.deactivateDoctor(actor(http).accountId(), id, version, source(http)));
    }

    static SchedulingApi.SpecializationResponse specialization(Specialization value) {
        return new SchedulingApi.SpecializationResponse(value.id(), value.name(), value.active(), value.version());
    }

    static SchedulingApi.DoctorResponse doctor(Doctor value) {
        return new SchedulingApi.DoctorResponse(value.id(), value.specializationId(), value.firstName(), value.lastName(),
                value.slotDurationMinutes(), value.active(), value.version());
    }

    static AuthenticatedAccount actor(HttpServletRequest request) {
        return (AuthenticatedAccount) request.getAttribute(AccountStateFilter.ACCOUNT_ATTRIBUTE);
    }

    static String source(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null ? "unknown" : address.substring(0, Math.min(address.length(), 100));
    }
}
