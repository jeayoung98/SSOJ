# 배포

이 문서는 현재 코드 기준의 배포형 실행 구성을 정리한다.

## 권장 구조

현재 가장 현실적인 배포 조합:

- orchestrator: Cloud Run 또는 HTTP 기반 Spring Boot 서비스
- async trigger: Cloud Tasks
- DB: Supabase PostgreSQL 또는 관리형 PostgreSQL
- runner: Docker 실행 가능한 VM 또는 별도 sandbox host

표준 Cloud Run runner에서 Docker executor가 그대로 동작한다고 전제하면 안 된다. 현재 runner는 Docker daemon 접근이 필요하므로 VM 기반 runner가 더 안전한 선택이다.

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
  "submissionId": 1,
  "problemId": 1,
  "language": "python",
  "sourceCode": "a,b=map(int,input().split())\nprint(a+b)",
  "testCases": [
    {
      "testCaseOrder": 1,
      "input": "1 2\n",
      "expectedOutput": "3\n"
    }
  ],
  "timeLimitMs": 3000,
  "memoryLimitMb": 128
}
```

runner 응답 예시:

```json
{
  "result": "AC",
  "executionTimeMs": 10,
  "memoryUsageKb": 128,
  "failedTestcaseOrder": null
}
```

runner는 DB/Redis/Cloud Tasks를 직접 사용하지 않는다.

## Cloud Tasks 계약

payload:

```json
{
  "submissionId": 1
}
```

target:

```text
POST /internal/judge-executions
```

현재 코드에는 Cloud Tasks task 생성 코드가 있다. 실제 queue 생성, IAM, Cloud Run invoker 권한은 인프라 설정 영역이며 저장소에서 자동 생성하지 않는다.

## 네트워크 기준

- orchestrator만 runner를 호출할 수 있어야 한다.
- runner endpoint는 공개 API로 열어두지 않는 것이 원칙이다.
- Cloud Run orchestrator에서 VM runner를 내부 IP로 호출하려면 VPC 연결과 egress 설정이 필요하다.
- 외부 IP로 임시 검증할 수는 있지만 운영 기준으로는 내부 통신이 더 안전하다.

## 보안 기준

- `/internal/judge-executions`는 공개 API가 아니다.
- `/internal/runner-executions`도 공개 API가 아니다.
- Cloud Tasks는 전용 service account로 orchestrator를 호출하는 구성이 바람직하다.
- runner는 신뢰할 수 없는 코드를 실행하므로 ingress, service account, 네트워크 권한을 제한해야 한다.
- Docker 실행 컨테이너는 network 차단, 메모리 제한, timeout, 작업 디렉터리 정리를 기본 전제로 둔다.

## 배포 전 확인

- DB 스키마가 entity와 맞는가
- `users.id`만 UUID이고 나머지 주요 entity ID는 Long인가
- `submissions.failed_testcase_order` 컬럼이 있는가
- `spring.jpa.hibernate.ddl-auto`가 운영에서 `validate` 또는 `none`인가
- runner host에서 Docker daemon을 사용할 수 있는가
- runner image에 C++/Java/Python 실행에 필요한 도구가 들어 있는가
- Cloud Tasks target URL이 orchestrator `/internal/judge-executions`인가
- Cloud Tasks payload의 `submissionId`가 Long 값인가
- runner base URL이 orchestrator에서 접근 가능한가
- runner 요청이 단일 input/output이 아니라 `testCases` 배열 구조인가
