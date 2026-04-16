# Next.js와 Spring Judge Worker 연동 체크리스트

## 범위

- 이 체크리스트는 현재 저장소의 Spring judge worker 구현을 기준으로 작성되었습니다.
- 아래 구성 요소 사이의 계약이 맞는지 빠르게 점검하기 위한 문서입니다.
  - Next.js 제출 API
  - Redis queue
  - PostgreSQL
  - Spring judge worker

## 1. Redis queue key

- [ ] Next.js가 Redis key `judge:queue`에 push 하는가
- [ ] Spring worker가 Redis key `judge:queue`를 읽는가
- [ ] 아래처럼 과거 키를 아직 쓰고 있지 않은가
  - `judge:submission-queue`
  - 환경별 커스텀 키
- [ ] Next.js와 Spring이 같은 Redis 인스턴스를 바라보는가

## 2. Payload 형식

- [ ] Next.js가 `submissionId` 하나만 push 하는가
- [ ] payload가 `Long`으로 파싱 가능한 plain value인가
- [ ] payload가 JSON이 아닌가
- [ ] 아래와 같은 추가 메타데이터를 같이 넣지 않는가
  - `problemId`
  - `language`
  - `sourceCode`
- [ ] Redis에서 직접 봤을 때 아래처럼 보이는가
  - `123`
  - `{"submissionId":123}`가 아님

## 3. Submission status enum

- [ ] Next.js와 Spring이 같은 status 집합을 쓰는가
- [ ] 현재 Spring worker의 status 값은 아래와 같은가
  - `PENDING`
  - `JUDGING`
  - `AC`
  - `WA`
  - `CE`
  - `RE`
  - `TLE`
  - `MLE`
  - `SYSTEM_ERROR`
- [ ] Next.js가 이 집합 밖의 상태 문자열을 쓰지 않는가
- [ ] DB 컬럼 값도 같은 대문자 문자열을 그대로 사용하는가
- [ ] 제출 생성 시 초기 상태가 `PENDING`인가

## 4. Language 값

- [ ] Next.js가 저장하는 `language` 값이 Spring executor 기대값과 정확히 일치하는가
- [ ] 현재 worker에서 실제 실행 가능한 언어는 아래와 같은가
  - `java`
  - `python`
- [ ] 현재 샘플만 있고 실행기 없는 언어는 아래와 같은가
  - `cpp`
- [ ] Next.js가 `cpp`를 보내면, 현재 worker는 end-to-end 채점을 수행할 수 없다는 점을 알고 있는가
- [ ] 아래와 같은 casing mismatch가 없는가
  - `Java` vs `java`
  - `Python` vs `python`
  - `C++` vs `cpp`
- [ ] 현재 추천 API 값은 아래 두 개인가
  - `java`
  - `python`

## 5. DB 테이블 및 컬럼명

- [ ] `submission` 테이블이 있는가
- [ ] `problem` 테이블이 있는가
- [ ] `test_case` 테이블이 있는가
- [ ] `submission_case_result` 테이블이 있는가

### submission

- [ ] `id`
- [ ] `problem_id`
- [ ] `language`
- [ ] `source_code`
- [ ] `status`
- [ ] `created_at`
- [ ] `started_at`
- [ ] `finished_at`

### problem

- [ ] `id`
- [ ] `title`
- [ ] `description`
- [ ] `time_limit_ms`
- [ ] `memory_limit_mb`

### test_case

- [ ] `id`
- [ ] `problem_id`
- [ ] `input`
- [ ] `output`
- [ ] `is_hidden`

### submission_case_result

- [ ] `id`
- [ ] `submission_id`
- [ ] `test_case_id`
- [ ] `status`
- [ ] `execution_time_ms`
- [ ] `memory_usage_kb`

## 6. 기대 상태 전이 순서

