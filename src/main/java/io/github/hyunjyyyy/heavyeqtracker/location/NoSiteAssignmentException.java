package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.common.UnprocessableException;

import java.time.Instant;

public class NoSiteAssignmentException extends UnprocessableException {
    public NoSiteAssignmentException(Long equipmentId, Instant recordedAt) {
        super("equipment " + equipmentId + " has no site assignment at " + recordedAt);
    }
}
