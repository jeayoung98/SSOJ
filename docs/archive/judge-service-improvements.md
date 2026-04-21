# JudgeService 개선 반영 현황

이 문서는 `JudgeService` 주변 구조에서 현재 코드에 반영된 개선 사항을 정리한다. 과거 개선 메모가 아니라 현재 동작 기준의 설명 문서다.

## 1. 책임 분리

`JudgeService`는 채점 흐름을 조율한다.

- 제출 시작 처리 호출
- hidden testcase 순회
- testcase 실행 결과 판정
- 첫 실패 시 조기 종료
- 최종 `JudgeRunResult` 생성

`JudgePersistenceService`는 DB 상태 변경을 담당한다.

- `PENDING -> JUDGING`
- hidden testcase snapshot 생성
- testcase result 저장
- `status=DONE`
- `result=AC/WA/...`
- `failed_testcase_order`
- `judged_at`, 실행 시간, 메모리 저장

실제 코드 실행은 local executor 또는 remote runner가 담당한다.

## 2. 상태와 결과 분리

현재 구조에서는 작업 상태와 채점 결과를 분리한다.

- `SubmissionStatus`: `PENDING`, `JUDGING`, `DONE`
- `SubmissionResult`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`

정상 채점 완료 예시는 `status=DONE`, `result=AC`다.

## 3. 첫 실패 시 조기 종료

`JudgeService.runJudgeLogic(...)`는 hidden testcase를 `testcase_order` 순서대로 실행하다가 `AC`가 아닌 결과가 나오면 이후 testcase 실행을 중단한다.

실패 testcase order를 저장하는 결과:

- `WA`
- `TLE`
- `RE`
- `MLE`

저장하지 않는 결과:

- `AC`
- `CE`
- `SYSTEM_ERROR`

## 4. 실행 시간과 메모리 집계

제출 단위 집계 기준은 실행된 testcase 중 최대값이다.

- `executionTimeMs`: 최대 실행 시간
- `memoryKb`: 최대 메모리 사용량

이 기준은 per-testcase 시간 제한/메모리 제한과 연결하기 쉽다. 마지막 실행값이나 합산값보다 온라인 저지 결과 표시와 제한 판정에 자연스럽다.

## 5. 저장 대상

`submissions`:

- `status`
- `result`
- `failed_testcase_order`
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

첫 실패 이후 실행하지 않은 testcase의 result row는 생성하지 않는다.

## 6. 제한값 연결

`Problem.timeLimitMs`, `Problem.memoryLimitMb`는 다음 흐름으로 전달된다.

```text
Problem -> StartedJudging -> JudgeContext -> ExecutionGateway -> JudgeExecutionResult -> JudgeService 판정
```

현재 구현:

- timeout이면 `TLE`
- `memoryUsageKb > memoryLimitMb * 1024`이면 `MLE`
- exit code `137`이면 `MLE`

local Docker executor의 실제 메모리 사용량 측정은 확실하지 않다.

## 7. 로컬과 원격 실행 차이

local profile:

- Redis queue consume
- local Docker executor 직접 실행

remote profile:

- Cloud Tasks HTTP trigger
- remote runner HTTP 호출

runner profile:

- DB 없이 단일 실행 요청 처리

`JudgeService`의 판단 로직은 공통이고, 실행 방식은 설정과 port 구현체로 나뉜다.

