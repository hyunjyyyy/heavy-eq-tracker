/* 공통 모듈은 다른 모듈을 의존하지 않는다. 기능 모듈이 common을 쓴다(#40 결정 2) */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package io.github.hyunjyyyy.heavyeqtracker.common;
