# Judge Worker 모드 설정

이 문서는 현재 코드 기준으로 local 모드와 remote 모드가 어떻게 나뉘는지 설명하는 설정 메모다. 운영 최종 배포 문서가 아니라, 점진 전환 구조를 코드와 설정 기준으로 이해하기 위한 문서다.

## 기준 설정값

현재 worker는 아래 3개 설정값으로 진입점, dispatch 방식, 실행 방식을 구분한다.

- `worker.mode`
- `judge.dispatch.mode`
- `judge.execution.mode`

각 설정의 역할은 아래와 같다.

- `worker.mode`
  - judge 실행 진입점을 결정한다.
  - `redis-polling`이면 `JudgeQueueConsumer`가 Redis queue를 polling한다.
  - `http-trigger`이면 `JudgeExecutionController`가 내부 HTTP 요청으로 `JudgeService`를 호출한다.
- `judge.dispatch.mode`
  - `submissionId`를 worker 쪽으로 전달하는 dispatch 구현을 결정한다.
  - `redis`면 `RedisJudgeDispatchService`가 Redis `judge:queue`에 `submissionId`를 넣는다.
  - `cloud-tasks`는 운영 경로용 확장 포인트이며, 현재는 구현 골격만 준비된 상태다.
- `judge.execution.mode`
  - 실제 코드 실행을 어떤 gateway로 위임할지 결정한다.
  - `docker`면 `DockerExecutionGateway`가 기존 `JavaExecutor`, `PythonExecutor`, `CppExecutor`를 사용한다.
  - `remote`면 `RemoteExecutionGateway`가 HTTP 기반 remote runner 호출을 사용한다.

## local 모드

local 모드는 기존 로컬 Docker 검증 경로다.

- `worker.mode=redis-polling`
- `judge.dispatch.mode=redis`
- `judge.execution.mode=docker`

동작:

- `JudgeQueueConsumer` 활성화
- Redis `judge:queue` polling
- `RedisJudgeDispatchService` 사용
- `DockerExecutionGateway` 사용
- 기존 `JavaExecutor` / `PythonExecutor` / `CppExecutor` 재사용

## remote 모드

remote 모드는 운영 전환을 위한 HTTP trigger + remote execution 조합이다.

- `worker.mode=http-trigger`
- `judge.dispatch.mode=cloud-tasks`
- `judge.execution.mode=remote`

동작:

- `JudgeQueueConsumer` 비활성화
- `JudgeExecutionController` 활성화
- `CloudTasksJudgeDispatchService` 선택 가능
- `RemoteExecutionGateway` 사용
- 실제 코드 실행은 remote runner HTTP 호출로 위임

메모:

- 현재 `CloudTasksJudgeDispatchService`는 골격만 있고 실제 SDK 연동은 아직 미구현이다.
- 현재 `RemoteExecutionGateway`는 HTTP 기반 remote runner 호출까지 정리된 상태다.
- 즉, 운영 모드용 조합은 코드상 준비되어 있지만 Cloud Tasks 실제 호출과 remote runner 운영 배포는 아직 별도 구현이 필요하다.

## application profile 예시

저장소에는 아래 예시 프로필 파일이 포함되어 있다.

- `application-local.properties`
- `application-remote.properties`

의도:

- `application-local.properties`
  - 로컬 개발/검증용 기본 조합
  - Redis polling + Docker execution
- `application-remote.properties`
  - 운영 전환 직전 검토용 조합
  - HTTP trigger + remote execution
  - dispatch는 `cloud-tasks`로 분리되어 있지만 실제 호출은 아직 미구현

실행 예시:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=remote"
```

## 간단한 흐름

### local

`submissionId -> Redis queue -> JudgeQueueConsumer -> JudgeService -> DockerExecutionGateway`

### remote

`submissionId -> dispatch 계층 -> HTTP trigger -> JudgeService -> RemoteExecutionGateway -> remote runner`

## 현재 상태

| 구분 | 상태 |
| --- | --- |
| local Redis polling + Docker execution | 구현됨 |
| HTTP trigger + RemoteExecutionGateway | 구현됨 |
| Cloud Tasks 실제 호출 | 미구현 |
| remote runner 실제 운영 배포 | 미구현 |
