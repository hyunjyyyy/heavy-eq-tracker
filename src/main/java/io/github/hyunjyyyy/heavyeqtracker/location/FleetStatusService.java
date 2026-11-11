package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.zone.EquipmentZonePresence;
import io.github.hyunjyyyy.heavyeqtracker.zone.EquipmentZonePresenceRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FleetStatusService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final EquipmentZonePresenceRepository presenceRepository;

    public FleetStatusService(StringRedisTemplate redis, ObjectMapper objectMapper,
                              EquipmentZonePresenceRepository presenceRepository) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.presenceRepository = presenceRepository;
    }

    public List<EquipmentStatus> fleet() {
        // 1. GEO 키는 내부가 Sorted Set → member 목록 = 위치를 보낸 적 있는 장비 id
        Set<String> ids = redis.opsForZSet().range(LatestLocationCache.FLEET_KEY, 0, -1);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // 2. loc:{id}를 MGET 한 번에
        List<String> keys = ids.stream().map(id -> "loc:" + id).toList();
        List<String> jsons = redis.opsForValue().multiGet(keys);

        // 3. 현재 구역은 DB presence 전체 1회 (행이 적다)
        Map<Long, List<Long>> zonesByEquipment = presenceRepository.findAll().stream()
                .collect(Collectors.groupingBy(EquipmentZonePresence::getEquipmentId,
                        Collectors.mapping(EquipmentZonePresence::getZoneId, Collectors.toList())));

        List<EquipmentStatus> out = new ArrayList<>();
        int i = 0;
        for (String id : ids) {
            String json = jsons == null ? null : jsons.get(i++);
            LatestLocation latest = json == null ? null : objectMapper.readValue(json, LatestLocation.class);
            Long equipmentId = Long.valueOf(id);
            out.add(new EquipmentStatus(equipmentId, latest, zonesByEquipment.getOrDefault(equipmentId, List.of())));
        }
        return out;
    }
}
