package io.github.hyunjyyyy.heavyeqtracker.site;

import io.github.hyunjyyyy.heavyeqtracker.common.ConflictException;

public class MoveConflictException extends ConflictException {
    public MoveConflictException(String message) {
        super(message);
    }
}
