package io.github.hyunjyyyy.heavyeqtracker.common;

/** 422. 형식은 맞지만 처리할 수 없음 */
public abstract class UnprocessableException extends RuntimeException {
    protected UnprocessableException(String message) {
        super(message);
    }
}
