package com.hospital.appointment.identity.infrastructure;

import com.hospital.appointment.identity.application.IdentityEmailSender;
import com.hospital.appointment.identity.domain.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("identity-dev")
class DevelopmentIdentityEmailSender implements IdentityEmailSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentIdentityEmailSender.class);

    @Override
    public void sendVerification(UserAccount account, String code) {
        log(account, "verification");
    }

    @Override
    public void sendRecovery(UserAccount account, String code) {
        log(account, "recovery");
    }

    @Override
    public void sendMfa(UserAccount account, String code) {
        log(account, "MFA");
    }

    private void log(UserAccount account, String purpose) {
        LOGGER.warn("Development identity email [{}] generated for account {}; no external email was sent.", purpose, account.id());
    }
}
