package io.github.hyunjyyyy.heavyeqtracker;

import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentType;
import io.github.hyunjyyyy.heavyeqtracker.equipment.PositioningSource;
import io.github.hyunjyyyy.heavyeqtracker.site.EquipmentSiteAssignment;
import io.github.hyunjyyyy.heavyeqtracker.site.EquipmentSiteAssignmentRepository;
import io.github.hyunjyyyy.heavyeqtracker.site.SiteContext;
import io.github.hyunjyyyy.heavyeqtracker.zone.ZoneCatalog;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/* 통합 시험은 전부 이걸 상속한다. 클래스마다 어노테이션이 다르면 컨텍스트가 하나 더 뜨고 컨테이너도 한 벌 더 뜬다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    /* 새 표를 만들면 여기에도 넣는다. flyway_schema_history는 넣지 않는다. */
    private static final List<String> TABLES = List.of(
            "proximity_event", "proximity_pair_state", "geofence_event",
            "equipment_zone_presence", "location_event", "zone",
            "equipment_site_assignment", "equipment");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ZoneCatalog zoneCatalog;

    /* SET과 TRUNCATE가 같은 커넥션에서 돌아야 해서 batchUpdate 한 번으로 보낸다.
       ZoneCatalog는 구역을 메모리에 들고 있어 표를 비운 뒤 다시 읽힌다. */
    @BeforeEach
    void cleanUp() {
        siteScope = SiteContext.enter(1L);   // 시험은 현장 1에서 돈다. 정하지 않으면 아무것도 안 보인다(NONE)
        List<String> sql = new ArrayList<>();
        sql.add("SET FOREIGN_KEY_CHECKS = 0");
        TABLES.forEach(t -> sql.add("TRUNCATE TABLE " + t));
        sql.add("DELETE FROM site WHERE id <> 1");   // 기본 현장은 Flyway가 넣은 것
        sql.add("SET FOREIGN_KEY_CHECKS = 1");
        jdbc.batchUpdate(sql.toArray(String[]::new));

        try (RedisConnection c = redis.getRequiredConnectionFactory().getConnection()) {
            c.serverCommands().flushAll();
        }
        zoneCatalog.reload();
    }

    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private EquipmentSiteAssignmentRepository assignmentRepository;

    /** 장비를 만들고 2000-01-01부터 끝없이 이 현장에 배정한다 */
    protected Long newEquipment(long siteId) {
        Long id = equipmentRepository
                .save(new Equipment("test-excavator", EquipmentType.EXCAVATOR, PositioningSource.PHONE))
                .getId();
        assignmentRepository.save(new EquipmentSiteAssignment(id, siteId, Instant.parse("2000-01-01T00:00:00Z"), null));
        return id;
    }

    protected Long newEquipment() {
        return newEquipment(1L);
    }

    private SiteContext.Scope siteScope;

    /* Awaitility 조건식은 기본으로 다른 스레드에서 돈다. 현재 현장은 ThreadLocal이라 따라가지 않아
       조건이 늘 빈 결과를 본다 → 시험 스레드에서 돌게 한다 */
    @BeforeAll
    static void pollInTestThread() {
        Awaitility.pollInSameThread();
    }

    @AfterEach
    void leaveSite() {
        siteScope.close();
    }
}
