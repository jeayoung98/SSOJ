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

## 채점 성능 개선

### 문제와 병목 분석

Python 제출의 전체 채점 시간이 약 44초까지 늘어나고, Runner 응답 대기가 당시 30초 read timeout을 넘으면 `SYSTEM_ERROR`로 처리되는 문제가 있었습니다. 당시 트러블슈팅 기록에서 DB의 개별 테스트케이스 실행 지표는 약 270ms였지만, 제출부터 채점 완료까지는 수십 초가 걸렸습니다.

Runner 로그에서 테스트케이스마다 컨테이너 생성·실행·삭제가 반복되는 것을 확인했습니다. read timeout 조정은 응답 대기 실패를 완화하는 조치였고, 반복 실행 준비 비용을 줄이기 위해 제출 단위 Batch 채점으로 변경했습니다.

### 현재 구현

- **제출 단위 실행:** 여러 테스트케이스를 하나의 Runner 요청으로 전달하고, 제출별 작업 공간에 입력·정답 파일과 `run_all.sh`를 준비합니다.
- **컨테이너 1회 실행:** `executeBatch()`가 제출당 한 번 `docker run --rm`을 호출하고 컨테이너 안에서 테스트케이스를 순차 실행합니다.
- **컴파일·프로세스 구분:** C++·Java는 제출당 한 번 컴파일합니다. 각 테스트케이스는 새 프로세스로 실행하며, Java JVM·Python 프로세스 시작 비용은 남아 있습니다.
- **첫 실패 중단:** WA·RE·TLE·MLE 발생 시 남은 테스트케이스 실행을 중단하고 첫 실패 번호를 반환합니다.
- **지표 분리:** 전체 채점 소요 시간과 `execution_time_ms`를 구분합니다. DB 필드는 실행한 테스트케이스 중 최대 실행 시간이며, 전체 채점 시간이나 실행 시간의 합계가 아닙니다.

여기서 Batch는 **한 제출의 여러 테스트케이스를 묶어 처리**한다는 뜻입니다. 여러 제출을 합치거나, 모든 입력을 하나의 사용자 프로세스에 이어 넣는 방식은 아닙니다.

### 기록된 개선 결과

| 항목 | 개선 전 | Batch 적용 후 |
|---|---|---|
| Python 전체 채점 시간 | 약 44초 | 약 13초 |
| 컨테이너 실행 단위 | 테스트케이스마다 생성·삭제 | 제출당 1회 실행 |
| 테스트케이스 실행 | 개별 실행 | 한 컨테이너 안에서 새 프로세스로 순차 실행 |

전체 채점 시간은 약 31초, 약 70% 감소했습니다. 수치는 기존 트러블슈팅의 Python 측정 기록이며, 포트폴리오에는 테스트케이스 100개 기준으로 정리되어 있습니다. 현재 커밋을 재측정한 벤치마크는 아닙니다. 원시 측정 로그·반복 횟수·서버 사양·cold/warm 조건은 확인되지 않아 평균·p95 또는 동일 환경 반복 검증 결과로 표현하지 않습니다. 조기 종료나 컨테이너 풀의 개별 기여도도 이 수치로 분리해 주장하지 않습니다.

### Warm Container Pool 구현 상태

`WarmContainerManager`·`WarmContainerPool`에는 획득·반환·교체 로직이 있습니다. 다만 현재 언어별 Executor → `DockerProcessExecutor.executeBatch()` 경로는 풀을 호출하지 않고 `docker run`을 사용합니다. 따라서 현재 채점에 풀이 연동되어 있다고 설명하거나, 위 44초→13초를 풀 적용 효과로 설명하지 않습니다.

### 구현·측정 근거

- [Batch 실행·run_all.sh 생성·첫 실패 중단](src/main/java/com/example/ssoj/judge/executor/DockerProcessExecutor.java)
- [Python 실행 경로](src/main/java/com/example/ssoj/judge/executor/PythonExecutor.java)
- [Runner 제출 처리](src/main/java/com/example/ssoj/judge/application/sevice/RunnerExecutionService.java)
- [Warm Container Pool 구현](src/main/java/com/example/ssoj/judge/executor/WarmContainerPool.java)
- [당시 병목 분석·Python 측정 기록 (Notion, 접근 권한 필요)](https://app.notion.com/p/353239a7dca7808b8705dfd6655868c7)
- [포트폴리오의 100개 테스트케이스 비교 기록 (Notion, 접근 권한 필요)](https://app.notion.com/p/7ca239a7dca78276aa1881375a9c8c07)

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
- 현재 Batch 실행 경로는 제출당 컨테이너를 한 번 생성합니다. Warm Container Pool 클래스의 실행 경로 연동은 확인되지 않습니다.
- Java / Python은 테스트케이스마다 runtime을 시작합니다. 남아 있는 비용의 비중은 별도 측정이 필요합니다.

## 주요 문서

- [Architecture](docs/architecture.md)
- [Deployment](docs/deployment.md)
- [Judging Model](docs/judging-model.md)
- [Local Development](docs/local-development.md)
- [Validation Checklist](docs/validation-checklist.md)
