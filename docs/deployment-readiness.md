# 배포 준비 상태 점검 문서

이 문서는 현재 코드 기준으로 배포 전에 확인해야 할 내용을 정리한다. 실제 코드와 설정에 있는 내용만 기준으로 하며, 아직 확실하지 않은 부분은 명시적으로 표시한다.

## 1. 현재 아키텍처 요약

로컬 검증 흐름:

```text
submissionId(UUID) -> Redis judge:queue -> JudgeQueueConsumer -> JudgeService -> Docker executor -> DB 저장
```

원격 배포 흐름:

```text
submissionId(UUID) -> Cloud Tasks -> /internal/judge-executions -> JudgeService -> remote runner HTTP -> DB 저장
```

역할 분리:

- orchestrator
  - DB에서 `submissions`, `problems`, `problem_testcases` 조회
  - hidden testcase를 순서대로 실행
  - runner 또는 local executor 호출
  - `submission_testcase_results` 저장
  - `submissions.status`, `submissions.result`, `judged_at`, 실행 시간/메모리 저장
- runner
  - 단일 실행 요청 처리
  - 사용자 코드 실행 결과 반환
  - DB, Redis, Cloud Tasks에 직접 접근하지 않음

## 2. 현재 구현 상태

구현되어 있는 것:

- Redis polling 기반 로컬 채점
- Cloud Tasks dispatch
- HTTP trigger 기반 orchestrator
- remote runner HTTP 호출
- runner 전용 프로필
- Docker 기반 C++/Java/Python executor
- Supabase PostgreSQL 복수형 테이블 매핑

운영 가능성이 높은 것:

- orchestrator를 Cloud Run에 배포
- Cloud Tasks로 orchestrator 내부 엔드포인트 호출
- runner를 Docker 실행 가능한 VM 또는 별도 host에서 운영

확실하지 않음:

- 현재 Docker 기반 runner를 표준 Cloud Run에서 그대로 실행할 수 있는지 여부

## 3. DB 기준

현재 코드가 따라야 하는 DB 테이블:

- `users`
- `problems`
- `problem_examples`
- `problem_testcases`
- `submissions`
- `submission_testcase_results`

ID 기준:

- `problems.id`: 문자열
- 나머지 주요 ID: UUID
- Redis와 Cloud Tasks payload의 `submissionId`: UUID 문자열

채점 상태 저장 기준:

- `submissions.status`: 작업 상태
  - `PENDING`
  - `JUDGING`
  - `DONE`
- `submissions.result`: 채점 결과
  - `AC`
  - `WA`
  - `CE`
  - `RE`
  - `TLE`
  - `MLE`
  - `SYSTEM_ERROR`

현재 코드에서는 예전 문서의 `started_at`, `finished_at`을 사용하지 않는다. 제출 시각과 판정 시각은 `submitted_at`, `judged_at`이다.

## 4. 환경 변수

공통:

