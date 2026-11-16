package io.github.hyunjyyyy.heavyeqtracker;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/* 모듈(앱 패키지 바로 아래 패키지) 사이의 순환과, 다른 모듈의 내부(하위 패키지)를 직접 쓰는 것을 잡는다(#40) */
class ModularityTest {

    @Test
    void modulesHaveNoCyclesAndUseOnlyPublicParts() {
        ApplicationModules.of(HeavyEqTrackerApplication.class).verify();
    }
}
