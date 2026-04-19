# Deployment Readiness

This document is the final deployment and verification checklist for the current project state.
It is intentionally conservative.
It only describes what is implemented now, what is operational now, and what still needs care.

## 1. One-page architecture

Local Docker verification path:

`submissionId -> Redis queue -> JudgeQueueConsumer -> JudgeService -> local Docker executors -> DB status update`

Remote operating path:

`submissionId -> Cloud Tasks -> /internal/judge-executions -> JudgeService -> remote runner HTTP -> execution result -> DB status update`

Role split:

- `orchestrator`
  - owns DB-backed judging flow
  - loads hidden test cases
  - loops through hidden test cases
  - calls runner for each case
  - decides final status
  - saves submission and case results
- `runner`
  - accepts one execution request
  - executes code for one case
  - returns execution result only
  - does not read or write submission state

Infra split:

- DB: PostgreSQL
- local queue: Redis
- remote async trigger: Cloud Tasks
- local execution: Docker-based executors
- remote execution contract: HTTP `POST /internal/runner-executions`

## 2. Current state summary

Implemented:

- local Redis polling + local Docker execution path
- remote orchestrator mode
- Cloud Tasks dispatch implementation
- remote runner HTTP contract
- orchestrator to runner remote execution flow
- local end-to-end verification for orchestrator -> runner HTTP path

Operational now:

- local Docker verification path on one machine
- orchestrator deployment on Cloud Run
- Cloud Tasks calling orchestrator internal HTTP endpoint

Not yet operational as fully described on standard Cloud Run:

- runner code execution on Cloud Run with the current Docker-based executor design

Still needs care:

- sandbox hardening is still MVP-level only
- runner ingress and service-to-service auth must be restricted
- Cloud Tasks service account permissions must be configured correctly
- production DB sizing, backups, and migrations need separate operations planning

## 3. Recommended starting combination for minimum cost

Start with this combination:

- orchestrator on Cloud Run
- PostgreSQL on a managed DB
- Cloud Tasks for async trigger
- runner on a single VM or other Docker-capable host

Reason:

- orchestrator already matches Cloud Run well
- Cloud Tasks fits the remote orchestration model
- current runner implementation still depends on local `docker run`
- putting the runner on standard Cloud Run now will not actually execute code successfully

Do not start with this if you need real execution immediately:

- orchestrator on Cloud Run
- runner on standard Cloud Run

That combination is configuration-ready but not execution-ready with the current code.

## 4. Environment variables

### Shared runtime

| Variable | Required | Used by | Notes |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Yes | orchestrator / runner | `remote` for orchestrator, `runner` for runner |
| `PORT` | Cloud Run injects | orchestrator / runner | app reads `server.port=${PORT:8080}` |

### Database

| Variable | Required | Used by | Notes |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL` | Yes for orchestrator | orchestrator | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes for orchestrator | orchestrator | DB user |
| `SPRING_DATASOURCE_PASSWORD` | Yes for orchestrator | orchestrator | DB password |

### Local queue only

| Variable | Required | Used by | Notes |
| --- | --- | --- | --- |
| `SPRING_DATA_REDIS_HOST` | Yes for local mode | local orchestrator | not needed for remote Cloud Run mode |
| `SPRING_DATA_REDIS_PORT` | Yes for local mode | local orchestrator | not needed for remote Cloud Run mode |

### Remote runner contract

| Variable | Required | Used by | Notes |
| --- | --- | --- | --- |
| `JUDGE_EXECUTION_REMOTE_BASE_URL` | Yes for remote orchestrator | orchestrator | base URL of runner service |

Runner base URL contract:

- orchestrator calls `POST {JUDGE_EXECUTION_REMOTE_BASE_URL}/internal/runner-executions`
- runner returns `RunnerExecutionResponse`

### Cloud Tasks

| Variable | Required | Used by | Notes |
| --- | --- | --- | --- |
| `JUDGE_DISPATCH_CLOUD_TASKS_PROJECT_ID` | Yes for remote orchestrator | orchestrator | GCP project id |
| `JUDGE_DISPATCH_CLOUD_TASKS_LOCATION` | Yes for remote orchestrator | orchestrator | Cloud Tasks region |
| `JUDGE_DISPATCH_CLOUD_TASKS_QUEUE_NAME` | Yes for remote orchestrator | orchestrator | queue name |
| `JUDGE_DISPATCH_CLOUD_TASKS_TARGET_URL` | Yes for remote orchestrator | orchestrator | usually `https://ORCHESTRATOR_URL/internal/judge-executions` |
| `JUDGE_DISPATCH_CLOUD_TASKS_SERVICE_ACCOUNT_EMAIL` | Optional but recommended | orchestrator | caller identity for OIDC-authenticated task |
| `JUDGE_DISPATCH_CLOUD_TASKS_OIDC_AUDIENCE` | Optional but recommended | orchestrator | usually orchestrator base URL |

