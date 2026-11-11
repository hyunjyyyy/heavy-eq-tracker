# heavy-eq-tracker
[![CI](https://github.com/hyunjyyyy/heavy-eq-tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/hyunjyyyy/heavy-eq-tracker/actions/workflows/ci.yml)

건설 중장비 위치 관제 서버. 단말이 보낸 위치를 Kafka로 받아 MySQL에 저장하고, 지오펜스 진입/이탈과 장비 간 근접을 판정하고, 최신 위치를 Redis에 캐시한다.

개인 학습 프로젝트다. 데이터는 전부 가짜이고 특정 회사·현장과 무관하다. 코드만큼 **왜 그렇게 결정했는지**가 산출물이라, 설계 결정은 전부 GitHub 이슈 댓글에 "결정 / 판단 이유 / 대안 / 바뀔 조건" 형식으로 남긴다.

## 구조

```
단말 ──POST /api/v1/locations──▶ RateLimitFilter (장비당 10/초, Redis Lua)
                                  └▶ LocationController (헤더·바디 검증)
                                      └▶ LocationIngestService
                                          ├─ IdempotencyStore   Redis SET NX EX 600  (중복 재전송 → 같은 202)
                                          ├─ EquipmentRepository.existsById         (없으면 404)
                                          └─ LocationEventProducer ──▶ Kafka location-events (파티션 3, 키 = equipmentId)
                                                                           │
                                                 LocationEventConsumer ◀───┘  (그룹 tracker-judge, 처리 → 커밋)
                                                   └▶ LocationEventWriter  @Transactional
                                                        ├─ findByIdempotencyKey → 있으면 건너뜀 (재소비 멱등)
                                                        ├─ INSERT location_event
                                                        ├─ publish LocationSaved (동기, 같은 트랜잭션)
                                                        │    ├▶ GeofenceService   구역 ENTER/EXIT
                                                        │    └▶ ProximityService  장비 간 NEAR/CLEAR
                                                        └─ publish LocationRecorded
                                                             └▶ LatestLocationCache  @TransactionalEventListener(AFTER_COMMIT)
                                                                  ├─ Redis SET loc:{id} JSON EX 86400
                                                                  └─ Redis GEOADD fleet

조회 ──▶ GET /api/v1/equipment                 현황 (GEO fleet member + loc:* MGET + 현재 구역)
     ──▶ GET /api/v1/equipment/{id}/latest     loc:{id} → 없으면 DB 최신 1건 → 캐시 채움
     ──▶ GET /api/v1/equipment/{id}/events     이력 (from~to, limit)
     ──▶ GET /api/v1/events/recent             전체 최근 이력
```

- 수집 API는 DB에 쓰지 않는다. DB·Kafka 이중 쓰기를 API에서 없애기 위해서다. 그래서 응답은 **202**이고 eventId를 돌려주지 않는다. ([#10 결정 1](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/10))
- 멱등 처리는 두 군데다. API의 Redis 선점(TTL 10분)과 컨슈머의 DB 조회 + UNIQUE. HTTP 재전송과 Kafka 재소비가 같은 장치를 쓴다.
- 판정은 위치 저장과 **같은 트랜잭션**이다. 판정이 실패하면 위치 행도 롤백되고, 컨슈머가 다시 받는다.
- Redis는 DB 트랜잭션 **밖**에서만 건드린다. 캐시·GEO 갱신은 커밋 뒤 리스너, 선점 해제는 실패 시 `DEL`.
- `location` 패키지는 판정 패키지(`zone`, `proximity`)를 모른다. 이벤트만 발행하고 판정 쪽이 듣는다. ([#24](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/24))

### 판정

- **지오펜스**: ray casting 점-다각형. 장비별 현재 구역(`equipment_zone_presence`)과 비교해 바뀐 것만 `geofence_event`에 ENTER/EXIT로 남긴다. 구역 목록은 기동 시 메모리에 읽는다(`ZoneCatalog`). 구역을 바꾸면 재기동한다.
- **근접**: GEO `fleet`에서 반경 30 m 후보 → 후보의 `loc:`이 없거나 10분 넘게 오래됐으면 제외 → 원본 좌표로 거리 재계산 → `거리 < 10 m + 내 오차 + 상대 오차`면 NEAR. 오차는 `horizontalAccuracyM`(없으면 PHONE 5 m / PHONE_RTK 0.05 m), 상한 10 m. 반경 30 m = 10 + 2×10이라 임계가 반경을 넘을 수 없다. 쌍 상태(`proximity_pair_state`)와 비교해 NEAR/CLEAR 전이만 `proximity_event`에 남긴다.

## 스택

| | 버전 | 비고 |
|---|---|---|
| Java / Spring Boot | 21 / 4.1.2 | Kotlin으로 시작해 Day 2에 Java로 전환 ([#4](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/4)) |
| MySQL | 9.7 LTS | Flyway 12, JPA/Hibernate 7, `ddl-auto: validate`, `open-in-view: false` |
| Redis | 8 | `StringRedisTemplate` + JSON 문자열, Lua, GEO |
| Kafka | 4.1 (KRaft) | `spring-boot-starter-kafka`, 파티션 3 |
| 로컬 인프라 | Docker Compose + Spring Boot Docker Compose Support | `bootRun`이 컨테이너를 띄운다 |
| 시험 | JUnit 6, Testcontainers 2, Awaitility | 시험이 자기 MySQL·Redis·Kafka 컨테이너를 띄운다 |
| CI | GitHub Actions | PR·main 푸시마다 `./gradlew build` |
| 빌드 | Gradle 9 (Kotlin DSL), Lombok(엔티티 `@Getter`·`@NoArgsConstructor`만) | |

## 실행

사전: JDK 21, Docker Desktop.

```powershell
Copy-Item .env.example .env      # 값 채우기 (로컬 전용)
.\gradlew.bat bootRun             # MySQL·Redis·Kafka 컨테이너 자동 기동, Flyway 적용
```

macOS·Linux는 `cp .env.example .env`, `./gradlew bootRun`.

장비가 하나는 있어야 수집이 된다. 구역은 선택이다(넣었으면 재기동).

```sql
INSERT INTO equipment (name, type, positioning_source) VALUES ('EX-01', 'EXCAVATOR', 'PHONE_RTK');
INSERT INTO zone (name, kind, polygon)
VALUES ('site-a', 'WORK', '[[37.565,126.976],[37.565,126.980],[37.568,126.980],[37.568,126.976]]');  -- [lat, lon]
```

### 시험

```powershell
.\gradlew.bat test
```

Docker만 켜져 있으면 된다. compose와 `.env`는 필요 없다. 시험이 Testcontainers로 MySQL 9.7·Redis 8·Kafka 4.1을 띄우고, `@ServiceConnection`이 접속 정보를 넣는다.

- 통합 시험은 전부 `IntegrationTest`를 상속한다. 어노테이션이 같아야 컨텍스트가 하나만 뜨고 컨테이너도 한 벌만 뜬다. ([#21 결정 1](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/21))
- 시험마다 `@BeforeEach`에서 표를 비우고 Redis를 `FLUSHALL`한다. 저장은 컨슈머 스레드가 따로 커밋해서 시험 트랜잭션 롤백으로는 지울 수 없다. (#21 결정 2)
- 비동기 결과는 Awaitility로 최대 10초 기다린다. (#21 결정 3)

## API

### `POST /api/v1/locations` — 위치 수집

헤더 `Idempotency-Key`(필수, 64자 이하), `X-Equipment-Id`(필수, 바디와 같아야 함), `Content-Type: application/json`

```json
{"equipmentId":1,"lat":37.5,"lon":127.0,"speedKmh":12.3,"heading":90.0,
 "fixType":"RTK_FIXED","horizontalAccuracyM":0.02,"recordedAt":"2026-11-09T08:00:00Z"}
```

| 응답 | 뜻 |
|---|---|
| 202 `{"idempotencyKey":"..."}` | 받아서 토픽에 넣었다. 같은 키 재전송도 202 |
| 400 | 검증 실패(ProblemDetail), 헤더·바디 장비 ID 불일치, 키 길이 초과 |
| 404 | 장비 없음 |
| 429 + `Retry-After: 1` | 장비당 초당 10건 초과 |

`fixType`: `RTK_FIXED` `RTK_FLOAT` `DGPS` `SINGLE` `NONE`. `speedKmh`·`heading`·`horizontalAccuracyM`은 생략 가능.

### 조회

| 요청 | 응답 |
|---|---|
| `GET /api/v1/equipment` | `[{equipmentId, latest, zoneIds}]` 위치를 보낸 적 있는 장비 전체 |
| `GET /api/v1/equipment/{id}/latest` | 최신 위치. 기록 없거나 장비 없으면 404 |
| `GET /api/v1/equipment/{id}/events?from=&to=&limit=` | 시각 범위 이력(ISO-8601), 최신순 |
| `GET /api/v1/events/recent?limit=` | 전체 장비 최근 이력, 최신순 |

`limit`은 기본 100, 1~1000으로 자른다.

## 데이터

Flyway `src/main/resources/db/migration/`, 표당 파일 하나(MySQL DDL은 트랜잭션에 안 들어가므로).

| 표 | 주요 열 |
|---|---|
| `equipment` | `type`, `positioning_source`(PHONE / PHONE_RTK) |
| `zone` | `kind`(WORK / DANGER), `polygon` JSON `[[lat, lon], ...]`, `active` |
| `location_event` | `equipment_id` FK, `lat`/`lon` DOUBLE, `fix_type`, `h_accuracy_m`, `recorded_at`(단말) / `received_at`(API 수신) DATETIME(6) UTC, `idempotency_key` UNIQUE. 인덱스 `(equipment_id, recorded_at)` |
| `geofence_event` | ENTER/EXIT, 원인 `location_event_id` |
| `equipment_zone_presence` | 장비가 지금 들어가 있는 구역 |
| `proximity_event` | NEAR/CLEAR, 장비 쌍(작은 id, 큰 id), `distance_m` |
| `proximity_pair_state` | 지금 근접 중인 쌍 |

## 측정한 것

| 항목 | 수치 | 출처 |
|---|---|---|
| HikariCP 풀 | `maximumPoolSize` 10, `minimumIdle` 10 (기본값, 고정 풀) | 기동 로그 |
| 속도 제한 | 30연속 요청 → 201×10, 429×20 (Kafka 전, 수집이 201이던 때) | [#7](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/7) |
| 캐시 적중 | GET에 SQL 0회, 미스 시 1회 | #7 |
| 파티션 키 | 장비 3대×20건 → 키마다 한 파티션, 오프셋 증가 순서 | [#12](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/12) |
| 재시작 | 종료 중 쌓인 2건을 재시작 후 이어 소비 (LAG 2 → 0) | #12 |
| 재소비 멱등 | 오프셋 earliest로 되돌려 62건 재소비 → 행 수 62 불변 | #12 |
| 리밸런스 | 컨슈머 1→2→1대, 26~185 ms | #12 |
| GEO 양자화 | GEO 거리 5.52 m → 원본 좌표 재계산 5.29 m | [#16](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/16) |
| 캐시 미스 쿼리 | 10만 건에서 25,047행 16.6 ms → 1행 0.31 ms (`(equipment_id, recorded_at)`) | [#17](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/17) |
| 이력 쿼리 | filesort → range scan 901행 0.80 ms | #17 |
| N+1 | 최근 이력 1+3 → 1, 장비 이력 1+1 → 1 (`@EntityGraph`) | #17 |
| 시험 | 11개 22 s, 컨테이너·앱 기동 1번 14.7 s, 통합 시험 셋 합 3.1 s | [#21](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/21) |
| CI | 캐시 없음 1분 55초 → 있음 1분 14초 | [#22](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/22) |

## 설계 결정 찾아보기

| 이슈 | 내용 |
|---|---|
| [#1](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/1) | DB·패키지·PK·시각·좌표·멱등키·DTO·연관·Flyway 분할·RTK 열·버전·.env·풀 크기 (1~15) |
| [#4](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/4) | Kotlin → Java, Lombok 범위 (16~18) |
| [#6](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/6) | 멱등키 Redis 선점 TTL, 직렬화 |
| [#7](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/7) | 최신 위치 캐시 자료구조·갱신 시점, 속도 제한, 장비 ID 헤더 |
| [#10](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/10) | Kafka를 쓰는 이유와 대가, 발행 시점 202, 파티션·키·직렬화·acks |
| [#11](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/11) | 컨슈머 책임, 처리→커밋, 멱등 소비, 재시도 |
| [#12](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/12) | Kafka 실험 결과 |
| [#15](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/15) | 지오펜스: 점-다각형, 구역 카탈로그, 진입/이탈 기록 |
| [#16](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/16) | 근접: GEO 후보, 오차 반경, NEAR/CLEAR 전이 |
| [#17](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/17) | 조회 API, EXPLAIN 인덱스 전후, N+1 |
| [#21](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/21) | Testcontainers: 컨테이너 생명주기, 시험 간 격리, 비동기 대기 |
| [#22](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/22) | GitHub Actions: 액션 버전, 캐시, 브랜치 보호 |
| [#23](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/23) | 남겨 둔 것 처리 방침 |
| [#24](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/24) | 코드 검수 반영: 멱등키 길이, 근접 반경·유령 장비, 패키지 순환 |

날짜별 학습 기록은 `study-log/`. 사고 과정 정리(`docs/`)는 작성 중.

## 남은 것

- 늦게 도착한 옛 위치가 최신 캐시를 덮는 문제(Lua 비교), 한도·임계값 설정 외부화, 처리 못 한 메시지 DLT ([#23](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/23))
- 다음 프로젝트: 폐차 장비의 GEO member 정리, 인증
