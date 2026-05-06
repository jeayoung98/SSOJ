# AGENTS.md

## Project overview

SSOJ is an online judge backend for running C++ / Java / Python submissions in Docker sandbox containers.

This repository focuses on the Spring Boot judging backend:

- Cloud Run Orchestrator
- GCE Runner VM application
- Docker-based code execution
- Judge result persistence
- SSE progress delivery from Orchestrator

Next.js Web/API and Supabase infrastructure are part of the full service, but their implementation is outside this repository.

## Current architecture

```text
Frontend / Next.js
-> Supabase DB
-> Cloud Run Orchestrator
-> Compute Engine Runner VM
-> Docker Sandbox Containers
```

## Important contracts

### Submission-first contract

Next.js must create a `submissions` row before triggering judging.

The judging pipeline uses `submissionId`, not `problemId`, as the job identifier.

### Judge trigger

```http
POST /internal/judge-executions
Content-Type: application/json
```

```json
{
  "submissionId": 222
}
```

A successful trigger returns `202 Accepted` with an empty body.

### Runner contract

The frontend must not call Runner directly.

Allowed:

```text
Frontend / Next.js -> Orchestrator -> Runner VM
```

Not allowed:

```text
Frontend / Next.js -> Runner VM
```

### Progress contract

Runner sends progress callbacks to Orchestrator.

Orchestrator forwards progress to frontend clients over SSE.

`completedTestcases` means executed testcase count. It does not mean passed testcase count.

Correct UI wording:

```text
테스트 실행 중... 37 / 100
```

Incorrect UI wording:

```text
37개 통과
```

## Result model

Final judge result is stored in `submissions`.

Do not introduce testcase-level result persistence unless explicitly requested.

Important fields:

- `status`: `PENDING`, `JUDGING`, `DONE`
- `result`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`
- `execution_time_ms`
- `memory_kb`
- `failed_testcase_order`
- `submitted_at`
- `judged_at`

## Execution model

The current execution model is `PER_CASE_PROCESS`.

This means:

- C++ is compiled once and executed once per testcase.
- Java is compiled once and executed once per testcase.
- Python is executed once per testcase.

This is slower than batched stdin execution, but it prevents testcase-to-testcase state contamination.

## Warm container model

Runner can use a Warm Container Pool.

Expected behavior:

- Language containers are created ahead of time.
- Runner uses `docker exec` for each judging job.
- Container recreation is a fallback path, not the normal path.

## SSE reliability model

The current SSE hub is memory-based.

This is acceptable for MVP usage when Cloud Run is constrained to a single instance.

Do not assume memory-based SSE is reliable under multi-instance Cloud Run scale-out.

If scale-out is required, introduce Redis Pub/Sub or another external broker.

## Documentation rules

When updating documentation:

- Do not document Cloud Tasks as the active production path unless the code and deployment actually use it.
- Do not document Redis as the production queue unless it is actually in the active deployment path.
- Do not expose real Cloud Run URLs, VM external IPs, DB URLs, service account emails, or secrets in public docs.
- Use placeholders for environment-specific values.
- Keep README focused on the current architecture.
- Keep old architecture notes out of active docs.
