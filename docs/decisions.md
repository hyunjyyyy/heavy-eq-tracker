# 설계 결정 찾아보기

코드만큼 **왜 그렇게 결정했는지**가 산출물이라, 설계 결정은 전부 GitHub 이슈 댓글에 "결정 / 판단 이유 / 대안 / 바뀔 조건" 형식으로 남긴다.

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

날짜별 학습 기록은 `study-log/`.
