package io.github.hyunjyyyy.heavyeqtracker.equipment;

import io.github.hyunjyyyy.heavyeqtracker.common.NotFoundException;

public class EquipmentNotFoundException extends NotFoundException {
    public EquipmentNotFoundException(Long id) {
        super("equipment " + id + " not found");
    }
}
