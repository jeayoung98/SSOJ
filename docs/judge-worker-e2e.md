# Judge Worker End-to-End Scenarios

## Scope

This document is based on the current implementation only.

- E2E executable now:
  - Java
  - Python
- Sample code only for now:
  - C++
  - Reason: `CppExecutor` is not implemented in the current worker

## Assumed problem

- Problem type: A+B
- Input:
  - one line with two integers
- Expected output:
  - sum of the two integers

Example:

- Input: `1 2`
- Output: `3`

## Expected judge flow

1. Insert a `submission` row with status `PENDING`
2. Insert hidden `test_case` rows for the target `problem`
3. Push `submissionId` to Redis queue `judge:queue`
4. Worker consumes the id
5. Worker changes `submission.status` to `JUDGING`
6. Worker runs the selected executor in Docker for each hidden test case
7. Worker stores one `submission_case_result` row per hidden test case
8. Worker sets final `submission.status`
9. Worker sets `submission.finished_at`

## Recommended hidden test cases

- Test case 1
  - input: `1 2`
  - output: `3`
- Test case 2
  - input: `10 20`
  - output: `30`
- Test case 3
  - input: `100 -5`
  - output: `95`

## Status scenarios

### Java

- `samples/submissions/java/ac/Main.java`
  - expected final status: `AC`
- `samples/submissions/java/wa/Main.java`
  - expected final status: `WA`
- `samples/submissions/java/ce/Main.java`
  - expected final status: `CE`
- `samples/submissions/java/re/Main.java`
  - expected final status: `RE`
- `samples/submissions/java/tle/Main.java`
  - expected final status: `TLE`

### Python

- `samples/submissions/python/ac/main.py`
  - expected final status: `AC`
- `samples/submissions/python/wa/main.py`
  - expected final status: `WA`
- `samples/submissions/python/re/main.py`
  - expected final status: `RE`
- `samples/submissions/python/tle/main.py`
  - expected final status: `TLE`

Note:
- Current Python executor does not have a compile phase.
- For Python, syntax/runtime failures are handled through execution failure.
- In the current implementation, a Python syntax error is effectively observed as `RE`.

### C++

- `samples/submissions/cpp/ac/main.cpp`
  - intended status after `CppExecutor` is added: `AC`
- `samples/submissions/cpp/wa/main.cpp`
  - intended status after `CppExecutor` is added: `WA`
- `samples/submissions/cpp/ce/main.cpp`
  - intended status after `CppExecutor` is added: `CE`
- `samples/submissions/cpp/re/main.cpp`
  - intended status after `CppExecutor` is added: `RE`
- `samples/submissions/cpp/tle/main.cpp`
  - intended status after `CppExecutor` is added: `TLE`

Note:
- C++ sample codes are included for future verification.
- Current worker cannot execute them end-to-end because `CppExecutor` is not implemented.

## Manual verification steps

### 1. Prepare infrastructure

- Start PostgreSQL
- Start Redis
- Start Docker
- Start the worker

Reference:
- [judge-worker.md](/C:/Users/SSAFY/IdeaProjects/SSOJ/docs/judge-worker.md:1)

### 2. Prepare database data

Insert one problem and hidden test cases.

Required tables used by current worker:
- `problem`
- `submission`
- `test_case`
- `submission_case_result`

Minimum data shape:
- `problem`
  - `id`
  - `title`
  - `description`
  - `time_limit_ms`
  - `memory_limit_mb`
- `test_case`
  - `problem_id`
  - `input`
  - `output`
  - `is_hidden=true`

### 3. Insert a submission

- Create a `submission` row
- Set:
  - `problem_id` to the prepared problem
  - `language` to `java` or `python`
  - `source_code` to one of the sample files
  - `status` to `PENDING`

### 4. Enqueue the submission

```powershell
redis-cli LPUSH judge:queue <submissionId>
```

### 5. Check worker logs

Expected log sequence:

- queue consume log
- `PENDING -> JUDGING`
- final status log

### 6. Check database results

Verify `submission`:

- `status` changes from `PENDING` to one of:
  - `AC`
  - `WA`
  - `CE`
  - `RE`
  - `TLE`
- `started_at` is filled
- `finished_at` is filled

Verify `submission_case_result`:

- one row per hidden test case
- `status` matches the executor result and output comparison

## What to verify per sample

- AC sample
  - all `submission_case_result.status` values should be `AC`
  - final `submission.status` should be `AC`
- WA sample
  - first wrong output should produce `WA`
  - final `submission.status` should be `WA`
- CE sample
  - Java only in current E2E range
  - final `submission.status` should be `CE`
- RE sample
  - process exits with failure
  - final `submission.status` should be `RE`
- TLE sample
  - process exceeds `time_limit_ms`
  - final `submission.status` should be `TLE`
