# heavy-eq-tracker
[![CI](https://github.com/hyunjyyyy/heavy-eq-tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/hyunjyyyy/heavy-eq-tracker/actions/workflows/ci.yml)

건설 중장비 위치 관제 서버. 단말이 보낸 위치를 Kafka로 받아 MySQL에 저장하고, 지오펜스 진입/이탈과 장비 간 근접을 판정하고, 최신 위치를 Redis에 캐시한다. 현장이 여러 개이고 현장마다 따로 운영되며, 장비는 현장 사이를 옮겨 다닌다. 본사는 전 현장을 본다.

개인 학습 프로젝트다. 데이터는 전부 가짜이고 특정 회사·현장과 무관하다. 코드만큼 **왜 그렇게 결정했는지**가 산출물이라, 설계 결정은 전부 GitHub 이슈 댓글에 "결정 / 판단 이유 / 대안 / 바뀔 조건" 형식으로 남긴다.

## 구조

```
단말 ──POST /api/v1/locations──▶ RateLimitFilter (장비당 10/초, Redis Lua)
                                  └▶ LocationController (헤더·바디 검증)
                                      └▶ LocationIngestService
                                          ├─ IdempotencyStore   Redis SET NX EX 600  (중복 재전송 → 같은 202)
                                          ├─ 배정 이력에서 recordedAt 시점의 현장 찾기 (배정 없음 422, 장비 없음 404)
                                          └─ LocationEventProducer ──▶ Kafka location-events (파티션 3, 키 = equipmentId, 값에 siteId)
                                                                           │
                                                 LocationEventConsumer ◀───┘  (그룹 tracker-judge, 처리 → 커밋,
                                                   │                             실패 시 1~10초 간격 재시도 → location-events-dlt)
                                                   └▶ SiteContext = 메시지의 siteId → LocationEventWriter  @Transactional
                                                        ├─ findByIdempotencyKey → 있으면 건너뜀 (재소비 멱등)
                                                        ├─ INSERT location_event
                                                        ├─ publish LocationSaved (동기, 같은 트랜잭션)
                                                        │    ├▶ GeofenceService   구역 ENTER/EXIT
                                                        │    └▶ ProximityService  장비 간 NEAR/CLEAR
                                                        └─ publish LocationRecorded
                                                             └▶ LatestLocationCache  @TransactionalEventListener(AFTER_COMMIT)
                                                                  └─ Lua: recordedAt이 더 클 때만 loc:{id} JSON·loc:{id}:ts·GEO fleet:{siteId} 갱신

조회 ──▶ SiteContextInterceptor (경로 {siteId}로 현재 현장, 없는 현장 404 / /hq/** 는 전 현장)
     ──▶ GET /api/v1/sites/{siteId}/equipment                현황 (GEO fleet:{siteId} + loc:* MGET + 현재 구역)
     ──▶ GET /api/v1/sites/{siteId}/equipment/{id}/latest    loc:{id}(이 현장 것일 때만) → 없으면 DB 최신 1건 → 캐시 채움
     ──▶ GET /api/v1/sites/{siteId}/equipment/{id}/events    이력 (from~to, limit)
     ──▶ GET /api/v1/sites/{siteId}/events/recent            현장 최근 이력
     ──▶ GET /api/v1/hq/sites, /api/v1/hq/events/recent      본사: 현장 목록, 전 현장 최근 이력
```

- 수집 API는 DB에 쓰지 않는다. DB·Kafka 이중 쓰기를 API에서 없애기 위해서다. 그래서 응답은 **202**이고 eventId를 돌려주지 않는다. ([#10 결정 1](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/10))
- 멱등 처리는 두 군데다. API의 Redis 선점(TTL 10분)과 컨슈머의 DB 조회 + UNIQUE. HTTP 재전송과 Kafka 재소비가 같은 장치를 쓴다.
- 판정은 위치 저장과 **같은 트랜잭션**이다. 판정이 실패하면 위치 행도 롤백되고, 컨슈머가 다시 받는다.
- Redis는 DB 트랜잭션 **밖**에서만 건드린다. 캐시·GEO 갱신은 커밋 뒤 리스너, 선점 해제는 실패 시 `DEL`.
- 캐시는 늦게 도착한 옛 위치에 덮이지 않는다. 컨슈머와 조회 API의 캐시 미스 채우기가 같은 키에 쓰므로 비교와 쓰기를 Lua 한 번으로 한다. ([#23 결정 1](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/23))
- 처리 못 한 메시지는 버리지 않는다. 잠깐 실패는 재시도(약 35초)로 버티고, 깨진 JSON·없는 장비는 재시도 없이 DLT로 간다. (#23 결정 4)
- 속도 제한·근접 값은 `application.yml`의 `tracker.*`에서 바꾼다. 잘못된 값이면 기동이 실패한다. (#23 결정 2)
- `location` 패키지는 판정 패키지(`zone`, `proximity`)를 모른다. 이벤트만 발행하고 판정 쪽이 듣는다. ([#24](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/24))

### 현장

- 표는 하나로 쓰고 `site_id`로 나눈다. 현장 표 6개는 Hibernate `@TenantId`가 모든 조회에 `site_id = ?`를 붙이고 저장 때 값을 채운다. 현재 현장은 `SiteContext`(`ThreadLocal`)가 들고, 조회는 경로, 컨슈머는 메시지, 시스템 작업은 전 현장(root)으로 정한다. ([#30](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/30))
- 현재 현장을 정하지 않으면 "아무 현장도 아님"이라 조회는 빈 결과, 저장은 FK로 실패한다. 남의 현장을 보거나 엉뚱한 현장에 쓰지 않는다. (#30 결정 6)
- 위치의 현장은 **찍힌 시각**의 배정으로 정한다. 끊겼다가 장비를 옮긴 뒤에 도착한 위치도 찍힌 곳의 현장으로 간다. (#30 결정 1)
- `@TenantId`는 DB만 막는다. Redis는 GEO 키를 현장별(`fleet:{siteId}`)로 두고, 장비 단위 `loc:{id}`는 꺼낸 값의 `siteId`로 거른다.
- Redis는 DB 사본이라 기동 때 최근 24시간 장비별 최신 위치로 다시 채운다. (#30 결정 4)

### 판정

- **지오펜스**: ray casting 점-다각형. 그 현장 구역만 본다. 장비별 현재 구역(`equipment_zone_presence`)과 비교해 바뀐 것만 `geofence_event`에 ENTER/EXIT로 남긴다. 구역 목록은 기동 시 전 현장을 메모리에 읽는다(`ZoneCatalog`). 구역을 바꾸면 재기동한다.
- **근접**: 같은 현장끼리만. GEO `fleet:{siteId}`에서 반경 30 m 후보 → 후보의 `loc:`이 없거나 10분 넘게 오래됐으면 제외 → 원본 좌표로 거리 재계산 → `거리 < 10 m + 내 오차 + 상대 오차`면 NEAR. 오차는 `horizontalAccuracyM`(없으면 PHONE 5 m / PHONE_RTK 0.05 m), 상한 10 m. 반경 30 m = 10 + 2×10이라 임계가 반경을 넘을 수 없다. 쌍 상태(`proximity_pair_state`)와 비교해 NEAR/CLEAR 전이만 `proximity_event`에 남긴다.

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

기본 현장(id 1)은 Flyway가 만든다. 장비는 현장에 배정돼 있어야 수집된다(위치의 `recordedAt`이 배정 기간 안). 구역은 선택이다(넣었으면 재기동).

```sql
INSERT INTO equipment (name, type, positioning_source) VALUES ('EX-01', 'EXCAVATOR', 'PHONE_RTK');
INSERT INTO equipment_site_assignment (equipment_id, site_id, assigned_from)
VALUES (LAST_INSERT_ID(), 1, '2026-01-01');                  -- assigned_to 비움 = 지금도 배정 중
INSERT INTO zone (site_id, name, kind, polygon)
VALUES (1, 'yard-a', 'WORK', '[[37.565,126.976],[37.565,126.980],[37.568,126.980],[37.568,126.976]]');  -- [lat, lon]
```

### 시험

```powershell
.\gradlew.bat test
```

Docker만 켜져 있으면 된다. compose와 `.env`는 필요 없다. 시험이 Testcontainers로 MySQL 9.7·Redis 8·Kafka 4.1을 띄우고, `@ServiceConnection`이 접속 정보를 넣는다.

- 통합 시험은 전부 `IntegrationTest`를 상속한다. 어노테이션이 같아야 컨텍스트가 하나만 뜨고 컨테이너도 한 벌만 뜬다. ([#21 결정 1](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/21))
- 시험마다 `@BeforeEach`에서 표를 비우고 Redis를 `FLUSHALL`한다. 저장은 컨슈머 스레드가 따로 커밋해서 시험 트랜잭션 롤백으로는 지울 수 없다. (#21 결정 2)
- 비동기 결과는 Awaitility로 최대 10초 기다린다. (#21 결정 3) 조건식은 시험 스레드에서 돈다(`pollInSameThread`). 현재 현장(`ThreadLocal`)이 다른 스레드로 따라가지 않아서다.
- 시험은 기본으로 현장 1에서 돈다(`IntegrationTest`). 장비는 `newEquipment()`(현장 1 배정 포함). 현장을 가로질러 확인할 땐 JDBC로 센다.

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
| 422 | 장비는 있는데 `recordedAt` 시점에 현장 배정이 없음(단말 시계 오류 등) |
| 429 + `Retry-After: 1` | 장비당 초당 10건 초과 |

현장은 보내지 않는다. 서버가 배정 이력에서 찾는다.

`fixType`: `RTK_FIXED` `RTK_FLOAT` `DGPS` `SINGLE` `NONE`. `speedKmh`·`heading`·`horizontalAccuracyM`은 생략 가능.

### 조회

| 요청 | 응답 |
|---|---|
| `GET /api/v1/sites/{siteId}/equipment` | `[{equipmentId, latest, zoneIds}]` 이 현장에서 최근 위치를 보낸 장비 |
| `GET /api/v1/sites/{siteId}/equipment/{id}/latest` | 이 현장에서의 최신 위치. 기록 없으면 404 |
| `GET /api/v1/sites/{siteId}/equipment/{id}/events?from=&to=&limit=` | 시각 범위 이력(ISO-8601), 최신순 |
| `GET /api/v1/sites/{siteId}/events/recent?limit=` | 현장 최근 이력, 최신순 |
| `GET /api/v1/hq/sites` | 본사: 현장 목록 `[{id, name}]` |
| `GET /api/v1/hq/events/recent?limit=` | 본사: 전 현장 최근 이력(`siteId` 포함) |

없는 현장은 404. `limit`은 기본 100, 1~1000으로 자른다. 권한은 아직 없다([#36](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/36)).

## 데이터

Flyway `src/main/resources/db/migration/`, 표당 파일 하나(MySQL DDL은 트랜잭션에 안 들어가므로).

★는 `site_id`가 있는 현장 표(`@TenantId`). 열은 기본값과 함께 추가했다가 코드가 채우게 된 뒤 기본값을 지웠다(#30 결정 3).

| 표 | 주요 열 |
|---|---|
| `site` | `name` UNIQUE. id 1 = 기본 현장(Flyway) |
| `equipment` | `type`, `positioning_source`(PHONE / PHONE_RTK). 현장을 가로지른다 |
| `equipment_site_assignment` | 장비의 현장 배정 이력 `[assigned_from, assigned_to)`, 끝이 비면 지금도 배정 중 |
| `zone` ★ | `kind`(WORK / DANGER), `polygon` JSON `[[lat, lon], ...]`, `active` |
| `location_event` ★ | `equipment_id` FK, `lat`/`lon` DOUBLE, `fix_type`, `h_accuracy_m`, `recorded_at`(단말) / `received_at`(API 수신) DATETIME(6) UTC, `idempotency_key` UNIQUE. 인덱스 `(equipment_id, recorded_at)`, `(site_id, recorded_at)` |
| `geofence_event` ★ | ENTER/EXIT, 원인 `location_event_id` |
| `equipment_zone_presence` ★ | 장비가 지금 들어가 있는 구역 |
| `proximity_event` ★ | NEAR/CLEAR, 장비 쌍(작은 id, 큰 id), `distance_m` |
| `proximity_pair_state` ★ | 지금 근접 중인 쌍 |

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
| 현장 열 추가 | 10만 행 `location_event`에 열 추가 + 인덱스 + FK 184 ms(V13), 6개 표 기본값 제거 64 ms(V18~V23). 행을 다시 쓰지 않음(INSTANT) | [#30](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/30) |

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
| [#30](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/30) | 현장 모델: 위치의 현장 시점, `@TenantId`, 열 추가 순서, Redis 재구성, 현장 경로·본사, 현장 미지정 시 fail-closed |

날짜별 학습 기록은 `study-log/`. 사고 과정 정리(`docs/`)는 작성 중.

## 남은 것

이슈 #31~#40. 순서: 장비 현장 이동(#31) → 현장별 설정(#32) → 시뮬레이터·부하 측정(#33) → 안드로이드 단말(#34) → 근접 알림(#35) → 인증·권한(#36) → 관제 화면(#37) → 돌려보기(#38) → 히트맵(#39). `location` 패키지 정리(#40).
