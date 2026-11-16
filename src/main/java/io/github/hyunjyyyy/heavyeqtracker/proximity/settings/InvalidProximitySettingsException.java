package io.github.hyunjyyyy.heavyeqtracker.proximity.settings;

import io.github.hyunjyyyy.heavyeqtracker.common.BadRequestException;

public class InvalidProximitySettingsException extends BadRequestException {
    public InvalidProximitySettingsException(String message) {
        super(message);
    }
}
