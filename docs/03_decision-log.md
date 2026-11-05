# 03_decision-log.md

GitHub 이슈 댓글(결정 하나 = 댓글 하나)을 옮긴 것. 원문: [hyunjyyyy/heavy-eq-tracker#1](https://github.com/hyunjyyyy/heavy-eq-tracker/issues/1)

## Day 1

### 1. DB: MySQL 9.7 LTS
* **결정**: MySQL 9.7 LTS (`mysql:9.7`)
* **판단 이유**: 공고 요건이 MySQL 설계·쿼리 튜닝. InnoDB 클러스터드 인덱스, EXPLAIN, REPEATABLE READ 기본 격리 등 PostgreSQL과 다른 지점을 직접 겪는 것이 목적. 8.0은 2026-04 EOL. 8.4 LTS(~2032)와 9.7 LTS(~2031) 중 신규 프로젝트 기본값인 9.7.
* **대안**: PostgreSQL 16 — 이미 운영 경험이 있고 DDL 트랜잭션·PostGIS로 이 프로젝트엔 더 편하다. 8.4 LTS — 실무 현장에 더 많이 깔려 있을 버전. 9.7로 제자리 업그레이드 가능.

### 2. 패키지 구조: 도메인별
* **결정**: `equipment/`, `zone/`, `location/`, 공통은 `common/`. 각 패키지 안에 엔티티·Repository·Service·Controller.
* **판단 이유**: 4일 차에 판정 로직이 들어오면 계층별 구조에서는 `service/`가 비대해진다. 메인 클래스가 최상위 패키지에 있어야 컴포넌트 스캔이 하위를 전부 잡는다.

### 3. `location_event` PK: `BIGINT AUTO_INCREMENT`
* **결정**: `BIGINT AUTO_INCREMENT`, JPA `GenerationType.IDENTITY`
* **판단 이유**: InnoDB는 PK 순서로 데이터가 물리 정렬(clustered)되고 보조 인덱스가 PK 값을 가리킨다. UUID는 길고 무작위라 INSERT가 흩어져 페이지 분할이 잦고 모든 보조 인덱스가 커진다.
* **대안**: 앱 생성 UUID — 배치 INSERT 가능, 분산 환경에서 ID 충돌 없음. 수집 서버가 여러 대에서 ID를 미리 알아야 하면 재검토. 4일 차 EXPLAIN에서 차이를 측정.

### 4. 시각: `DATETIME(6)` + `Instant`, `recorded_at` / `received_at` 분리
* **결정**: 열은 `DATETIME(6)`, Kotlin은 `java.time.Instant`, `hibernate.jdbc.time_zone=UTC`. 단말 측위 시각과 서버 수신 시각을 따로 둔다.
* **판단 이유**: `TIMESTAMP`는 세션 시간대 변환이 끼고 2038년 한계. `LocalDateTime`은 시간대 정보가 없어 UTC 14:00이 KST 23:00인지 코드만 봐선 모른다. 두 시각을 분리해야 단말 시계 오차·네트워크 지연을 구분할 수 있고 3일 차 Kafka 순서 실험의 기준이 된다.
* **확인**: 로그 `binding parameter (8:TIMESTAMP_UTC) <- [...Z]`. JVM은 KST, DB엔 UTC.

### 5. 좌표: `lat` / `lon` `DOUBLE` 두 열
* **결정**: `lat DOUBLE`, `lon DOUBLE`. `lng`가 아니라 `lon`.
* **판단 이유**: JPA 매핑이 단순하고 점-다각형 판정은 앱에서 한다. `lon`은 GNSS/NMEA 관례라 RTK 단말 도메인에 맞다. 단말 JSON·DTO·엔티티·DB 열을 같은 표기로 통일.
* **대안**: MySQL `POINT` — 공간 인덱스 가능, hibernate-spatial 필요. 4일 차 "JPQL로 안 되는 공간 계산 → 네이티브 쿼리" 경계를 보여 줄 때 `V4`로 추가하는 선택지.

### 6. 멱등키: 헤더 + DB UNIQUE
* **결정**: `Idempotency-Key` 헤더. `location_event.idempotency_key VARCHAR(64) NOT NULL`, `UNIQUE KEY uk_location_event_idempotency`.
* **판단 이유**: 헤더에 두면 Filter/Interceptor에서 처리하는 선택지가 살아 있다(바디는 Filter에서 읽으면 스트림이 소모된다). Node 서버의 "메모리 Map 60초 + DB 유니크 두 겹" 중 DB 겹을 먼저 만든 것. `NOT NULL`이 없으면 MySQL UNIQUE는 NULL을 여러 개 허용해 구멍이 남는다. 제약에 이름을 주면 중복 에러 메시지와 EXPLAIN에서 바로 알아볼 수 있다.

### 7. 요청/응답 DTO와 엔티티 분리
* **결정**: 요청 `LocationReport`, 응답 `LocationAck`는 `data class`. 엔티티는 일반 `class`.
* **판단 이유**: 단말 JSON 필드명과 DB 열은 바뀌는 속도와 주체가 다르다. 검증 어노테이션은 경계(DTO)에 둔다. `open-in-view=false`라 LAZY 필드를 Controller에서 못 건드리므로 Service가 DTO로 바꿔 돌려줘야 한다. 엔티티에 `data class`를 쓰지 않는 이유: `equals/hashCode`가 모든 필드로 계산돼 id 변화·LAZY 로딩과 충돌한다.

### 8. `location_event.equipment_id`: `@ManyToOne`
* **결정**: `@ManyToOne(fetch = LAZY) @JoinColumn(name = "equipment_id")` + DB FK `fk_location_event_equipment`.
* **판단 이유**: `Long equipmentId`로 두면 N+1이 아예 생기지 않는다는 걸 알면서도, 4일 차에 N+1을 직접 겪고 fetch join / `@EntityGraph`로 고치는 과정을 기록하려고 연관을 걸었다. 학습 목적이 설계 목적보다 우선한 결정.
* **주의**: `fetch = LAZY`를 명시하지 않으면 기본 EAGER라 항상 N+1. `allOpen`이 빠지면 프록시를 못 만들어 LAZY가 조용히 EAGER로 바뀐다.

### 9. Flyway 파일: 표당 하나
* **결정**: `V1__equipment.sql`, `V2__zone.sql`, `V3__location_event.sql`
* **판단 이유**: MySQL은 DDL이 트랜잭션에 포함되지 않아(암묵적 커밋) 한 파일 중간에 실패하면 앞의 표가 남는다. 파일당 표 하나면 실패 지점이 곧 복구 지점. PostgreSQL이었다면 DDL도 롤백되므로 안 쪼갰을 것.
* **실제로 겪은 것**: 02의 1.5.

### 10. RTK 품질 열 오늘 추가
* **결정**: `equipment.positioning_source` (PHONE / PHONE_RTK), `location_event.fix_type` (RTK_FIXED / RTK_FLOAT / DGPS / SINGLE / NONE), `location_event.h_accuracy_m` (NULL 허용).
* **판단 이유**: 장비에 따라 측위가 핸드폰 GPS(수 m)와 핸드폰+RTK(cm급)로 갈린다. 표 설계 단계에 넣는 게 가장 싸다.
* **다음**: 4일 차 근접 판정 임계값을 두 장비 오차 반경의 합으로, 지오펜스 판정을 안/모호/밖 3상태로 확장하는 선택지.

### 11. Spring Boot 4.1 / JDK 21
* **결정**: Spring Boot 4.1.2, Kotlin 2.3, JDK 21
* **판단 이유**: 2026-11 신규 프로젝트에 3.x를 고를 이유 없음. 4.1 기준선은 17이고 25까지 지원. 21은 LTS이고 프로젝트가 쓰는 기능 중 21 초과를 요구하는 게 없다.
* **비용**: 블로그·튜토리얼 대부분 3.x 기준. 스타터 이름 세분화(`starter-webmvc`), Jackson 3(`tools.jackson`), Hibernate 7 로그 키(`org.hibernate.orm.jdbc.bind`)가 다르다.

### 12. 로컬 자격증명 `.env` 분리, Gradle 시험 태스크가 `.env` 로드
* **결정**: `compose.yaml`은 `${VAR}` 치환, 값은 `.env`(gitignore) + `.env.example`(더미 값 커밋). `application.yml`은 기본값 없이 `${DB_NAME}`. `tasks.withType<Test>`가 `.env`를 읽어 환경변수로 주입.
* **판단 이유**: 로컬 일회용 컨테이너 비밀번호라 보안 문제는 아니지만, 2~5일 차에 같은 패턴을 재사용하고 운영 전환 경로를 보여 주기 위해. Spring은 `.env`를 읽지 않으므로 yml 기본값을 두면 값이 두 곳에 산다. `.env`를 단일 출처로.
* **대안**: yml 기본값 `${DB_NAME:tracker}` — 클론 후 설정 없이 돌지만 값 중복. PowerShell에서 수동 로드.

### 13. 시험 DB: 오늘은 로컬 compose MySQL
* **결정**: `contextLoads`는 main의 `application.yml`을 그대로 읽어 로컬 compose MySQL에 붙는다. 시험용 yml 없음.
* **판단 이유**: `spring-boot-docker-compose`는 `developmentOnly`라 시험 클래스패스에 없다. URL 명시 + 포트 `3306:3306` 고정이면 컨테이너가 떠 있는 한 같은 DB. Node에서 "시험마다 실제 DB를 띄우던" 방식과 같다.
* **다음**: 5일 차 Testcontainers + `@ServiceConnection`으로 교체하면서 무엇이 달라졌는지 기록.

### 14. 멱등키 중복 시 응답: B → Redis 도입 후 C
* **결정**: 오늘은 B. `findByIdempotencyKey`로 먼저 조회해 있으면 기존 이벤트 id를 200으로, 없으면 저장 후 201. 2일 차 Redis 키가 들어오면 C로 확장.
* **판단 이유**: 멱등의 본뜻은 "같은 요청에 같은 응답". 409는 단말이 재전송을 실패로 보게 한다. B의 동시성 구멍(두 요청이 조회를 둘 다 통과 → 하나는 UNIQUE 위반)은 알고 두고, Redis 겹으로 메우면서 Node의 두 겹과 비교.
* **에러 응답 형식**: Spring 내장 ProblemDetail(RFC 9457). 직접 정의한 JSON 대신 표준.

### 15. 커넥션 풀 크기: 기본값 10 유지
* **결정**: HikariCP `maximumPoolSize` 기본값 10, `minimumIdle` 기본값(=10, 고정 크기 풀). yml에 적지 않는다.
* **판단 이유**: HikariCP 권고 식 `코어 × 2 + 디스크 수`가 로컬 4코어 SSD에서 약 10. 연결이 많을수록 DB 쪽 락·컨텍스트 스위칭이 늘어 오히려 느려진다. Tomcat 스레드 200개는 10개 연결을 기다리는 것이 의도된 설계. Node에서 `max: 5`를 근거 없이 둔 것과 달리 "측정 전이므로 기본값, 4~5일 차 부하 측정 후 재검토"를 근거로 적는다.

---
* **Q. PostgreSQL이 더 편한 걸 알면서 MySQL을 고른 결정, 프로젝트가 끝나고도 맞았다고 생각하나?**
    * (직접 채우기)
* **Q. `@ManyToOne`을 학습 목적으로 걸었다. 실무였다면 어떻게 했을 건가?**
    * (직접 채우기)
