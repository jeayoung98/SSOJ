# Cloud Run Runner

This service is the execution-only runner role.

Runner responsibilities:

- accept `POST /internal/runner-executions`
- execute code for one request
- return `RunnerExecutionResponse`

Runner does not do any of the following:

- read or write submission status
- load problems or test cases from DB
- decide final judging status
- orchestrate hidden test case loops

The orchestrator remains the source of truth for submission state.

## Active contract

Orchestrator calls the runner at:

- `POST {JUDGE_EXECUTION_REMOTE_BASE_URL}/internal/runner-executions`

Request body:

```json
{
  "submissionId": 123,
  "problemId": 456,
  "language": "python",
  "sourceCode": "print(1)",
  "input": "",
  "timeLimitMs": 1000,
  "memoryLimitMb": 128
}
```

Response body:

```json
{
  "success": true,
  "stdout": "1\n",
  "stderr": "",
  "exitCode": 0,
  "executionTimeMs": 10,
  "memoryUsageKb": 128,
  "systemError": false,
  "timedOut": false
}
```

## Runner configuration

Runner profile:

- `SPRING_PROFILES_ACTIVE=runner`
- `worker.role=runner`
- `judge.execution.mode=docker`

Supported languages:

- `cpp`
- `java`
- `python`

Execution limits:

- timeout is provided per request by `timeLimitMs`
- memory limit is provided per request by `memoryLimitMb`
- CPU limit is currently fixed in the executor implementation to 1 vCPU

Executor image settings:

- `worker.executor.cpp.image`
- `worker.executor.java.image`
- `worker.executor.python.image`

## Exposed endpoints

Runner role should expose only:

- `/internal/runner-executions`
- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

`/internal/judge-executions` is not part of the runner role.

## Required environment variables

Required:

- `SPRING_PROFILES_ACTIVE=runner`

Optional executor settings:

- `WORKER_EXECUTOR_CPP_IMAGE`
- `WORKER_EXECUTOR_JAVA_IMAGE`
- `WORKER_EXECUTOR_PYTHON_IMAGE`

Runtime:

- `PORT`

## Important Cloud Run limitation

The current runner implementation reuses `DockerProcessExecutor`, `JavaExecutor`, `PythonExecutor`, and `CppExecutor`.
Those executors call `docker run` locally.

Standard Cloud Run does not provide a Docker daemon inside the container.
That means the current runner is configuration-ready as a separate service, but it is not execution-ready on standard Cloud Run until the sandbox backend is replaced with a Cloud Run-compatible execution mechanism.

In other words:

- role separation is ready
- endpoint contract is ready
- Cloud Run deployment steps are documentable
- actual code execution will fail on standard Cloud Run with the current Docker-based executor design

## Deploy flow

Build and deploy use the same application image, but with the runner profile:

```powershell
gcloud builds submit --tag asia-northeast3-docker.pkg.dev/PROJECT_ID/REPO/ssoj-runner
```

```powershell
gcloud run deploy ssoj-runner ^
  --image asia-northeast3-docker.pkg.dev/PROJECT_ID/REPO/ssoj-runner ^
  --region asia-northeast3 ^
  --platform managed ^
  --allow-unauthenticated=false ^
  --set-env-vars SPRING_PROFILES_ACTIVE=runner
```

If a Cloud Run-compatible execution backend replaces Docker later, this deploy shape can stay the same.

## Security notes

- The runner should not be publicly callable.
- Only the orchestrator service should be allowed to invoke `/internal/runner-executions`.
- Prefer authenticated service-to-service calls with a dedicated caller identity.
- Because the runner executes untrusted code, keep ingress restricted as tightly as possible.
