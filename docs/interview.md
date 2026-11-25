# 면접 설명

## 1. 수집 API가 DB에 쓰지 않고 Kafka에만 넣는 이유와 202

근거: `LocationIngestService.ingest()`, `LocationController`, #10

**Q. 왜 DB에 저장하지 않고 Kafka에만 넣고 202를 주나?**
가장 큰 이유는 DB와 kafka의 불일치를 막기 위해서이다. 202는 일단 kafka가 받았고 그 이후 처리는 나중에 한다는 뜻이며 DB는 카프카에서 받은 값을 받아서 저장하는 방식이다. 대신 저장과 판정이 성공했는지는 바로 알 수 없다는게 단점이다.

**Q. 불일치는 구체적으로 어떤 두 경우인가?**
DB에는 저장됐는데 kafka는 모르면 판정이 돌지 않아서 위치가 유실되고, 반대의 경우는 저장되지 않은 위치로 판정이 돈다

**Q. Kafka 발행이 실패하면?**
- 틀렸던 답: 멱등키를 release하고 RuntimeException을 줌... 이게 뭔말일까
- 바른 답: 500 받고, 키를 지웠으니 같은 키로 다시 보내면 받아줌

**Q. 202를 준 뒤 컨슈머가 DB 저장에 실패하면?**
재시도하다가 안 되면 DLT로 가서 안 사라짐

## 2. 멱등 세 겹: Redis 선점 / 컨슈머 키 조회 / DB UNIQUE

근거: `IdempotencyStore.tryAcquire()`, `LocationEventWriter.save()`의 `findByIdempotencyKey`, `V3__location_event.sql`의 UNIQUE, `IngestGuardIT.sameKeyTwiceSavesOnce`, #6, #11

**Q. 멱등 처리를 세 군데서 하는데, 각각 무엇을 막나?**
redis는 http 재전송을 kafka에 넣기 전에 거르고, 컨슈머는 kafka 재소비를 거릅니다. db는 동시에 조회되었을때 둘 다 insert 되는걸 막는 마지막 장치입니다.

**Q. Redis가 재시작돼 선점 키가 날아간 사이 재전송이 오면?**
kafka 컨슈머에서 키를 조회하면 중복을 거를 수 있음

**Q. 컨슈머 키 조회를 빼고 DB UNIQUE만 남기면?**
- 처음 답: 예외 나서 재시도하다가 DLT로 감 (근데 그렇게 되면 뭐가 안종ㅎ은데?)
- 다시 한 답: 정상적인 중복도 dlt에 쌓여서 실제로 문제가 발생했을 때 확인하기 어려움. 또한 로그가 계속 warn/error 찍히면서 정상인데 문제가 있는 것처럼 보임

## 4. 현장 모델: `@TenantId`, fail-closed, 현장별 Redis 키

근거: `@TenantId`(현장 표 6개), `SiteTenantResolver`, `SiteContext`(`ROOT = 0`, `NONE = -1`), `SiteContextInterceptor`, `LocationEventConsumer`의 `SiteContext.run`, `LatestLocationQueryService.latestOf()`, `SiteFailClosedIT`, #30

**Q. `@TenantId`는 어디까지 막고, 현장을 정하지 않으면 어떻게 되나?**
hibernate로 하는 조회와 저장은 tenantid로 알아서 나눠줌 그러나 redis, nativesql, tenantid 없는 표는 막지 못함. 현장을 정하지 않으면 site_id를 -1로 돌려주기 때문에 조회가 되지 않음 fk위반으로 실패하게됨

**Q. 정하지 않으면 전체(ROOT)로 보게 하면 안 되나?**
전체로 보면 실수했을 때 남의 현장이 다 보임 보안문제

**Q. `@Transactional` 메서드 안에서 `SiteContext`를 바꾸면?**
세션이 이미 열려서 바꿔도 옛 현장으로 감

**Q. Redis GEO 키를 현장별로 나누지 않으면?**
남의 현장 장비가 근접 후보로 잡혀서 NEAR가 뜸

## 5. 장비 이동: 비관적 락과 열린 배정 유일 인덱스, 늦게 온 옛 현장 위치

근거: `EquipmentMoveService.move()`, `EquipmentRepository.findWithLockById`(`PESSIMISTIC_WRITE`), `V24`(생성 열 `open_marker` + UNIQUE), `LocationEventWriter.belongsToOpenAssignment()`, `EquipmentMoveIT`, `EquipmentSiteAssignmentIT`, #31

**Q. 비관적 락과 유일 인덱스를 둘 다 둔 이유는? 각각 무슨 역할인가?**
비관적 락은 동시 요청을 줄 세워서 중복을 막고 유일 인덱스는 뚫렸을 때의 안전망이다

**Q. 락만 있고 유일 인덱스가 없으면 언제 열린 배정이 둘 생기나?**
잠금 없이 배정 넣는 코드를 새로 짜면 두 개 생김

**Q. 유일 인덱스만 있고 락이 없으면, 동시 이동 두 개는?**
하나는 DB 예외로 실패해서 에러

**Q. 이동 전에 찍혀 이동 뒤에 도착한 옛 현장 위치는?**
옛 현장으로 기록만 하고 판정은 안 함, 정리한 게 되살아나서

## 6. 통합 시험: 컨텍스트 캐싱, 롤백으로 격리 못 하는 이유, Awaitility, 대조군

근거: `IntegrationTest`(공통 어노테이션, `cleanUp()`, `pollInTestThread()`), `ProximityScenarioIT`의 대조군, `SiteFailClosedIT`, #21

**Q. 시험 데이터를 롤백이 아니라 TRUNCATE로 지우는 이유와, 전부 같은 바탕 클래스를 상속하는 이유는?**
- 틀렸던 답: 컨슈머가 따로 커밋해서 롤백으로 안 지워짐, 같은 어노테이션이라 컨텍스트 하나만 뜸 그럼 캐싱이 되기 때문에 약 30초 아낄수있음~
- 바른 답: 컨슈머가 따로 커밋해서 롤백으로 안 지워짐, 같은 어노테이션이라 컨텍스트 하나만 뜸 그럼 캐싱이 되기 때문에 약 15초 아낄수있음~

**Q. `pollInSameThread()`를 빼면?**
조건식이 다른 스레드에서 돌아서 현장이 -1, 늘 빈 결과

**Q. "안 생긴다" 시험 옆에 대조군을 두는 이유는?**
판정을 아예 안 해서 통과한 게 아닌지 확인하려고
