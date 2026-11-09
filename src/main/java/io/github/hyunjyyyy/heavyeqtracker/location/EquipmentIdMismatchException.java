package io.github.hyunjyyyy.heavyeqtracker.location;

public class EquipmentIdMismatchException extends RuntimeException {
    public EquipmentIdMismatchException(Long header, Long body) {
        super("X-Equipment-Id " + header + " does not match body equipmentId " + body);
    }
}
