package io.github.hyunjyyyy.heavyeqtracker.site;

import java.time.Instant;

/** 장비가 현장을 옮겼다. 옛 현장의 체류·근접 쌍·GEO를 정리하라는 신호(#31 결정 3) */
public record EquipmentMoved(Long equipmentId, long fromSiteId, long toSiteId, Instant movedAt) {
}
