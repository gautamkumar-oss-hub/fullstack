package com.hospital.appointment.config;

import org.junit.jupiter.api.Test;
import org.springframework.validation.DataBinder;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPropertiesValidatorTest {

    @Test
    void rejectsAnInvalidClinicTimeZone() {
        ApplicationProperties properties = new ApplicationProperties("Test", "Invalid/Zone", 90, 24, 24);
        DataBinder binder = new DataBinder(properties);
        binder.addValidators(new ApplicationPropertiesValidator());

        binder.validate();

        assertThat(binder.getBindingResult().hasFieldErrors("clinicTimeZone")).isTrue();
    }
}
