package com.hospital.appointment.identity.application;

public record AuthSession(String accessToken, long expiresInSeconds, AccountView account, String refreshToken, String csrfToken,
                          boolean freshLogin) {
}
