# Architecture

This document describes the current SSOJ judging backend architecture.

## Repository Boundary

This repository contains the Spring Boot judging backend.

It does not contain the Next.js Web/API code. The Web/API layer is an external client that creates a `submissions` row first and then dispatches the created `submissionId`.

## Components

| Component | Role |
| --- | --- |
| Web/API | Creates submissions and requests judging. External to this repository. |
| Spring Boot orchestrator | Loads DB state, coordinates judging, and saves final results. |
| Spring Boot runner | Executes submitted code through Docker and returns the final batch result. |
| PostgreSQL | Stores users, problems, examples, hidden testcases, submissions, and final results. |
| Redis | Local async dispatch path. |
| Cloud Tasks | Deployment async dispatch path. |
| Docker executor | Compiles/runs C++, Java, and Python code in isolated containers. |

## Main Packages

| Path | Role |
| --- | --- |
| `judge/application/sevice` | Judge flow, queue consumer, persistence, runner service |
| `judge/domain/model` | Judge commands, context snapshots, and result records |
| `judge/executor` | Language-specific Docker execution |
| `judge/infrastructure/redis` | Redis dispatch |
| `judge/infrastructure/cloudtasks` | Cloud Tasks dispatch |
| `judge/infrastructure/remote` | Remote runner HTTP client |
| `judge/presentation` | Internal HTTP endpoints |
| `problem`, `testcase`, `submission`, `user` | JPA entity/repository packages |

Note: the package name `sevice` is currently used in code and should be treated as the actual current path unless it is refactored later.

## Local Redis Flow

```text
Web/API or manual test
-> Redis judge:queue receives submissionId(Long)
-> JudgeQueueConsumer
-> JudgeService
-> JudgePersistenceService.startJudging
-> DockerExecutionGateway
-> LanguageExecutor
-> submissions result update
```

## Deployment Flow

```text
Web/API
-> create submissions row
-> Cloud Tasks task with submissionId(Long)
-> orchestrator POST /internal/judge-executions
-> JudgeService
-> RemoteExecutionGateway
-> runner POST /internal/runner-executions
-> Docker batch execution
-> orchestrator saves final result to submissions
```

## Orchestrator Responsibility

The orchestrator:

1. receives or consumes `submissionId`
2. changes `submissions.status` from `PENDING` to `JUDGING`
3. loads source code, language, problem limits, and hidden testcases
4. delegates execution locally or remotely
5. saves final judge result to `submissions`

It stores submission-level results only. It does not persist testcase-level result rows.

## Runner Responsibility

```text
POST /internal/runner-executions
-> RunnerExecutionService
-> RunnerLanguageExecutorSelector
-> LanguageExecutor
-> DockerProcessExecutor
-> RunnerExecutionResponse
```

Runner mode is execution-only. It does not use DB, Redis, or Cloud Tasks directly.

Runner request shape:

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

Runner response shape:

```json
{
  "result": "AC",
  "executionTimeMs": 10,
  "memoryUsageKb": 128,
  "failedTestcaseOrder": null
}
```

## Judging Policy

```text
load hidden problem_testcases
-> execute by testcase_order
-> stop immediately on first WA/TLE/RE/MLE
-> aggregate max executionTimeMs and memoryKb among executed testcases
-> save final result to submissions
```

`CE` is treated as compile failure and is not tied to a testcase order. `SYSTEM_ERROR` is also not assumed to be caused by a specific testcase.

## DB Model

Active tables expected by the current JPA model:

- `users`
- `problems`
- `problem_examples`
- `problem_testcases`
- `submissions`

ID policy:

| Entity | ID type |
| --- | --- |
| `users.id` | `UUID` |
| `problems.id` | `Long` |
| `problem_examples.id` | `Long` |
| `problem_testcases.id` | `Long` |
| `submissions.id` | `Long` |

Submission state/result fields:

- `submissions.status`: `PENDING`, `JUDGING`, `DONE`
- `submissions.result`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`
- `submissions.failed_testcase_order`
- `submissions.execution_time_ms`
- `submissions.memory_kb`
- `submissions.submitted_at`
- `submissions.judged_at`

## External Web/API Contract

Web/API must create a `submissions` row before enqueue/dispatch.

Required values:

- `id`: Long
- `user_id`: UUID
- `problem_id`: Long
- `language`
- `source_code`
- `status=PENDING`
- `submitted_at`

Redis local payload:

```text
judge:queue -> 1
```

Cloud Tasks payload:

```json
{
  "submissionId": 1
}
```
