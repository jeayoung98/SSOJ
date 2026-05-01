# Deployment

이 문서는 현재 SSOJ 채점 백엔드의 배포 기준을 정리합니다.

## 권장 배포 구조

현재 구조에서 가장 현실적인 배포 조합은 다음입니다.

| 구성 요소 | 권장 배포 위치 | 이유 |
| --- | --- | --- |
| Orchestrator | Cloud Run | 요청 기반 실행, 관리형 배포, Cloud Tasks 연동 |
| Async trigger | Cloud Tasks | HTTP 기반 dispatch, 재시도, rate limit, 동시성 제어 |
| Runner | GCE VM 또는 Docker 실행 가능한 host | Docker daemon 접근 필요 |
| DB | Supabase PostgreSQL 또는 관리형 PostgreSQL | 문제/제출/결과 저장 |
| Docker runtime | Runner host 내부 | 사용자 코드 격리 실행 |

표준 Cloud Run 환경에서 Runner가 Docker executor를 그대로 실행할 수 있다고 전제하면 안 됩니다. 현재 Runner는 Docker daemon 접근이 필요하므로 GCE VM 기반 Runner가 더 안전한 기준입니다.

## 배포 흐름

```text
Next.js Web/API
-> submission 생성
-> Cloud Tasks task 생성
-> Cloud Run Orchestrator /internal/judge-executions
-> GCE Runner VM /internal/runner-executions
-> Docker Sandbox
-> Orchestrator가 submissions 결과 저장
```

## Orchestrator profile

배포 Orchestrator는 `remote` profile을 사용합니다.

```properties
SPRING_PROFILES_ACTIVE=remote
worker.role=orchestrator
worker.mode=http-trigger
judge.dispatch.mode=cloud-tasks
judge.execution.mode=remote
```

Orchestrator 책임:

- Cloud Tasks 내부 요청 수신
- `submissionId` 기준 제출 조회
- `PENDING -> JUDGING` 상태 변경
- hidden testcase 조회
- Runner 호출
- 최종 결과 저장

## Runner profile

Runner는 `runner` profile을 사용합니다.

```properties
SPRING_PROFILES_ACTIVE=runner
worker.role=runner
worker.mode=runner
judge.execution.mode=docker
```

Runner 책임:

- `/internal/runner-executions` 수신
- 언어별 source file 생성
- C++ / Java 컴파일
- Python 실행
- Docker 컨테이너 실행
- 실행 시간/메모리 측정
- 최종 batch 결과 반환

Runner는 DB, Redis, Cloud Tasks를 직접 사용하지 않습니다.

## 필수 환경변수

### Orchestrator

| 환경변수 | 설명 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `remote` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `JUDGE_EXECUTION_REMOTE_BASE_URL` | Runner base URL |
| `JUDGE_DISPATCH_CLOUD_TASKS_PROJECT_ID` | GCP project id |
| `JUDGE_DISPATCH_CLOUD_TASKS_LOCATION` | Cloud Tasks location |
| `JUDGE_DISPATCH_CLOUD_TASKS_QUEUE_NAME` | Queue name |
| `JUDGE_DISPATCH_CLOUD_TASKS_TARGET_URL` | Orchestrator `/internal/judge-executions` URL |

선택 환경변수:

| 환경변수 | 설명 |
| --- | --- |
| `JUDGE_DISPATCH_CLOUD_TASKS_SERVICE_ACCOUNT_EMAIL` | Cloud Tasks OIDC service account |
| `JUDGE_DISPATCH_CLOUD_TASKS_OIDC_AUDIENCE` | OIDC audience |

### Runner

| 환경변수 | 설명 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `runner` |
| `PORT` | Runner server port |
| `WORKER_EXECUTOR_WORKSPACE_ROOT` | 임시 workspace root |
| `WORKER_EXECUTOR_CPP_IMAGE` | C++ 실행 이미지 override |
| `WORKER_EXECUTOR_JAVA_IMAGE` | Java 실행 이미지 override |
| `WORKER_EXECUTOR_PYTHON_IMAGE` | Python 실행 이미지 override |

## Cloud Tasks 계약

Payload:

```json
{
  "submissionId": 1
}
```

Target:

```text
POST /internal/judge-executions
```

현재 저장소에는 Cloud Tasks task 생성 코드가 있습니다. Queue 생성, IAM, Cloud Run invoker 권한은 인프라 설정 영역입니다.

## Runner API 계약

Endpoint:

```text
POST /internal/runner-executions
```

Request:

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

Response:

```json
{
  "result": "AC",
  "executionTimeMs": 10,
  "memoryUsageKb": 128,
  "failedTestcaseOrder": null
}
```

## 네트워크 기준

- 외부 사용자는 Runner에 직접 접근하지 않아야 합니다.
- Orchestrator만 Runner를 호출할 수 있어야 합니다.
- Cloud Run Orchestrator에서 VM Runner 내부 IP로 접근하려면 VPC 연결과 egress 설정이 필요합니다.
- 외부 IP를 이용한 Runner 호출은 임시 검증에는 가능하지만 운영 기준으로는 권장하지 않습니다.
- DB는 애플리케이션 서버에서만 접근하는 것을 기준으로 합니다.

## 보안 기준

- `/internal/judge-executions`는 공개 API가 아닙니다.
- `/internal/runner-executions`도 공개 API가 아닙니다.
- Cloud Tasks는 전용 service account로 Orchestrator를 호출하는 구성이 바람직합니다.
- Runner는 신뢰할 수 없는 코드를 실행하므로 ingress, service account, 네트워크 권한을 제한해야 합니다.
- Docker 실행 컨테이너는 network 차단, memory limit, timeout, workspace cleanup을 기본 전제로 둡니다.

## 배포 전 확인

- DB schema가 JPA entity와 일치하는가
- `users.id`만 UUID이고 주요 entity ID는 Long인가
- `submissions.failed_testcase_order` 컬럼이 있는가
- 운영 환경에서 `spring.jpa.hibernate.ddl-auto`가 `validate` 또는 `none`인가
- Cloud Tasks target URL이 Orchestrator `/internal/judge-executions`인가
- Cloud Tasks payload의 `submissionId`가 Long 값인가
- Runner base URL이 Orchestrator에서 접근 가능한가
- Runner host에서 Docker daemon을 사용할 수 있는가
- Runner image에 C++ / Java / Python 실행 도구가 있는가
- Runner 요청이 단일 input/output이 아니라 `testCases` 배열 구조인가

## 장애 처리 기준

| 상황 | 처리 기준 |
| --- | --- |
| 이미 완료된 submission 재호출 | 재채점하지 않도록 멱등성 고려 |
| Runner 미응답 | timeout 후 `SYSTEM_ERROR` 저장 |
| Docker 실행 실패 | 해당 제출 `SYSTEM_ERROR` 처리 |
| 컴파일 실패 | `CE`, `failed_testcase_order=null` |
| 실행 시간 초과 | `TLE`, 첫 실패 testcase order 저장 |
| 메모리 초과 | `MLE`, 첫 실패 testcase order 저장 |

## Smoke test

Orchestrator health 확인:

```bash
curl https://<orchestrator-url>/actuator/health
```

Runner health 확인:

```bash
curl http://<runner-host>:<port>/actuator/health
```

Orchestrator 내부 채점 trigger:

```bash
curl -X POST https://<orchestrator-url>/internal/judge-executions \
  -H "Content-Type: application/json" \
  -d '{"submissionId":1}'
```
