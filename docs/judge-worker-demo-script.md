# Judge Worker Demo Script

## Goal

Use this script for a short 3 to 5 minute demo.

Current implementation scope:

- Redis queue consume
- Spring judge worker
- Java and Python executor
- Docker-based execution
- Hidden test case judging
- `submission_case_result` storage
- final status update
- max concurrency `2`

## 1. Before the demo

Prepare these in advance:

- [ ] PostgreSQL is running
- [ ] Redis is running
- [ ] Docker is running
- [ ] Spring worker is running
- [ ] At least 1 `problem` exists
- [ ] Hidden `test_case` rows exist for that problem
- [ ] Sample submissions are prepared

Recommended sample files:

- AC:
  - `samples/submissions/java/ac/Main.java`
- WA:
  - `samples/submissions/java/wa/Main.java`
- CE:
  - `samples/submissions/java/ce/Main.java`
- Concurrency demo:
  - `samples/submissions/java/tle/Main.java`
  - or `samples/submissions/python/tle/main.py`

Recommended screens to prepare:

- terminal 1: worker logs
- terminal 2: Redis commands
- DB client: `submission` and `submission_case_result`
- optional: Next.js submission page or API client

## 2. Shortest recommended demo flow

Recommended order:

1. AC submission
2. WA or CE submission
3. 2 to 3 concurrent submissions

This is enough to show:

- queue contract
- worker consume
- status transition
- result persistence
- concurrency limit

## 3. AC demo sequence

### What to submit

- Java AC sample:
  - `samples/submissions/java/ac/Main.java`

### What to say

- "The submission is first stored as `PENDING`."
- "Next.js pushes only the `submissionId` into Redis."
- "The Spring worker consumes that id and performs judging asynchronously."

### What to show

- [ ] A new `submission` row with status `PENDING`
- [ ] Redis enqueue to `judge:queue`
- [ ] Worker log:
  - `Received submissionId=... from Redis queue judge:queue`
- [ ] DB state change:
  - `PENDING -> JUDGING -> AC`
- [ ] `started_at` and `finished_at`
- [ ] `submission_case_result` rows for hidden test cases

## 4. WA or CE demo sequence

Choose one of these.

### Option A: WA demo

Use:

- `samples/submissions/java/wa/Main.java`

What to show:

- [ ] Submission starts at `PENDING`
- [ ] Worker consumes queue item
- [ ] Status becomes `JUDGING`
- [ ] Final status becomes `WA`
- [ ] `submission_case_result` shows a failed case

Why this is good:

- easiest to explain
- clearly shows output comparison

### Option B: CE demo

Use:

- `samples/submissions/java/ce/Main.java`

What to show:

- [ ] Submission starts at `PENDING`
- [ ] Worker consumes queue item
- [ ] Status becomes `JUDGING`
- [ ] Final status becomes `CE`

Why this is good:

- clearly shows compile-stage failure handling

Recommended choice for a short demo:

- use `WA` if you want to explain output matching
- use `CE` if you want to explain executor failure classification

## 5. Status change checkpoints

These are the core points to highlight during the demo:

- [ ] `PENDING`
  - created by submission side
- [ ] `JUDGING`
  - set by Spring worker when queue item is consumed
- [ ] final status
  - `AC`
  - `WA`
  - `CE`
  - `RE`
  - `TLE`
  - `SYSTEM_ERROR`

Minimum visual proof:

- `submission.status`
- `started_at`
- `finished_at`

## 6. What to show from Redis and worker logs

### Redis

Show queue push or queue length:

```powershell
redis-cli LPUSH judge:queue <submissionId>
redis-cli LLEN judge:queue
```

What to emphasize:

- the payload is only `submissionId`
- queue key is `judge:queue`

### Worker log

The most useful log line to show:

- `Received submissionId=... from Redis queue judge:queue`

Then show:

- `Submission ... changed from PENDING to JUDGING`
- `Submission ... finished with status=...`

## 7. What to show from DB or result API

If using DB directly, show:

### submission

- `id`
- `status`
- `started_at`
- `finished_at`

### submission_case_result

- `submission_id`
- `test_case_id`
- `status`
- `execution_time_ms`

If you already have a Next.js result page or API:

- show the same fields there
- do not claim extra functionality beyond current implementation

## 8. 2 to 3 concurrent submission demo

This part should be very short.

### Goal

Show that current worker runs at most 2 judging tasks at once.

### Recommended setup

Use slow samples:

- `samples/submissions/java/tle/Main.java`
- or `samples/submissions/python/tle/main.py`

Set up 3 submissions:

- submission A
- submission B
- submission C

### Demo steps

1. Put 3 `PENDING` submissions into DB
2. Push all 3 ids quickly into Redis
3. Watch worker logs and DB

### What to highlight

- [ ] first 2 submissions move to `JUDGING`
- [ ] the 3rd stays `PENDING` for a while
- [ ] after one finishes, the next one starts

### Useful commands

```powershell
redis-cli LPUSH judge:queue 201
redis-cli LPUSH judge:queue 202
redis-cli LPUSH judge:queue 203
```

DB query:

```sql
select id, status, started_at, finished_at
from submission
where id in (201, 202, 203)
order by id;
```

## 9. Recommended 3 to 5 minute script

### 0:00 to 0:30

- "This worker receives `submissionId` through Redis and judges asynchronously in Spring Boot."

### 0:30 to 1:30

- Show AC submission
- Show `PENDING -> JUDGING -> AC`

### 1:30 to 2:30

- Show WA or CE submission
- Show final failure status and saved case result

### 2:30 to 4:00

- Show 3 queued slow submissions
- Point out only 2 become `JUDGING` at the same time

### 4:00 to 5:00

- Summarize:
  - Redis queue decouples API and worker
  - worker updates status and stores case results
  - Docker executes code safely per submission
  - concurrency is capped at 2 in current MVP

## 10. Demo tips

- Prefer Java samples for a stable short demo
- Avoid switching too many screens
- Prepare submission ids and SQL queries beforehand
- If time is short:
  - show AC
  - show one failure case
  - show the concurrency DB view only
