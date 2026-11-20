package com.hospital.appointment.identity.application;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class DevelopmentCodeService {
    private final boolean enabled;
    private final String code;

    public DevelopmentCodeService(Environment environment) {
        this.enabled = environment.matchesProfiles("dev") && environment.matchesProfiles("identity-dev");
        this.code = enabled ? "123456" : null;
    }

    public String code() {
        return code;
    }
}
