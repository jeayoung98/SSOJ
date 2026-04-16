# Judge Worker Concurrency Check

## Goal

Verify that the current judge worker runs at most 2 judging tasks at the same time.

Current implementation basis:

- Redis queue key: `judge:queue`
- Polling consumer
- `worker.max-concurrency=2`
- Concurrency control: semaphore + fixed thread pool

## 1. Test preparation

- [ ] Start PostgreSQL
- [ ] Start Redis
- [ ] Start Docker
- [ ] Start the Spring worker
- [ ] Confirm `src/main/resources/application.properties` has:
  - `worker.max-concurrency=2`
- [ ] Prepare one `problem`
- [ ] Prepare several hidden `test_case` rows for that problem
- [ ] Prepare multiple `submission` rows with status `PENDING`

### Recommended test data

To make concurrent execution visible, use code that does not finish immediately.

Recommended choice:

- Java `TLE` sample or Python `TLE` sample
- Reason:
  - long-running submissions make overlap easy to observe

Examples:

- `samples/submissions/java/tle/Main.java`
- `samples/submissions/python/tle/main.py`

Set the problem time limit small enough to finish within a few seconds.

Example:

- `time_limit_ms = 3000`

## 2. How to enqueue multiple submission ids

Insert several `submission` rows first, for example:

- `101`
- `102`
- `103`
- `104`
- `105`

Then push them into Redis quickly.

### Windows PowerShell example

```powershell
redis-cli LPUSH judge:queue 101
redis-cli LPUSH judge:queue 102
redis-cli LPUSH judge:queue 103
redis-cli LPUSH judge:queue 104
redis-cli LPUSH judge:queue 105
```

### One-line PowerShell loop

```powershell
101..105 | ForEach-Object { redis-cli LPUSH judge:queue $_ }
```

### Check queue length

```powershell
redis-cli LLEN judge:queue
```

## 3. Which logs to inspect

### Expected log pattern

You should see queue consume logs like:

- `Received submissionId=101 from Redis queue judge:queue`
- `Received submissionId=102 from Redis queue judge:queue`

Then, while those two are still running, you should not immediately see consume logs for all remaining items.

What to look for:

- [ ] At first, only 2 submissions move into active judging close together
- [ ] Remaining queue items stay unconsumed until one running job finishes
- [ ] After one of the first 2 finishes, the next queued item is consumed

### Practical interpretation

If max concurrency works:

- submission 1 starts
- submission 2 starts
- submission 3 waits in Redis
- submission 4 waits in Redis
- submission 5 waits in Redis
- after one running task finishes, submission 3 starts

### Current log limitations

The current worker logs do not print active worker count directly.

So use these indirect signals:

- queue consume timing
- status changes in DB
- queue length decrease timing

## 4. What to verify in DB

### submission table

Watch these columns:

- `status`
- `started_at`
- `finished_at`

Expected behavior:

- [ ] At most 2 rows are in `JUDGING` at the same time
- [ ] Remaining rows stay `PENDING` until a slot opens
- [ ] When one running submission finishes, one pending submission changes to `JUDGING`

### Suggested SQL checks

Count currently running submissions:

```sql
select count(*)
from submission
where status = 'JUDGING';
```

Check state progression:

```sql
select id, status, started_at, finished_at
from submission
where id in (101, 102, 103, 104, 105)
order by id;
```

### submission_case_result table

Useful confirmation:

- [ ] Rows are created only for submissions that actually started judging
- [ ] Waiting submissions do not create results yet

## 5. What indicates concurrency=2 is working

Use this checklist during a run:

- [ ] Right after enqueueing 5 submissions, Redis queue length does not instantly drop to 0
- [ ] Only 2 submissions switch from `PENDING` to `JUDGING`
- [ ] `count(status='JUDGING')` never exceeds 2
- [ ] A 3rd submission starts only after one of the first 2 finishes
- [ ] Queue length decreases step-by-step, not all at once

## 6. If the test fails, what to suspect first

### More than 2 submissions become JUDGING at the same time

- [ ] `worker.max-concurrency` is not actually `2`
- [ ] Multiple worker processes are running
- [ ] Another service is also consuming the same Redis queue
- [ ] DB status transition logic was bypassed elsewhere

### Only 1 submission runs at a time

- [ ] Worker may be running but only one task is being submitted
- [ ] Docker execution might be finishing too quickly to observe overlap
- [ ] Test code may not be slow enough
- [ ] Poll interval may make starts look serialized

### Queue drains immediately

- [ ] More than one worker instance may be active
- [ ] Concurrency limit may have been changed
- [ ] Current running worker may not be the code version you expect

### Submissions stay in PENDING forever

- [ ] Worker is not running
- [ ] Redis connection is wrong
- [ ] Queue key is not `judge:queue`
- [ ] Payload values are not valid `Long`
- [ ] `worker.enabled=false`

### Submission gets stuck in JUDGING

- [ ] Docker execution hung unexpectedly
- [ ] Exception occurred during judging and final status was not written correctly
- [ ] DB transaction or connection issue happened
- [ ] Check worker logs for executor or JudgeService errors

## 7. Simple local load test suggestion

This is not a benchmark. It is only a visibility test for the concurrency cap.

### Suggested approach

- Create 10 `PENDING` submissions using TLE code
- Push all 10 ids quickly into Redis
- Repeatedly check:
  - Redis queue length
  - count of `JUDGING`
  - count of finished submissions

### Redis queue polling

```powershell
1..10 | ForEach-Object { redis-cli LLEN judge:queue; Start-Sleep -Seconds 1 }
```

### DB polling idea

Run repeatedly in your DB client:

```sql
select status, count(*)
from submission
where id in (101, 102, 103, 104, 105)
group by status
order by status;
```

Expected pattern:

- first: `2 JUDGING`, remaining `PENDING`
- later: finished statuses appear, next pending ones move to `JUDGING`

## 8. Recommended verification order

1. Start worker and infrastructure
2. Insert 5 slow submissions
3. Push all 5 ids to `judge:queue`
4. Immediately check `LLEN judge:queue`
5. Check worker logs for first 2 consumes
6. Query DB and confirm only 2 submissions are `JUDGING`
7. Wait for one to finish
8. Confirm exactly one waiting submission starts next
9. Repeat until all submissions complete
