# SSOJ

SSOJ is a Spring Boot based online judge worker/orchestrator repository.

This repository contains the judging backend only. The Next.js Web/API layer is treated as an external integration that creates submissions and requests judging.

## Current Scope

- Spring Boot orchestrator: loads submissions/testcases from PostgreSQL and saves final judge results.
- Spring Boot runner: executes submitted code through Docker and returns a batch judge result.
- PostgreSQL: stores problems, examples, hidden testcases, submissions, and final judge results.
- Redis: local async dispatch path.
- Cloud Tasks: deployment async dispatch path.
- Docker: isolated execution for C++, Java, and Python.

Out of scope for this repository:

- Next.js Web/API implementation
- ranking
- plagiarism detection
- special judge
- advanced sandbox hardening
- realtime/SSE push

## Architecture Summary

```text
Web/API
-> create submissions row
-> dispatch submissionId
-> orchestrator
-> load submission + hidden testcases
-> runner or local Docker executor
-> stop at first failed testcase
-> save final result to submissions
```

Deployment-oriented split:

```text
Cloud Tasks
-> Cloud Run orchestrator /internal/judge-executions
-> remote runner /internal/runner-executions
-> Docker sandbox
-> PostgreSQL result update
```

The runner is execution-only. It does not use PostgreSQL, Redis, or Cloud Tasks directly.

## Judging Model

- Hidden testcases are executed by `testcase_order`.
- The runner executes testcases in batch, not one container per testcase.
- Judging stops immediately on the first `WA`, `TLE`, `RE`, or `MLE`.
- Submission-level `execution_time_ms` and `memory_kb` store the maximum value among executed testcases.
- Testcase-level result rows are not persisted.

Final result fields are stored in `submissions`:

- `status`
- `result`
- `failed_testcase_order`
- `execution_time_ms`
- `memory_kb`
- `submitted_at`
- `judged_at`

`failed_testcase_order` is `null` for `AC`, `CE`, and `SYSTEM_ERROR`.

## DB Baseline

Active JPA tables:

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

This repository does not currently include DDL or migration files. Existing external DB schemas must be kept aligned separately.

## Run Locally

Requirements:

- JDK 17
- PostgreSQL
- Redis
- Docker daemon

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue example:

```powershell
redis-cli LPUSH judge:queue 1
```

The Redis payload is a plain `submissionId` Long string.

## Run Tests

```powershell
.\gradlew.bat test
```

## Main Docs

- [Architecture](docs/architecture.md)
- [Local Development](docs/local-development.md)
- [Deployment](docs/deployment.md)
- [Judging Model](docs/judging-model.md)
- [Validation Checklist](docs/validation-checklist.md)

Older documents under `docs/archive/` are historical references only. Prefer the main docs above when they conflict.
