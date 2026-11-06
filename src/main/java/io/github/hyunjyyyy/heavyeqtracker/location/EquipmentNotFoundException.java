package io.github.hyunjyyyy.heavyeqtracker.location;

public class EquipmentNotFoundException extends RuntimeException {
    public EquipmentNotFoundException(Long id) {
        super("equipment " + id + " not found");
    }
}
