# Judge Worker Cleanup Check

## Scope

This document is based on the current implementation only.

Current cleanup-related behavior in this worker:

- executors create a temp directory under the local OS temp area
- executors run Docker with `--rm`
- executors delete temp directories in `finally`
- executors destroy the process in `finally` if still alive
- `JudgeService` finalizes submission state in `finally`

Current executable languages:

- `java`
- `python`

## Goal

Verify that:

- temp directories are removed after judging
- Docker containers do not remain after judging
- cleanup still runs on success, compile/runtime failure, timeout, and unexpected exception paths

## 1. Success case checks

Use:

- Java AC sample
  - `samples/submissions/java/ac/Main.java`
- Python AC sample
  - `samples/submissions/python/ac/main.py`

Checklist:

- [ ] Submission starts judging normally
- [ ] Final `submission.status` becomes `AC`
- [ ] `started_at` is filled
- [ ] `finished_at` is filled
- [ ] `submission_case_result` rows are stored
- [ ] Temp directory created for the executor is removed after judging
- [ ] No Docker container remains after execution

## 2. Failure case checks

### CE case

Current practical scope:

- Java CE
  - `samples/submissions/java/ce/Main.java`

Checklist:

- [ ] Final `submission.status` becomes `CE`
- [ ] Temp directory is removed
- [ ] Docker container does not remain
- [ ] Worker continues processing later submissions

### RE case

Use:

- Java RE
  - `samples/submissions/java/re/Main.java`
- Python RE
  - `samples/submissions/python/re/main.py`

Checklist:

- [ ] Final `submission.status` becomes `RE`
- [ ] Temp directory is removed
- [ ] Docker container does not remain
- [ ] `finished_at` is filled

### TLE case

Use:

- Java TLE
  - `samples/submissions/java/tle/Main.java`
- Python TLE
  - `samples/submissions/python/tle/main.py`

Checklist:

- [ ] Final `submission.status` becomes `TLE`
- [ ] Timed-out process is terminated
- [ ] Temp directory is removed
- [ ] Docker container does not remain
- [ ] `finished_at` is filled

## 3. How to check temp directories

### Current temp directory prefixes

- Java executor:
  - `judge-java-`
- Python executor:
  - `judge-python-`

### Windows PowerShell check

Before test:

```powershell
Get-ChildItem $env:TEMP -Directory | Where-Object { $_.Name -like 'judge-java-*' -or $_.Name -like 'judge-python-*' }
```

Run a submission, wait until it finishes, then check again:

```powershell
Get-ChildItem $env:TEMP -Directory | Where-Object { $_.Name -like 'judge-java-*' -or $_.Name -like 'judge-python-*' }
```

Expected result:

- [ ] No leftover temp directories for finished runs

### Optional repeated watch

```powershell
1..10 | ForEach-Object {
    Get-ChildItem $env:TEMP -Directory | Where-Object { $_.Name -like 'judge-java-*' -or $_.Name -like 'judge-python-*' }
    Start-Sleep -Seconds 1
}
```

## 4. How to check Docker container leftovers

The current executors use `docker run --rm`, so finished containers should not remain.

### Check running containers

```powershell
docker ps
```

### Check all containers including exited ones

```powershell
docker ps -a
```

### Recommended observation method

1. Check container list before test
2. Trigger one submission
3. During execution, a container may appear briefly
4. After execution finishes, check again

Expected result:

- [ ] No leftover container from the judge run remains after completion

### Useful filtered check

If you want a quick repeated view:

```powershell
1..10 | ForEach-Object {
    docker ps -a
    Start-Sleep -Seconds 1
}
```

## 5. Exception-path cleanup verification

Current implementation catches executor failures and marks them as `SYSTEM_ERROR`.
`JudgeService` also uses `finally` to avoid leaving submission state in `JUDGING`.

### Practical ways to trigger exception-like paths

#### Docker launch failure

Possible ways:

- stop Docker Desktop
- set a wrong image name in `application.properties`

Examples:

- set `worker.executor.java.image=not-found-image`
- or stop Docker completely

Expected result:

- [ ] Final `submission.status` becomes `SYSTEM_ERROR`
- [ ] `finished_at` is filled
- [ ] No temp directory remains
- [ ] No Docker container remains

#### Missing executor

Possible way:

- set `language=cpp` in `submission`

Expected result with current implementation:

- [ ] Final `submission.status` becomes `SYSTEM_ERROR`
- [ ] `finished_at` is filled

Note:
- This is not a Docker exception path, but it is still useful to confirm submission finalization logic.

## 6. Cleanup failure suspicion points

If temp directories remain:

- [ ] `finally` block did not run because the process was killed externally
- [ ] Worker process terminated unexpectedly during judging
- [ ] Files inside temp directory were still locked by the OS
- [ ] Antivirus or another process interfered with file deletion
- [ ] Temp path permissions are unusual

If Docker containers remain:

- [ ] Container was not started with `--rm`
- [ ] Docker daemon was interrupted during execution
- [ ] Worker process was terminated before Docker cleanup completed
- [ ] Manual local Docker activity is being confused with judge worker containers

If submission stays in `JUDGING`:

- [ ] Worker process crashed before transaction commit
- [ ] Database connection issue prevented final update
- [ ] Exception occurred outside the expected service flow
- [ ] Multiple worker versions are running and interfering

## 7. Recommended manual verification order

1. Check existing temp directories
2. Check existing Docker containers
3. Run one AC submission
4. Confirm final DB status and cleanup
5. Run one CE or RE submission
6. Confirm final DB status and cleanup
7. Run one TLE submission
8. Confirm final DB status and cleanup
9. Stop Docker or misconfigure image to simulate executor failure
10. Confirm `SYSTEM_ERROR`, `finished_at`, and cleanup behavior