### Runner executor settings

| Variable | Required | Used by | Notes |
| --- | --- | --- | --- |
| `WORKER_EXECUTOR_CPP_IMAGE` | Optional | runner | overrides default C++ image |
| `WORKER_EXECUTOR_JAVA_IMAGE` | Optional | runner | overrides default Java image |
| `WORKER_EXECUTOR_PYTHON_IMAGE` | Optional | runner | overrides default Python image |

Execution limit source of truth:

- `timeLimitMs`: request field from orchestrator
- `memoryLimitMb`: request field from orchestrator
- CPU: fixed to 1 vCPU in current executor implementation

## 5. Local Docker verification path

Use this path when validating MVP behavior on one machine.

Components:

- PostgreSQL
- Redis
- Spring app with `local` profile or equivalent local settings
- local Docker daemon

Flow:

- submission is persisted first
- `submissionId` goes into Redis `judge:queue`
- `JudgeQueueConsumer` polls Redis
- local Docker executors run code
- final state is written to DB

Checklist:

1. Start PostgreSQL.
2. Start Redis.
3. Ensure local Docker is available.
4. Run the application with local profile.
5. Create problem, hidden test cases, and a pending submission.
6. Push `submissionId` to Redis queue or trigger through the app path that enqueues it.
7. Verify:
   - submission moves `PENDING -> JUDGING -> final status`
   - `submission_case_result` rows are created
   - executor logs show Docker execution

Reference:

- [local-orchestrator-runner.md](/C:/Users/user/OneDrive/Desktop/JeaYoung/프로젝트/SSOJ/SSOJ/docs/local-orchestrator-runner.md)

## 6. Remote operating path

Use this path for Cloud Run-style orchestration.

Components:

- orchestrator service
- runner service
- PostgreSQL
- Cloud Tasks

Flow:

1. upstream app persists submission
2. orchestrator dispatches `submissionId` through Cloud Tasks
3. Cloud Tasks calls orchestrator `POST /internal/judge-executions`
4. orchestrator loads submission and hidden test cases from DB
5. orchestrator calls runner for each hidden test case
6. runner returns execution result for each case
7. orchestrator decides final status and saves DB results

References:

- [cloud-run-orchestrator.md](/C:/Users/user/OneDrive/Desktop/JeaYoung/프로젝트/SSOJ/SSOJ/docs/cloud-run-orchestrator.md)
- [cloud-run-runner.md](/C:/Users/user/OneDrive/Desktop/JeaYoung/프로젝트/SSOJ/SSOJ/docs/cloud-run-runner.md)
- [cloud-tasks-dispatch.md](/C:/Users/user/OneDrive/Desktop/JeaYoung/프로젝트/SSOJ/SSOJ/docs/cloud-tasks-dispatch.md)

## 7. Deployment checklist

### A. Runner deployment

Use this checklist only if the runner host can actually execute Docker-based workloads.

