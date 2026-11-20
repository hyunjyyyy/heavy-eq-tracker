package io.github.hyunjyyyy.heavyeqtracker.simulator.route;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTest {

    @Test
    void shuttleTurnsBackAtEnd() {
        Route r = new ShuttleRoute(0, 0, 10, 0, 1);
        Sample s = null;
        for (int i = 0; i < 15; i++) s = r.next(1);
        assertEquals(5, s.eastM(), 1e-9);       // 10 m 가서 5 m 돌아옴
        assertEquals(270, s.headingDeg(), 1e-9); // 서쪽
    }

    @Test
    void sameSeedSamePath() {
        Route a = new RandomWalkRoute(7, 100, 2);
        Route b = new RandomWalkRoute(7, 100, 2);
        for (int i = 0; i < 1000; i++) assertEquals(a.next(1), b.next(1));
    }

    @Test
    void randomWalkStaysNearSite() {
        Route r = new RandomWalkRoute(7, 100, 2);
        for (int i = 0; i < 10_000; i++) {
            Sample s = r.next(1);
            assertTrue(Math.hypot(s.eastM(), s.northM()) <= 100 + 2); // 한 걸음만큼은 넘을 수 있음
        }
    }

    @Test
    void approachPairMeetsThenParts() {
        List<Route> pair = ApproachScenario.pair(40, 4, 1);
        double min = Double.MAX_VALUE, gap = 0;
        for (int i = 0; i < 36; i++) {
            Sample a = pair.get(0).next(1), b = pair.get(1).next(1);
            gap = Math.hypot(a.eastM() - b.eastM(), a.northM() - b.northM());
            min = Math.min(min, gap);
        }
        assertEquals(4, min, 1e-9);  // 18초에 가장 가까움
        assertEquals(40, gap, 1e-9); // 36초에 처음 자리
    }
}
