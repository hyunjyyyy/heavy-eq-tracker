package io.github.hyunjyyyy.heavyeqtracker.site;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import io.github.hyunjyyyy.heavyeqtracker.zone.Zone;
import io.github.hyunjyyyy.heavyeqtracker.zone.ZoneKind;
import io.github.hyunjyyyy.heavyeqtracker.zone.ZoneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/* 현장을 정하지 않은 코드는 남의 현장을 보거나 엉뚱한 현장에 쓰지 못하고, 아무것도 못 보거나 실패한다(#30 결정 6) */
class SiteFailClosedIT extends IntegrationTest {

    private static final String SQUARE =
            "[[37.565,126.976],[37.565,126.980],[37.568,126.980],[37.568,126.976]]";

    @Autowired ZoneRepository zoneRepository;

    @Test
    void withoutSiteNothingIsVisibleAndNothingIsWritten() throws Exception {
        zoneRepository.save(new Zone("site-1-yard", ZoneKind.WORK, SQUARE));   // 시험 스레드 = 현장 1

        ExecutorService noSite = Executors.newSingleThreadExecutor();          // ThreadLocal이 빈 새 스레드
        try {
            List<Zone> seen = noSite.submit(() -> zoneRepository.findAll()).get();
            assertThat(seen).isEmpty();

            assertThatThrownBy(() -> noSite.submit(
                    () -> zoneRepository.save(new Zone("orphan", ZoneKind.WORK, SQUARE))).get())
                    .hasCauseInstanceOf(DataIntegrityViolationException.class);   // site_id = -1 → FK 위반
        } finally {
            noSite.shutdown();
        }

        assertThat(zoneRepository.findAll()).hasSize(1);   // 현장 1에서는 보인다(대조군)
    }
}
