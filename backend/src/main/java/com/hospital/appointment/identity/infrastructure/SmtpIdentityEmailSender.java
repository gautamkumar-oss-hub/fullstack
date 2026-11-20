package com.hospital.appointment.identity.infrastructure;

import com.hospital.appointment.identity.application.IdentityEmailSender;
import com.hospital.appointment.identity.domain.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
class SmtpIdentityEmailSender implements IdentityEmailSender {
    private final JavaMailSender mailSender;
    private final String from;

    SmtpIdentityEmailSender(JavaMailSender mailSender, @Value("${application.identity.email.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendVerification(UserAccount account, String code) {
        send(account.email(), "Verify your appointment account", "Your verification code is " + code + ". It expires in 24 hours.");
    }

    @Override
    public void sendRecovery(UserAccount account, String code) {
        send(account.email(), "Appointment account recovery", "Your recovery code is " + code + ". It expires in 15 minutes.");
    }

    @Override
    public void sendMfa(UserAccount account, String code) {
        send(account.email(), "Your sign-in verification code", "Your one-time sign-in code is " + code + ". It expires in five minutes.");
    }

    private void send(String recipient, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
