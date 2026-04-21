# 로컬 실행 코드와 배포 코드 구분 가이드

## 1. 문서 목적

이 문서는 SSOJ 저장소를 다시 볼 때 다음 질문에 바로 답하기 위한 기준 문서다.

- 어떤 코드는 로컬 개발/테스트용인가
- 어떤 코드는 실제 배포 환경에서 사용하는가
- 어떤 코드는 로컬과 배포가 함께 쓰는 공통 코드인가
- 실행할 때 어떤 profile과 설정 조합을 써야 하는가

현재 프로젝트는 하나의 Spring Boot 코드베이스 안에서 `orchestrator`와 `runner` 역할을 profile과 property로 나누고 있다. 또한 로컬 검증 경로는 Redis polling과 로컬 Docker executor를 사용하지만, 배포 경로는 Cloud Tasks, HTTP trigger, remote runner 조합을 전제로 한다. 이 구분을 명확히 하지 않으면 로컬에서만 필요한 Redis/Docker 설정을 운영 경로로 착각하거나, 배포용 profile을 로컬 MVP 검증 경로로 오해하기 쉽다.

이 문서는 새 아키텍처를 제안하는 문서가 아니라, 현재 저장소에 존재하는 코드와 설정을 기준으로 로컬용/배포용/공통 영역을 분류한다.

## 2. 기존 문서 상태와 판단

먼저 확인한 문서:

- `README.md`
- `docs/worker-mode-configuration.md`
- `docs/local-orchestrator-runner.md`
- `docs/cloud-run-orchestrator.md`
- `docs/cloud-run-runner.md`
- `docs/cloud-tasks-dispatch.md`
- `docs/deployment-readiness.md`
- `docs/current-vs-target-architecture.md`
- 그 외 `docs/judge-*`, `docs/worker-*`, `docs/nextjs-*` 문서 목록

기존 문서의 역할:

- `README.md`: 프로젝트 개요와 로컬 실행 흐름을 담고 있으나, 현재 일부 내용은 인코딩이 깨져 보이고 예전 단수형 테이블명/상태 enum 설명이 남아 있다.
- `docs/worker-mode-configuration.md`: `worker.mode`, `judge.dispatch.mode`, `judge.execution.mode` 조합을 설명한다.
- `docs/local-orchestrator-runner.md`: 로컬에서 orchestrator와 runner를 분리 실행하는 예시를 제공한다.
- `docs/cloud-run-orchestrator.md`: Cloud Run orchestrator 배포 설정과 환경변수를 설명한다.
- `docs/cloud-run-runner.md`: runner 역할과 Cloud Run 배포 시 주의점을 설명한다.
- `docs/deployment-readiness.md`: 배포 가능 상태, 운영 권장 조합, 환경변수, 체크리스트를 정리한다.
- `docs/current-vs-target-architecture.md`: 현재 구현 구조와 검토 중인 목표 구조를 비교한다.

판단:

- 기존 문서들은 특정 실행 방식 또는 배포 방식의 세부 설명에 가깝다.
- 이 문서의 목적은 저장소 전체를 기준으로 로컬용/배포용/공통 코드를 한 장에서 구분하는 것이다.
- 따라서 기존 문서를 수정하기보다 새 문서를 추가하는 것이 적절하다.
- 단, 이 문서는 기존 문서의 세부 내용을 대체하지 않는다. 세부 배포 절차는 `cloud-run-*`, `deployment-readiness.md`를 계속 참조한다.

## 3. 현재 프로젝트 구조 요약

### 3.1 최상위 파일

| 경로 | 분류 | 역할 |
| --- | --- | --- |
| `build.gradle` | 공통 | Spring Boot, JPA, Redis, Cloud Tasks, Web, Testcontainers 등 전체 빌드 의존성 정의 |
| `settings.gradle` | 공통 | Gradle 프로젝트 이름 설정 |
| `Dockerfile` | 배포용 + 로컬 컨테이너 검증용 | Spring Boot jar 이미지를 빌드하고 실행한다. Docker CLI도 설치한다. |
| `docker-compose.yml` | 로컬 실행/검증용 | 로컬 PostgreSQL, Redis, judge-worker 컨테이너를 함께 띄운다. |
| `README.md` | 문서 | 프로젝트 개요와 실행 흐름 설명. 현재 기준 문서로 쓰기에는 일부 내용이 오래되었거나 깨져 보인다. |
| `AGENTS.md` | 개발 지침 | Codex/작업자용 프로젝트 지침. 런타임 코드가 아니다. |

