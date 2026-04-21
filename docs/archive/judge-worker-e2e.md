# Judge Worker E2E 시나리오

이 문서는 현재 코드 기준으로 로컬 end-to-end 채점 흐름을 확인하는 절차를 정리한다. 운영 배포 문서가 아니라, 로컬 개발과 회귀 검증을 위한 문서다.

## 1. 기준 구조

로컬 E2E 흐름:

```text
submissions.id(UUID) -> Redis judge:queue -> JudgeQueueConsumer -> JudgeService -> Docker executor -> DB 저장
```

사용 테이블:

- `problems`
- `problem_testcases`
- `submissions`
- `submission_testcase_results`

사용하지 않는 예전 이름:

- `problem`
- `test_case`
- `submission`
- `submission_case_result`

## 2. 상태와 결과 기준

`submissions.status`는 작업 상태다.

- `PENDING`
- `JUDGING`
- `DONE`

`submissions.result`는 채점 결과다.

- `AC`
- `WA`
- `CE`
- `RE`
- `TLE`
- `MLE`
- `SYSTEM_ERROR`

예전 문서처럼 `submission.status=AC`로 판단하지 않는다. 최종 완료 상태는 `status=DONE`, 판정은 `result=AC`처럼 분리해서 확인한다.

## 3. 사전 준비

필요한 로컬 구성:

- PostgreSQL
- Redis
- Docker daemon
- Spring Boot 앱 `local` profile

문제 데이터 예시:

- `problems.id`: `A_PLUS_B`
- hidden testcase:
  - `1 2\n` -> `3\n`
  - `10 20\n` -> `30\n`

## 4. 실행 절차

1. `problems`에 문제를 생성한다.
2. `problem_testcases`에 hidden testcase를 생성한다.
3. `submissions`에 `PENDING` 제출을 생성한다.
4. Redis `judge:queue`에 UUID `submissionId`를 push한다.
5. worker가 queue를 consume하는지 확인한다.
6. Docker executor 로그를 확인한다.
7. DB에서 최종 결과를 확인한다.

Redis 예시:

```powershell
redis-cli LPUSH judge:queue 018f2f1e-8d2f-7a44-9f2e-efb0c8a33f11
```

## 5. DB 확인 항목

`submissions`:

- `status`: `DONE`
- `result`: 기대 판정
- `execution_time_ms`
- `memory_kb`
- `submitted_at`
- `judged_at`

`submission_testcase_results`:

- `submission_id`
- `testcase_id`
- `result`
- `execution_time_ms`
- `memory_kb`
- `error_message`

## 6. 언어별 확인 포인트

지원 언어:

- `cpp`
- `java`
- `python`

공통 확인:

- AC 제출은 모든 hidden testcase 결과가 `AC`인지 확인한다.
- WA 제출은 실패한 testcase에서 `result=WA`가 저장되는지 확인한다.
- RE/TLE는 실행 결과가 `submissions.result`와 testcase result에 반영되는지 확인한다.
- CE는 컴파일 실패가 `result=CE`로 반영되는지 확인한다.

## 7. 로컬과 배포의 차이

이 문서는 로컬 Docker 실행 기준이다. `remote` profile에서는 orchestrator가 직접 Docker executor를 호출하지 않고 runner HTTP API를 호출한다. 배포 흐름은 `deployment-readiness.md`, `cloud-run-orchestrator.md`, `cloud-run-runner.md`를 기준으로 확인한다.
