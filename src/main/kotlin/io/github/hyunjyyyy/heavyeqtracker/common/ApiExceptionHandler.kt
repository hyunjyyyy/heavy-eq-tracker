package io.github.hyunjyyyy.heavyeqtracker.common

import io.github.hyunjyyyy.heavyeqtracker.location.EquipmentNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(EquipmentNotFoundException::class)
    fun equipmentNotFound(e: EquipmentNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message)
}
