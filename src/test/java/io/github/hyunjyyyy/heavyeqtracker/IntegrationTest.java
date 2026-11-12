package io.github.hyunjyyyy.heavyeqtracker;

import io.github.hyunjyyyy.heavyeqtracker.zone.ZoneCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

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
        List<String> sql = new ArrayList<>();
        sql.add("SET FOREIGN_KEY_CHECKS = 0");
        TABLES.forEach(t -> sql.add("TRUNCATE TABLE " + t));
        sql.add("SET FOREIGN_KEY_CHECKS = 1");
        jdbc.batchUpdate(sql.toArray(String[]::new));

        try (RedisConnection c = redis.getRequiredConnectionFactory().getConnection()) {
            c.serverCommands().flushAll();
        }
        zoneCatalog.reload();
    }
}
