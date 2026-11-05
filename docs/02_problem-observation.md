# 02_problem-observation.md

## Day 1 — 2026-11-05 (수)

### 1.1 용어가 비어 있었다
* **관찰**: "HikariCP 풀은 누가 만들고 Repository는 어떻게 받나"라는 질문에 답을 못 했다. 풀, 싱글턴, `fun`, `mutableMapOf`가 뭔지 몰랐다.
* **원인**: Node 서버의 `pg.Pool({ max: 5 })`이 커넥션 풀이었고 `db.js`가 모듈 캐시 덕에 싱글턴이었는데, 그걸 이름으로 알지 못한 채 쓰고 있었다.
* **영향**: 개념 설명 → 문제 → 답 → 다음 개념 순서로 바꿨다. 문법 때문에 막히면 답이 아니라 문법만 묻기로 했다.

### 1.2 저장소 안에 저장소가 들어갔다
* **관찰**: 첫 커밋에 `heavy-eq-tracker`라는 이름의 항목이 모드 `160000`(gitlink)으로 들어갔다. 작업 폴더 안에 같은 저장소의 두 번째 클론이 있었고 `git add .`가 서브모듈처럼 기록했다.
* **조치**: `git rm --cached` + 폴더 삭제. 지우기 전에 안에 작업물이 없는지 먼저 확인했다.

### 1.3 설정이 없으면 Spring은 죽고, Node는 조용히 돈다
* **관찰**: `gradlew build`가 `Failed to determine a suitable driver class`로 실패. `application.properties`가 비어 있었다.
* **읽는 법**: `Caused by` 사슬의 맨 아래가 진짜 원인. 사슬 자체가 자동 구성이 만든 의존 그래프(`DataSource` → `Flyway` → `EntityManagerFactory`)를 보여 줬다.
* **대비**: Node에서 `.env` 키 오타는 `undefined` → `pg`가 localhost 기본값으로 조용히 넘어가고 첫 쿼리에서 터졌다. Spring은 기동에서 막는다. yml 키 오타는 Spring도 조용히 넘어가지만, IDE 메타데이터와 `@Validated`로 막을 수 있다.

### 1.4 `${DB_NAME:tracker}`는 `.env`를 무의미하게 만든다
* **관찰**: yml에 기본값을 두면 같은 값이 `.env`와 yml 두 곳에 산다는 걸 내가 먼저 지적했다.
* **원인**: Spring은 `.env`를 읽지 않는다. compose만 읽는다. 시험은 compose를 안 띄우므로 환경변수가 없다.
* **조치**: 기본값을 빼고 Gradle 시험 태스크가 `.env`를 읽어 환경변수로 넣게 했다(결정 12). 설정이 없으면 죽는 쪽이 맞다.

### 1.5 Flyway V2 실패와 이력 표
* **관찰**: 두 번째 기동에서 `Detected failed migration to version 2 (zone)`. 진짜 원인(SQL 에러)은 첫 기동 로그에만 있었고 덮어써졌다.
* **확인**: `flyway_schema_history`에 `version=1 success=1`, `version=2 success=0`. `SHOW TABLES`에 `zone` 없음. 직접 만든 도구의 "적용된 파일 목록"이 이 표였다.
* **원인**: V1을 복사해 V2를 만들고 표 이름을 안 바꿔 `Table 'equipment' already exists`.
* **조치**: 로컬이라 `docker compose down -v`로 초기화. 머지 전 브랜치의 로컬 전용 DB라 V2/V3를 직접 수정했고, 머지 이후부터는 `V4`로만 고친다는 선을 확인했다.
* **덤**: 파일을 표당 하나로 쪼갠 덕에 실패 지점이 V2 하나로 좁혀졌다(결정 9).

### 1.6 `bootRun`이 85%에서 멈춘 줄 알았다
* **관찰**: Gradle 진행률이 85%에서 안 움직였다.
* **실제**: 서버가 떠 있는 상태. Gradle은 `bootRun`을 실행 중인 태스크로 보기 때문에 끝날 때까지 100%가 안 된다. `node app.js` 치고 커서만 깜빡이는 것과 같다.

### 1.7 컨테이너 이름이 PC마다 달랐다
* **관찰**: Windows와 Mac에서 컨테이너 이름이 달랐다.
* **원인**: Compose 프로젝트 이름 기본값이 `compose.yaml`이 있는 폴더 이름.
* **조치**: `compose.yaml`에 `name: heavy-eq-tracker` 고정.

### 1.8 첫 요청이 201이 아니라 200
* **관찰**: `k-001`로 보낸 첫 curl이 200. 코드 버그로 의심했다.
* **실제**: 새 키 `k-003`은 201. `k-001`은 이전 시도에서 이미 저장돼 있었고, 200은 멱등 처리(결정 14의 B)가 정상 동작한 것.
* **로그로 확인**: 재전송 시 `select ... where idempotency_key=?` 하나만 나가고 `insert`가 없다.

### 1.9 수집 요청 하나에 쿼리 3회
* **관찰**: `select location_event by key` → `select equipment by id` → `insert location_event`.
* **메모**: 두 번째는 장비 `id`만 필요한데 행 전체를 읽는다. `getReferenceById`로 없앨 수 있지만 "없는 장비" 처리가 FK 위반으로 바뀐다. 4일 차 쿼리 수 줄이기에서 다룬다.

### 1.10 로그에서 확인한 것들
* `Isolation level: REPEATABLE_READ [default]` — MySQL 기본값. PostgreSQL이면 READ_COMMITTED.
* `maximumPoolSize=10`, `minimumIdle=10` — 고정 크기 풀. `pg.Pool`은 `min: 0` 탄력 풀이었다.
* `binding parameter (8:TIMESTAMP_UTC) <- [2026-11-05T12:34:57Z]` — JVM은 KST인데 DB엔 UTC로. `hibernate.jdbc.time_zone=UTC` + `Instant`의 결과.
* `(2:ENUM) <- [RTK_FIXED]` — `@Enumerated(STRING)`.

### 1.11 전체 코드를 받은 지점
* 엔티티 `Zone`, `LocationEvent`: 표 형식 설명이 두 번 안 읽혀서 파일 전체를 받아 보면서 타이핑했다.
* Service `ingest`의 네 줄: 설계가 아니라 Kotlin 문법(변수에 담기, `Optional.orElseThrow`, named argument)에서 막혔다. 한 줄씩 설명을 받아 채웠다.
* Controller, ExceptionHandler, DTO: 뼈대를 받았다.
* V2/V3 SQL 수정: 리뷰 지적 후 직접 고쳐 달라고 요청했고 diff를 확인했다.
