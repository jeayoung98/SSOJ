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
submissionId(Long)
-> Redis judge:queue
-> JudgeQueueConsumer
-> JudgeService
-> DockerExecutionGateway
-> LanguageExecutor
-> submissions update
```

## Requirements

- JDK 17
- PostgreSQL
- Redis
- Docker daemon

Without Docker daemon, the local Docker execution path cannot run submissions.

## Run Local Orchestrator

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue:

```powershell
redis-cli LPUSH judge:queue 1
```

The queue payload is a plain Long string, not JSON and not UUID.

## Local Orchestrator/Runner Split

Use this when validating the same boundary as deployment.

Runner:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=runner --server.port=8081"
```

Orchestrator:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=remote --server.port=8080 --judge.execution.remote.base-url=http://localhost:8081"
```

Trigger orchestrator:

```powershell
curl -X POST http://localhost:8080/internal/judge-executions ^
  -H "Content-Type: application/json" ^
  -d "{\"submissionId\":1}"
```

Direct runner smoke test:

```powershell
curl -X POST http://localhost:8081/internal/runner-executions ^
  -H "Content-Type: application/json" ^
  -d "{\"submissionId\":1,\"problemId\":1,\"language\":\"python\",\"sourceCode\":\"a,b=map(int,input().split())\nprint(a+b)\",\"testCases\":[{\"testCaseOrder\":1,\"input\":\"1 2\n\",\"expectedOutput\":\"3\n\"}],\"timeLimitMs\":3000,\"memoryLimitMb\":128}"
```

Expected runner response example:

```json
{
  "result": "AC",
  "executionTimeMs": 10,
  "memoryUsageKb": 128,
  "failedTestcaseOrder": null
}
```

Actual time and memory values depend on the Docker runtime environment.

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
- runner profile isolation from DB/JPA/Redis
- remote runner request/response mapping
- Cloud Tasks dispatch payload creation

## Useful DB Check

```sql
select id,
       problem_id,
       language,
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
- If an existing DB still has `submission_testcase_results`, dropping it is a later code-external DB cleanup task.
- Docker executor may return `null` for real memory usage depending on environment and image support.
- SSE is not part of the local worker flow.
