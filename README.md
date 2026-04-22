# SSOJ

SSOJ is a Spring Boot based online judge worker repository for the MVP phase.
The current repository contains the worker/orchestrator/runner code, not the
Next.js Web/API code.

## Current Implementation

- Web/API is expected to create a `submissions` row first, then pass the
  `submissionId` through Redis or Cloud Tasks.
- Spring Boot can run as an orchestrator or runner depending on profile/property.
- Local validation uses Redis polling and Docker execution.
- Deployment mode uses an HTTP-triggered orchestrator and a remote runner.
- SSE/realtime push is out of scope.

## Judging Flow

```text
submission created
-> submissionId dispatched
-> JudgeService
-> hidden problem_testcases executed by testcase_order
-> stop immediately on first WA/TLE/RE/MLE
-> update submissions with final result only
```

The worker no longer writes testcase-level result rows. A submission result is
stored in `submissions` with:

- `status`
- `result`
- `failed_testcase_order`
- `execution_time_ms`
- `memory_kb`
- `submitted_at`
- `judged_at`

`failed_testcase_order` is `null` for `AC`, `CE`, and `SYSTEM_ERROR`.

## DB Baseline

The active JPA mappings use these tables:

- `users`
- `problems`
- `problem_examples`
- `problem_testcases`
- `submissions`

Important IDs:

- `problems.id`: `String`
- `submissions.id`: `UUID`
- `problem_testcases.id`: `UUID`

Note: this repository does not add DDL/migration files. If an existing DB still
has `submission_testcase_results`, dropping it is a code-external follow-up.

## Run

Local:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue:

```powershell
redis-cli LPUSH judge:queue 00000000-0000-0000-0000-000000000001
```

Tests:

```powershell
.\gradlew.bat test
```

## Docs

- [Architecture](docs/architecture.md)
- [Local Development](docs/local-development.md)
- [Deployment](docs/deployment.md)
- [Judging Model](docs/judging-model.md)
- [Validation Checklist](docs/validation-checklist.md)
