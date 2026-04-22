# Validation Checklist

This document lists manual checks and automated test expectations for the
current worker.

## Basic Run

Local run:

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

## DB Check

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

## Judge Result Expectations

| Scenario | Expected value |
| --- | --- |
| All testcases AC | `result=AC`, `failed_testcase_order=null` |
| testcase 1 WA | `result=WA`, `failed_testcase_order=1`, later cases not executed |
| Middle testcase WA/TLE/RE/MLE | matching order saved, later cases not executed |
| CE | `result=CE`, `failed_testcase_order=null` |
| SYSTEM_ERROR | `result=SYSTEM_ERROR`, `failed_testcase_order=null` |

Only `submissions` is updated with the final result.

## Time And Memory

When multiple testcases are executed, submission-level values use the maximum
among executed testcases.

Example:

| testcase | executionTimeMs | memoryKb |
| --- | ---: | ---: |
| 1 | 5 | 128 |
| 2 | 6 | 64 |

Expected:

- `submissions.execution_time_ms = 6`
- `submissions.memory_kb = 128`

Local Docker executor may return `null` for real memory usage.

## Remote Validation

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

Check:

- orchestrator accepts `/internal/judge-executions`
- runner accepts `/internal/runner-executions`
- runner executes without DB/Redis
- orchestrator saves final result to `submissions`

## Docker Cleanup

Check:

- containers are not left behind after timeout
- temp workspaces are removed
- `.container.cid` files are not left behind
- failed submissions still finish with `submissions.status=DONE`

## Deployment Checks

- DB has the submission result columns used by `Submission`
- production `ddl-auto` is `validate` or `none`
- Cloud Tasks payload is UUID JSON
- Redis local payload is plain UUID string
- runner host has Docker daemon if Docker execution is used

## Archive Reference

Older E2E/demo/cleanup/concurrency/C++ notes live under `docs/archive/`.
Those files are historical references and may mention removed testcase result
storage.