현재 저장소 기준으로 별도 최상위 `config`, `worker`, `orchestrator` 디렉토리는 확인되지 않았다. worker/orchestrator 구분은 디렉토리명이 아니라 `src/main/java/com/example/ssoj/judge` 내부 코드와 `worker.role`, `worker.mode` 설정 조합으로 나뉜다. README와 AGENTS에는 Next.js Web/API 역할이 언급되어 있지만, 이 저장소 안에서는 Next.js 소스 디렉토리가 확인되지 않았다. 따라서 이 문서에서 Next.js 코드의 로컬/배포 구분은 확실하지 않음으로 본다.

### 3.2 `src/main/java`

| 경로 | 분류 | 역할 |
| --- | --- | --- |
| `com.example.ssoj.SsojApplication` | 공통 + profile 조건부 | Spring Boot 진입점. orchestrator일 때 DB 연결 체크, Redis dispatch 모드일 때 Redis 연결 체크를 조건부 실행한다. |
| `judge/domain/model` | 공통 | 채점 흐름에서 공유하는 command, context, result record 정의 |
| `judge/application/port` | 공통 | 실행 gateway, dispatch port, remote execution client 인터페이스 |
| `judge/application/sevice/JudgeService.java` | 배포/로컬 공통 orchestrator 코드 | 채점 루프, 출력 비교, 최종 결과 결정 |
| `judge/application/sevice/JudgePersistenceService.java` | 배포/로컬 공통 orchestrator 코드 | DB에서 submission/testcase를 읽고 결과를 저장 |
| `judge/application/sevice/JudgeQueueConsumer.java` | 로컬 기본 경로 | `worker.mode=redis-polling`에서 Redis `judge:queue`를 polling |
| `judge/application/sevice/RunnerExecutionService.java` | runner 코드 | `worker.role=runner`에서 단일 실행 요청 처리 |
| `judge/application/selector` | runner 코드 | runner에서 language executor 선택 |
| `judge/executor` | 로컬 실행 + runner 실행 공통 | `CppExecutor`, `JavaExecutor`, `PythonExecutor`, `DockerProcessExecutor`가 Docker 기반 실행 담당 |
| `judge/infrastructure/docker` | 로컬 기본 실행 + runner 실행 | `judge.execution.mode=docker`에서 Docker executor gateway 활성화 |
| `judge/infrastructure/redis` | 로컬 dispatch | `judge.dispatch.mode=redis`에서 Redis queue에 `submissionId` push |
| `judge/infrastructure/cloudtasks` | 배포용 dispatch | `judge.dispatch.mode=cloud-tasks`에서 Cloud Tasks HTTP task 생성 |
| `judge/infrastructure/remote` | 배포용 orchestrator 실행 경로 | `judge.execution.mode=remote`에서 remote runner HTTP 호출 |
| `judge/infrastructure/config` | 배포용 설정 바인딩 | Cloud Tasks 관련 property 바인딩 |
| `judge/presentation/JudgeExecutionController.java` | 배포용 orchestrator endpoint | `worker.mode=http-trigger`에서 `/internal/judge-executions` 제공 |
| `judge/presentation/RunnerExecutionController.java` | runner endpoint | `worker.role=runner`에서 `/internal/runner-executions` 제공 |
| `problem/domain`, `submission/domain`, `testcase/domain`, `user/domain` | 공통 DB 매핑 | Supabase PostgreSQL 기존 테이블에 맞춘 JPA entity |
| `problem/infrastructure`, `submission/infrastructure`, `testcase/infrastructure`, `user/infrastructure` | 공통 repository | JPA repository. orchestrator에서 DB 접근 시 사용 |

