# 아키텍처

이 문서는 현재 SSOJ 채점 백엔드 아키텍처를 설명합니다.

## 저장소 경계

이 저장소는 Spring Boot 기반 채점 백엔드를 포함합니다.

Next.js Web/API 코드는 포함하지 않습니다. Web/API 계층은 이 저장소 외부의 클라이언트이며, 먼저 `submissions` row를 생성한 뒤 생성된 `submissionId`로 채점을 요청합니다.

## 구성 요소

| 구성 요소 | 역할 |
| --- | --- |
| Web/API | 제출을 생성하고 채점을 요청합니다. 이 저장소 외부에 있습니다. |
| Spring Boot orchestrator | DB 상태를 조회하고 채점을 조율하며 최종 결과를 저장합니다. |
| Spring Boot runner | Docker를 통해 제출 코드를 실행하고 최종 batch 결과를 반환합니다. |
| PostgreSQL | 사용자, 문제, 예제, hidden testcase, 제출, 최종 결과를 저장합니다. |
| Redis | 로컬 비동기 dispatch 경로입니다. |
| Cloud Tasks | 배포 환경의 비동기 dispatch 경로입니다. |
| Docker executor | C++, Java, Python 코드를 격리 컨테이너에서 컴파일/실행합니다. |

## 주요 패키지

| 경로 | 역할 |
| --- | --- |
| `judge/application/sevice` | 채점 흐름, queue consumer, persistence, runner service |
| `judge/domain/model` | 채점 command, context snapshot, result record |
| `judge/executor` | 언어별 Docker 실행 |
| `judge/infrastructure/redis` | Redis dispatch |
| `judge/infrastructure/cloudtasks` | Cloud Tasks dispatch |
| `judge/infrastructure/remote` | remote runner HTTP client |
| `judge/presentation` | 내부 HTTP endpoint |
| `problem`, `testcase`, `submission`, `user` | JPA entity/repository 패키지 |

주의: 현재 코드에는 `sevice`라는 패키지명이 실제로 사용되고 있습니다. 추후 리팩터링하기 전까지는 이 경로를 현재 기준으로 봅니다.

## 로컬 Redis 흐름

```text
Web/API 또는 수동 테스트
-> Redis judge:queue에 submissionId(Long) 적재
-> JudgeQueueConsumer
-> JudgeService
-> JudgePersistenceService.startJudging
-> DockerExecutionGateway
-> LanguageExecutor
-> submissions result update
```

## 배포 흐름

```text
Web/API
-> submissions row 생성
-> submissionId(Long)를 담은 Cloud Tasks task 생성
-> orchestrator POST /internal/judge-executions
-> JudgeService
-> RemoteExecutionGateway
-> runner POST /internal/runner-executions
-> Docker batch execution
-> orchestrator가 submissions에 최종 결과 저장
```

## Orchestrator 책임

orchestrator는 다음을 담당합니다.

1. `submissionId`를 수신하거나 소비합니다.
2. `submissions.status`를 `PENDING`에서 `JUDGING`으로 변경합니다.
3. source code, language, problem limit, hidden testcase를 조회합니다.
4. 실행을 local 또는 remote runner로 위임합니다.
5. 최종 채점 결과를 `submissions`에 저장합니다.

orchestrator는 제출 단위 결과만 저장합니다. 테스트케이스별 결과 row는 저장하지 않습니다.

## Runner 책임

```text
POST /internal/runner-executions
-> RunnerExecutionService
-> RunnerLanguageExecutorSelector
-> LanguageExecutor
-> DockerProcessExecutor
-> RunnerExecutionResponse
```

runner mode는 실행 전용입니다. DB, Redis, Cloud Tasks를 직접 사용하지 않습니다.

runner 요청 구조:

```json
{
  "submissionId": 1,
  "problemId": 1,
  "language": "python",
  "sourceCode": "print(1)",
  "testCases": [
    {
      "testCaseOrder": 1,
      "input": "",
      "expectedOutput": "1\n"
    }
  ],
  "timeLimitMs": 1000,
  "memoryLimitMb": 128
}
```

runner 응답 구조:

```json
{
  "result": "AC",
  "executionTimeMs": 10,
  "memoryUsageKb": 128,
  "failedTestcaseOrder": null
}
```

## 채점 정책

```text
hidden problem_testcases 조회
-> testcase_order 순서로 실행
-> 첫 WA/TLE/RE/MLE 발생 시 즉시 중단
-> 실행된 testcase 중 executionTimeMs와 memoryKb 최댓값 집계
-> submissions에 최종 결과 저장
```

`CE`는 컴파일 실패로 처리하며 특정 testcase order와 연결하지 않습니다. `SYSTEM_ERROR`도 특정 testcase에서 발생했다고 단정하지 않습니다.

## DB 모델

현재 JPA 모델 기준 활성 테이블:

- `users`
- `problems`
- `problem_examples`
- `problem_testcases`
- `submissions`

ID 정책:

| Entity | ID type |
| --- | --- |
| `users.id` | `UUID` |
| `problems.id` | `Long` |
| `problem_examples.id` | `Long` |
| `problem_testcases.id` | `Long` |
| `submissions.id` | `Long` |

제출 상태/결과 필드:

- `submissions.status`: `PENDING`, `JUDGING`, `DONE`
- `submissions.result`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`
- `submissions.failed_testcase_order`
- `submissions.execution_time_ms`
- `submissions.memory_kb`
- `submissions.submitted_at`
- `submissions.judged_at`

## 외부 Web/API 계약

Web/API는 enqueue/dispatch 전에 반드시 `submissions` row를 먼저 생성해야 합니다.

필수 값:

- `id`: Long
- `user_id`: UUID
- `problem_id`: Long
- `language`
- `source_code`
- `status=PENDING`
- `submitted_at`

Redis 로컬 payload:

```text
judge:queue -> 1
```

Cloud Tasks payload:

```json
{
  "submissionId": 1
}
```
