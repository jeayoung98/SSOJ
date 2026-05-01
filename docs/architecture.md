# Architecture

이 문서는 현재 SSOJ 채점 백엔드의 실제 코드 구조와 배포 구조를 설명합니다.

## 저장소 경계

이 저장소는 Spring Boot 기반 채점 백엔드입니다.

Next.js Web/API 코드는 이 저장소에 포함하지 않습니다. 전체 서비스에서는 Next.js가 사용자 화면과 제출 API 계층을 담당하고, 이 저장소의 Spring Boot 애플리케이션은 제출 ID를 기준으로 채점 작업을 수행합니다.

## 전체 배포 흐름

```text
Browser
-> Next.js Web/API
-> Cloud Run Orchestrator
-> Cloud Tasks
-> Cloud Run Orchestrator /internal/judge-executions
-> GCE Runner VM /internal/runner-executions
-> Docker Sandbox
-> Cloud Run Orchestrator
-> PostgreSQL / Supabase
```

핵심은 **요청 처리와 코드 실행의 분리**입니다.

## 구성 요소

| 구성 요소 | 역할 |
| --- | --- |
| Next.js Web/API | 문제 조회, 코드 제출, 결과 조회. 이 저장소 외부에 있음 |
| Spring Boot Orchestrator | submission 조회, testcase 조회, Runner 호출, 최종 결과 저장 |
| Cloud Tasks | 배포 환경에서 `/internal/judge-executions` 호출 |
| Spring Boot Runner | `/internal/runner-executions` 요청을 받아 Docker 실행 수행 |
| Docker Sandbox | C++ / Java / Python 코드 격리 실행 |
| PostgreSQL / Supabase | 문제, 예제, hidden testcase, 제출, 결과 저장 |
| Redis | 로컬 개발 환경의 dispatch 경로 |

## 주요 패키지

| 경로 | 역할 |
| --- | --- |
| `judge/application/sevice` | 채점 흐름, persistence, Redis consumer, Runner service |
| `judge/domain/model` | 채점 command, context, result model |
| `judge/executor` | 언어별 Docker 실행 |
| `judge/infrastructure/redis` | 로컬 Redis dispatch |
| `judge/infrastructure/cloudtasks` | Cloud Tasks dispatch |
| `judge/infrastructure/remote` | Remote Runner HTTP client |
| `judge/presentation` | 내부 HTTP endpoint |
| `problem`, `testcase`, `submission`, `user` | JPA entity/repository 패키지 |

주의: 현재 코드에는 `judge/application/sevice`라는 패키지명이 실제로 사용됩니다. 오타처럼 보이지만 현재 기준으로는 이 경로가 맞습니다.

## Runtime profile

| Profile | 역할 | 주요 설정 |
| --- | --- | --- |
| `local` | 로컬 Orchestrator | `worker.mode=redis-polling`, `judge.dispatch.mode=redis`, `judge.execution.mode=docker` |
| `remote` | 배포 Orchestrator | `worker.mode=http-trigger`, `judge.dispatch.mode=cloud-tasks`, `judge.execution.mode=remote` |
| `runner` | 실행 전용 Runner | `worker.role=runner`, `judge.execution.mode=docker` |

## 로컬 흐름

```text
submissionId(Long)
-> Redis judge:queue
-> JudgeQueueConsumer
-> JudgeService
-> DockerExecutionGateway
-> LanguageExecutor
-> DockerProcessExecutor
-> submissions result update
```

로컬에서는 Redis를 사용하지만, 이는 배포 필수 구성요소가 아닙니다. 배포 기준 dispatch는 Cloud Tasks입니다.

## 배포 흐름

```text
Cloud Tasks
-> POST /internal/judge-executions
-> JudgeService
-> JudgePersistenceService.startJudging
-> RemoteExecutionGateway
-> POST runner /internal/runner-executions
-> RunnerExecutionService
-> LanguageExecutor
-> DockerProcessExecutor
-> RunnerExecutionResponse
-> JudgePersistenceService.saveResultsAndFinish
```

## Orchestrator 책임

Orchestrator는 다음을 담당합니다.

1. `submissionId` 수신
2. `submissions.status`를 `PENDING`에서 `JUDGING`으로 변경
3. source code, language, problem limit, hidden testcase 조회
4. 실행을 local Docker executor 또는 remote Runner로 위임
5. 최종 결과를 `submissions`에 저장

Orchestrator는 테스트케이스별 결과 row를 저장하지 않습니다.

## Runner 책임

Runner는 실행 전용 컴포넌트입니다.

```text
POST /internal/runner-executions
-> RunnerExecutionService
-> RunnerLanguageExecutorSelector
-> LanguageExecutor
-> DockerProcessExecutor
-> RunnerExecutionResponse
```

Runner는 DB, Redis, Cloud Tasks를 직접 사용하지 않습니다.

Runner 요청 구조:

```json
{
  "submissionId": 1,
  "problemId": 1,
  "language": "python",
  "sourceCode": "a,b=map(int,input().split())\nprint(a+b)",
  "testCases": [
    {
      "testCaseOrder": 1,
      "input": "1 2\n",
      "expectedOutput": "3\n"
    }
  ],
  "timeLimitMs": 3000,
  "memoryLimitMb": 128
}
```

Runner 응답 구조:

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
-> testcase_order 순서로 batch 실행
-> 첫 WA/TLE/RE/MLE 발생 시 즉시 중단
-> 실행된 testcase 중 executionTimeMs와 memoryKb 최댓값 집계
-> submissions에 최종 결과 저장
```

`CE`는 컴파일 실패입니다. 특정 testcase 실패 번호로 노출하지 않습니다.

`SYSTEM_ERROR`는 내부 오류입니다. 특정 testcase 때문에 발생했다고 단정하지 않습니다.

## DB 모델

현재 JPA 모델 기준 활성 테이블:

| Entity | Table | ID type |
| --- | --- | --- |
| `User` | `users` | `UUID` |
| `Problem` | `problems` | `Long` |
| `ProblemExample` | `problem_examples` | `Long` |
| `ProblemTestcase` | `problem_testcases` | `Long` |
| `Submission` | `submissions` | `Long` |

제출 상태/결과 필드:

- `submissions.status`: `PENDING`, `JUDGING`, `DONE`
- `submissions.result`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`
- `submissions.failed_testcase_order`
- `submissions.execution_time_ms`
- `submissions.memory_kb`
- `submissions.submitted_at`
- `submissions.judged_at`

## 외부 Web/API 계약

Web/API는 채점 dispatch 전에 `submissions` row를 생성해야 합니다.

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