주의: 현재 패키지명이 `judge/application/sevice`로 되어 있다. 철자는 `service`가 아니라 `sevice`이며, 실제 코드 기준으로는 이 경로가 맞다.

### 3.3 `src/main/resources`

| 파일 | 분류 | 역할 |
| --- | --- | --- |
| `application.properties` | 공통 기본값 | 기본 DB/Redis/worker/dispatch/execution 설정. 기본 조합은 orchestrator + Redis polling + Docker execution |
| `application-local.properties` | 로컬 검증용 | Redis polling + Redis dispatch + Docker execution 조합 |
| `application-remote.properties` | 배포용 orchestrator | HTTP trigger + Cloud Tasks dispatch + remote runner execution 조합 |
| `application-runner.properties` | 배포/로컬 runner | runner 역할. DB/JPA/Redis auto-configuration 제외 |

### 3.4 `src/test`

| 경로 | 분류 | 역할 |
| --- | --- | --- |
| `src/test/java` | 로컬 테스트/검증용 | 단위 테스트, 통합 테스트, profile 검증, executor 테스트 |
| `src/test/resources/application.properties` | 테스트용 | 테스트 기본 datasource 설정 |
| `src/test/resources/h2-init.sql` | 테스트용 | H2에서 PostgreSQL enum 타입명을 domain으로 흉내 내기 위한 테스트 전용 SQL |

`src/test` 아래 코드는 운영 런타임에 포함하지 않는다. Testcontainers, H2, mock Redis, fake executor 등은 로컬 검증을 위한 장치다.

### 3.5 `docs`

| 문서 | 분류 | 역할 |
| --- | --- | --- |
| `worker-mode-configuration.md` | 설정 설명 | mode/profile 조합 설명 |
| `local-orchestrator-runner.md` | 로컬 실행 | 로컬에서 orchestrator/runner를 나눠 띄우는 예시 |
| `cloud-run-orchestrator.md` | 배포 | Cloud Run orchestrator 설정 |
| `cloud-run-runner.md` | 배포 | runner 역할과 Cloud Run 제약 설명 |
| `cloud-tasks-dispatch.md` | 배포 | Cloud Tasks dispatch 설명 |
| `deployment-readiness.md` | 배포 판단 | 운영 가능/불가능 영역과 체크리스트 |
| `current-vs-target-architecture.md` | 아키텍처 비교 | 현재 구조와 검토 중인 구조 비교 |
| `judge-*`, `worker-*` 문서 | 검증/개선 | worker 동작, cleanup, concurrency, E2E 검증 등 세부 문서 |

## 4. 로컬에서 사용하는 코드/설정

### 4.1 로컬 기본 실행 조합

로컬 MVP 검증의 기본 조합은 다음이다.

```properties
worker.role=orchestrator
worker.mode=redis-polling
judge.dispatch.mode=redis
judge.execution.mode=docker
```

이 조합은 `application.properties`의 기본값과 `application-local.properties`가 가리키는 경로다.

실행 흐름:

```text
submissionId -> Redis judge:queue -> JudgeQueueConsumer -> JudgeService -> DockerExecutionGateway -> DockerProcessExecutor -> DB 저장
```

로컬용으로 분류하는 근거:

- `JudgeQueueConsumer`는 Redis queue를 직접 polling한다.
- `RedisJudgeDispatchService`는 Redis List에 `submissionId`를 넣는다.
- `DockerExecutionGateway`와 언어별 executor는 현재 머신의 Docker daemon을 사용한다.
- `docker-compose.yml`은 PostgreSQL/Redis/judge-worker를 로컬 컨테이너로 묶어 실행한다.

### 4.2 로컬 설정 파일

`src/main/resources/application-local.properties`

```properties
worker.mode=redis-polling
judge.dispatch.mode=redis
judge.dispatch.redis.queue-key=judge:queue
judge.execution.mode=docker
```

사용 예:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

### 4.3 로컬 인프라

`docker-compose.yml`은 로컬 실행/검증용으로 보는 것이 맞다.

포함 서비스:

- `postgres`: 로컬 PostgreSQL `5432`
- `redis`: 로컬 Redis `6379`
- `judge-worker`: 현재 Dockerfile로 빌드한 Spring Boot worker

