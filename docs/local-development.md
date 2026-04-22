# Local Development

This document summarizes how to run and validate the current worker locally.

## Default Local Setup

```properties
SPRING_PROFILES_ACTIVE=local
worker.role=orchestrator
worker.mode=redis-polling
judge.dispatch.mode=redis
judge.execution.mode=docker
```

Flow:

```text
submissionId(UUID)
-> Redis judge:queue
-> JudgeQueueConsumer
-> JudgeService
-> DockerExecutionGateway
-> submissions update
```

## Requirements

- JDK 17
- PostgreSQL
- Redis
- Docker daemon

Without Docker daemon, the local Docker execution path cannot run submissions.

## Run

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue:

```powershell
redis-cli LPUSH judge:queue 00000000-0000-0000-0000-000000000001
```

The queue payload is a plain UUID string, not JSON.

## Local Orchestrator/Runner Split

Runner:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=runner --server.port=8081"
```

Orchestrator:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=remote --server.port=8080 --judge.execution.remote.base-url=http://localhost:8081"
```

Trigger:

```powershell
curl -X POST http://localhost:8080/internal/judge-executions ^
  -H "Content-Type: application/json" ^
  -d "{\"submissionId\":\"00000000-0000-0000-0000-000000000001\"}"
```

## Tests

```powershell
.\gradlew.bat test
```

The tests cover:

- stop at first failed testcase
- no execution after failure
- `failedTestcaseOrder` storage
- max execution time and memory storage
- AC/CE/SYSTEM_ERROR failed order handling

## Useful DB Check

```sql
select id,
       status,
       result,
       failed_testcase_order,
       execution_time_ms,
       memory_kb,
       submitted_at,
       judged_at
from submissions
where id = :submission_id;
```

No testcase-level result table is required for user-facing result lookup.

## Notes

- This repository does not add DDL/migration files.
- If an existing DB still has `submission_testcase_results`, dropping it is a
  later code-external DB cleanup task.
- Docker executor may return `null` for real memory usage.
- SSE is not part of the local worker flow.
