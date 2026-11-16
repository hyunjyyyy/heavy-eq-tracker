package io.github.hyunjyyyy.heavyeqtracker.location.ingest;

import io.github.hyunjyyyy.heavyeqtracker.common.BadRequestException;

public class EquipmentIdMismatchException extends BadRequestException {
    public EquipmentIdMismatchException(Long header, Long body) {
        super("X-Equipment-Id " + header + " does not match body equipmentId " + body);
    }
}