주의:

- `judge-worker`는 `/var/run/docker.sock`을 mount한다.
- 이는 worker 컨테이너 안에서 host Docker daemon을 호출하기 위한 설정이다.
- 운영 배포에도 같은 방식으로 쓸 수 있는지는 배포 환경에 따라 다르며, 특히 표준 Cloud Run에서는 일반적으로 Docker daemon을 제공하지 않는다.

### 4.4 로컬 테스트 코드

다음 파일들은 로컬 테스트/검증용이다.

- `JudgeServiceTest`
- `JudgeQueueConsumerTest`
- `JudgePipelineIntegrationTest`
- `OrchestratorRemoteRunnerIntegrationTest`
- `RunnerExecutionServiceTest`
- `RunnerExecutionControllerTest`
- `CloudTasksJudgeDispatchServiceTest`
- `RedisJudgeDispatchServiceTest`
- `CppExecutorTest`
- `PythonExecutorTest`
- `DockerProcessExecutorTest`
- `RealLanguageExecutorTest`
- `RunnerProfileIsolationTest`
- `SsojApplicationTests`

분류 근거:

- H2, mock, fake executor, Testcontainers, local Docker 조건부 테스트를 사용한다.
- `src/test/resources/h2-init.sql`은 H2 테스트 DB에서 PostgreSQL enum 타입명을 흉내 내기 위한 파일이다.
- 운영 배포 시 이 파일들은 런타임에 사용되지 않는다.

### 4.5 로컬 전용 또는 로컬 중심 코드

로컬 중심 코드:

- `JudgeQueueConsumer`
- `RedisJudgeDispatchService`
- `DockerExecutionGateway`
- `DockerProcessExecutor`
- `CppExecutor`
- `JavaExecutor`
- `PythonExecutor`
- `WorkspaceDirectoryFactory`

단, 언어별 executor와 `DockerProcessExecutor`는 runner profile에서도 사용된다. 따라서 “로컬 전용”이라기보다 “Docker 기반 실행 경로”로 분류하는 것이 정확하다.

## 5. 배포 시 사용하는 코드/설정

### 5.1 배포 orchestrator 조합

배포 orchestrator의 의도된 조합은 `application-remote.properties`에 있다.

```properties
worker.role=orchestrator
worker.mode=http-trigger
judge.dispatch.mode=cloud-tasks
judge.execution.mode=remote
```

실행 흐름:

```text
submissionId -> Cloud Tasks -> POST /internal/judge-executions -> JudgeService -> RemoteExecutionGateway -> runner HTTP -> DB 저장
```

배포용으로 분류하는 근거:

- `worker.mode=http-trigger`는 Redis polling 대신 HTTP endpoint를 활성화한다.
- `judge.dispatch.mode=cloud-tasks`는 Cloud Tasks를 비동기 trigger로 사용한다.
- `judge.execution.mode=remote`는 현재 프로세스에서 Docker 실행을 하지 않고 runner service로 위임한다.
- `cloud-run-orchestrator.md`와 `deployment-readiness.md`가 이 조합을 Cloud Run orchestrator 경로로 설명한다.

### 5.2 배포 runner 조합

runner profile은 `application-runner.properties`에 있다.

```properties
worker.role=runner
worker.mode=runner
judge.execution.mode=docker
spring.autoconfigure.exclude=...
```

runner 역할:

- `/internal/runner-executions` 요청을 받는다.
- 한 번의 요청에 대해 한 테스트케이스 실행만 처리한다.
- DB를 읽거나 쓰지 않는다.
- Redis를 사용하지 않는다.
- 결과를 `RunnerExecutionResponse`로 반환한다.

배포용으로 분류하는 근거:

- `RunnerExecutionController`와 `RunnerExecutionService`는 `worker.role=runner`에서만 활성화된다.
- `application-runner.properties`는 DB/JPA/Redis auto-configuration을 제외해 실행 전용 서비스로 띄운다.
- `cloud-run-runner.md`는 runner를 별도 서비스로 배포하는 방식을 설명한다.

중요한 제한:

