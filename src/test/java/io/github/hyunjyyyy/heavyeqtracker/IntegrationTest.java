package io.github.hyunjyyyy.heavyeqtracker;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/* 통합 시험은 전부 이걸 상속한다. 클래스마다 어노테이션이 다르면 컨텍스트가 하나 더 뜨고 컨테이너도 한 벌 더 뜬다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {
}
