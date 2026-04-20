# Cloud Run 오케스트레이터

이 서비스는 Cloud Run에서 오케스트레이터 역할로 실행될 수 있습니다.

원격 오케스트레이터 모드는 다음과 같은 고정 조합입니다.

- `worker.role=orchestrator`
- `worker.mode=http-trigger`
- `judge.dispatch.mode=cloud-tasks`
- `judge.execution.mode=remote`

Redis 폴링이 꺼져 있는 이유:

- `JudgeQueueConsumer`는 `worker.mode=redis-polling`일 때만 생성됩니다.
- 원격 오케스트레이터는 `worker.mode=http-trigger`를 사용하므로 Cloud Run에서 Redis 폴링이 시작되지 않습니다.
- Redis 시작 체크도 `judge.dispatch.mode=redis`일 때만 실행되므로 함께 건너뜁니다.

## 필수 환경 변수

데이터베이스:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

원격 러너:

- `JUDGE_EXECUTION_REMOTE_BASE_URL`

Cloud Tasks:

- `JUDGE_DISPATCH_CLOUD_TASKS_PROJECT_ID`
- `JUDGE_DISPATCH_CLOUD_TASKS_LOCATION`
- `JUDGE_DISPATCH_CLOUD_TASKS_QUEUE_NAME`
- `JUDGE_DISPATCH_CLOUD_TASKS_TARGET_URL`

선택적 Cloud Tasks 인증:

- `JUDGE_DISPATCH_CLOUD_TASKS_SERVICE_ACCOUNT_EMAIL`
- `JUDGE_DISPATCH_CLOUD_TASKS_OIDC_AUDIENCE`

런타임:

- `SPRING_PROFILES_ACTIVE=remote`
- `PORT`

## Dockerfile

기존 [Dockerfile](/C:/Users/user/OneDrive/Desktop/JeaYoung/프로젝트/SSOJ/SSOJ/Dockerfile)은 이미 Cloud Run에서 동작 가능한 형태입니다.

- Spring Boot jar를 빌드합니다.
- `8080` 포트를 노출합니다.
- `java -jar /app/app.jar`로 애플리케이션을 시작합니다.

애플리케이션은 `server.port=${PORT:8080}` 설정을 통해 Cloud Run의 `PORT` 값을 읽습니다.

## 배포 흐름

1. 이미지를 빌드하고 푸시합니다.

```powershell
gcloud builds submit --tag asia-northeast3-docker.pkg.dev/PROJECT_ID/REPO/ssoj-orchestrator
```

2. Cloud Run 서비스를 배포합니다.

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

3. Cloud Tasks 호출용 서비스 계정에 오케스트레이터 Cloud Run 서비스를 호출할 권한을 부여합니다.

## 헬스 체크 및 내부 엔드포인트

헬스 체크:

- liveness: `/actuator/health/liveness`
- readiness: `/actuator/health/readiness`
- 기본 헬스 체크: `/actuator/health`

내부 엔드포인트:

- `/internal/judge-executions`: 오케스트레이터 내부 비동기 트리거 진입점
- `/internal/runner-executions`: 러너 내부 실행 진입점이며, 오케스트레이터 역할에서는 제공되지 않음

보안 메모:

- Cloud Run 오케스트레이터는 `/internal/judge-executions`에 대한 공개 접근을 허용하면 안 됩니다.
- Cloud Tasks는 전용 서비스 계정의 OIDC 토큰을 사용해 이 엔드포인트를 호출해야 합니다.
- 러너 기본 URL은 내부 호출 또는 인증된 호출만 허용하는 러너 서비스를 가리켜야 합니다.