- 현재 runner 구현은 내부에서 `docker run`을 호출한다.
- 표준 Cloud Run 컨테이너 안에서는 일반적으로 Docker daemon을 사용할 수 없다.
- 따라서 runner를 Cloud Run에 배포하는 설정은 문서화되어 있지만, 현재 Docker 기반 executor 그대로는 표준 Cloud Run에서 실제 코드 실행이 불가능할 수 있다.
- `deployment-readiness.md` 기준으로는 Docker-capable VM 또는 Docker 실행이 가능한 별도 host가 현재 runner 실행에 더 적합하다.

### 5.3 배포용 인프라 코드

배포용으로 보는 코드:

- `CloudTasksJudgeDispatchService`
- `GoogleCloudTasksGateway`
- `CloudTasksClientFactory`
- `CloudTasksGateway`
- `CloudTasksDispatchProperties`
- `RemoteExecutionGateway`
- `HttpRemoteExecutionClient`
- `JudgeExecutionController`
- `RunnerExecutionController`

근거:

- Cloud Tasks, remote HTTP runner, internal HTTP endpoint는 로컬 Redis polling 경로가 아니라 배포/원격 실행 경로를 위한 구성이다.

### 5.4 배포용 환경변수

공통:

- `SPRING_PROFILES_ACTIVE`
- `PORT`

orchestrator DB:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

remote runner:

- `JUDGE_EXECUTION_REMOTE_BASE_URL`

Cloud Tasks:

- `JUDGE_DISPATCH_CLOUD_TASKS_PROJECT_ID`
- `JUDGE_DISPATCH_CLOUD_TASKS_LOCATION`
- `JUDGE_DISPATCH_CLOUD_TASKS_QUEUE_NAME`
- `JUDGE_DISPATCH_CLOUD_TASKS_TARGET_URL`
- `JUDGE_DISPATCH_CLOUD_TASKS_SERVICE_ACCOUNT_EMAIL`
- `JUDGE_DISPATCH_CLOUD_TASKS_OIDC_AUDIENCE`

runner executor image override:

- `WORKER_EXECUTOR_CPP_IMAGE`
- `WORKER_EXECUTOR_JAVA_IMAGE`
- `WORKER_EXECUTOR_PYTHON_IMAGE`

### 5.5 Dockerfile의 위치

`Dockerfile`은 배포 이미지 빌드에 사용 가능하다.

특징:

- build stage에서 `./gradlew bootJar` 실행
- runtime stage에서 `eclipse-temurin:17-jre` 사용
- Docker CLI 설치
- `java -jar /app/app.jar` 실행
- `server.port=${PORT:8080}` 설정과 함께 Cloud Run `PORT` 값 수용 가능

분류:

- 배포용 이미지 빌드 파일이다.
- 동시에 `docker-compose.yml`에서 로컬 컨테이너 검증에도 사용한다.
- Docker CLI 설치는 Docker 기반 executor가 `docker run`을 호출하기 위한 준비다.

## 6. 공통 코드

공통 코드는 profile에 따라 진입 방식이 달라도 로컬과 배포에서 함께 쓰는 코드다.

### 6.1 도메인/DB 매핑

공통 entity:

- `problem/domain/Problem.java`
- `problem/domain/ProblemExample.java`
- `testcase/domain/ProblemTestcase.java`
- `submission/domain/Submission.java`
- `submission/domain/SubmissionStatus.java`
- `submission/domain/SubmissionResult.java`
- `submission/domain/SubmissionTestcaseResult.java`
- `user/domain/User.java`

공통 repository:

- `ProblemRepository`
- `ProblemTestcaseRepository`
- `SubmissionRepository`
- `SubmissionTestcaseResultRepository`
- `UserRepository`

이 코드는 Supabase PostgreSQL의 기존 테이블 구조에 맞춰 매핑되어 있으며, 운영과 로컬 검증 모두 같은 모델을 사용한다.

### 6.2 채점 비즈니스 흐름

공통 orchestrator 흐름:

- `JudgeService`
- `JudgePersistenceService`
- `CaseJudgeResult`
- `HiddenTestCaseSnapshot`
- `JudgeContext`
- `JudgeRunResult`
- `StartedJudging`

