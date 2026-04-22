# Architecture

This document describes the current repository structure and worker boundaries.

## Components

| Component | Role |
| --- | --- |
| Spring Boot orchestrator | Reads DB state and coordinates judging |
| Spring Boot runner | Executes a single code run request |
| PostgreSQL | Stores problems, testcases, submissions, and final judge results |
| Redis | Local queue path |
| Cloud Tasks | Deployment HTTP trigger dispatch |
| Docker executor | Runs C++/Java/Python code |

The repository does not currently contain the Next.js Web/API code. Web/API is
treated as an external integration that creates submissions and dispatches IDs.

## Main Packages

| Path | Role |
| --- | --- |
| `judge/application/sevice` | Judge flow, queue consumer, runner service |
| `judge/domain/model` | Judge command/result/context records |
| `judge/executor` | Language-specific Docker execution |
| `judge/infrastructure/redis` | Redis dispatch |
| `judge/infrastructure/cloudtasks` | Cloud Tasks dispatch |
| `judge/infrastructure/remote` | Remote runner HTTP client |
| `judge/presentation` | Internal HTTP endpoints |
| `problem`, `testcase`, `submission`, `user` | JPA entity/repository packages |

## Orchestrator Flow

```text
submissionId
-> JudgeService
-> load hidden testcases
-> execute by testcase_order
-> stop at first WA/TLE/RE/MLE
-> save final result to submissions
```

The orchestrator stores submission-level results only. It does not persist
testcase-level result rows.

## Runner Flow

```text
POST /internal/runner-executions
-> RunnerExecutionService
-> LanguageExecutor
-> DockerProcessExecutor
-> RunnerExecutionResponse
```

Runner mode does not use DB or Redis.

## DB Model

Active tables expected by the current JPA model:

- `users`
- `problems`
- `problem_examples`
- `problem_testcases`
- `submissions`

Submission state/result fields:

- `submissions.status`: `PENDING`, `JUDGING`, `DONE`
- `submissions.result`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`
- `submissions.failed_testcase_order`
- `submissions.execution_time_ms`
- `submissions.memory_kb`
- `submissions.submitted_at`
- `submissions.judged_at`

If an existing DB still has `submission_testcase_results`, removing that table is
a later DB operation outside this code change.

## External Web/API Contract

Web/API must create a `submissions` row before enqueue/dispatch.

Required values:

- `id`: UUID
- `user_id`: UUID
- `problem_id`: `problems.id`
- `language`
- `source_code`
- `status=PENDING`
- `submitted_at`

Redis local payload:

```text
judge:queue -> UUID string
```

Cloud Tasks payload:

```json
{
  "submissionId": "00000000-0000-0000-0000-000000000001"
}
```