1. Decide the runner host.
   - recommended now: Docker-capable VM or equivalent host
   - not recommended now: standard Cloud Run for real execution
2. Build and deploy the runner image.
3. Run with `SPRING_PROFILES_ACTIVE=runner`.
4. Restrict ingress so only orchestrator can call it.
5. Verify `/actuator/health`.
6. Verify `POST /internal/runner-executions` with a simple request.
7. Confirm Docker execution actually works on the host.

### B. Orchestrator deployment

1. Build and deploy the orchestrator image.
2. Run with `SPRING_PROFILES_ACTIVE=remote`.
3. Set DB environment variables.
4. Set `JUDGE_EXECUTION_REMOTE_BASE_URL`.
5. Set Cloud Tasks environment variables.
6. Confirm `worker.mode=http-trigger`.
7. Confirm `judge.dispatch.mode=cloud-tasks`.
8. Confirm `judge.execution.mode=remote`.
9. Verify `/actuator/health/readiness`.
10. Verify Redis polling is not active in logs.

### C. Cloud Tasks setup

1. Create the queue in the target region.
2. Set `JUDGE_DISPATCH_CLOUD_TASKS_PROJECT_ID`.
3. Set `JUDGE_DISPATCH_CLOUD_TASKS_LOCATION`.
4. Set `JUDGE_DISPATCH_CLOUD_TASKS_QUEUE_NAME`.
5. Set `JUDGE_DISPATCH_CLOUD_TASKS_TARGET_URL` to orchestrator `/internal/judge-executions`.
6. Configure OIDC caller settings if using authenticated invocation.

### D. Internal invocation permissions

1. Make orchestrator non-public if possible.
2. Give Cloud Tasks caller identity permission to invoke orchestrator.
3. Make runner non-public if possible.
4. Allow only orchestrator identity to invoke runner.
5. Verify requests to internal endpoints fail from unauthorized callers.

### E. Final end-to-end verification

1. Insert or submit a real pending submission in the DB through the normal app path.
2. Trigger dispatch so Cloud Tasks enqueues the job.
3. Confirm Cloud Tasks creates and delivers the request.
4. Confirm orchestrator receives `/internal/judge-executions`.
5. Confirm orchestrator loads hidden test cases and calls runner per case.
6. Confirm runner returns execution results.
7. Confirm submission status and case results are written to DB.
8. Confirm failure paths work for at least one unsupported language or system error case.

## 8. Operating status matrix

| Area | Implemented | Operational now | Notes |
| --- | --- | --- | --- |
| Local Redis polling path | Yes | Yes | requires Redis, PostgreSQL, Docker |
| Local Docker execution | Yes | Yes | current baseline verification path |
| Remote orchestrator profile | Yes | Yes | good fit for Cloud Run |
| Cloud Tasks dispatch | Yes | Yes | requires GCP IAM and queue setup |
| Remote runner HTTP contract | Yes | Yes | orchestrator contract is stable |
| Runner execution on Docker-capable host | Yes | Yes | VM or similar host is suitable |
| Runner execution on standard Cloud Run | Yes in config only | No | current executors call local Docker |
| Strong sandbox hardening | No | No | do not describe current sandbox as hardened |

## 9. Security notes

- `/internal/judge-executions` is an internal orchestrator endpoint, not a public API.
- `/internal/runner-executions` is an internal runner endpoint, not a public API.
- Cloud Tasks should call orchestrator with a dedicated service account.
- Orchestrator should call runner with a restricted service identity or internal ingress.
- The current sandbox is still MVP-level and should not be described as strongly hardened.

## 10. Practical go-live recommendation

If you need the cheapest path that can actually run today:

- orchestrator on Cloud Run
- PostgreSQL managed externally
- Cloud Tasks for async trigger
- runner on a single small Docker-capable VM

If you want both services on Cloud Run:

- orchestrator is fine
- runner still needs an execution backend rewrite away from local Docker

That is the main remaining gap between the current codebase and a fully Cloud Run-native judge runtime.
