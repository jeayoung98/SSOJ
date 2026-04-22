# Judge Worker 모드 설정

이 문서는 현재 코드 기준으로 `worker.role`, `worker.mode`, `judge.dispatch.mode`, `judge.execution.mode`가 어떤 Spring bean과 실행 경로를 선택하는지 정리한다.

## 1. 설정 축

### `worker.role`

서비스 역할을 결정한다.

| 값 | 의미 | 활성 코드 |
| --- | --- | --- |
| `orchestrator` | DB 기반 채점 흐름 담당 | `JudgeService`, `JudgePersistenceService`, dispatch 구현 |
| `runner` | 단일 코드 실행 담당 | `RunnerExecutionController`, `RunnerExecutionService`, `RunnerLanguageExecutorSelector` |

기본값은 `orchestrator`에 가깝다. 여러 bean이 `matchIfMissing = true`로 orchestrator 경로를 기본 활성화한다.

### `worker.mode`

orchestrator의 진입 방식을 결정한다.

| 값 | 의미 | 활성 코드 |
| --- | --- | --- |
| `redis-polling` | Redis queue를 주기적으로 polling | `JudgeQueueConsumer` |
| `http-trigger` | 내부 HTTP 요청으로 채점 시작 | `JudgeExecutionController` |
| `runner` | runner profile에서 사용하는 값 | runner 활성 조건은 `worker.role=runner`가 핵심 |

### `judge.dispatch.mode`

submissionId를 채점 시작 지점으로 보내는 방식을 결정한다.

| 값 | 의미 | 활성 코드 |
| --- | --- | --- |
| `redis` | Redis List `judge:queue`에 UUID 문자열 push | `RedisJudgeDispatchService` |
| `cloud-tasks` | Cloud Tasks HTTP task 생성 | `CloudTasksJudgeDispatchService`, `GoogleCloudTasksGateway` |

### `judge.execution.mode`

실제 코드 실행 위치를 결정한다.

| 값 | 의미 | 활성 코드 |
| --- | --- | --- |
| `docker` | 현재 프로세스가 Docker executor로 실행 | `DockerExecutionGateway` |
| `remote` | remote runner HTTP 호출 | `RemoteExecutionGateway`, `HttpRemoteExecutionClient` |

## 2. 로컬 검증 조합

```properties
worker.role=orchestrator
worker.mode=redis-polling
judge.dispatch.mode=redis
judge.execution.mode=docker
```

사용 profile:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

흐름:

```text
submissionId(UUID) -> Redis judge:queue -> JudgeQueueConsumer -> JudgeService -> DockerExecutionGateway -> DB 저장
```

로컬용으로 보는 근거:

- Redis polling은 장기 실행 worker 모델이다.
- Docker executor는 로컬 Docker daemon 또는 Docker-capable host가 필요하다.
- `docker-compose.yml`은 PostgreSQL, Redis, judge-worker를 로컬 검증용으로 묶는다.

## 3. 배포 orchestrator 조합

```properties
worker.role=orchestrator
worker.mode=http-trigger
judge.dispatch.mode=cloud-tasks
judge.execution.mode=remote
```

사용 profile:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=remote"
```

흐름:

```text
submissionId(UUID) -> Cloud Tasks -> POST /internal/judge-executions -> JudgeService -> RemoteExecutionGateway -> runner HTTP -> DB 저장
```

배포용으로 보는 근거:

- Redis polling을 사용하지 않는다.
- Cloud Tasks가 비동기 trigger 역할을 한다.
- 실제 실행은 별도 runner에 위임한다.

## 4. Runner 조합

```properties
worker.role=runner
worker.mode=runner
judge.execution.mode=docker
```

사용 profile:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=runner --server.port=8081"
```

흐름:

```text
POST /internal/runner-executions -> RunnerExecutionService -> LanguageExecutor -> DockerProcessExecutor -> RunnerExecutionResponse
```

runner는 DB와 Redis를 사용하지 않는다. `application-runner.properties`에서 DataSource, JPA, Redis auto-configuration을 제외한다.

## 5. 현재 구현 상태

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| local Redis polling + Docker execution | 구현됨 | `JudgeQueueConsumer`, `RedisJudgeDispatchService`, `DockerExecutionGateway` |
| HTTP trigger orchestrator | 구현됨 | `JudgeExecutionController` |
| Cloud Tasks dispatch | 구현됨 | `CloudTasksJudgeDispatchService`, `GoogleCloudTasksGateway` |
| Remote runner HTTP 호출 | 구현됨 | `RemoteExecutionGateway`, `HttpRemoteExecutionClient` |
| Runner endpoint | 구현됨 | `RunnerExecutionController` |
| 표준 Cloud Run runner에서 Docker 실행 | 확실하지 않음 | 현재 executor가 `docker run`을 호출하므로 Docker daemon이 필요 |

## 6. 헷갈리기 쉬운 점

- `worker.role=runner`와 `worker.mode=runner`는 같은 의미가 아니다. runner bean 활성화의 핵심 조건은 `worker.role=runner`다.
- `judge.dispatch.mode`는 채점 요청을 보내는 방식이고, `judge.execution.mode`는 코드를 실행하는 방식이다.
- `application.properties` 기본값은 운영 기본값이 아니라 로컬 검증에 가까운 기본값이다.
- 운영 배포에서는 `SPRING_PROFILES_ACTIVE=remote` 또는 `SPRING_PROFILES_ACTIVE=runner`를 명시해야 한다.
