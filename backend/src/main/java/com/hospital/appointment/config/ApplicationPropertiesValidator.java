package com.hospital.appointment.config;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
class ApplicationPropertiesValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return ApplicationProperties.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ApplicationProperties properties = (ApplicationProperties) target;
        try {
            properties.clinicZoneId();
        } catch (IllegalArgumentException exception) {
            errors.rejectValue("clinicTimeZone", "invalid.time.zone", exception.getMessage());
        }
    }
}
