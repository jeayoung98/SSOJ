# Cloud Run Orchestrator

This service can run on Cloud Run as the orchestrator role.

Remote orchestrator mode is the following fixed combination:

- `worker.role=orchestrator`
- `worker.mode=http-trigger`
- `judge.dispatch.mode=cloud-tasks`
- `judge.execution.mode=remote`

Why Redis polling is off:

- `JudgeQueueConsumer` is only created when `worker.mode=redis-polling`.
- Remote orchestrator uses `worker.mode=http-trigger`, so Redis polling never starts on Cloud Run.
- The Redis startup check is also skipped because it only runs when `judge.dispatch.mode=redis`.

## Required environment variables

Database:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

Remote runner:

- `JUDGE_EXECUTION_REMOTE_BASE_URL`

Cloud Tasks:

- `JUDGE_DISPATCH_CLOUD_TASKS_PROJECT_ID`
- `JUDGE_DISPATCH_CLOUD_TASKS_LOCATION`
- `JUDGE_DISPATCH_CLOUD_TASKS_QUEUE_NAME`
- `JUDGE_DISPATCH_CLOUD_TASKS_TARGET_URL`

Optional Cloud Tasks auth:

- `JUDGE_DISPATCH_CLOUD_TASKS_SERVICE_ACCOUNT_EMAIL`
- `JUDGE_DISPATCH_CLOUD_TASKS_OIDC_AUDIENCE`

Runtime:

- `SPRING_PROFILES_ACTIVE=remote`
- `PORT`

## Dockerfile

The existing [Dockerfile](/C:/Users/user/OneDrive/Desktop/JeaYoung/프로젝트/SSOJ/SSOJ/Dockerfile) already works for Cloud Run:

- builds the Spring Boot jar
- exposes `8080`
- starts `java -jar /app/app.jar`

The app reads Cloud Run's `PORT` through `server.port=${PORT:8080}`.

## Deploy flow

1. Build and push the image.

```powershell
gcloud builds submit --tag asia-northeast3-docker.pkg.dev/PROJECT_ID/REPO/ssoj-orchestrator
```

2. Deploy the Cloud Run service.

```powershell
gcloud run deploy ssoj-orchestrator ^
  --image asia-northeast3-docker.pkg.dev/PROJECT_ID/REPO/ssoj-orchestrator ^
  --region asia-northeast3 ^
  --platform managed ^
  --allow-unauthenticated=false ^
  --set-env-vars SPRING_PROFILES_ACTIVE=remote ^
  --set-env-vars SPRING_DATASOURCE_URL=jdbc:postgresql://DB_HOST:5432/ssoj ^
  --set-env-vars SPRING_DATASOURCE_USERNAME=postgres ^
  --set-env-vars SPRING_DATASOURCE_PASSWORD=postgres ^
  --set-env-vars JUDGE_EXECUTION_REMOTE_BASE_URL=https://RUNNER_URL ^
  --set-env-vars JUDGE_DISPATCH_CLOUD_TASKS_PROJECT_ID=PROJECT_ID ^
  --set-env-vars JUDGE_DISPATCH_CLOUD_TASKS_LOCATION=asia-northeast3 ^
  --set-env-vars JUDGE_DISPATCH_CLOUD_TASKS_QUEUE_NAME=ssoj-judge ^
  --set-env-vars JUDGE_DISPATCH_CLOUD_TASKS_TARGET_URL=https://ORCHESTRATOR_URL/internal/judge-executions ^
  --set-env-vars JUDGE_DISPATCH_CLOUD_TASKS_SERVICE_ACCOUNT_EMAIL=cloud-tasks-invoker@PROJECT_ID.iam.gserviceaccount.com ^
  --set-env-vars JUDGE_DISPATCH_CLOUD_TASKS_OIDC_AUDIENCE=https://ORCHESTRATOR_URL
```

3. Grant the Cloud Tasks caller service account permission to invoke the orchestrator Cloud Run service.

## Health and internal endpoints

Health checks:

- liveness: `/actuator/health/liveness`
- readiness: `/actuator/health/readiness`
- basic health: `/actuator/health`

Internal endpoints:

- `/internal/judge-executions`: orchestrator internal async trigger entrypoint
- `/internal/runner-executions`: runner internal execution entrypoint, not served by orchestrator role

Security notes:

- Cloud Run orchestrator should not allow public access to `/internal/judge-executions`.
- Cloud Tasks should call it with an OIDC token using a dedicated service account.
- The runner base URL should point to a runner service that is also restricted to internal or authenticated calls.
