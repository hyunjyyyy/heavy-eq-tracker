package io.github.hyunjyyyy.heavyeqtracker.site;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentType;
import io.github.hyunjyyyy.heavyeqtracker.equipment.PositioningSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentSiteAssignmentIT extends IntegrationTest {

    @Autowired EquipmentRepository equipmentRepository;
    @Autowired SiteRepository siteRepository;
    @Autowired EquipmentSiteAssignmentRepository assignments;

    @Test
    void findAtUsesHalfOpenInterval() {
        Long equipmentId = equipmentRepository
                .save(new Equipment("test-excavator", EquipmentType.EXCAVATOR, PositioningSource.PHONE))
                .getId();
        Long siteB = siteRepository.save(new Site("test-site-b")).getId();
        Instant moved = Instant.parse("2026-11-12T09:00:00Z");
        Instant start = moved.minus(Duration.ofDays(10));

        assignments.save(new EquipmentSiteAssignment(equipmentId, 1L, start, moved));
        assignments.save(new EquipmentSiteAssignment(equipmentId, siteB, moved, null));

        assertThat(assignments.findAt(equipmentId, start.minusSeconds(1))).isEmpty();
        assertThat(assignments.findAt(equipmentId, start)).get()
                .extracting(EquipmentSiteAssignment::getSiteId).isEqualTo(1L);
        assertThat(assignments.findAt(equipmentId, moved.minusNanos(1_000))).get()   // 이동 1 µs 전 (DATETIME(6))
                .extracting(EquipmentSiteAssignment::getSiteId).isEqualTo(1L);
        assertThat(assignments.findAt(equipmentId, moved)).get()                     // 이동 시각부터 새 현장
                .extracting(EquipmentSiteAssignment::getSiteId).isEqualTo(siteB);
    }
}
