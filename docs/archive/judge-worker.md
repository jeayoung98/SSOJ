# Judge Worker 현재 동작 문서

이 문서는 현재 코드 기준의 Spring judge worker 동작을 설명한다. 운영 배포 절차가 아니라 worker 흐름과 로컬/배포 경로 차이를 이해하기 위한 문서다.

## 1. 역할 구분

애플리케이션은 `worker.role`로 역할을 나눈다.

- `orchestrator`
  - DB에서 submission, problem, hidden testcase를 읽는다.
  - testcase별 실행을 Docker 또는 remote runner로 수행한다.
  - 첫 실패 testcase에서 즉시 종료한다.
  - 결과를 DB에 저장한다.
- `runner`
  - 단일 실행 요청을 HTTP로 받는다.
  - Docker 기반 executor로 코드를 실행한다.
  - 실행 결과만 반환한다.

## 2. Queue 계약

로컬 Redis polling 경로:

- Redis key: `judge:queue`
- payload: `submissionId` UUID 문자열

```powershell
redis-cli LPUSH judge:queue 00000000-0000-0000-0000-000000000001
```

숫자형 `123` payload는 현재 코드 기준으로 유효하지 않다.

## 3. 상태와 결과

`submissions.status`:

- `PENDING`
- `JUDGING`
- `DONE`

`submissions.result`:

- `AC`
- `WA`
- `CE`
- `RE`
- `TLE`
- `MLE`
- `SYSTEM_ERROR`

결과 관련 필드:

- `failed_testcase_order`
- `execution_time_ms`
- `memory_kb`
- `submitted_at`
- `judged_at`

`started_at`, `finished_at`은 현재 `Submission` entity에 없다.

## 4. 주요 테이블

현재 JPA 매핑 기준:

- `users`
- `problems`
- `problem_examples`
- `problem_testcases`
- `submissions`
- `submission_testcase_results`

`failed_testcase_order`는 현재 코드에 추가된 `submissions` 컬럼 매핑이다. 기존 Supabase DB에 없다면 DB 반영이 필요하다.

## 5. Orchestrator 흐름

로컬 Redis polling:

```text
Redis judge:queue
-> JudgeQueueConsumer
-> JudgeService.judge(UUID)
-> JudgePersistenceService.startJudging(UUID)
-> JudgeService.runJudgeLogic(...)
-> ExecutionGateway
-> JudgePersistenceService.saveResultsAndFinish(...)
```

remote HTTP trigger:

```text
POST /internal/judge-executions
-> JudgeExecutionController
-> JudgeService.judge(UUID)
-> RemoteExecutionGateway
-> runner /internal/runner-executions
-> DB 저장
```

## 6. 채점 규칙

- hidden testcase만 채점한다.
- `ProblemTestcaseRepository.findAllByProblem_IdAndHiddenTrueOrderByTestcaseOrderAsc(...)`로 `testcase_order` 순서대로 조회한다.
- testcase마다 `JudgeExecutionResult`를 `SubmissionResult`로 판정한다.
- `AC`가 아닌 결과가 나오면 이후 testcase 실행을 중단한다.
- 실행한 testcase까지만 `submission_testcase_results`에 저장한다.
- 최종 결과는 `submissions.result`에 저장하고 `submissions.status`는 `DONE`으로 바꾼다.

`failed_testcase_order`가 저장되는 결과:

- `WA`
- `TLE`
- `RE`
- `MLE`

`failed_testcase_order`가 `null`인 결과:

- `AC`
- `CE`
- `SYSTEM_ERROR`

## 7. 실행 시간과 메모리

제출 단위 저장 기준:

- `execution_time_ms`: 실행된 testcase 중 최대 실행 시간
- `memory_kb`: 실행된 testcase 중 최대 메모리 사용량

testcase 단위 저장 기준:

- `submission_testcase_results.execution_time_ms`
- `submission_testcase_results.memory_kb`

현재 local Docker executor는 실행 시간은 측정하지만 실제 메모리 사용량은 `null`일 수 있다. remote runner가 `memoryUsageKb`를 반환하면 저장될 수 있다.

## 8. 시간/메모리 제한

`Problem`의 제한 값:

- `time_limit_ms`
- `memory_limit_mb`

연결 흐름:

```text
Problem -> StartedJudging -> JudgeContext -> executor/runner -> JudgeExecutionResult -> JudgeService 판정
```

현재 구현:

- timeout이면 `TLE`
- `memoryUsageKb > memoryLimitMb * 1024`이면 `MLE`
- exit code `137`이면 `MLE`로 본다.

## 9. 출력 비교

현재 MVP 출력 비교 정책:

- 전체 출력 `trim`
- 줄 단위 분리
- 각 줄 `trim`
- line-by-line 비교
- trailing newline 차이는 무시

special judge는 구현되어 있지 않다.

## 10. 관련 문서

- [제출 결과 모델과 채점 종료 규칙](./judge-result-model.md)
- [Judge Worker 모드 설정](./worker-mode-configuration.md)
- [로컬 Orchestrator/Runner 분리 실행](./local-orchestrator-runner.md)
- [배포 준비 체크리스트](./deployment-readiness.md)

