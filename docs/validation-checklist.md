# Validation Checklist

This document lists manual checks and automated test expectations for the current worker.

## Basic Run

Local run:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue:

```powershell
redis-cli LPUSH judge:queue 1
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

## ID Check

| Table | Expected ID type |
| --- | --- |
| `users` | UUID |
| `problems` | Long / identity |
| `problem_examples` | Long / identity |
| `problem_testcases` | Long / identity |
| `submissions` | Long / identity |

Redis and Cloud Tasks payloads must contain a Long `submissionId`, for example `1`.

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

When multiple testcases are executed, submission-level values use the maximum among executed testcases.

Example:

| testcase | executionTimeMs | memoryKb |
| --- | ---: | ---: |
| 1 | 5 | 128 |
| 2 | 6 | 64 |

Expected:

- `submissions.execution_time_ms = 6`
- `submissions.memory_kb = 128`

Local Docker executor may return `null` for real memory usage depending on environment and image support.

## Remote Validation

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

Direct runner check:

```powershell
curl -X POST http://localhost:8081/internal/runner-executions ^
  -H "Content-Type: application/json" ^
  -d "{\"submissionId\":1,\"problemId\":1,\"language\":\"python\",\"sourceCode\":\"a,b=map(int,input().split())\nprint(a+b)\",\"testCases\":[{\"testCaseOrder\":1,\"input\":\"1 2\n\",\"expectedOutput\":\"3\n\"}],\"timeLimitMs\":3000,\"memoryLimitMb\":128}"
```

Check:

- orchestrator accepts `/internal/judge-executions`
- runner accepts `/internal/runner-executions`
- runner request uses `testCases` array
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
- Cloud Tasks payload contains a Long `submissionId`
- Redis local payload is a plain Long string
- runner host has Docker daemon if Docker execution is used
- runner images contain required runtime tools for batch execution
- orchestrator can reach runner base URL
- `/internal/judge-executions` and `/internal/runner-executions` are not treated as public APIs

## Archive Reference

Older E2E/demo/cleanup/concurrency/C++ notes live under `docs/archive/`.
Those files are historical references and may mention removed testcase result storage or older UUID payload examples.
