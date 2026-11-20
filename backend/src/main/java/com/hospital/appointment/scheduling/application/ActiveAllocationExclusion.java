package com.hospital.appointment.scheduling.application;

import com.hospital.appointment.scheduling.domain.DirectoryRecords.ConflictPreview;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.TimeInterval;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Phase 5 extension point. Availability implementations must subtract the intervals
 * returned here after schedule projection. Phase 3 deliberately has no allocation tables.
 */
public interface ActiveAllocationExclusion {
    List<TimeInterval> activeIntervals(UUID doctorId, Instant from, Instant to);

    ConflictPreview preview(UUID doctorId, LocalDate from, LocalDate to);
}
