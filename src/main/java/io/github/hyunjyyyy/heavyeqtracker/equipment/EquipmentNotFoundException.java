package io.github.hyunjyyyy.heavyeqtracker.equipment;

public class EquipmentNotFoundException extends RuntimeException {
    public EquipmentNotFoundException(Long id) {
        super("equipment " + id + " not found");
    }
}
