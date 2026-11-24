# heavy-eq-tracker
[![CI](https://github.com/hyunjyyyy/heavy-eq-tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/hyunjyyyy/heavy-eq-tracker/actions/workflows/ci.yml)

건설 중장비들의 위치를 받아 지오펜스 진입/이탈과 장비 간 근접을 판정하는 관제 백엔드.<br>
Java 21 · Spring Boot 4.1 · Kafka · MySQL · Redis, 시험은 Testcontainers.<br>
시뮬레이터로 부하를 재서 컨슈머 병목을 찾아 장비 100대 판정 지연 p99를 45초 → 0.34초로 줄임.

현장이 여러 개이고 현장마다 따로 운영. 장비는 현장 사이를 옮겨다닐 수 있음. 본사는 모든 현장을 볼 수 있음.

개인 학습 프로젝트. 데이터는 전부 가짜이고 특정 회사/현장과 무관함.

## 구조

```
단말 ──POST /api/v1/locations──▶ 수집 API (속도 제한·멱등키는 Redis) ──202
                                   └▶ Kafka location-events (키 = 장비 ID, 파티션 3)
                                        └▶ 컨슈머 3개 ── 한 트랜잭션: 위치 저장 + 구역·근접 판정 ──▶ MySQL
                                                      └─ 커밋 뒤: 최신 위치·GEO ──▶ Redis
조회 API ──▶ Redis(최신 위치·GEO) + MySQL(이력)
```

<details><summary>수집 경로 자세히</summary>

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
```

</details>

<details><summary>조회 경로 자세히</summary>

```
조회 ──▶ SiteContextInterceptor (경로 {siteId}로 현재 현장, 없는 현장 404 / /hq/** 는 전 현장)
     ──▶ GET /api/v1/sites/{siteId}/equipment                현황 (GEO fleet:{siteId} + loc:* MGET + 현재 구역)
     ──▶ GET /api/v1/sites/{siteId}/equipment/{id}/latest    loc:{id}(이 현장 것일 때만) → 없으면 DB 최신 1건 → 캐시 채움
     ──▶ GET /api/v1/sites/{siteId}/equipment/{id}/events    이력 (from~to, limit)
     ──▶ GET /api/v1/sites/{siteId}/events/recent            현장 최근 이력
     ──▶ GET /api/v1/hq/sites, /api/v1/hq/events/recent      본사: 현장 목록, 전 현장 최근 이력
```

</details>

## 측정한 것

| 항목 | 수치 | 출처 |
|---|---|---|
| 캐시 미스 쿼리 | 10만 건에서 25,047행 16.6 ms → 1행 0.31 ms (`(equipment_id, recorded_at)`) | [#17](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/17) |
| N+1 | 최근 이력 1+3 → 1, 장비 이력 1+1 → 1 (`@EntityGraph`) | #17 |
| CI | 캐시 없음 1분 55초 → 있음 1분 14초 | [#22](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/22) |
| 늦게 오기 | 10%(3초), 반경 30 m, 장비 10대 60초: 늦은 위치로 판정된 이벤트 33 → 0(늦은 위치는 저장만), NEAR·CLEAR 112·108 → 82·76(기준 86·80) | [#45](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/45) |
| 부하(장비 100대) | 컨슈머 스레드 1 → 3: 판정 처리 50.0 → 97.6건/초, 판정 지연 p99 45 → 0.34초, lag 3,666 → 0 | [#46](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/46) |

## 주요 설계 결정

- 수집 API는 Kafka에만 넣고 202를 줌. DB·Kafka 이중 쓰기를 없애기 위함 ([#10](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/10))
- 멱등은 두 겹: API의 Redis 선점과 컨슈머의 DB 조회 + UNIQUE ([#6](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/6), [#11](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/11))
- 판정은 위치 저장과 같은 트랜잭션, Redis는 커밋 뒤. 롤백되는 것만 트랜잭션 안 ([#16](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/16))
- 현장은 표 하나에 `site_id`로 나누고, Hibernate `@TenantId`가 모든 조회에 붙임 ([#30](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/30))
- 장비 이동은 장비 행 잠금으로 줄 세우고, "열린 배정 하나" 유일 제약이 안전망 ([#31](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/31))

## 실행

사전: JDK 21, Docker Desktop.

```powershell
Copy-Item .env.example .env      # 값 채우기 (로컬 전용)
.\gradlew.bat bootRun             # MySQL·Redis·Kafka 컨테이너 자동 기동, Flyway 적용
```

macOS·Linux는 `cp .env.example .env`, `./gradlew bootRun`.

기본 현장(id 1)은 Flyway. 장비는 현장에 배정돼 있어야 수집(위치의 `recordedAt`이 배정 기간 안). 구역은 선택사항(넣었으면 재기동).

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

Docker만 켜져 있으면 됨. 시험이 Testcontainers로 MySQL 9.7·Redis 8·Kafka 4.1을 띄우고, `@ServiceConnection`이 접속 정보를 넣음.

- 통합 시험은 전부 `IntegrationTest`를 상속. 어노테이션이 같아야 컨텍스트가 하나만 뜨고 컨테이너도 한 벌만. ([#21 결정 1](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/21))
- 시험마다 `@BeforeEach`에서 표를 비우고 Redis를 `FLUSHALL`. 저장은 컨슈머 스레드가 따로 커밋해서 시험 트랜잭션 롤백으로는 지울 수 없음. (#21 결정 2)
- 비동기 결과는 Awaitility로 최대 10초 기다림. (#21 결정 3) 조건식은 시험 스레드에서(`pollInSameThread`). 현재 현장(`ThreadLocal`)이 다른 스레드로 따라가지 않아서
- 시험은 기본으로 현장 1에서(`IntegrationTest`). 장비는 `newEquipment()`(현장 1 배정 포함). 현장을 가로질러 확인할 땐 JDBC로 셈.

## 스택

| | 버전 | 비고 |
|---|---|---|
| Java / Spring Boot | 21 / 4.1.2 | Kotlin으로 시작해 Day 2에 Java로 전환 ([#4](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/4)) |
| MySQL | 9.7 LTS | Flyway 12, JPA/Hibernate 7, `ddl-auto: validate`, `open-in-view: false` |
| Redis | 8 | `StringRedisTemplate` + JSON 문자열, Lua, GEO |
| Kafka | 4.1 (KRaft) | `spring-boot-starter-kafka`, 파티션 3 |
| 지표 | Actuator + Micrometer, Prometheus 3.5 | `/actuator/prometheus`, 시간 지표는 히스토그램 |
| 로컬 인프라 | Docker Compose + Spring Boot Docker Compose Support | `bootRun`이 컨테이너를 띄움 |
| 시험 | JUnit 6, Testcontainers 2, Awaitility | 시험이 자기 MySQL·Redis·Kafka 컨테이너를 띄움 |
| CI | GitHub Actions | PR·main 푸시마다 `./gradlew build` |
| 빌드 | Gradle 9 (Kotlin DSL), Lombok(엔티티 `@Getter`·`@NoArgsConstructor`만) | |

## 구조 상세

- 수집 API는 DB에 쓰지 않음.
  - DB·Kafka 이중 쓰기를 API에서 없애기 위해서
  - 응답은 **202**이고 eventId를 돌려주지 않음. ([#10 결정 1](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/10))
- 멱등 처리는 두 군데
  - API의 Redis 선점(TTL 10분)과 컨슈머의 DB 조회 + UNIQUE. HTTP 재전송과 Kafka 재소비
- 판정은 위치 저장과 **같은 트랜잭션**
  - 판정이 실패하면 위치 행도 롤백되고, 컨슈머가 다시 받음
- Redis는 DB 트랜잭션 **밖**에서만. 캐시·GEO 갱신은 커밋 뒤 리스너, 선점 해제는 실패 시 `DEL`
- 캐시는 늦게 도착한 옛 위치에 덮이지 않음. 컨슈머와 조회 API의 캐시 미스 채우기가 같은 키에 쓰므로 비교와 쓰기를 Lua 한 번으로 함. ([#23 결정 1](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/23))
- 처리 못 한 메시지는 버리지 않음. 잠깐 실패는 재시도(약 35초)로 버티고, 깨진 JSON·없는 장비는 재시도 없이 DLT로 감. (#23 결정 4)
- 속도 제한·근접 값의 기본은 `application.yml`의 `tracker.*`임. 잘못된 값이면 기동이 실패함. (#23 결정 2) 근접 값은 현장마다 API로 덮어쓸 수 있고(항목마다, 비우면 기본값), 판정은 합친 값을 메모리에 두고 바뀌면 커밋 뒤 지우며 60초마다 다시 읽음. 속도 제한은 서버 보호 장치라 현장별로 두지 않음. ([#32](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/32))
- `location` 패키지는 판정 패키지(`zone`, `proximity`)를 모름. 이벤트만 발행하고 판정 쪽이 들음. ([#24](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/24))

### 모듈

앱 패키지 바로 아래 패키지 하나가 모듈 하나(Spring Modulith). 모듈의 맨 위 패키지만 다른 모듈이 쓸 수 있고, 하위 패키지는 내부. `ModularityTest`가 모듈 사이의 순환과 내부 접근을 시험으로 막음. ([#40](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/40))

| 모듈 | 하는 일 | 다른 모듈에 여는 것 |
|---|---|---|
| `common` | 예외 바탕(404·422·400)과 처리기, 거리 계산 | 전부. **다른 모듈을 의존하지 않는다**(`allowedDependencies = {}`) |
| `equipment` | 장비 | 전부 |
| `site` | 현장, 배정 이력, 현재 현장(`SiteContext`), 경로 인터셉터, 장비 이동(`EquipmentMoved` 발행) | 전부 |
| `location` | 위치 수집·저장·캐시·이력 | `LocationEvent`, `FixType`, `LocationSaved`, `location::latest`(`LatestLocation`, `LatestLocationCache`), `location::history`(`LocationHistoryService`, `LocationEventView`) |
| `zone` | 구역, 지오펜스 판정 | 전부 |
| `proximity` | 근접 판정(`judge`), 현장별 근접 설정(`settings`) | 없음 |
| `status` | 현황, 본사 조회 | 전부 |

`location`은 하는 일별로 나눔: `ingest`(수집 API·멱등·속도 제한) → `pipeline`(Kafka 발행·소비·저장·DLT) → `latest`(최신 위치 캐시·재구성), `history`(이력 조회), `store`(리포지토리). 하위 패키지끼리도 한 방향만 가리킴.

### 현장

- 표는 하나로 쓰고 `site_id`로 나눔
  - 현장 표 6개는 Hibernate `@TenantId`가 모든 조회에 `site_id = ?`를 붙이고 저장 때 값을 채움
  - 현재 현장은 `SiteContext`(`ThreadLocal`)가 들고, 조회는 경로, 컨슈머는 메시지, 시스템 작업은 전 현장(root) ([#30](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/30))
- 현재 현장을 정하지 않으면 "아무 현장도 아님"이라 조회는 빈 결과, 저장은 FK로 실패함. 남의 현장을 보거나 엉뚱한 현장에 쓰지 않음. (#30 결정 6)
- 위치의 현장은 **찍힌 시각**의 배정으로 정함. 끊겼다가 장비를 옮긴 뒤에 도착한 위치도 찍힌 곳의 현장으로 감. (#30 결정 1)
- `@TenantId`는 DB만 막음. Redis는 GEO 키를 현장별(`fleet:{siteId}`)로 두고, 장비 단위 `loc:{id}`는 꺼낸 값의 `siteId`로 거름.
- Redis는 DB 사본이라 기동 때 최근 24시간 장비별 최신 위치로 다시 채움. (#30 결정 4)
- **장비 이동**
  - 옛 현장 경로로 `POST …/move`를 받으면 장비 행을 잠그고, 옛 배정을 닫고, 새 배정을 열고, `EquipmentMoved`를 발행
  - 같은 트랜잭션에서 옛 현장의 구역 체류는 EXIT, 근접 쌍은 CLEAR로 닫고(원인 위치 없음), 커밋 뒤 `fleet:{옛 현장}`에서 뺌
  - `site`는 다른 모듈을 모르고 이벤트만 냄 ([#31](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/31))
- 이동 뒤에 도착한 옛 현장 위치(이동 전에 찍힘)는 옛 현장으로 **기록만** 하고 판정·캐시하지 않음. 정리한 체류·쌍·GEO가 되살아나지 않게. 저장도 이동과 같은 장비 잠금을 잡아 둘이 섞이지 않음. (#31 결정 2)
- 장비당 열린 배정은 하나. 이동은 장비 행 잠금으로 줄 세우고, DB 유일 제약이 안전망. (#31 결정 4)

### 판정

- **지오펜스**: ray casting 점-다각형. 그 현장 구역만 봄. 장비별 현재 구역(`equipment_zone_presence`)과 비교해 바뀐 것만 `geofence_event`에 ENTER/EXIT로 남김. 구역 목록은 기동 시 전 현장을 메모리에 읽음(`ZoneCatalog`). 구역을 바꾸면 재기동.
- **근접**: 같은 현장끼리만, 그 현장의 설정으로(아래 수치는 yml 기본값). GEO `fleet:{siteId}`에서 반경 30 m 후보 → 후보의 `loc:`이 없거나 10분 넘게 오래됐으면 제외 → 원본 좌표로 거리 재계산 → `거리 < 10 m + 내 오차 + 상대 오차`면 NEAR. 오차는 `horizontalAccuracyM`(없으면 PHONE 5 m / PHONE_RTK 0.05 m), 상한 10 m. 반경 30 m = 10 + 2×10이라 임계가 반경을 넘을 수 없음. 쌍 상태(`proximity_pair_state`)와 비교해 NEAR/CLEAR 전이만 `proximity_event`에 남김.

## 시뮬레이터·부하 측정

`simulator/`는 Spring 없는 Gradle 하위 모듈. 장비마다 가상 스레드 하나가 시드 고정 경로로 위치를 보냄. 현장 `100+n`, 장비 `현장id×1000+순번` 규칙을 `dev/seed.sql`과 같이 씀.

```powershell
$OutputEncoding = New-Object System.Text.UTF8Encoding $false   # 5.1은 파이프를 ASCII로 보내 한글 주석이 깨짐
Get-Content dev\seed.sql | docker exec -i heavy-eq-tracker-mysql-1 mysql -uroot "-p$DB_ROOT_PASSWORD" "$DB_NAME"   # 현장 10 × 장비 100, 다시 돌려도 됨
.\gradlew.bat bootRun --args="--spring.profiles.active=load"   # 측정용: 로그 WARN
.\dev\measure.ps1 -SimArgs "sites=1 perSite=100 warmupSec=15 durationSec=60"
```

| 인자 | 기본 | 뜻 |
|---|---|---|
| `sites`, `perSite` | 1, 10 | 현장 수(≤10), 현장당 장비 수(≤100) |
| `periodMs`, `durationSec`, `warmupSec` | 1000, 30, 0 | 보내는 주기, 측정 구간, 집계에서 뺄 예열 |
| `seed`, `radiusM` | 42, 300 | 경로 시드, 랜덤 워크 반경 |
| `lateRate`, `lateMs` | 0, 3000 | 이 비율만큼 이만큼 늦게 보냄(`recordedAt`은 그대로) |
| `resendRate` | 0 | 같은 키·본문을 한 번 더 보냄 |
| `dropRate`, `dropMs` | 0, 5000 | 끊겼다가 복구될 때 몰아 보냄. `dropMs`가 길면 속도 제한(10/초)에 걸림 |

- 판정 지연 지표는 둘임. `tracker_location_judged_since_received_seconds`(서버가 받은 때부터, 서버 성능)와 `..._since_recorded_seconds`(단말이 잰 때부터, 늦게 오기 포함). Prometheus는 http://localhost:9090
- 수집 API가 202라 시뮬레이터의 응답 시간은 Kafka에 넣기까지. 포화돼도 거의 그대로라 판정 지연은 서버 지표로 봄.
- lag은 `kafka_consumer_fetch_manager_records_lag_max`로 보지 않음. 컨슈머가 처리에 묶이면 실제 수만 건일 때도 0. `measure.ps1`은 그룹 명령(커밋된 오프셋)으로 셈. ([#33 결정 9](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/33))
- compose에 서비스를 더하면 `bootRun`이 띄우지 않음. 다른 서비스가 이미 떠 있으면 Boot가 `up`을 건너뜀. `docker compose up -d prometheus`

## API

### `POST /api/v1/locations` — 위치 수집

헤더 `Idempotency-Key`(필수, 64자 이하), `X-Equipment-Id`(필수, 바디와 같아야 함), `Content-Type: application/json`

```json
{"equipmentId":1,"lat":37.5,"lon":127.0,"speedKmh":12.3,"heading":90.0,
 "fixType":"RTK_FIXED","horizontalAccuracyM":0.02,"recordedAt":"2026-11-09T08:00:00Z"}
```

| 응답 | 뜻 |
|---|---|
| 202 `{"idempotencyKey":"..."}` | 받아서 토픽에 넣음. 같은 키 재전송도 202 |
| 400 | 검증 실패(ProblemDetail), 헤더·바디 장비 ID 불일치, 키 길이 초과 |
| 404 | 장비 없음 |
| 422 | 장비는 있는데 `recordedAt` 시점에 현장 배정이 없음(단말 시계 오류 등) |
| 429 + `Retry-After: 1` | 장비당 초당 10건 초과 |

현장은 보내지 않음. 서버가 배정 이력에서 찾음.

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

없는 현장은 404. `limit`은 기본 100, 1~1000으로 자름.

### `GET`·`PUT /api/v1/sites/{siteId}/settings/proximity` — 현장 근접 설정

`PUT` 바디는 덮어쓸 값만: `{"thresholdM": 20, "maxErrorM": null, "staleAfterSeconds": null, "defaultPhoneErrorM": null, "defaultRtkErrorM": null}`. `null`이면 yml 기본값을 따름.

응답 `{"overrides": {현장 값}, "effective": {합친 값 + searchRadiusM}}`. 0 이하 값은 400, 없는 현장은 404. 바꾸면 바로 판정에 반영됨.

### `POST /api/v1/sites/{fromSiteId}/equipment/{id}/move` — 장비 현장 이동

바디 `{"toSiteId": 2}`. 이동 시각은 서버가 요청을 받은 시각(과거·미래 지정 없음).

| 응답 | 뜻 |
|---|---|
| 200 `{"equipmentId", "siteId", "assignedFrom"}` | 새 배정 |
| 404 | 장비 또는 옮길 현장 없음 |
| 409 | 장비가 지금 `fromSiteId`에 없음, 같은 현장으로 옮김, 동시 이동에서 늦은 쪽 |

## 데이터

Flyway `src/main/resources/db/migration/`, 표당 파일 하나(MySQL DDL은 트랜잭션에 안 들어가므로).

★는 `site_id`가 있는 현장 표(`@TenantId`). 열은 기본값과 함께 추가했다가 코드가 채우게 된 뒤 기본값을 지움(#30 결정 3).

| 표 | 주요 열 |
|---|---|
| `site` | `name` UNIQUE. id 1 = 기본 현장(Flyway) |
| `equipment` | `type`, `positioning_source`(PHONE / PHONE_RTK). 현장을 가로지름 |
| `equipment_site_assignment` | 장비의 현장 배정 이력 `[assigned_from, assigned_to)`, 끝이 비면 지금도 배정 중. 장비당 열린 배정 하나(생성 열 `open_marker` + 유일 인덱스) |
| `zone` ★ | `kind`(WORK / DANGER), `polygon` JSON `[[lat, lon], ...]`, `active` |
| `location_event` ★ | `equipment_id` FK, `lat`/`lon` DOUBLE, `fix_type`, `h_accuracy_m`, `recorded_at`(단말) / `received_at`(API 수신) DATETIME(6) UTC, `idempotency_key` UNIQUE. 인덱스 `(equipment_id, recorded_at)`, `(site_id, recorded_at)` |
| `geofence_event` ★ | ENTER/EXIT, 원인 `location_event_id`(NULL = 장비 이동으로 닫힘) |
| `equipment_zone_presence` ★ | 장비가 지금 들어가 있는 구역 |
| `proximity_event` ★ | NEAR/CLEAR, 장비 쌍(작은 id, 큰 id), `distance_m`, 원인 `location_event_id`(NULL = 장비 이동으로 닫힘) |
| `proximity_pair_state` ★ | 지금 근접 중인 쌍 |
| `site_proximity_settings` | 현장별 근접 설정 덮어쓰기(현장 id가 PK, 열이 NULL이면 yml 기본값) |

## 측정 전체

| 항목 | 수치 | 출처 |
|---|---|---|
| 캐시 적중 | GET에 SQL 0회, 미스 시 1회 | [#7](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/7) |
| 파티션 키 | 장비 3대×20건 → 키마다 한 파티션, 오프셋 증가 순서 | [#12](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/12) |
| 재시작 | 종료 중 쌓인 2건을 재시작 후 이어 소비 (LAG 2 → 0) | #12 |
| 재소비 멱등 | 오프셋 earliest로 되돌려 62건 재소비 → 행 수 62 불변 | #12 |
| 리밸런스 | 컨슈머 1→2→1대, 26~185 ms | #12 |
| GEO 양자화 | GEO 거리 5.52 m → 원본 좌표 재계산 5.29 m | [#16](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/16) |
| 이력 쿼리 | filesort → range scan 901행 0.80 ms | #17 |
| 현장 열 추가 | 10만 행 `location_event`에 열 추가 + 인덱스 + FK 184 ms(V13), 6개 표 기본값 제거 64 ms(V18~V23). 행을 다시 쓰지 않음(INSTANT) | [#30](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/30) |
| 재전송 | 10%: 보냄 317, 재전송 27 → DB 290 | [#33](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/33) |
| 부하 | 장비 10 / 100 / 1000대(예열 15초, 60초), 컨슈머 스레드 1 → 3: 판정 처리 9.7 / 50.0 / 32.9 → 9.7 / 97.6 / 127.6건/초, 판정 지연 p99 0.04 / 45 / 68 → 0.06 / 0.34 / 65초, 실행 직후 lag 0 / 3,666 / 약 69,500 → 0 / 0 / 63,273. 100대까지 따라감. 1000대는 파티션 3개가 상한이라 범위 밖 | #33, [#46](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/46) |

이슈별 설계 결정 전체는 [docs/decisions.md](docs/decisions.md).

## 남은 것

- [#49](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/49): 실패한 CI 실행이 Gradle 캐시를 저장해 의존성 해석 실패가 반복됨.
- 단말 앱·근접 알림·인증·관제 화면·돌려보기·히트맵은 범위 밖으로 미룸. [범위 밖 — 보류](https://github.com/hyunjyyyy/heavy-eq-tracker/labels/%EB%B2%94%EC%9C%84%20%EB%B0%96%20%E2%80%94%20%EB%B3%B4%EB%A5%98)
