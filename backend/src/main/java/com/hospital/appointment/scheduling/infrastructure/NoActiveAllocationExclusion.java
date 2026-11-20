package com.hospital.appointment.scheduling.infrastructure;

import com.hospital.appointment.scheduling.application.ActiveAllocationExclusion;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ConflictPreview;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.TimeInterval;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
class NoActiveAllocationExclusion implements ActiveAllocationExclusion {
    @Override
    public List<TimeInterval> activeIntervals(UUID doctorId, Instant from, Instant to) {
        return List.of();
    }

    @Override
    public ConflictPreview preview(UUID doctorId, LocalDate from, LocalDate to) {
        return new ConflictPreview(doctorId, from, to, false,
                "Preview only: allocation conflict enforcement is unavailable until allocation records are introduced in Phase 5.");
    }
}
