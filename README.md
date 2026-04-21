# SSOJ

SSOJ는 온라인 저지 MVP를 위한 Spring Boot 기반 judge worker 저장소입니다.

현재 이 저장소에는 Next.js Web/API 코드는 포함되어 있지 않습니다. README와 일부 문서에서 Next.js가 언급되는 이유는 전체 서비스 아키텍처에서 submission 생성과 Redis/Cloud Tasks dispatch를 담당하는 상위 Web/API 계층을 전제로 하기 때문입니다.

## 현재 코드 기준 아키텍처

현재 Spring Boot 애플리케이션은 하나의 코드베이스에서 두 역할을 지원합니다.

- `orchestrator`
  - DB에서 submission, problem, hidden testcase를 조회합니다.
  - testcase별 실행을 수행하거나 runner에 위임합니다.
  - 최종 `SubmissionResult`를 결정하고 DB에 저장합니다.
- `runner`
  - HTTP로 단일 실행 요청을 받습니다.
  - C++, Java, Python 코드를 Docker 기반 executor로 실행합니다.
  - 실행 결과만 반환하고 DB/Redis에는 접근하지 않습니다.

## 주요 구성 요소

- Spring Boot
- Spring Data JPA
- PostgreSQL
- Redis
- Cloud Tasks
- Docker 기반 code executor
- Gradle

## 현재 DB 매핑 기준

JPA entity는 기존 Supabase PostgreSQL 스키마를 그대로 따른다는 전제입니다. 애플리케이션이 테이블을 생성하거나 변경하지 않습니다.

주요 테이블:

- `users`
- `problems`
- `problem_examples`
- `problem_testcases`
- `submissions`
- `submission_testcase_results`

중요 ID 타입:

- `problems.id`: `String`
- `users.id`: `UUID`
- `problem_examples.id`: `UUID`
- `problem_testcases.id`: `UUID`
- `submissions.id`: `UUID`
- `submission_testcase_results.id`: `UUID`

상태와 결과는 분리되어 있습니다.

- `SubmissionStatus`: `PENDING`, `JUDGING`, `DONE`
- `SubmissionResult`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`

## 실행 모드

설정 축은 네 가지입니다.

- `worker.role`
  - `orchestrator`
  - `runner`
- `worker.mode`
  - `redis-polling`
  - `http-trigger`
  - `runner`
- `judge.dispatch.mode`
  - `redis`
  - `cloud-tasks`
- `judge.execution.mode`
  - `docker`
  - `remote`

## 로컬 기본 실행

로컬 MVP 검증 기본 조합:

```properties
worker.role=orchestrator
worker.mode=redis-polling
judge.dispatch.mode=redis
judge.execution.mode=docker
```

실행 흐름:

```text
submissionId(UUID) -> Redis judge:queue -> JudgeQueueConsumer -> JudgeService -> DockerExecutionGateway -> DB 저장
```

로컬 실행 예:

```powershell
docker compose up --build
```

또는 로컬 PostgreSQL/Redis/Docker를 직접 준비한 뒤:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis queue payload는 UUID 문자열입니다.

```powershell
redis-cli LPUSH judge:queue 00000000-0000-0000-0000-000000000001
```

## 배포 orchestrator 실행

배포 orchestrator 조합:

```properties
SPRING_PROFILES_ACTIVE=remote
worker.role=orchestrator
worker.mode=http-trigger
judge.dispatch.mode=cloud-tasks
judge.execution.mode=remote
```

실행 흐름:

```text
submissionId(UUID) -> Cloud Tasks -> POST /internal/judge-executions -> JudgeService -> RemoteExecutionGateway -> runner HTTP -> DB 저장
```

필수 환경변수:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JUDGE_EXECUTION_REMOTE_BASE_URL`
- `JUDGE_DISPATCH_CLOUD_TASKS_PROJECT_ID`
- `JUDGE_DISPATCH_CLOUD_TASKS_LOCATION`
- `JUDGE_DISPATCH_CLOUD_TASKS_QUEUE_NAME`
- `JUDGE_DISPATCH_CLOUD_TASKS_TARGET_URL`

## 배포 runner 실행

runner 조합:

```properties
SPRING_PROFILES_ACTIVE=runner
worker.role=runner
judge.execution.mode=docker
```

runner는 다음 endpoint를 제공합니다.

- `POST /internal/runner-executions`

runner 요청 예:

```json
{
  "submissionId": "00000000-0000-0000-0000-000000000001",
  "problemId": "1000",
  "language": "python",
  "sourceCode": "print(1)",
  "input": "",
  "timeLimitMs": 1000,
  "memoryLimitMb": 128
}
```

주의: 현재 runner는 내부에서 `docker run`을 호출합니다. 표준 Cloud Run 환경은 일반적으로 Docker daemon을 제공하지 않으므로, 현재 구현 그대로는 Docker-capable VM 또는 동등한 실행 환경이 더 적합합니다.

## 설정 파일

- `src/main/resources/application.properties`
  - 공통 기본값
  - 기본값은 로컬 검증에 가까운 `orchestrator + redis-polling + redis + docker`
- `src/main/resources/application-local.properties`
  - 로컬 Redis polling + Docker execution
- `src/main/resources/application-remote.properties`
  - 배포 orchestrator
  - Cloud Tasks + remote runner
- `src/main/resources/application-runner.properties`
  - runner 전용
  - DB/JPA/Redis auto-configuration 제외

## 테스트

```powershell
.\gradlew.bat test
```

테스트 전용 보조 파일:

- `src/test/resources/application.properties`
- `src/test/resources/h2-init.sql`

일부 Docker/Testcontainers/real executor 테스트는 system property가 있어야 실행됩니다.

## 관련 문서

먼저 읽을 문서:

- [로컬 실행 코드와 배포 코드 구분 가이드](docs/local-vs-deployment-code-guide.md)
- [문서 최신화 점검 결과](docs/documentation-maintenance-report.md)
- [Judge Worker 모드 설정](docs/worker-mode-configuration.md)
- [배포 준비 체크리스트](docs/deployment-readiness.md)
- [Cloud Run Orchestrator](docs/cloud-run-orchestrator.md)
- [Cloud Run Runner](docs/cloud-run-runner.md)

## 현재 구분의 핵심

현재 구조는 `local = Redis polling + Docker execution`, `remote orchestrator = Cloud Tasks + HTTP trigger + remote runner`, `runner = DB/Redis 없는 실행 전용 서비스`로 구분합니다.
