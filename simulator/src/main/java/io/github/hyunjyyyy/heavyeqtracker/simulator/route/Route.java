package io.github.hyunjyyyy.heavyeqtracker.simulator.route;

public interface Route {
    /** seconds만큼 움직이고 새 위치를 돌려준다 */
    Sample next(double seconds);
}
