package io.github.hyunjyyyy.heavyeqtracker.site;

import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentNotFoundException;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class EquipmentMoveService {

    private final EquipmentRepository equipmentRepository;
    private final SiteRepository sites;
    private final EquipmentSiteAssignmentRepository assignments;
    private final ApplicationEventPublisher events;

    public EquipmentMoveService(EquipmentRepository equipmentRepository, SiteRepository sites,
                                EquipmentSiteAssignmentRepository assignments, ApplicationEventPublisher events) {
        this.equipmentRepository = equipmentRepository;
        this.sites = sites;
        this.assignments = assignments;
        this.events = events;
    }

    /** 현재 현장이 fromSiteId여야 한다(옛 현장 경로). 리스너가 쓰는 EXIT·CLEAR가 옛 현장의 행이 된다 */
    @Transactional
    public EquipmentSiteAssignment move(long fromSiteId, Long equipmentId, long toSiteId) {
        equipmentRepository.findWithLockById(equipmentId)
                .orElseThrow(() -> new EquipmentNotFoundException(equipmentId));
        if (toSiteId == fromSiteId) {
            throw new MoveConflictException("equipment " + equipmentId + " is already in site " + toSiteId);
        }
        if (!sites.existsById(toSiteId)) {
            throw new SiteNotFoundException(toSiteId);
        }
        EquipmentSiteAssignment current = assignments.findByEquipmentIdAndAssignedToIsNull(equipmentId)
                .filter(a -> a.getSiteId() == fromSiteId)
                .orElseThrow(() -> new MoveConflictException("equipment " + equipmentId + " is not in site " + fromSiteId));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);   // DATETIME(6)
        current.close(now);
        assignments.flush();   // Hibernate는 INSERT를 UPDATE보다 먼저 보낸다. 닫기를 먼저 내보내야 V24 제약에 안 걸린다
        EquipmentSiteAssignment next = assignments.save(new EquipmentSiteAssignment(equipmentId, toSiteId, now, null));

        events.publishEvent(new EquipmentMoved(equipmentId, fromSiteId, toSiteId, now));
        return next;
    }
}
