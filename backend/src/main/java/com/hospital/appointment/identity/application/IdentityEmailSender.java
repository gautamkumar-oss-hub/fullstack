package com.hospital.appointment.identity.application;

import com.hospital.appointment.identity.domain.UserAccount;

public interface IdentityEmailSender {
    void sendVerification(UserAccount account, String code);

    void sendRecovery(UserAccount account, String code);

    void sendMfa(UserAccount account, String code);
}