역할:

- submission 조회
- hidden testcase 조회
- testcase별 실행 요청
- 출력 비교
- 최종 result 결정
- submission 및 testcase result 저장

### 6.3 실행 모델

공통 인터페이스:

- `ExecutionGateway`
- `JudgeDispatchPort`
- `RemoteExecutionClient`

실행 구현은 설정에 따라 달라진다.

- `judge.execution.mode=docker`: `DockerExecutionGateway`
- `judge.execution.mode=remote`: `RemoteExecutionGateway`

dispatch 구현도 설정에 따라 달라진다.

- `judge.dispatch.mode=redis`: `RedisJudgeDispatchService`
- `judge.dispatch.mode=cloud-tasks`: `CloudTasksJudgeDispatchService`

## 7. 헷갈리기 쉬운 부분

### 7.1 `worker.role`과 `worker.mode`는 다르다

`worker.role`은 서비스의 책임을 나눈다.

- `orchestrator`: DB 기반 채점 흐름 담당
- `runner`: 단일 실행 요청 담당

`worker.mode`는 orchestrator의 진입 방식을 나눈다.

- `redis-polling`: Redis queue polling
- `http-trigger`: `/internal/judge-executions` HTTP trigger
- `runner`: runner profile에서 설정되지만, runner 활성화의 핵심 조건은 `worker.role=runner`다.

### 7.2 `judge.dispatch.mode`와 `judge.execution.mode`도 다르다

`judge.dispatch.mode`는 submissionId를 채점 시작 지점으로 보내는 방식이다.

- `redis`: Redis List
- `cloud-tasks`: Cloud Tasks HTTP task

`judge.execution.mode`는 실제 코드 실행을 어디서 할지 정한다.

- `docker`: 현재 프로세스가 Docker executor 사용
- `remote`: remote runner HTTP 호출

### 7.3 Docker는 로컬 전용이 아니라 “Docker 기반 실행 경로”다

`DockerProcessExecutor`는 로컬 개발에서 주로 사용되지만, runner profile에서도 사용된다. 따라서 Docker 관련 코드는 “로컬 전용”이 아니라 “Docker daemon이 있는 환경에서 사용하는 실행 코드”로 봐야 한다.

현재 표준 Cloud Run runner에서 이 경로가 바로 동작한다고 보면 안 된다. 이 부분은 `cloud-run-runner.md`와 `deployment-readiness.md`에서도 주의점으로 설명한다.

### 7.4 `application.properties` 기본값은 운영 기본값이 아니다

`application.properties`의 기본 조합은 다음이다.

```properties
worker.role=orchestrator
worker.mode=redis-polling
judge.dispatch.mode=redis
judge.execution.mode=docker
```

이는 로컬 MVP 검증에 가까운 기본값이다. 배포 orchestrator는 `SPRING_PROFILES_ACTIVE=remote`, runner는 `SPRING_PROFILES_ACTIVE=runner`를 명시해야 한다.

### 7.5 runner profile은 DB/Redis를 끈다

`application-runner.properties`는 다음 auto-configuration을 제외한다.

- DataSource
- Hibernate JPA
- JPA repositories
- Redis
- Redis repositories

runner는 DB 상태를 몰라야 하며, submission/testcase 저장은 orchestrator 책임이다.

### 7.6 테스트용 H2 설정은 운영 스키마가 아니다

`src/test/resources/h2-init.sql`은 테스트 DB에서 PostgreSQL enum 타입명을 흉내 내기 위한 파일이다. Supabase schema를 생성하거나 변경하는 파일이 아니다.

### 7.7 README와 일부 docs는 최신 코드와 완전히 일치하지 않을 수 있다

README와 일부 기존 문서는 예전 단수형 테이블명 또는 이전 status enum 설명을 포함하고 있을 수 있다. 현재 DB 매핑 기준은 `src/main/java`의 entity와 repository를 우선한다.

## 8. 실행 흐름 요약

### 8.1 로컬 개발/검증 흐름

