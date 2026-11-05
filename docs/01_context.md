# 01_context.md

## 1. 배경

### 1.1 목표

* 카카오페이 "서버 개발자 – 데이터 플랫폼" 공고 요건(Kotlin, Spring Boot, MySQL 설계·쿼리 튜닝, Redis, 대용량·분산 이해 / 우대: Kafka, Docker, MongoDB)을 **사이드 프로젝트 하나로** 채운다.
* 기간 약 1주, 하루 4~6시간. 2026-11-05 시작.

### 1.2 나는 누구인가

* CS 전공, 2026년 8월 입사한 1년 차 백엔드 개발자.
* Node.js + Express + PostgreSQL로 기기 데이터 수집 → 판정 → 관제 화면 서빙 서버를 혼자 만들어 운영까지 했다. 다만 상당 부분을 AI 보조로 작성해서, "왜 이렇게 했나"가 비어 있는 지점이 있다는 걸 알고 있다.
* Kotlin은 안드로이드 앱에서 써 봤다. Spring, JPA, MySQL 튜닝, Kafka는 처음이다.
* 손코딩이 익숙한 언어는 C++, Python.

### 1.3 왜 이 주제인가

* Node 서버에서 이미 겪은 문제(멱등성, 메모리 상태 때문에 서버를 못 늘린 것, 캐시 신선도, 커넥션 풀 크기 근거 없음, 직접 만든 마이그레이션 도구)를 **같은 도메인**에서 Spring으로 다시 풀어 보면 비교가 된다.
* 데이터는 전부 가짜이고 회사 프로젝트와 무관하다.

<br>

## 2. 만드는 것

건설 중장비·트럭 위치 관제 서비스.
장비 단말이 위치·속도를 보내면 작업 구역 진입·이탈, 장비 간 근접, 이동 이력을 판정·저장하고 현재 상태를 조회 API로 서빙한다.

장비에 따라 측위 방식이 둘이다. 핸드폰 GPS만 쓰는 장비(수 m 오차)와 핸드폰 + RTK 수신기를 쓰는 장비(cm급). 같은 수집 API로 들어오되 이벤트마다 품질(fix type, 수평 오차)이 다르고, 판정이 그 품질을 봐야 한다.

<br>

## 3. 스택과 버전 (2026-11 기준)

| 항목 | 선택 | 왜 |
|---|---|---|
| 언어 / 런타임 | Kotlin 2.3 / JDK 21 (Temurin) | Boot 4.1 기준선은 17, 25까지 지원. 21은 LTS이고 채용 환경이 대개 17~21 |
| 프레임워크 | Spring Boot 4.1.2 | 2026-11 신규 프로젝트에 3.x를 고를 이유 없음. 비용은 블로그 대부분이 3.x 기준이라는 것 |
| 빌드 | Gradle 9 (Kotlin DSL), wrapper | 따로 설치하지 않는다 |
| DB | MySQL 9.7 LTS | 공고 요건. 8.0은 2026-04 EOL. PostgreSQL이 이 프로젝트엔 더 편한 걸 알면서 골랐다 |
| 마이그레이션 | Flyway 11 | Node에서 직접 만든 도구와 설계가 같아 비교 대상 |
| ORM | JPA / Hibernate 7 | N+1 → fetch join, 네이티브 쿼리 경계를 겪기 위해 |
| 캐시 | Redis 8 (2일 차) | |
| 메시징 | Kafka 4.x KRaft (3일 차) | ZooKeeper 없는 구성 |
| 로컬 인프라 | Docker Compose v2, Boot Docker Compose Support | |
| 시험 | JUnit 5 + MockK, Testcontainers (5일 차) | |
| CI | GitHub Actions (Node 24 액션) | |
| IDE | IntelliJ IDEA Ultimate (교육용 라이선스) | yml 키 자동완성·오타 경고 |

<br>

## 4. 일정과 제약

1. 프로젝트 생성, 계층 구조, Flyway 표 3개, 수집 API
2. Redis: 장비별 최신 위치 캐시, 멱등키 TTL, 속도 제한
3. Kafka: 수집 → 토픽 → 판정 컨슈머 분리. 재시작·순서 실험
4. 지오펜스·근접 판정, 현황 조회 API, EXPLAIN 인덱스 전후
5. Testcontainers, GitHub Actions, README

제약: Windows와 Mac을 오가며 작업한다. 환경 의존적인 것(컨테이너 이름, 셸 명령)은 둘 다에서 돌아야 한다.

<br>

## 5. 작업 방식

* GitHub 이슈 하루 1개. 설계 결정은 이슈 댓글 하나에 결정 하나.
* 브랜치 `N-dayN-...` → PR `Closes #N` → squash merge.
* 코드는 내가 쓴다. AI는 개념 설명, 설계 질문, 코드 리뷰만. 전체 코드를 받은 건 명시적으로 요청했을 때로 한정하고 그 사실을 02에 적는다.
