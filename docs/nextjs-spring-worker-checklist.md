# Next.js and Spring Judge Worker Integration Checklist

## Scope

- This checklist is based on the current Spring judge worker implementation in this repository.
- It is intended to validate the contract between:
  - Next.js submission API
  - Redis queue
  - PostgreSQL
  - Spring judge worker

## 1. Redis queue key

- [ ] Next.js pushes to Redis key `judge:queue`
- [ ] Spring worker reads from Redis key `judge:queue`
- [ ] There is no old key still in use such as:
  - `judge:submission-queue`
  - any environment-specific custom key
- [ ] Redis host and port used by Next.js and Spring point to the same Redis instance

## 2. Payload format

- [ ] Next.js pushes only `submissionId`
- [ ] Payload is a plain value that can be parsed as `Long`
- [ ] Payload does not contain JSON
- [ ] Payload does not contain extra metadata such as:
  - `problemId`
  - `language`
  - `sourceCode`
- [ ] A manual Redis check shows queue values like:
  - `123`
  - not `{"submissionId":123}`

## 3. Submission status enum

- [ ] Next.js and Spring both use the same status set
- [ ] Current Spring worker status values are:
  - `PENDING`
  - `JUDGING`
  - `AC`
  - `WA`
  - `CE`
  - `RE`
  - `TLE`
  - `MLE`
  - `SYSTEM_ERROR`
- [ ] Next.js never writes a status string outside this set
- [ ] DB column values also use the same exact uppercase strings
- [ ] Initial status created by submission API is `PENDING`

## 4. Language values

- [ ] Next.js stores `language` values exactly as Spring executors expect
- [ ] Current executable languages in this worker are:
  - `java`
  - `python`
- [ ] Current sample-only language:
  - `cpp`
- [ ] If Next.js sends `cpp`, current worker will not judge it end-to-end because `CppExecutor` is not implemented
- [ ] Check for casing mismatches such as:
  - `Java` vs `java`
  - `Python` vs `python`
  - `C++` vs `cpp`
- [ ] Recommended current API values:
  - `java`
  - `python`

## 5. DB table and column names

- [ ] Table `submission` exists
- [ ] Table `problem` exists
- [ ] Table `test_case` exists
- [ ] Table `submission_case_result` exists

### submission

- [ ] `id`
- [ ] `problem_id`
- [ ] `language`
- [ ] `source_code`
- [ ] `status`
- [ ] `created_at`
- [ ] `started_at`
- [ ] `finished_at`

### problem

- [ ] `id`
- [ ] `title`
- [ ] `description`
- [ ] `time_limit_ms`
- [ ] `memory_limit_mb`

### test_case

- [ ] `id`
- [ ] `problem_id`
- [ ] `input`
- [ ] `output`
- [ ] `is_hidden`

### submission_case_result

- [ ] `id`
- [ ] `submission_id`
- [ ] `test_case_id`
- [ ] `status`
- [ ] `execution_time_ms`
- [ ] `memory_usage_kb`

## 6. Expected status transition order

- [ ] Next.js inserts `submission` with status `PENDING`
- [ ] Next.js pushes `submissionId` to Redis `judge:queue`
- [ ] Spring worker consumes the queue item
- [ ] Spring worker loads the `submission`
- [ ] Spring worker changes status from `PENDING` to `JUDGING`
- [ ] Spring worker fills `started_at`
- [ ] Spring worker loads hidden test cases for the submission's problem
- [ ] Spring worker runs the matching executor for each hidden test case
- [ ] Spring worker stores one row per test case in `submission_case_result`
- [ ] Spring worker sets final `submission.status`
- [ ] Spring worker fills `finished_at`

### Current final statuses possible in worker

- [ ] `AC`
- [ ] `WA`
- [ ] `CE`
- [ ] `RE`
- [ ] `TLE`
- [ ] `SYSTEM_ERROR`

## 7. Where to check first when something breaks

### Submission never gets judged

- [ ] Check Next.js really inserted `submission` with `PENDING`
- [ ] Check Next.js really pushed `submissionId` to Redis
- [ ] Check Redis key is `judge:queue`
- [ ] Check Spring worker is running
- [ ] Check worker log for `Received submissionId=...`

### Submission stays in PENDING

- [ ] Check queue item exists in Redis
- [ ] Check worker can connect to Redis
- [ ] Check payload is numeric and parseable as `Long`
- [ ] Check `worker.enabled` is not disabled

### Submission changes to JUDGING and never finishes

- [ ] Check Docker is running
- [ ] Check selected language has a matching executor
- [ ] Check `language` value is one of:
  - `java`
  - `python`
- [ ] Check Docker image exists locally or can be pulled
- [ ] Check hidden test cases exist for the target problem
- [ ] Check process timeout and input handling

### Final status is SYSTEM_ERROR

- [ ] Check Docker command can start locally
- [ ] Check Docker image name in Spring config
- [ ] Check mounted temp directory is accessible
- [ ] Check worker log for executor exception
- [ ] Check worker log for `JudgeService failed while processing submission`

### Final status is wrong

- [ ] Check expected output stored in `test_case.output`
- [ ] Check actual output trimming behavior
- [ ] Current policy is:
  - trim whole output
  - split by line
  - trim each line
  - compare line-by-line
- [ ] Check language string mismatch did not select the wrong path

### submission_case_result rows are missing

- [ ] Check hidden test cases exist with `is_hidden=true`
- [ ] Check `problem_id` on submission points to the expected problem
- [ ] Check worker has write access to `submission_case_result`

## Quick manual verification

- [ ] Insert a `problem`
- [ ] Insert hidden `test_case` rows
- [ ] Insert a `submission` with status `PENDING`
- [ ] Push the `submissionId` with:

```powershell
redis-cli LPUSH judge:queue <submissionId>
```

- [ ] Confirm worker logs show queue consume
- [ ] Confirm DB status transitions:
  - `PENDING`
  - `JUDGING`
  - final status
- [ ] Confirm `started_at` and `finished_at` are filled
- [ ] Confirm `submission_case_result` rows were created
