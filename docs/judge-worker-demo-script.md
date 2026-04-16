# Judge Worker 시연 스크립트

## 목표

이 문서는 3~5분 분량의 짧은 발표/포트폴리오 시연을 위한 스크립트입니다.

현재 구현 범위:

- Redis queue consume
- Spring judge worker
- Java / Python executor
- Docker 기반 실행
- hidden test case 채점
- `submission_case_result` 저장
- 최종 status 반영
- 최대 동시성 `2`

## 1. 시연 전 준비사항

아래 항목을 미리 준비합니다.

- [ ] PostgreSQL 실행
- [ ] Redis 실행
- [ ] Docker 실행
- [ ] Spring worker 실행
- [ ] `problem` 최소 1개 준비
- [ ] 해당 problem의 hidden `test_case` 준비
- [ ] 샘플 submission 코드 준비

추천 샘플:

- AC:
  - `samples/submissions/java/ac/Main.java`
- WA:
  - `samples/submissions/java/wa/Main.java`
- CE:
  - `samples/submissions/java/ce/Main.java`
- 동시성 시연:
  - `samples/submissions/java/tle/Main.java`
  - 또는 `samples/submissions/python/tle/main.py`

추천 화면 구성:

- 터미널 1: worker 로그
- 터미널 2: Redis 명령
- DB 클라이언트: `submission`, `submission_case_result`
- 선택: Next.js 제출 화면 또는 API 클라이언트

## 2. 가장 짧은 추천 시연 흐름

추천 순서:

1. AC 제출
2. WA 또는 CE 제출
3. 동시 제출 2~3건

이 순서면 아래를 한 번에 보여줄 수 있습니다.

- queue 계약
- worker consume
- 상태 전이
- 결과 저장
- 동시성 제한

## 3. AC 시연 순서

### 제출할 코드

- Java AC 샘플:
  - `samples/submissions/java/ac/Main.java`

### 설명 멘트

- "제출은 먼저 `PENDING` 상태로 저장됩니다."
- "Next.js는 `submissionId`만 Redis에 넣습니다."
- "Spring worker는 이 id를 consume해서 비동기로 채점합니다."

### 화면에서 보여줄 것

- [ ] 새 `submission` row가 `PENDING` 상태로 생성된 것
- [ ] Redis `judge:queue`에 enqueue 되는 것
- [ ] worker 로그:
  - `Received submissionId=... from Redis queue judge:queue`
- [ ] DB 상태 변화:
  - `PENDING -> JUDGING -> AC`
- [ ] `started_at`, `finished_at`
- [ ] hidden test case에 대한 `submission_case_result` row

## 4. WA 또는 CE 시연 순서

둘 중 하나를 고르면 됩니다.

### 옵션 A: WA 시연

사용 코드:

- `samples/submissions/java/wa/Main.java`

보여줄 것:

- [ ] 제출 시작 상태가 `PENDING`
- [ ] worker가 queue item consume
- [ ] 상태가 `JUDGING`로 변경
- [ ] 최종 상태가 `WA`
- [ ] `submission_case_result`에 실패 케이스가 저장됨

장점:

- 출력 비교 로직 설명이 가장 쉽습니다.

### 옵션 B: CE 시연

사용 코드:

- `samples/submissions/java/ce/Main.java`

보여줄 것:

- [ ] 제출 시작 상태가 `PENDING`
- [ ] worker가 queue item consume
- [ ] 상태가 `JUDGING`로 변경
- [ ] 최종 상태가 `CE`

장점:

- compile 단계 실패 처리 설명이 쉽습니다.

짧은 시연 기준 추천:

- 출력 비교를 설명하고 싶으면 `WA`
- executor 실패 분류를 보여주고 싶으면 `CE`

## 5. 상태 변화 확인 포인트

시연 중 반드시 강조할 핵심 포인트:

- [ ] `PENDING`
  - 제출 쪽에서 생성
