package io.github.hyunjyyyy.heavyeqtracker.common;

/** 409. 지금 상태와 맞지 않는 요청 */
public abstract class ConflictException extends RuntimeException {
    protected ConflictException(String message) {
        super(message);
    }
}
