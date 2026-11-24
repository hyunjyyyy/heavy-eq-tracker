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