1. 로컬 PostgreSQL과 Redis를 준비한다.
2. 로컬 Docker daemon을 사용할 수 있어야 한다.
3. `SPRING_PROFILES_ACTIVE=local` 또는 기본값으로 Spring app을 실행한다.
4. submission이 저장된 뒤 Redis `judge:queue`에 `submissionId`가 들어간다.
5. `JudgeQueueConsumer`가 queue를 읽는다.
6. `JudgeService`가 submission을 `JUDGING`으로 바꾸고 hidden testcase를 조회한다.
7. `DockerExecutionGateway`가 언어별 executor를 통해 Docker 실행을 수행한다.
8. 결과를 `submissions`, `submission_testcase_results`에 저장한다.

실행 예:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

### 8.2 테스트 실행 흐름

1. `.\gradlew.bat test` 실행
2. 단위 테스트는 mock/fake executor 사용
3. 통합 테스트는 H2 또는 Testcontainers 조건부 테스트 사용
4. Docker/real executor 테스트 일부는 system property가 있어야 실행된다.

테스트 전용 구성:

- `src/test/resources/application.properties`
- `src/test/resources/h2-init.sql`
- `@EnabledIfSystemProperty`가 붙은 Docker/Testcontainers/real executor 테스트

### 8.3 배포 orchestrator 흐름

1. `SPRING_PROFILES_ACTIVE=remote`로 실행한다.
2. DB 환경변수와 Cloud Tasks 환경변수를 설정한다.
3. upstream 또는 API 계층이 submission을 저장한다.
4. Cloud Tasks가 `/internal/judge-executions`를 호출한다.
5. orchestrator가 DB에서 submission과 hidden testcase를 읽는다.
6. orchestrator가 runner의 `/internal/runner-executions`를 testcase마다 호출한다.
7. runner 응답을 바탕으로 최종 result를 저장한다.

### 8.4 배포 runner 흐름

1. `SPRING_PROFILES_ACTIVE=runner`로 실행한다.
2. DB/Redis 없이 실행된다.
3. `/internal/runner-executions` 요청을 받는다.
4. 언어별 executor가 코드를 실행한다.
5. 실행 결과만 HTTP 응답으로 반환한다.

주의:

- 현재 runner는 Docker 기반 executor를 사용한다.
- Docker daemon이 없는 배포 환경에서는 실제 코드 실행이 실패할 수 있다.

## 9. 실제 운영 기준 권장 규칙

### 9.1 파일/패키지 분류 규칙

앞으로 새 코드를 추가할 때는 다음 기준으로 분류한다.

- `domain`, `repository`: DB 모델과 영속성. 기본적으로 공통 코드
- `application`: 비즈니스 흐름. profile 조건이 없으면 공통, 조건이 있으면 해당 role 코드
- `infrastructure/redis`: 로컬 또는 Redis 기반 dispatch 코드
- `infrastructure/cloudtasks`: 배포용 Cloud Tasks 코드
- `infrastructure/docker`: Docker daemon이 있는 실행 환경용 코드
- `infrastructure/remote`: remote runner 호출 코드
- `presentation`: endpoint 코드. endpoint의 활성 조건을 반드시 문서화
- `src/test`: 로컬 테스트 전용

### 9.2 profile 분리 원칙

- 로컬 검증은 `local`
- 배포 orchestrator는 `remote`
- 실행 전용 runner는 `runner`
- 운영 배포에서는 `SPRING_PROFILES_ACTIVE`를 반드시 명시한다.
- `application.properties`의 기본값에 의존해 운영 배포하지 않는다.

### 9.3 설정 이름 규칙

현재 설정 축은 계속 유지하는 것이 좋다.

- 역할: `worker.role`
- 진입 방식: `worker.mode`
- dispatch 방식: `judge.dispatch.mode`
- 실행 방식: `judge.execution.mode`

새 설정을 추가할 때는 어느 축에 속하는지 문서에 남긴다.

### 9.4 문서화 규칙

새 실행 경로나 profile을 추가할 때는 다음을 함께 문서화한다.

