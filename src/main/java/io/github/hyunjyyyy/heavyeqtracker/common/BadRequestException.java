package io.github.hyunjyyyy.heavyeqtracker.common;

/** 400. 요청이 잘못됨 */
public abstract class BadRequestException extends RuntimeException {
    protected BadRequestException(String message) {
        super(message);
    }
}
