package io.github.hyunjyyyy.heavyeqtracker.location;

import java.time.Instant;

public class NoSiteAssignmentException extends RuntimeException {
    public NoSiteAssignmentException(Long equipmentId, Instant recordedAt) {
        super("equipment " + equipmentId + " has no site assignment at " + recordedAt);
    }
}
