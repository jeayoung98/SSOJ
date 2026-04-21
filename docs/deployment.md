# 배포

이 문서는 현재 코드 기준의 배포형 실행 구성을 정리한다.

## 권장 구조

현재 가장 보수적인 배포 조합:

- orchestrator: Cloud Run 또는 HTTP 기반 서비스
- async trigger: Cloud Tasks
- DB: Supabase PostgreSQL 또는 관리형 PostgreSQL
- runner: Docker 실행 가능한 VM 또는 별도 sandbox host

표준 Cloud Run runner에서 현재 Docker executor가 그대로 동작하는지는 확실하지 않다.

## Orchestrator

설정:

```properties
SPRING_PROFILES_ACTIVE=remote
worker.role=orchestrator
worker.mode=http-trigger
judge.dispatch.mode=cloud-tasks
judge.execution.mode=remote
```

흐름:

```text
Cloud Tasks
-> POST /internal/judge-executions
-> JudgeService
-> RemoteExecutionGateway
-> runner /internal/runner-executions
-> DB 저장
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

선택 환경변수:

- `JUDGE_DISPATCH_CLOUD_TASKS_SERVICE_ACCOUNT_EMAIL`
- `JUDGE_DISPATCH_CLOUD_TASKS_OIDC_AUDIENCE`

## Runner

설정:

```properties
SPRING_PROFILES_ACTIVE=runner
worker.role=runner
worker.mode=runner
judge.execution.mode=docker
```

endpoint:

```text
POST /internal/runner-executions
```

runner 요청 예시:

```json
{
  "submissionId": "00000000-0000-0000-0000-000000000001",
  "problemId": "A_PLUS_B",
  "language": "python",
  "sourceCode": "print(1)",
  "input": "",
  "timeLimitMs": 1000,
  "memoryLimitMb": 128
}
```

runner 응답 예시:

```json
{
  "success": true,
  "stdout": "1\n",
  "stderr": "",
  "exitCode": 0,
  "executionTimeMs": 10,
  "memoryUsageKb": 128,
  "systemError": false,
  "timedOut": false
}
```

runner는 DB/Redis/Cloud Tasks를 직접 사용하지 않는다.

## Cloud Tasks 계약

payload:

```json
{
  "submissionId": "00000000-0000-0000-0000-000000000001"
}
```

target:

```text
POST /internal/judge-executions
```

현재 코드에는 Cloud Tasks task 생성 코드가 있다. 실제 queue 생성, IAM, Cloud Run invoker 권한은 인프라 설정 영역이며 저장소에서 자동 생성하지 않는다.

## 보안 기준

- `/internal/judge-executions`는 공개 API가 아니다.
- `/internal/runner-executions`도 공개 API가 아니다.
- Cloud Tasks는 전용 service account로 orchestrator를 호출하는 구성이 바람직하다.
- orchestrator만 runner를 호출할 수 있어야 한다.
- runner는 신뢰할 수 없는 코드를 실행하므로 ingress, service account, 네트워크 권한을 제한해야 한다.

## 배포 전 확인

- DB 스키마가 entity와 맞는가
- 특히 `submissions.failed_testcase_order` 컬럼이 있는가
- `spring.jpa.hibernate.ddl-auto`가 운영에서 `validate` 또는 `none`인가
- runner host에서 Docker daemon을 사용할 수 있는가
- Cloud Tasks target URL이 orchestrator `/internal/judge-executions`인가
- runner base URL이 orchestrator에서 접근 가능한가
- 표준 Cloud Run runner 실행 가능 여부는 확실하지 않음을 문서/운영 판단에 반영했는가
