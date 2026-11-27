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

## 3. 판정은 같은 트랜잭션, Redis는 커밋 뒤

근거: `LocationEventWriter.save()`(이벤트 두 개), `ProximityService`·`GeofenceService`의 `@EventListener`, `LatestLocationCache.onRecorded`의 `@TransactionalEventListener(AFTER_COMMIT)`, #7, #16

**Q. 판정은 같은 트랜잭션, Redis 캐시 갱신은 커밋 뒤. 왜 나눴나?**
- 틀렸던 답: 가장 큰 원칙은 롤백 가능한 것은 트랜잭션 안에, 아닌 것은 밖에 하는 것입니다. DB는 트랜잭션이 실패하면 롤백되고 한 트랜잭션 안에서는 원자성이 성립함. 그러나 redis는 위치와 판정 자체를 저장해야 하기 때문에 이 모든일이 끝나고 되어야 함.
redis도 같은 트랜잭션에 묶이면 롤백은 됐는데 redis에는 롤백된 위치와 판정이 저장되는 일이 생길 수 있음
- 바른 답: 가장 큰 원칙은 롤백 가능한 것은 트랜잭션 안에, 아닌 것은 밖에 하는 것입니다. DB는 트랜잭션이 실패하면 롤백되고 한 트랜잭션 안에서는 원자성이 성립합니다. 판정과 위치 저장은 동시에 진행되어야 하기 때문에 한 트랜잭션으로 묶습니다. 원자성 때문에 둘중에 하나만 있는 경우는 저장되지 않습니다. redis는 롤백이 되지 않기 때문에 밖에서 해야합니다. redis를 트랜잭션 안에서 쓰면 db가 롤백되어도  redis는 그대로 남기 때문에 둘이 어긋나게 됩니다.

**Q. `@EventListener`와 `@TransactionalEventListener(AFTER_COMMIT)`의 차이는?**
- 틀렸던 답: 이거 몇번 봤는데 또 까먹었다...
- 바른 답: EventListener는 바로 실행, AFTER_COMMIT은 커밋 성공 뒤에만 실행

**Q. 판정 리스너에 `AFTER_COMMIT`을 붙이면?**
위치는 저장되었는데 판정은 저장되지 않음

**Q. 캐시 갱신(`AFTER_COMMIT`) 리스너에서 예외가 나면?**
이미 커밋돼서 롤백 안 되고, 캐시만 안 바뀜

**변형 질문** (날을 나눠 다시 묻기)
- [x] 1. (11-25) 장비 이동 때 CLEAR 기록은 같은 트랜잭션, 옛 현장 GEO 빼기는 커밋 뒤. 왜 다르게 했고, GEO 빼기를 트랜잭션 안에서 하면?
  - clear는 DB, geo는 redis기 때문에 이전에 말했던것 처럼 롤백 가능한것과 그렇지 않은것을 분리한것과 같은 맥락
  - 롤백 되어도 redis geo에서는 현장에서 빠진다 판정에 사용되는 후보장비에 들어가지 않음
- [x] 2. (11-27) 현장 근접 설정을 바꿀 때 메모리 캐시 지우기를 트랜잭션 안이 아니라 커밋 뒤에 하는 이유는?
  - 처음 답: 커밋 전에 지워버리는데 다른 스레드가 설정을 읽으러 오면 다른 스레드에서 온 값이 그 위에 덮어씌워지나?
  - 다시 한 답: 옛값이지 그러면 새로 업데이트 하려고 캐시를 지웠는데 또 옛날값을 올리게 됨

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
