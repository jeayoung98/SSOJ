# Judge Worker 현재 동작 문서

이 문서는 현재 코드 기준의 Spring judge worker 동작을 설명한다. 운영 배포 절차가 아니라, 구현된 worker 흐름과 로컬/배포 경로의 차이를 이해하기 위한 문서다.

## 1. 현재 역할 구분

현재 애플리케이션은 `worker.role`로 역할을 나눈다.

- `orchestrator`
  - DB에서 submission, problem, hidden testcase를 읽는다.
  - testcase별 실행을 Docker 또는 remote runner로 수행한다.
  - 결과를 DB에 저장한다.
- `runner`
  - 단일 실행 요청을 HTTP로 받는다.
  - Docker 기반 executor로 코드를 실행한다.
  - 실행 결과만 반환한다.

## 2. Queue 계약

로컬 Redis polling 경로:

- Redis key: `judge:queue`
- payload: `submissionId` UUID 문자열

예:

```powershell
redis-cli LPUSH judge:queue 00000000-0000-0000-0000-000000000001
```

`JudgeQueueConsumer`는 문자열을 `UUID.fromString(...)`으로 파싱한다. 숫자형 `123` payload는 현재 코드 기준 유효하지 않다.

## 3. DB 상태와 결과

현재 DB 모델은 상태와 결과를 분리한다.

`submissions.status`:

- `PENDING`
- `JUDGING`
- `DONE`

`submissions.result`:

- `AC`
- `WA`
- `CE`
- `RE`
- `TLE`
- `MLE`
- `SYSTEM_ERROR`

시간 필드:

- 제출 시각: `submitted_at`
- 채점 완료 시각: `judged_at`

이전 문서의 `started_at`, `finished_at`은 현재 `Submission` entity에 없다.

## 4. 주요 테이블

현재 JPA 매핑 기준:

- `users`
- `problems`
- `problem_examples`
- `problem_testcases`
- `submissions`
- `submission_testcase_results`

이전 단수형 이름인 `problem`, `test_case`, `submission`, `submission_case_result`는 현재 entity 매핑 기준이 아니다.

## 5. Orchestrator 동작 흐름

로컬 Redis polling 경로:

```text
Redis judge:queue
-> JudgeQueueConsumer
-> JudgeService.judge(UUID)
-> JudgePersistenceService.startJudging(UUID)
-> JudgeService.runJudgeLogic(...)
-> ExecutionGateway
-> JudgePersistenceService.saveResultsAndFinish(...)
```

remote HTTP trigger 경로:

```text
POST /internal/judge-executions
-> JudgeExecutionController
-> JudgeService.judge(UUID)
-> RemoteExecutionGateway
-> runner /internal/runner-executions
-> DB 저장
```

## 6. 채점 정책

- hidden testcase만 채점한다.
- `ProblemTestcaseRepository.findAllByProblem_IdAndHiddenTrueOrderByTestcaseOrderAsc(...)`로 조회한다.
- 첫 실패 결과가 나오면 이후 testcase 실행을 중단한다.
- 실행한 testcase까지만 `submission_testcase_results`에 저장한다.
- 최종 결과는 `submissions.result`에 저장하고 `submissions.status`는 `DONE`으로 바꾼다.

## 7. 출력 비교

현재 MVP 출력 비교 정책:

- 전체 출력 `trim`
- 줄 단위 분리
- 각 줄 `trim`
- line-by-line 비교
- trailing newline 차이는 무시

special judge는 구현하지 않는다.

## 8. 언어 실행

지원 언어:

- `cpp`
- `java`
- `python`

executor:

- `CppExecutor`
- `JavaExecutor`
- `PythonExecutor`
- `DockerProcessExecutor`

Docker 실행 옵션은 `DockerProcessExecutor`가 구성한다.

- `--network none`
- memory limit
- cpu limit
- timeout
- workspace mount
- container/temp file cleanup

## 9. 로컬 실행 기준

로컬 기본 profile:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

필요한 것:

- JDK 17
- PostgreSQL
- Redis
- Docker daemon

`docker-compose.yml`로 PostgreSQL, Redis, judge-worker를 함께 띄울 수 있다.

```powershell
docker compose up --build
```

## 10. 배포 기준

배포 orchestrator:

- `SPRING_PROFILES_ACTIVE=remote`
- `worker.mode=http-trigger`
- `judge.dispatch.mode=cloud-tasks`
- `judge.execution.mode=remote`

배포 runner:

- `SPRING_PROFILES_ACTIVE=runner`
- `worker.role=runner`
- `judge.execution.mode=docker`

주의: 현재 runner는 Docker daemon이 필요하다. 표준 Cloud Run에서 Docker 기반 실행이 가능한지는 확실하지 않으며, 현재 문서 체계에서는 Docker-capable VM 또는 별도 실행 backend를 권장한다.

## 11. 관련 문서

- [로컬 실행 코드와 배포 코드 구분 가이드](./local-vs-deployment-code-guide.md)
- [Judge Worker 모드 설정](./worker-mode-configuration.md)
- [로컬 Orchestrator/Runner 분리 실행](./local-orchestrator-runner.md)
- [배포 준비 체크리스트](./deployment-readiness.md)
