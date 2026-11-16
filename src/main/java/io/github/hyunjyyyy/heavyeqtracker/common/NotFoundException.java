package io.github.hyunjyyyy.heavyeqtracker.common;

/** 404. 기능 모듈의 "없음" 예외가 상속한다 */
public abstract class NotFoundException extends RuntimeException {
    protected NotFoundException(String message) {
        super(message);
    }
}
