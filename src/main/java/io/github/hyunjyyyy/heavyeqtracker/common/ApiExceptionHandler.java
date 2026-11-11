package io.github.hyunjyyyy.heavyeqtracker.common;

import io.github.hyunjyyyy.heavyeqtracker.location.EquipmentIdMismatchException;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EquipmentNotFoundException.class)
    public ProblemDetail equipmentNotFound(EquipmentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(EquipmentIdMismatchException.class)
    public ProblemDetail equipmentIdMismatch(EquipmentIdMismatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
