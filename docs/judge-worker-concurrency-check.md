# Judge Worker 동시성 점검

이 문서는 현재 Docker 기반 로컬 개발/검증용 worker 기준 문서입니다. 운영 최종 구조 문서가 아니며, 현재 단일 로컬 worker 프로세스 기준 동시성 제한만 점검합니다. 운영 환경 적용 전에는 멀티 인스턴스, 분산 락, 소비 전략을 다시 검토해야 합니다.

## 문서 범위

- 구현 완료된 현재 worker 동시성 제한 확인
- 로컬 개발과 수동 검증 기준
- 운영 구조로는 재검토 필요

## 공통 기준

- Redis queue key: `judge:queue`
- 상태값:
  - `PENDING`
  - `JUDGING`
  - `AC`
  - `WA`
  - `CE`
  - `RE`
  - `TLE`
  - `MLE`
  - `SYSTEM_ERROR`
- 현재 실행 가능한 언어:
  - `java`
  - `python`
  - `cpp`
- 실행 방식:
  - Docker 기반 실행
- 현재 동시성 설정:
  - `worker.max-concurrency=2`
  - semaphore + fixed thread pool

## 목표

동시에 최대 2개의 submission만 채점되는지 확인합니다.

## 준비

- PostgreSQL 실행
- Redis 실행
- Docker 실행
- Spring worker 실행
- `worker.max-concurrency=2` 확인
- hidden test case가 연결된 problem 준비
- `PENDING` submission 여러 개 준비

## 추천 테스트 데이터

겹쳐서 실행되는 모습이 잘 보이도록 느린 샘플을 사용합니다.

- Java TLE: `samples/submissions/java/tle/Main.java`
- Python TLE: `samples/submissions/python/tle/main.py`
- C++ TLE: `samples/submissions/cpp/tle/main.cpp`

## enqueue 예시

```powershell
101..105 | ForEach-Object { redis-cli LPUSH judge:queue $_ }
redis-cli LLEN judge:queue
```

## 확인 포인트

- 처음에는 최대 2개만 `JUDGING`
- 나머지는 `PENDING` 유지
- 하나가 끝난 뒤 다음 submission이 시작
- 어느 시점에도 `status='JUDGING'` row 수가 2를 넘지 않음

## 추천 SQL

현재 실행 중 submission 수:

```sql
select count(*)
from submission
where status = 'JUDGING';
```

상태 전이 확인:

```sql
select id, status, started_at, finished_at
from submission
where id in (101, 102, 103, 104, 105)
order by id;
```

## 로그에서 볼 부분

- `Received submissionId=101 from Redis queue judge:queue`
- `Received submissionId=102 from Redis queue judge:queue`
- `Submission 101 changed from PENDING to JUDGING`
- `Submission 102 changed from PENDING to JUDGING`

## 현재 정책 기준 해석

- `JUDGING`는 실행 슬롯을 확보한 submission만 진입
- hidden test case는 submission 내부에서 순차 실행
- 첫 실패 시 즉시 중단하지만, 동시성 제한과는 별개로 submission 단위 슬롯을 사용

## 실패 시 의심할 점

- 동시에 3개 이상 `JUDGING`
  - 실제 설정값이 2가 아님
  - worker 프로세스가 여러 개 실행 중
  - 다른 consumer가 같은 Redis를 읽고 있음
- 계속 1개씩만 실행
  - 샘플이 너무 빨리 끝남
  - poll timing 때문에 겹침이 안 보임
- 계속 `PENDING`
  - Redis 연결 실패
  - queue key 불일치
  - payload 파싱 실패
  - `worker.enabled=false`