| 변수 | 용도 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local`, `remote`, `runner` 중 실행 프로필 선택 |
| `PORT` | Cloud Run 또는 실행 환경의 HTTP port |

DB:

| 변수 | 용도 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 |

Redis 로컬 모드:

| 변수 | 용도 |
| --- | --- |
| `SPRING_DATA_REDIS_HOST` | Redis host |
| `SPRING_DATA_REDIS_PORT` | Redis port |

Cloud Tasks:

| 변수 | 용도 |
| --- | --- |
| `JUDGE_DISPATCH_CLOUD_TASKS_PROJECT_ID` | GCP project id |
| `JUDGE_DISPATCH_CLOUD_TASKS_LOCATION` | Cloud Tasks region |
| `JUDGE_DISPATCH_CLOUD_TASKS_QUEUE_NAME` | Cloud Tasks queue |
| `JUDGE_DISPATCH_CLOUD_TASKS_TARGET_URL` | orchestrator `/internal/judge-executions` URL |
| `JUDGE_DISPATCH_CLOUD_TASKS_SERVICE_ACCOUNT_EMAIL` | OIDC 호출 service account |
| `JUDGE_DISPATCH_CLOUD_TASKS_OIDC_AUDIENCE` | OIDC audience |

runner 호출:

| 변수 | 용도 |
| --- | --- |
| `JUDGE_EXECUTION_REMOTE_BASE_URL` | runner base URL |

executor 이미지:

| 변수 | 용도 |
| --- | --- |
| `WORKER_EXECUTOR_CPP_IMAGE` | C++ 실행 이미지 |
| `WORKER_EXECUTOR_JAVA_IMAGE` | Java 실행 이미지 |
| `WORKER_EXECUTOR_PYTHON_IMAGE` | Python 실행 이미지 |

## 5. 로컬 검증 체크리스트

1. PostgreSQL을 실행한다.
2. Redis를 실행한다.
3. Docker daemon을 실행한다.
4. `SPRING_PROFILES_ACTIVE=local`로 앱을 실행한다.
5. `problems`, `problem_testcases`, `submissions`에 검증 데이터를 준비한다.
6. Redis `judge:queue`에 UUID `submissionId`를 넣는다.
7. 다음을 확인한다.
   - `submissions.status`: `PENDING -> JUDGING -> DONE`
   - `submissions.result`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR` 중 하나
   - `submissions.judged_at` 저장
   - `submission_testcase_results` 생성

## 6. 배포 검증 체크리스트

orchestrator:

1. `SPRING_PROFILES_ACTIVE=remote`로 실행한다.
2. `worker.mode=http-trigger`인지 확인한다.
3. `judge.dispatch.mode=cloud-tasks`인지 확인한다.
4. `judge.execution.mode=remote`인지 확인한다.
5. DB 환경 변수를 설정한다.
6. Cloud Tasks 환경 변수를 설정한다.
7. `JUDGE_EXECUTION_REMOTE_BASE_URL`을 설정한다.
8. `/actuator/health/readiness`를 확인한다.

runner:

1. `SPRING_PROFILES_ACTIVE=runner`로 실행한다.
2. DB, Redis 없이 기동되는지 확인한다.
3. `/internal/runner-executions` 직접 호출로 실행 결과가 반환되는지 확인한다.
4. 실제 host에서 Docker 실행이 가능한지 확인한다.

Cloud Tasks:

1. queue를 생성한다.
2. target URL을 orchestrator `/internal/judge-executions`로 지정한다.
3. payload가 `{"submissionId":"UUID"}` 형태인지 확인한다.
4. 인증을 사용하는 경우 service account 권한을 확인한다.

## 7. 운영 상태 매트릭스

| 영역 | 구현 여부 | 현재 운영 판단 |
| --- | --- | --- |
| 로컬 Redis polling | 구현됨 | 사용 가능 |
| 로컬 Docker 실행 | 구현됨 | 사용 가능 |
| Cloud Tasks dispatch | 구현됨 | 설정 후 사용 가능 |
| remote orchestrator | 구현됨 | Cloud Run 배포 가능 |
| runner HTTP 계약 | 구현됨 | 사용 가능 |
| runner Docker 실행 | 구현됨 | Docker 가능한 host 필요 |
| 표준 Cloud Run runner 실행 | 설정은 가능 | 확실하지 않음 |
| 강한 sandbox hardening | 미완료 | MVP 수준 |

## 8. 실전 권장 조합

현재 코드 기준으로 가장 현실적인 최소 배포 조합:

- orchestrator: Cloud Run
- async trigger: Cloud Tasks
- DB: Supabase PostgreSQL 또는 관리형 PostgreSQL
- runner: Docker 실행 가능한 VM 또는 별도 host

runner까지 Cloud Run으로 올리는 구성은 역할 분리 측면에서는 준비되어 있지만, 현재 Docker executor 구조에서는 실제 코드 실행 가능 여부가 확실하지 않다.
