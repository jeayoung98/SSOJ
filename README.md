# SSOJ

SSOJ는 Spring Boot 기반 온라인 저지 worker/orchestrator 저장소입니다.

이 저장소는 채점 백엔드만 포함합니다. Next.js Web/API 계층은 이 저장소 외부의 통합 대상이며, 제출 정보를 생성한 뒤 채점을 요청하는 역할로 가정합니다.

## 현재 범위

- Spring Boot orchestrator: PostgreSQL에서 제출과 테스트케이스를 조회하고 최종 채점 결과를 저장합니다.
- Spring Boot runner: Docker를 통해 제출 코드를 실행하고 batch 채점 결과를 반환합니다.
- PostgreSQL: 문제, 예제, hidden testcase, 제출, 최종 채점 결과를 저장합니다.
- Redis: 로컬 비동기 dispatch 경로로 사용합니다.
- Cloud Tasks: 배포 환경의 비동기 dispatch 경로로 사용합니다.
- Docker: C++, Java, Python 코드를 격리 실행합니다.

이 저장소의 범위가 아닌 것:

- Next.js Web/API 구현
- 랭킹
- 표절 검사
- special judge
- 고도화된 sandbox hardening
- realtime/SSE push

## 아키텍처 요약

```text
Web/API
-> submissions row 생성
-> submissionId dispatch
-> orchestrator
-> submission + hidden testcases 조회
-> runner 또는 local Docker executor 실행
-> 첫 실패 테스트케이스에서 즉시 중단
-> submissions에 최종 결과 저장
```

배포 기준 분리 구조:

```text
Cloud Tasks
-> Cloud Run orchestrator /internal/judge-executions
-> remote runner /internal/runner-executions
-> Docker sandbox
-> PostgreSQL result update
```

runner는 실행 전용 컴포넌트입니다. PostgreSQL, Redis, Cloud Tasks를 직접 사용하지 않습니다.

## 채점 모델

- hidden testcase는 `testcase_order` 순서로 실행합니다.
- runner는 테스트케이스마다 컨테이너를 새로 만들지 않고 batch로 실행합니다.
- 첫 `WA`, `TLE`, `RE`, `MLE` 발생 시 즉시 채점을 중단합니다.
- 제출 단위의 `execution_time_ms`, `memory_kb`에는 실행된 테스트케이스 중 최댓값을 저장합니다.
- 테스트케이스별 결과 row는 저장하지 않습니다.

최종 결과는 `submissions`에 저장합니다.

- `status`
- `result`
- `failed_testcase_order`
- `execution_time_ms`
- `memory_kb`
- `submitted_at`
- `judged_at`

`failed_testcase_order`는 `AC`, `CE`, `SYSTEM_ERROR`인 경우 `null`입니다.

## DB 기준

현재 JPA 기준 활성 테이블:

- `users`
- `problems`
- `problem_examples`
- `problem_testcases`
- `submissions`

ID 정책:

| Entity | ID type |
| --- | --- |
| `users.id` | `UUID` |
| `problems.id` | `Long` |
| `problem_examples.id` | `Long` |
| `problem_testcases.id` | `Long` |
| `submissions.id` | `Long` |

이 저장소는 현재 DDL 또는 migration 파일을 포함하지 않습니다. 외부 DB 스키마는 별도로 현재 JPA 모델과 맞춰야 합니다.

## 로컬 실행

필수 조건:

- JDK 17
- PostgreSQL
- Redis
- Docker daemon

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue 예시:

```powershell
redis-cli LPUSH judge:queue 1
```

Redis payload는 단순한 `submissionId` Long 문자열입니다.

## 테스트 실행

```powershell
.\gradlew.bat test
```

## 주요 문서

- [Architecture](docs/architecture.md)
- [Local Development](docs/local-development.md)
- [Deployment](docs/deployment.md)
- [Judging Model](docs/judging-model.md)
- [Validation Checklist](docs/validation-checklist.md)

`docs/archive/` 아래의 오래된 문서는 과거 기록용입니다. 내용이 충돌하면 위 주요 문서를 기준으로 봅니다.
