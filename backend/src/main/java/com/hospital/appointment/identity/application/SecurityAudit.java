package com.hospital.appointment.identity.application;

import java.util.Map;
import java.util.UUID;

public interface SecurityAudit {
    void record(String eventType, UUID accountId, String sourceAddress, Map<String, String> details);

    default void record(String eventType, UUID accountId, String sourceAddress) {
        record(eventType, accountId, sourceAddress, Map.of());
    }
}
