# Judge Worker 동시성 제한 검증

## 목표

현재 judge worker가 동시에 최대 2개의 채점만 실행하는지 검증합니다.

현재 구현 기준:

- Redis queue key: `judge:queue`
- polling consumer
- `worker.max-concurrency=2`
- 동시성 제어 방식: semaphore + fixed thread pool

## 1. 테스트 준비

- [ ] PostgreSQL 실행
- [ ] Redis 실행
- [ ] Docker 실행
- [ ] Spring worker 실행
- [ ] `src/main/resources/application.properties`에 아래 값이 있는지 확인
  - `worker.max-concurrency=2`
- [ ] `problem` 1개 준비
- [ ] 해당 problem에 hidden `test_case` 여러 개 준비
- [ ] `status='PENDING'`인 `submission` 여러 개 준비

### 추천 테스트 데이터

동시 실행이 눈에 잘 보이도록, 금방 끝나지 않는 코드를 쓰는 것이 좋습니다.

추천:

- Java `TLE` 샘플 또는 Python `TLE` 샘플
- 이유:
  - 오래 실행되는 submission일수록 겹쳐서 실행되는 모습을 관찰하기 쉽습니다.

예시:

- `samples/submissions/java/tle/Main.java`
- `samples/submissions/python/tle/main.py`

문제의 time limit은 몇 초 안에 끝날 정도로 작게 설정하는 것이 좋습니다.

예시:

- `time_limit_ms = 3000`

## 2. 여러 submission id를 Redis queue에 넣는 방법

먼저 여러 `submission` row를 생성합니다.

예시:

- `101`
- `102`
- `103`
- `104`
- `105`

그 다음 Redis에 빠르게 넣습니다.

### Windows PowerShell 예시

```powershell
redis-cli LPUSH judge:queue 101
redis-cli LPUSH judge:queue 102
redis-cli LPUSH judge:queue 103
redis-cli LPUSH judge:queue 104
redis-cli LPUSH judge:queue 105
```

### 한 줄 PowerShell loop

```powershell
101..105 | ForEach-Object { redis-cli LPUSH judge:queue $_ }
```

### 큐 길이 확인

```powershell
redis-cli LLEN judge:queue
```

## 3. 어떤 로그를 보면 병렬도 2를 확인할 수 있는지

### 기대 로그 패턴

아래와 같은 queue consume 로그가 보여야 합니다.

- `Received submissionId=101 from Redis queue judge:queue`
- `Received submissionId=102 from Redis queue judge:queue`

그 다음, 이 두 개가 아직 실행 중일 때는 나머지 item이 바로 모두 consume 되지 않아야 합니다.

확인할 포인트:

- [ ] 처음에는 2개 submission만 거의 동시에 active judging 상태로 진입
- [ ] 나머지 queue item은 첫 2개가 끝날 때까지 Redis에 남아 있음
- [ ] 첫 실행 2개 중 하나가 끝나면 다음 queue item이 consume 됨

### 실전 해석

max concurrency가 정상이라면:

- submission 1 시작
- submission 2 시작
- submission 3 대기
- submission 4 대기
- submission 5 대기
- 첫 2개 중 하나가 끝난 뒤 submission 3 시작

### 현재 로그 한계

현재 worker 로그는 active worker count를 직접 찍지 않습니다.

그래서 아래 간접 신호를 같이 봐야 합니다.

- queue consume 시점
- DB 상태 전이
- queue length 감소 시점

## 4. DB에서 어떤 상태 변화를 보면 되는지

### submission 테이블

아래 컬럼을 봅니다.

- `status`
- `started_at`
- `finished_at`

기대 동작:

- [ ] 어느 시점에도 `JUDGING` 상태 row는 최대 2개
- [ ] 나머지 row는 슬롯이 날 때까지 `PENDING`
- [ ] 실행 중 하나가 끝나면 대기 중 하나가 `JUDGING`로 바뀜

### 추천 SQL

현재 실행 중 submission 수 확인:

```sql
select count(*)
from submission
where status = 'JUDGING';
```

