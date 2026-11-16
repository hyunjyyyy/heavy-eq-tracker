package io.github.hyunjyyyy.heavyeqtracker.proximity.settings;

/** 현장 설정이 바뀌었다. 해석기가 커밋 뒤 그 현장 캐시를 지운다 */
public record ProximitySettingsChanged(long siteId) {
}
