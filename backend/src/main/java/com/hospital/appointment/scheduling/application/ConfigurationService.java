package com.hospital.appointment.scheduling.application;

import com.hospital.appointment.identity.application.SecurityAudit;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ClinicConfiguration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class ConfigurationService {
    private final ConfigurationRepository repository;
    private final SecurityAudit audit;

    public ConfigurationService(ConfigurationRepository repository, SecurityAudit audit) {
        this.repository = repository;
        this.audit = audit;
    }

    /** Reads the current value for each operation; updated values never alter existing records. */
    @Transactional(readOnly = true)
    public ClinicConfiguration current() {
        return repository.current();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ClinicConfiguration update(UUID actor, int horizon, int hold, int cutoff, long version, String source) {
        if (horizon < 1 || horizon > 365 || hold < 1 || hold > 168 || cutoff < 1 || cutoff > 720) {
            throw SchedulingException.invalid("Configuration values are outside their permitted ranges.");
        }
        ClinicConfiguration result = repository.update(horizon, hold, cutoff, version);
        audit.record("clinic_configuration_updated", actor, source, Map.of("configurationVersion", Long.toString(result.version())));
        return result;
    }
}