상태 전이 전체 확인:

```sql
select id, status, started_at, finished_at
from submission
where id in (101, 102, 103, 104, 105)
order by id;
```

### submission_case_result 테이블

추가 확인 포인트:

- [ ] 실제로 채점이 시작된 submission에 대해서만 row가 생성
- [ ] 아직 대기 중인 submission에는 결과 row가 생기지 않음

## 5. concurrency=2가 정상 동작한다는 판단 기준

아래 항목을 체크합니다.

- [ ] submission 5개를 넣은 직후 Redis queue 길이가 즉시 0이 되지 않음
- [ ] 동시에 `JUDGING`가 되는 submission은 최대 2개
- [ ] `count(status='JUDGING')`가 2를 넘지 않음
- [ ] 실행 중 하나가 끝난 뒤에야 3번째 submission이 시작됨
- [ ] queue length가 한 번에 다 줄지 않고 단계적으로 감소함

## 6. 실패 시 의심해야 할 원인

### 동시에 3개 이상 JUDGING가 됨

- [ ] `worker.max-concurrency`가 실제로 2가 아님
- [ ] worker 프로세스가 여러 개 떠 있음
- [ ] 같은 Redis queue를 다른 consumer도 읽고 있음
- [ ] DB 상태 변경 로직이 다른 곳에서 우회되고 있음

### 1개씩만 실행되는 것처럼 보임

- [ ] worker는 돌아가지만 실제로 한 번에 하나씩만 submit 되고 있을 수 있음
- [ ] Docker 실행이 너무 빨리 끝나서 겹침이 잘 안 보일 수 있음
- [ ] 테스트 코드가 충분히 느리지 않을 수 있음
- [ ] poll interval 때문에 직렬처럼 보일 수 있음

### queue가 바로 비워짐

- [ ] worker 인스턴스가 여러 개일 수 있음
- [ ] concurrency 제한 값이 바뀌었을 수 있음
- [ ] 현재 실행 중인 worker가 기대한 코드 버전이 아닐 수 있음

### submission이 계속 PENDING

- [ ] worker가 실행 중이 아님
- [ ] Redis 연결 정보가 틀림
- [ ] queue key가 `judge:queue`가 아님
- [ ] payload가 `Long`으로 파싱되지 않음
- [ ] `worker.enabled=false`

### submission이 JUDGING에서 멈춤

- [ ] Docker 실행이 예상과 다르게 멈춤
- [ ] 채점 중 예외가 나고 최종 상태 저장이 실패함
- [ ] DB transaction 또는 연결 문제
- [ ] worker 로그의 executor/JudgeService 오류 확인 필요

## 7. 간단한 로컬 부하 테스트 방식

이건 벤치마크가 아니라 concurrency cap이 보이는지 확인하는 용도입니다.

### 추천 방식

- TLE 코드로 `PENDING` submission 10개 생성
- Redis에 id 10개를 빠르게 push
- 아래 값들을 반복 확인
  - Redis queue length
  - `JUDGING` 개수
  - 완료된 submission 개수

### Redis queue polling

```powershell
1..10 | ForEach-Object { redis-cli LLEN judge:queue; Start-Sleep -Seconds 1 }
```

### DB polling 예시

DB 클라이언트에서 반복 실행:

```sql
select status, count(*)
from submission
where id in (101, 102, 103, 104, 105)
group by status
order by status;
```

기대 패턴:

- 처음: `2 JUDGING`, 나머지 `PENDING`
- 이후: 완료 상태가 생기고, 다음 pending이 `JUDGING`로 이동

## 8. 추천 검증 순서

1. worker와 인프라 실행
2. 느린 submission 5개 생성
3. 5개 id를 `judge:queue`에 push
4. 즉시 `LLEN judge:queue` 확인
5. worker 로그에서 처음 2개 consume 확인
6. DB에서 `JUDGING`가 2개 이하인지 확인
7. 하나가 끝날 때까지 대기
8. 다음 대기 submission이 시작하는지 확인
9. 모두 끝날 때까지 반복
