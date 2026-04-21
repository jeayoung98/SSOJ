# SSOJ

SSOJ는 온라인 저지 MVP를 위한 Spring Boot 기반 judge worker 저장소다.

현재 저장소에는 Next.js Web/API 코드는 포함되어 있지 않다. Next.js는 전체 서비스 아키텍처에서 제출 생성, 제출 조회, Redis 또는 Cloud Tasks dispatch를 담당하는 상위 계층으로만 문서에서 언급한다.

## 현재 구조

Spring Boot 애플리케이션은 하나의 코드베이스에서 두 역할을 지원한다.

- `orchestrator`
  - DB에서 `submissions`, `problems`, hidden `problem_testcases`를 읽는다.
  - testcase별 실행을 local Docker executor 또는 remote runner에 위임한다.
  - 첫 실패 testcase에서 즉시 종료한다.
  - 최종 결과, 실패 testcase 번호, 실행 시간, 메모리 사용량을 DB에 저장한다.
- `runner`
  - HTTP로 단일 실행 요청을 받는다.
  - C++, Java, Python 코드를 Docker 기반 executor로 실행한다.
  - 실행 결과만 반환하고 DB/Redis에는 접근하지 않는다.

## 주요 구성 요소

- Spring Boot
- Spring Data JPA
- PostgreSQL
- Redis
- Cloud Tasks
- Docker 기반 code executor
- Gradle

## DB 매핑 기준

JPA entity는 기존 Supabase PostgreSQL 스키마를 따르는 것을 전제로 한다. 애플리케이션이 테이블을 생성하거나 변경하지 않는다.

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

상태와 결과는 분리되어 있다.

- `SubmissionStatus`: `PENDING`, `JUDGING`, `DONE`
- `SubmissionResult`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`

제출 결과 관련 필드:

- `submissions.result`: 최종 채점 결과
- `submissions.execution_time_ms`: 실행된 testcase 중 최대 실행 시간
- `submissions.memory_kb`: 실행된 testcase 중 최대 메모리 사용량
- `submissions.failed_testcase_order`: 실패한 testcase 순서
- `submissions.judged_at`: 채점 완료 시각

주의: `failed_testcase_order`는 현재 코드에 추가된 컬럼 매핑이다. 기존 Supabase 스키마에 이 컬럼이 없다면 `ddl-auto=validate`에서 실패하므로 DB 반영이 필요하다. 이 저장소에는 DDL/migration을 추가하지 않는다.

## 채점 흐름

```text
submission 생성
-> submissionId enqueue 또는 Cloud Tasks trigger
-> JudgeService.judge(UUID)
-> PENDING -> JUDGING
-> hidden problem_testcases를 testcase_order 순서로 실행
-> 각 testcase 결과 판정
-> 첫 실패(WA/TLE/RE/MLE)에서 즉시 종료
-> 실행된 testcase까지만 submission_testcase_results 저장
-> submissions.status=DONE, result/executionTime/memory/failedTestcaseOrder 저장
```

AC인 경우 모든 hidden testcase를 통과해야 하며 `failedTestcaseOrder`는 `null`이다. CE 또는 SYSTEM_ERROR처럼 특정 testcase 실패로 보기 어려운 결과도 현재 코드 기준으로 `failedTestcaseOrder=null`이다.

## 실행 모드

설정 축은 네 가지다.

- `worker.role`: `orchestrator`, `runner`
- `worker.mode`: `redis-polling`, `http-trigger`, `runner`
- `judge.dispatch.mode`: `redis`, `cloud-tasks`
- `judge.execution.mode`: `docker`, `remote`

## 로컬 실행

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

실행:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis queue payload는 UUID 문자열이다.

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

흐름:

```text
submissionId(UUID) -> Cloud Tasks -> POST /internal/judge-executions -> JudgeService -> RemoteExecutionGateway -> runner HTTP -> DB 저장
```

## 배포 runner 실행

runner 조합:

```properties
SPRING_PROFILES_ACTIVE=runner
worker.role=runner
judge.execution.mode=docker
```

runner endpoint:

- `POST /internal/runner-executions`

주의: 현재 runner는 내부에서 `docker run`을 호출한다. 표준 Cloud Run 환경에서는 Docker daemon이 일반적으로 제공되지 않으므로, 현재 구현 그대로는 Docker-capable VM 또는 동등한 실행 환경이 더 적합하다.

## 제출 결과 응답 모델

현재 사용자용 제출 조회 controller는 저장소에 없다. 대신 향후 controller에서 사용할 수 있는 `SubmissionResponse` DTO가 있다.

응답에 포함할 수 있는 핵심 값:

- `result`
- `failedTestcaseOrder`
- `executionTimeMs`
- `memoryKb`
- `submittedAt`
- `judgedAt`

자세한 내용은 [제출 결과 모델과 채점 종료 규칙](docs/judge-result-model.md)을 본다.

## 테스트

```powershell
.\gradlew.bat test
```

검증되는 핵심 동작:

- 모든 testcase 통과 시 AC
- 첫 실패 testcase에서 즉시 종료
- 실패 이후 testcase 미실행
- `failedTestcaseOrder` 저장
- AC/CE의 `failedTestcaseOrder=null`
- 실행 시간/메모리 최대값 저장

## 관련 문서

- [제출 결과 모델과 채점 종료 규칙](docs/judge-result-model.md)
- [Judge Worker 현재 동작](docs/judge-worker.md)
- [JudgeService 개선 반영 현황](docs/judge-service-improvements.md)
- [Judge Worker 검증 체크리스트](docs/worker-validation-checklist.md)
- [Next.js/API와 Spring Worker 연동 체크리스트](docs/nextjs-spring-worker-checklist.md)
- [로컬 실행 코드와 배포 코드 구분 가이드](docs/local-vs-deployment-code-guide.md)
- [문서 최신화 점검 보고서](docs/documentation-maintenance-report.md)

