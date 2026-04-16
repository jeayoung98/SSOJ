# Judge Worker Local Run

## Required environment

- `JAVA_HOME`
  - JDK 17 or later is required to run Gradle and Spring Boot locally.
  - Example on Windows:
    - `C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`

## Local dependencies

- PostgreSQL
  - The worker reads `submission`, `problem`, `test_case`, `submission_case_result`.
  - Default connection in `src/main/resources/application.properties`:
    - `spring.datasource.url=jdbc:postgresql://localhost:5432/ssoj`
    - `spring.datasource.username=postgres`
    - `spring.datasource.password=postgres`
- Redis
  - Default connection:
    - `spring.data.redis.host=localhost`
    - `spring.data.redis.port=6379`
  - Queue key:
    - `judge:queue`
- Docker
  - Required because Java and Python executors run user code inside Docker.
  - Current images configured:
    - `eclipse-temurin:17-jdk`
    - `python:3.11`

## Worker run

1. Start PostgreSQL, Redis, and Docker.
2. Make sure the database schema already exists.
3. Set `JAVA_HOME` to JDK 17+.
4. Run the worker:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat bootRun
```

## Queue test

Push a submission id into Redis:

```powershell
redis-cli LPUSH judge:queue 123
```

The worker polls Redis and starts judging when there is an available slot.
Current max concurrency is `2`.

## Validation points

- Redis queue consume
  - Worker log should show `Received submissionId=123 from Redis queue judge:queue`.
- Submission start
  - `submission.status` should change from `PENDING` to `JUDGING`.
  - `submission.started_at` should be filled.
- Test case execution
  - Hidden test cases of the submission's problem should be loaded and executed in order.
  - One `submission_case_result` row should be stored per hidden test case.
- Final result
  - If all test cases pass, `submission.status` should become `AC`.
  - If one test case fails, final status should become the first failure status.
  - `submission.finished_at` should be filled.
- Failure handling
  - If Docker execution fails or an unexpected exception occurs during judging, final status should become `SYSTEM_ERROR`.

## Current scope notes

- Current executors: `java`, `python`
- Current output policy: trim and compare line-by-line
- Current queue consume 방식: polling
- This document does not cover APIs, controller endpoints, or Docker sandbox hardening because they are not implemented here.
