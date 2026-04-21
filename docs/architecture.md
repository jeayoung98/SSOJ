# 아키텍처

이 문서는 현재 저장소 기준의 전체 구조와 컴포넌트 경계를 설명한다.

## 현재 구현됨

| 구성 | 역할 |
| --- | --- |
| Spring Boot orchestrator | DB 기반 채점 흐름 담당 |
| Spring Boot runner | 단일 코드 실행 요청 처리 |
| PostgreSQL | 문제, testcase, 제출, 결과 저장 |
| Redis | 로컬 queue 경로 |
| Cloud Tasks | 배포형 HTTP trigger dispatch |
| Docker executor | C++/Java/Python 코드 실행 |

현재 저장소에는 Next.js Web/API 코드가 없다. Web/API는 외부 계층으로 보고, submission 저장과 enqueue/dispatch를 담당한다고 가정한다.

## 주요 패키지

| 경로 | 역할 |
| --- | --- |
| `judge/application/sevice` | 채점 흐름, queue consume, runner 실행 서비스 |
| `judge/domain/model` | 채점 command/result/context record |
| `judge/executor` | 언어별 Docker 실행 |
| `judge/infrastructure/redis` | Redis dispatch |
| `judge/infrastructure/cloudtasks` | Cloud Tasks dispatch |
| `judge/infrastructure/remote` | remote runner HTTP 호출 |
| `judge/presentation` | 내부 HTTP endpoint |
| `problem`, `testcase`, `submission`, `user` | JPA entity/repository |

## 실행 역할

### Orchestrator

orchestrator는 DB를 읽고 최종 결과를 저장한다.

```text
submissionId
-> JudgeService
-> hidden testcase 조회
-> testcase별 실행 요청
-> 첫 실패 시 종료
-> DB 저장
```

활성 조건:

- `worker.role=orchestrator`
- 기본값도 orchestrator에 가깝다.

### Runner

runner는 실행 전용 서비스다.

```text
POST /internal/runner-executions
-> RunnerExecutionService
-> LanguageExecutor
-> DockerProcessExecutor
-> RunnerExecutionResponse
```

runner는 DB/Redis를 사용하지 않는다.

## DB 모델

현재 기준 테이블:

- `users`
- `problems`
- `problem_examples`
- `problem_testcases`
- `submissions`
- `submission_testcase_results`

상태와 결과:

- `submissions.status`: `PENDING`, `JUDGING`, `DONE`
- `submissions.result`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`

결과 지표:

- `submissions.failed_testcase_order`
- `submissions.execution_time_ms`
- `submissions.memory_kb`
- `submission_testcase_results.execution_time_ms`
- `submission_testcase_results.memory_kb`

주의: `failed_testcase_order`는 현재 코드 매핑에 필요한 컬럼이다. 기존 Supabase DB에 없으면 DB 반영이 필요하다.

## 외부 Web/API 계약

Web/API는 enqueue 전에 `submissions` row를 먼저 저장해야 한다.

필수 값:

- `id`: UUID
- `user_id`: UUID
- `problem_id`: `problems.id`
- `language`
- `source_code`
- `status=PENDING`
- `submitted_at`

로컬 Redis payload:

```text
judge:queue -> UUID 문자열
```

Cloud Tasks payload:

```json
{
  "submissionId": "00000000-0000-0000-0000-000000000001"
}
```

## 확실하지 않음

- 표준 Cloud Run에서 현재 Docker 기반 runner가 그대로 사용자 코드를 실행할 수 있는지는 확실하지 않다.
- local Docker executor는 실행 시간은 측정하지만 실제 메모리 사용량 측정은 확실하지 않다.
- 사용자용 제출 조회 controller는 현재 저장소에 없다. `SubmissionResponse` DTO만 있다.

## Archive

이전 세부 문서와 검토 메모는 `docs/archive/`에 있다. 현재 기준 판단은 이 문서를 우선한다.
