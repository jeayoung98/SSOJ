# Judge Worker 동시성 점검

이 문서는 현재 로컬 Redis polling worker의 동시 처리 제한을 확인하는 문서다.

## 1. 현재 기준

현재 동시성 설정:

- `worker.max-concurrency=2`
- semaphore와 thread pool 기반 처리

검증 대상:

- 동시에 `JUDGING` 상태인 submission 수가 설정값을 넘지 않는지
- queue payload가 UUID `submissionId`로 처리되는지
- 하나의 제출이 끝나면 다음 제출이 시작되는지

## 2. 준비

필요한 것:

- PostgreSQL
- Redis
- Docker
- Spring Boot 앱 `local` profile
- 실행 시간이 긴 제출 여러 개

느린 제출 예시:

- Java TLE sample
- Python TLE sample
- C++ TLE sample

## 3. enqueue 예시

```powershell
redis-cli LPUSH judge:queue 018f2f1e-8d2f-7a44-9f2e-efb0c8a33f11
redis-cli LPUSH judge:queue 018f2f1e-8d2f-7a44-9f2e-efb0c8a33f12
redis-cli LPUSH judge:queue 018f2f1e-8d2f-7a44-9f2e-efb0c8a33f13
redis-cli LPUSH judge:queue 018f2f1e-8d2f-7a44-9f2e-efb0c8a33f14
redis-cli LPUSH judge:queue 018f2f1e-8d2f-7a44-9f2e-efb0c8a33f15
```

## 4. 확인 SQL

현재 실행 중인 submission 수:

```sql
select count(*)
from submissions
where status = 'JUDGING';
```

상태 확인:

```sql
select id, status, result, submitted_at, judged_at
from submissions
where id in (
  '018f2f1e-8d2f-7a44-9f2e-efb0c8a33f11',
  '018f2f1e-8d2f-7a44-9f2e-efb0c8a33f12',
  '018f2f1e-8d2f-7a44-9f2e-efb0c8a33f13',
  '018f2f1e-8d2f-7a44-9f2e-efb0c8a33f14',
  '018f2f1e-8d2f-7a44-9f2e-efb0c8a33f15'
)
order by submitted_at;
```

## 5. 기대 결과

- 동시에 `JUDGING`인 row 수가 2를 넘지 않는다.
- 나머지는 `PENDING`으로 대기한다.
- 하나가 `DONE`으로 끝나면 다음 submission이 `JUDGING`으로 바뀐다.
- 최종적으로 모든 submission이 `DONE`이 되고 `result`가 저장된다.

## 6. 실패 시 확인할 점

동시에 3개 이상 `JUDGING`:

- 실제 `worker.max-concurrency` 값이 2인지 확인
- worker process가 여러 개 떠 있는지 확인
- 다른 consumer가 같은 Redis queue를 읽는지 확인

계속 `PENDING`:

- Redis 연결 실패
- queue key 불일치
- payload UUID parsing 실패
- local profile이 아닌 설정으로 실행됨

계속 1개씩만 실행:

- sample이 너무 빨리 끝나는지 확인
- Docker pull 또는 compile 단계에서 병목이 생기는지 확인
