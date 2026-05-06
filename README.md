# SSOJ

SSOJ는 사용자가 제출한 C++ / Java / Python 코드를 Docker sandbox에서 실행하고, 최종 채점 결과를 저장하는 온라인 저지 채점 백엔드입니다.

이 저장소는 **Spring Boot 기반 Orchestrator / Runner 코드**를 중심으로 관리합니다. Next.js Web/API와 Supabase 설정은 전체 서비스의 다른 계층으로 보고, 이 저장소는 `submissionId`를 기준으로 채점 파이프라인을 수행하는 역할에 집중합니다.

## 현재 구조

```text
Frontend / Next.js
-> Supabase DB
-> Cloud Run Orchestrator
-> Compute Engine Runner VM
-> Docker Sandbox Containers
```

## 구성 요소

| 구성 요소 | 역할 |
|---|---|
| Frontend / Next.js | 코드 제출, `submissions` row 생성, Orchestrator 채점 트리거, SSE 진행률 수신 |
| Supabase DB | problems, testcases, submissions, 최종 채점 결과 저장 |
| Cloud Run Orchestrator | 제출 조회, 테스트케이스 조회, Runner 호출, 결과 저장, SSE 이벤트 전달 |
| Compute Engine Runner VM | 실제 코드 실행 요청 처리, Docker executor 실행 |
| Docker Sandbox Containers | C++ / Java / Python 사용자 코드 격리 실행 |

## 채점 흐름

```text
1. Next.js가 사용자 제출을 받는다.
2. Supabase `submissions` 테이블에 row를 생성한다.
3. 생성된 `submissionId`를 Orchestrator에 전달한다.
4. Orchestrator가 `submissionId` 기준으로 제출과 테스트케이스를 조회한다.
5. Orchestrator가 Runner VM에 실행 요청을 보낸다.
6. Runner가 Docker sandbox에서 테스트케이스를 실행한다.
7. Runner가 progress callback을 Orchestrator에 보낸다.
8. Orchestrator가 SSE로 프론트에 진행률을 전달한다.
9. Orchestrator가 최종 결과를 `submissions` row에 저장한다.
10. 프론트는 SSE와 DB polling을 함께 사용해 결과를 표시한다.
```

## 주요 API 계약

### 채점 트리거

```http
POST /internal/judge-executions
Content-Type: application/json
```

```json
{
  "submissionId": 222
}
```

정상 응답은 `202 Accepted`이며, 응답 body가 없을 수 있습니다.

### SSE 진행률

```http
GET /api/submissions/{submissionId}/events
```

진행 이벤트 예시:

```json
{
  "submissionId": 222,
  "phase": "RUNNING",
  "completedTestcases": 37,
  "totalTestcases": 94,
  "progressPercent": 39,
  "result": null
}
```

완료 이벤트 예시:

```json
{
  "submissionId": 222,
  "phase": "DONE",
  "completedTestcases": 94,
  "totalTestcases": 94,
  "progressPercent": 100,
  "result": "AC"
}
```

`completedTestcases`는 통과 개수가 아니라 실행 완료 개수입니다.

## 결과 저장 모델

최종 결과는 `submissions` 테이블에 저장합니다.

| 컬럼 | 의미 |
|---|---|
| `status` | 작업 상태: `PENDING`, `JUDGING`, `DONE` |
| `result` | 채점 결과: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR` |
| `execution_time_ms` | 실행된 testcase 중 최대 실행 시간 |
| `memory_kb` | 실행된 testcase 중 최대 메모리 사용량 |
| `failed_testcase_order` | 첫 실패 testcase 번호. `AC`, `CE`, `SYSTEM_ERROR`는 `null` |
| `submitted_at` | 제출 생성 시각 |
| `judged_at` | 채점 완료 시각 |

테스트케이스별 결과 row는 저장하지 않습니다.

## Runtime profile

| Profile | 역할 |
|---|---|
| `remote` | Cloud Run Orchestrator |
| `runner` | GCE Runner VM 내부 Runner 서버 |
| `local` | 로컬 검증용 실행 |

## 현재 설계 판단

- 프론트는 Runner를 직접 호출하지 않습니다.
- 채점은 `problemId`가 아니라 `submissionId` 기준으로 실행합니다.
- SSE는 진행률 UX를 위한 보조 수단입니다.
- 최종 결과 보장은 DB polling으로 보완합니다.
- 현재 SSE hub는 메모리 기반입니다.
- Cloud Run scale-out이 필요해지면 Redis Pub/Sub 같은 외부 브로커가 필요합니다.
- Runner는 Warm Container Pool을 사용해 Docker container 생성 비용을 줄입니다.
- Java / Python은 테스트케이스마다 runtime이 실행되므로, 성능 병목은 Docker 생성보다 runtime startup 쪽에 더 가깝습니다.

## 주요 문서

- [Architecture](docs/architecture.md)
- [Deployment](docs/deployment.md)
- [Judging Model](docs/judging-model.md)
- [Local Development](docs/local-development.md)
- [Validation Checklist](docs/validation-checklist.md)
