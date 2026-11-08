package io.github.hyunjyyyy.heavyeqtracker.common;

import io.github.hyunjyyyy.heavyeqtracker.location.DuplicateInProgressException;
import io.github.hyunjyyyy.heavyeqtracker.location.EquipmentNotFoundException;
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

    @ExceptionHandler(DuplicateInProgressException.class)
    public ProblemDetail duplicateInProgress(DuplicateInProgressException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