- 어떤 profile에서 활성화되는가
- 어떤 `@ConditionalOnProperty`와 연결되는가
- DB/Redis/Docker/Cloud Tasks 중 무엇이 필요한가
- 로컬 실행 가능한가
- 배포 실행 가능한가
- 표준 Cloud Run에서 가능한가, 아니면 Docker-capable host가 필요한가

### 9.5 “로컬용/배포용/공통” 판단 기준

로컬용으로 본다:

- H2, mock, fake executor, Testcontainers, local Docker 검증 코드
- `docker-compose.yml` 기반 실행
- Redis polling + Docker execution을 한 머신에서 검증하는 코드/설정

배포용으로 본다:

- Cloud Tasks dispatch
- HTTP trigger orchestrator
- remote runner 호출
- Cloud Run profile 문서와 연결된 설정
- 환경변수 기반 외부 서비스 연동

공통으로 본다:

- entity/repository
- 채점 비즈니스 로직
- DTO/record 계약
- 언어 실행 인터페이스
- profile에 따라 구현만 바뀌는 port/interface

## 10. 추천 파일명과 위치

추천 파일명 3개:

1. `local-vs-deployment-code-guide.md`
2. `runtime-code-classification.md`
3. `local-deploy-boundary-guide.md`

추천 위치:

- `docs/local-vs-deployment-code-guide.md`

이유:

- `docs` 폴더의 기존 실행/배포 문서들과 같은 계층에 두는 것이 자연스럽다.
- 특정 실행 절차가 아니라 저장소 기준 분류 문서이므로 `cloud-run-*`나 `judge-*` 이름보다 독립적인 이름이 적합하다.

## 11. 다음으로 정리하면 좋은 문서

1. 환경변수 전체 표준 문서
   - `application.properties`, `application-remote.properties`, `application-runner.properties`의 모든 환경변수와 필수 여부를 한 문서로 정리한다.

2. Supabase DB 매핑 기준 문서
   - `users`, `problems`, `problem_testcases`, `submissions`, `submission_testcase_results`와 JPA entity/repository 매핑을 표로 정리한다.

3. 운영 실행 가이드
   - orchestrator 배포, runner 배포, Cloud Tasks queue, DB 연결, 권한 설정, smoke test를 순서대로 정리한다.

## 12. 참고/변경 내역

### 참고한 기존 문서 목록

- `README.md`
- `docs/worker-mode-configuration.md`
- `docs/local-orchestrator-runner.md`
- `docs/cloud-run-orchestrator.md`
- `docs/cloud-run-runner.md`
- `docs/cloud-tasks-dispatch.md`
- `docs/deployment-readiness.md`
- `docs/current-vs-target-architecture.md`
- `docs/judge-worker.md`
- `docs/judge-worker-e2e.md`
- `docs/worker-validation-checklist.md`
- `docs/nextjs-spring-worker-checklist.md`
- `docs/judge-worker-concurrency-check.md`
- `docs/judge-worker-cleanup-check.md`
- `docs/judge-worker-demo-script.md`
- `docs/cpp-docker-judge-check.md`

### 수정한 기존 파일 목록

- 없음

### 새로 생성한 파일 목록

- `docs/local-vs-deployment-code-guide.md`

### 수정 대신 생성을 선택한 이유

기존 문서들은 각각 특정 주제를 다룬다.

- `worker-mode-configuration.md`: property 조합 설명
- `local-orchestrator-runner.md`: 로컬 분리 실행 예시
- `cloud-run-orchestrator.md`, `cloud-run-runner.md`: Cloud Run 배포 설명
- `deployment-readiness.md`: 배포 가능 상태와 체크리스트

이번 문서의 목적은 저장소 전체를 기준으로 “로컬용 / 배포용 / 공통 코드”를 구분하는 운영 기준 문서다. 기존 문서 하나를 확장하면 문서의 목적이 섞이고 중복이 커진다. 따라서 새 문서를 생성하고, 세부 실행/배포 절차는 기존 문서를 참조하도록 분리했다.

## 최종 한줄 요약

현재 구조의 핵심은 `local = Redis polling + Docker execution`, `remote orchestrator = Cloud Tasks + HTTP trigger + remote runner`, `runner = DB/Redis 없는 실행 전용 서비스`로 구분하는 것이다.