- [ ] Next.js가 `submission`을 `PENDING` 상태로 저장한다
- [ ] Next.js가 `submissionId`를 Redis `judge:queue`에 push 한다
- [ ] Spring worker가 queue item을 consume 한다
- [ ] Spring worker가 `submission`을 조회한다
- [ ] Spring worker가 상태를 `PENDING`에서 `JUDGING`로 변경한다
- [ ] Spring worker가 `started_at`을 채운다
- [ ] Spring worker가 해당 problem의 hidden test case를 조회한다
- [ ] Spring worker가 테스트케이스별 executor를 실행한다
- [ ] Spring worker가 테스트케이스별 `submission_case_result`를 저장한다
- [ ] Spring worker가 최종 `submission.status`를 저장한다
- [ ] Spring worker가 `finished_at`을 채운다

### 현재 worker가 만들 수 있는 최종 상태

- [ ] `AC`
- [ ] `WA`
- [ ] `CE`
- [ ] `RE`
- [ ] `TLE`
- [ ] `SYSTEM_ERROR`

## 7. 문제가 생겼을 때 어디부터 볼지

### 제출이 아예 채점되지 않음

- [ ] Next.js가 정말 `PENDING` 상태로 `submission`을 저장했는지 확인
- [ ] Next.js가 정말 `submissionId`를 Redis에 넣었는지 확인
- [ ] Redis key가 `judge:queue`인지 확인
- [ ] Spring worker가 실행 중인지 확인
- [ ] worker 로그에 `Received submissionId=...`가 찍히는지 확인

### Submission이 계속 PENDING

- [ ] Redis 큐에 item이 남아 있는지 확인
- [ ] worker가 Redis에 연결 가능한지 확인
- [ ] payload가 `Long`으로 파싱 가능한지 확인
- [ ] `worker.enabled`가 꺼져 있지 않은지 확인

### Submission이 JUDGING에서 끝나지 않음

- [ ] Docker가 실행 중인지 확인
- [ ] `language` 값에 맞는 executor가 있는지 확인
- [ ] `language` 값이 아래 중 하나인지 확인
  - `java`
  - `python`
- [ ] Docker 이미지가 존재하거나 pull 가능한지 확인
- [ ] 대상 problem에 hidden test case가 있는지 확인
- [ ] timeout과 입력 처리에 문제가 없는지 확인

### 최종 상태가 SYSTEM_ERROR

- [ ] Docker 명령이 로컬에서 정상 시작되는지 확인
- [ ] Spring 설정의 Docker 이미지명이 올바른지 확인
- [ ] mount 된 temp directory에 접근 가능한지 확인
- [ ] worker 로그에 executor 예외가 있는지 확인
- [ ] worker 로그에 `JudgeService failed while processing submission`가 있는지 확인

### 최종 상태가 예상과 다름

- [ ] `test_case.output` 값이 기대 정답인지 확인
- [ ] 출력 trim 정책을 확인
- [ ] 현재 정책은 아래와 같음
  - 전체 output trim
  - 줄 단위 분리
  - 각 줄 trim
  - line-by-line 비교
- [ ] 잘못된 `language` 값 때문에 다른 경로로 빠지지 않았는지 확인

### submission_case_result row가 없음

- [ ] `is_hidden=true`인 hidden test case가 존재하는지 확인
- [ ] `submission.problem_id`가 기대한 problem을 가리키는지 확인
- [ ] worker가 `submission_case_result`에 쓰기 가능한지 확인

## 빠른 수동 검증

- [ ] `problem` row 생성
- [ ] hidden `test_case` row 생성
- [ ] `status='PENDING'`인 `submission` row 생성
- [ ] 아래 명령으로 `submissionId` push

```powershell
redis-cli LPUSH judge:queue <submissionId>
```

- [ ] worker 로그에서 queue consume 확인
- [ ] DB에서 상태 전이 확인
  - `PENDING`
  - `JUDGING`
  - 최종 상태
- [ ] `started_at`, `finished_at`이 채워졌는지 확인
- [ ] `submission_case_result` row가 생성되었는지 확인
