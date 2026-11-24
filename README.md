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
- 속도 제한·근접 값의 기본은 `application.yml`의 `tracker.*`다. 잘못된 값이면 기동이 실패한다. (#23 결정 2) 근접 값은 현장마다 API로 덮어쓸 수 있고(항목마다, 비우면 기본값), 판정은 합친 값을 메모리에 두고 바뀌면 커밋 뒤 지우며 60초마다 다시 읽는다. 속도 제한은 서버 보호 장치라 현장별로 두지 않는다. ([#32](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/32))
- `location` 패키지는 판정 패키지(`zone`, `proximity`)를 모른다. 이벤트만 발행하고 판정 쪽이 듣는다. ([#24](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/24))

### 모듈

앱 패키지 바로 아래 패키지 하나가 모듈 하나다(Spring Modulith). 모듈의 맨 위 패키지만 다른 모듈이 쓸 수 있고, 하위 패키지는 내부다. `ModularityTest`가 모듈 사이의 순환과 내부 접근을 시험으로 막는다. ([#40](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/40))

| 모듈 | 하는 일 | 다른 모듈에 여는 것 |
|---|---|---|
| `common` | 예외 바탕(404·422·400)과 처리기, 거리 계산 | 전부. **다른 모듈을 의존하지 않는다**(`allowedDependencies = {}`) |
| `equipment` | 장비 | 전부 |
| `site` | 현장, 배정 이력, 현재 현장(`SiteContext`), 경로 인터셉터, 장비 이동(`EquipmentMoved` 발행) | 전부 |
| `location` | 위치 수집·저장·캐시·이력 | `LocationEvent`, `FixType`, `LocationSaved`, `location::latest`(`LatestLocation`, `LatestLocationCache`), `location::history`(`LocationHistoryService`, `LocationEventView`) |
| `zone` | 구역, 지오펜스 판정 | 전부 |
| `proximity` | 근접 판정(`judge`), 현장별 근접 설정(`settings`) | 없음 |
| `status` | 현황, 본사 조회 | 전부 |

`location`은 하는 일별로 나눈다: `ingest`(수집 API·멱등·속도 제한) → `pipeline`(Kafka 발행·소비·저장·DLT) → `latest`(최신 위치 캐시·재구성), `history`(이력 조회), `store`(리포지토리). 하위 패키지끼리도 한 방향만 가리킨다.

### 현장

- 표는 하나로 쓰고 `site_id`로 나눈다. 현장 표 6개는 Hibernate `@TenantId`가 모든 조회에 `site_id = ?`를 붙이고 저장 때 값을 채운다. 현재 현장은 `SiteContext`(`ThreadLocal`)가 들고, 조회는 경로, 컨슈머는 메시지, 시스템 작업은 전 현장(root)으로 정한다. ([#30](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/30))
- 현재 현장을 정하지 않으면 "아무 현장도 아님"이라 조회는 빈 결과, 저장은 FK로 실패한다. 남의 현장을 보거나 엉뚱한 현장에 쓰지 않는다. (#30 결정 6)
- 위치의 현장은 **찍힌 시각**의 배정으로 정한다. 끊겼다가 장비를 옮긴 뒤에 도착한 위치도 찍힌 곳의 현장으로 간다. (#30 결정 1)
- `@TenantId`는 DB만 막는다. Redis는 GEO 키를 현장별(`fleet:{siteId}`)로 두고, 장비 단위 `loc:{id}`는 꺼낸 값의 `siteId`로 거른다.
- Redis는 DB 사본이라 기동 때 최근 24시간 장비별 최신 위치로 다시 채운다. (#30 결정 4)
- **장비 이동**: 옛 현장 경로로 `POST …/move`를 받으면 장비 행을 잠그고, 옛 배정을 닫고, 새 배정을 열고, `EquipmentMoved`를 발행한다. 같은 트랜잭션에서 옛 현장의 구역 체류는 EXIT, 근접 쌍은 CLEAR로 닫고(원인 위치 없음), 커밋 뒤 `fleet:{옛 현장}`에서 뺀다. `site`는 다른 모듈을 모르고 이벤트만 낸다. ([#31](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/31))
- 이동 뒤에 도착한 옛 현장 위치(이동 전에 찍힘)는 옛 현장으로 **기록만** 하고 판정·캐시하지 않는다. 정리한 체류·쌍·GEO가 되살아나지 않게. 저장도 이동과 같은 장비 잠금을 잡아 둘이 섞이지 않는다. (#31 결정 2)
- 장비당 열린 배정은 하나. 이동은 장비 행 잠금으로 줄 세우고, DB 유일 제약이 안전망이다. (#31 결정 4)

### 판정

- **지오펜스**: ray casting 점-다각형. 그 현장 구역만 본다. 장비별 현재 구역(`equipment_zone_presence`)과 비교해 바뀐 것만 `geofence_event`에 ENTER/EXIT로 남긴다. 구역 목록은 기동 시 전 현장을 메모리에 읽는다(`ZoneCatalog`). 구역을 바꾸면 재기동한다.
- **근접**: 같은 현장끼리만, 그 현장의 설정으로(아래 수치는 yml 기본값). GEO `fleet:{siteId}`에서 반경 30 m 후보 → 후보의 `loc:`이 없거나 10분 넘게 오래됐으면 제외 → 원본 좌표로 거리 재계산 → `거리 < 10 m + 내 오차 + 상대 오차`면 NEAR. 오차는 `horizontalAccuracyM`(없으면 PHONE 5 m / PHONE_RTK 0.05 m), 상한 10 m. 반경 30 m = 10 + 2×10이라 임계가 반경을 넘을 수 없다. 쌍 상태(`proximity_pair_state`)와 비교해 NEAR/CLEAR 전이만 `proximity_event`에 남긴다.

## 스택

| | 버전 | 비고 |
|---|---|---|
| Java / Spring Boot | 21 / 4.1.2 | Kotlin으로 시작해 Day 2에 Java로 전환 ([#4](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/4)) |
| MySQL | 9.7 LTS | Flyway 12, JPA/Hibernate 7, `ddl-auto: validate`, `open-in-view: false` |
| Redis | 8 | `StringRedisTemplate` + JSON 문자열, Lua, GEO |
| Kafka | 4.1 (KRaft) | `spring-boot-starter-kafka`, 파티션 3 |
| 지표 | Actuator + Micrometer, Prometheus 3.5 | `/actuator/prometheus`, 시간 지표는 히스토그램 |
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

### 시뮬레이터·부하 측정

`simulator/`는 Spring 없는 Gradle 하위 모듈이다. 장비마다 가상 스레드 하나가 시드 고정 경로로 위치를 보낸다. 현장 `100+n`, 장비 `현장id×1000+순번` 규칙을 `dev/seed.sql`과 같이 쓴다.

```powershell
$OutputEncoding = New-Object System.Text.UTF8Encoding $false   # 5.1은 파이프를 ASCII로 보내 한글 주석이 깨진다
Get-Content dev\seed.sql | docker exec -i heavy-eq-tracker-mysql-1 mysql -uroot "-p$DB_ROOT_PASSWORD" "$DB_NAME"   # 현장 10 × 장비 100, 다시 돌려도 된다
.\gradlew.bat bootRun --args="--spring.profiles.active=load"   # 측정용: 로그 WARN
.\dev\measure.ps1 -SimArgs "sites=1 perSite=100 warmupSec=15 durationSec=60"
```

| 인자 | 기본 | 뜻 |
|---|---|---|
| `sites`, `perSite` | 1, 10 | 현장 수(≤10), 현장당 장비 수(≤100) |
| `periodMs`, `durationSec`, `warmupSec` | 1000, 30, 0 | 보내는 주기, 측정 구간, 집계에서 뺄 예열 |
| `seed`, `radiusM` | 42, 300 | 경로 시드, 랜덤 워크 반경 |
| `lateRate`, `lateMs` | 0, 3000 | 이 비율만큼 이만큼 늦게 보낸다(`recordedAt`은 그대로) |
| `resendRate` | 0 | 같은 키·본문을 한 번 더 보낸다 |
| `dropRate`, `dropMs` | 0, 5000 | 끊겼다가 복구될 때 몰아 보낸다. `dropMs`가 길면 속도 제한(10/초)에 걸린다 |

- 판정 지연 지표는 둘이다. `tracker_location_judged_since_received_seconds`(서버가 받은 때부터, 서버 성능)와 `..._since_recorded_seconds`(단말이 잰 때부터, 늦게 오기 포함). Prometheus는 http://localhost:9090
- 수집 API가 202라 시뮬레이터의 응답 시간은 Kafka에 넣기까지다. 포화돼도 거의 그대로라 판정 지연은 서버 지표로 본다.
- lag은 `kafka_consumer_fetch_manager_records_lag_max`로 보지 않는다. 컨슈머가 처리에 묶이면 실제 수만 건일 때도 0이 나온다. `measure.ps1`은 그룹 명령(커밋된 오프셋)으로 센다. ([#33 결정 9](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/33))
- compose에 서비스를 더하면 `bootRun`이 띄우지 않는다. 다른 서비스가 이미 떠 있으면 Boot가 `up`을 건너뛴다. `docker compose up -d prometheus`

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

### `GET`·`PUT /api/v1/sites/{siteId}/settings/proximity` — 현장 근접 설정

`PUT` 바디는 덮어쓸 값만: `{"thresholdM": 20, "maxErrorM": null, "staleAfterSeconds": null, "defaultPhoneErrorM": null, "defaultRtkErrorM": null}`. `null`이면 yml 기본값을 따른다.

응답 `{"overrides": {현장 값}, "effective": {합친 값 + searchRadiusM}}`. 0 이하 값은 400, 없는 현장은 404. 바꾸면 바로 판정에 반영된다.

### `POST /api/v1/sites/{fromSiteId}/equipment/{id}/move` — 장비 현장 이동

바디 `{"toSiteId": 2}`. 이동 시각은 서버가 요청을 받은 시각이다(과거·미래 지정 없음).

| 응답 | 뜻 |
|---|---|
| 200 `{"equipmentId", "siteId", "assignedFrom"}` | 새 배정 |
| 404 | 장비 또는 옮길 현장 없음 |
| 409 | 장비가 지금 `fromSiteId`에 없음, 같은 현장으로 옮김, 동시 이동에서 늦은 쪽 |

## 데이터

Flyway `src/main/resources/db/migration/`, 표당 파일 하나(MySQL DDL은 트랜잭션에 안 들어가므로).

★는 `site_id`가 있는 현장 표(`@TenantId`). 열은 기본값과 함께 추가했다가 코드가 채우게 된 뒤 기본값을 지웠다(#30 결정 3).

| 표 | 주요 열 |
|---|---|
| `site` | `name` UNIQUE. id 1 = 기본 현장(Flyway) |
| `equipment` | `type`, `positioning_source`(PHONE / PHONE_RTK). 현장을 가로지른다 |
| `equipment_site_assignment` | 장비의 현장 배정 이력 `[assigned_from, assigned_to)`, 끝이 비면 지금도 배정 중. 장비당 열린 배정 하나(생성 열 `open_marker` + 유일 인덱스) |
| `zone` ★ | `kind`(WORK / DANGER), `polygon` JSON `[[lat, lon], ...]`, `active` |
| `location_event` ★ | `equipment_id` FK, `lat`/`lon` DOUBLE, `fix_type`, `h_accuracy_m`, `recorded_at`(단말) / `received_at`(API 수신) DATETIME(6) UTC, `idempotency_key` UNIQUE. 인덱스 `(equipment_id, recorded_at)`, `(site_id, recorded_at)` |
| `geofence_event` ★ | ENTER/EXIT, 원인 `location_event_id`(NULL = 장비 이동으로 닫힘) |
| `equipment_zone_presence` ★ | 장비가 지금 들어가 있는 구역 |
| `proximity_event` ★ | NEAR/CLEAR, 장비 쌍(작은 id, 큰 id), `distance_m`, 원인 `location_event_id`(NULL = 장비 이동으로 닫힘) |
| `proximity_pair_state` ★ | 지금 근접 중인 쌍 |
| `site_proximity_settings` | 현장별 근접 설정 덮어쓰기(현장 id가 PK, 열이 NULL이면 yml 기본값) |

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
| 재전송 | 10%: 보냄 317, 재전송 27 → DB 290 | [#33](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/33) |
| 늦게 오기 | 10%(3초), 반경 30 m, 장비 10대 60초: 늦은 위치로 판정된 이벤트 33 → 0(늦은 위치는 저장만), NEAR·CLEAR 112·108 → 82·76(기준 86·80) | [#45](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/45) |
| 부하 | 장비 10 / 100 / 1000대(예열 15초, 60초), 컨슈머 스레드 1 → 3: 판정 처리 9.7 / 50.0 / 32.9 → 9.7 / 97.6 / 127.6건/초, 판정 지연 p99 0.04 / 45 / 68 → 0.06 / 0.34 / 65초, 실행 직후 lag 0 / 3,666 / 약 69,500 → 0 / 0 / 63,273. 100대까지 따라간다. 1000대는 파티션 3개가 상한이라 범위 밖 | #33, [#46](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/46) |

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
| [#31](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/31) | 장비 현장 이동: 이동 시각·경로, 이동 뒤 도착한 위치는 기록만, 동기 이벤트로 정리, 잠금 + 열린 배정 하나, 원인 위치 NULL |
| [#32](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/32) | 현장별 설정: 캐시 + 커밋 뒤 무효화 + TTL, 합친 설정이 반경 계산, 속도 제한은 전체 하나 |
| [#33](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/33) | 시뮬레이터: 하위 모듈, 시드 고정 경로, id 규칙, 시험만 의존, Prometheus·판정 지연 둘, load 프로필, 예열, 포화는 기록만, lag은 그룹 명령, 측정 스크립트 |
| [#40](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/40) | 모듈 경계: `location` 하위 패키지, 클래스 단위 named interface, `common` 예외 바탕 클래스 |

날짜별 학습 기록은 `study-log/`. 사고 과정 정리(`docs/`)는 작성 중.

## 남은 것

이슈 #34~#39, #45, #46. #33 측정에서 나온 것: 늦게 도착한 위치 판정(#45), 컨슈머 처리량(#46). 순서: 안드로이드 단말(#34) → 근접 알림(#35) → 인증·권한(#36) → 관제 화면(#37) → 돌려보기(#38) → 히트맵(#39).