- [ ] `JUDGING`
  - Spring worker가 queue item을 consume 한 뒤 설정
- [ ] 최종 상태
  - `AC`
  - `WA`
  - `CE`
  - `RE`
  - `TLE`
  - `SYSTEM_ERROR`

최소 확인 항목:

- `submission.status`
- `started_at`
- `finished_at`

## 6. Redis queue와 worker 로그에서 보여줄 부분

### Redis

queue push 또는 queue 길이를 보여줍니다.

```powershell
redis-cli LPUSH judge:queue <submissionId>
redis-cli LLEN judge:queue
```

강조할 포인트:

- payload는 `submissionId` 하나뿐
- queue key는 `judge:queue`

### Worker 로그

가장 중요한 로그:

- `Received submissionId=... from Redis queue judge:queue`

그 다음 보여줄 로그:

- `Submission ... changed from PENDING to JUDGING`
- `Submission ... finished with status=...`

## 7. DB 또는 결과 조회 API에서 보여줄 부분

DB를 직접 보여줄 경우:

### submission

- `id`
- `status`
- `started_at`
- `finished_at`

### submission_case_result

- `submission_id`
- `test_case_id`
- `status`
- `execution_time_ms`

이미 Next.js 결과 페이지나 결과 조회 API가 있으면:

- 같은 항목을 그 화면에서 보여주면 됩니다.
- 현재 구현 범위를 넘는 기능은 설명하지 않는 것이 좋습니다.

## 8. 동시 제출 2~3건 시연 순서

이 부분은 짧게 지나가는 것이 좋습니다.

### 목표

현재 worker가 동시에 최대 2개만 채점한다는 점을 보여줍니다.

### 추천 세팅

느린 샘플 사용:

- `samples/submissions/java/tle/Main.java`
- 또는 `samples/submissions/python/tle/main.py`

submission 3개 준비:

- submission A
- submission B
- submission C

### 시연 순서

1. `PENDING` submission 3개를 DB에 넣음
2. Redis에 id 3개를 빠르게 push
3. worker 로그와 DB를 같이 확인

### 강조할 포인트

- [ ] 처음 2개만 `JUDGING`
- [ ] 3번째는 잠시 `PENDING`
- [ ] 실행 중 하나가 끝난 뒤 다음 하나가 시작

### 사용할 명령 예시

```powershell
redis-cli LPUSH judge:queue 201
redis-cli LPUSH judge:queue 202
redis-cli LPUSH judge:queue 203
```

DB 조회 예시:

```sql
select id, status, started_at, finished_at
from submission
where id in (201, 202, 203)
order by id;
```

## 9. 추천 3~5분 시연 타임라인

### 0:00 ~ 0:30

- "이 worker는 Redis를 통해 `submissionId`를 받아서 Spring Boot에서 비동기로 채점합니다."

### 0:30 ~ 1:30

- AC submission 시연
- `PENDING -> JUDGING -> AC` 확인

### 1:30 ~ 2:30

- WA 또는 CE submission 시연
- 실패 상태와 case result 저장 확인

### 2:30 ~ 4:00

- 느린 submission 3개 enqueue
- 동시에 최대 2개만 `JUDGING`인 점 강조

### 4:00 ~ 5:00

- 정리 멘트:
  - Redis queue로 API와 worker 분리
  - worker가 상태를 바꾸고 case result를 저장
  - Docker 내부에서 사용자 코드 실행
  - 현재 MVP에서는 동시성 2로 제한

## 10. 시연 팁

- 짧은 시연에서는 Java 샘플이 가장 안정적입니다.
- 화면 전환을 너무 많이 하지 않는 것이 좋습니다.
- submission id와 SQL은 미리 준비해두는 것이 좋습니다.
- 시간이 부족하면 아래만 보여도 충분합니다.
  - AC
  - 실패 1건
  - 동시성은 DB 화면만으로 확인
