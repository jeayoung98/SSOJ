# Validation Checklist

이 문서는 현재 SSOJ 채점 백엔드의 수동 검증 항목과 자동 테스트 기대값을 정리합니다.

## 기본 실행 확인

로컬 실행:

```bash
# Spring Boot 애플리케이션을 local profile로 실행합니다.
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

테스트:

```bash
# 자동 테스트를 실행합니다.
.\gradlew.bat test
```

## Orchestrator / Runner 분리 확인

Runner:

```bash
# Runner 서버를 8081 포트로 실행합니다.
.\gradlew.bat bootRun --args="--spring.profiles.active=runner --server.port=8081"
```

Orchestrator:

```bash
# Orchestrator 서버를 8080 포트로 실행합니다.
.\gradlew.bat bootRun --args="--spring.profiles.active=remote --server.port=8080 --judge.execution.remote.base-url=http://localhost:8081"
```

Trigger:

```bash
# submissionId 기준으로 채점을 트리거합니다.
curl -X POST http://localhost:8080/internal/judge-executions -H "Content-Type: application/json" -d "{\"submissionId\":1}"
```

확인 항목:

- Orchestrator가 `/internal/judge-executions`를 수신하는가
- 요청 body에 `submissionId`가 있는가
- 정상 응답이 `202 Accepted`인가
- 존재하지 않는 `submissionId`에 대해 안전하게 실패하는가
- 이미 `DONE`인 submission을 중복 채점하지 않는가

## DB 확인

```sql
select id,
       problem_id,
       language,
       status,
       result,
       failed_testcase_order,
       execution_time_ms,
       memory_kb,
       submitted_at,
       judged_at
from submissions
where id = :submission_id;
```

확인 항목:

- 채점 시작 시 `PENDING -> JUDGING`으로 바뀌는가
- 채점 완료 시 `status=DONE`이 되는가
- 최종 결과가 `result`에 저장되는가
- `judged_at`이 채점 완료 시점에 기록되는가

## ID 확인

| Table | 기대 ID 타입 |
|---|---|
| `users` | UUID |
| `problems` | Long / identity |
| `problem_examples` | Long / identity |
| `problem_testcases` | Long / identity |
| `submissions` | Long / identity |

채점 trigger payload에는 Long 타입 `submissionId`가 들어가야 합니다.

## 채점 결과 기대값

| 상황 | 기대값 |
|---|---|
| 모든 testcase AC | `result=AC`, `failed_testcase_order=null` |
| 1번 testcase WA | `result=WA`, `failed_testcase_order=1`, 이후 testcase 미실행 |
| 중간 testcase WA/TLE/RE/MLE | 해당 order 저장, 이후 testcase 미실행 |
| CE | `result=CE`, `failed_testcase_order=null` |
| SYSTEM_ERROR | `result=SYSTEM_ERROR`, `failed_testcase_order=null` |

최종 결과는 `submissions`에만 저장합니다.

## 시간과 메모리

여러 testcase를 실행한 경우, 제출 단위 값은 실행된 testcase 중 최댓값을 사용합니다.

예시:

| testcase | executionTimeMs | memoryKb |
|---|---:|---:|
| 1 | 5 | 128 |
| 2 | 6 | 64 |

기대값:

- `submissions.execution_time_ms = 6`
- `submissions.memory_kb = 128`

Docker executor는 환경과 이미지 지원 여부에 따라 실제 memory usage를 `null`로 반환할 수 있습니다.

## Progress / SSE 확인

확인 항목:

- Frontend가 `/api/submissions/{submissionId}/events`에 연결되는가
- Runner가 `/internal/judge-progress`로 callback을 보내는가
- Orchestrator가 progress를 SSE client에게 전달하는가
- `completedTestcases`가 실행 완료 개수로 표시되는가
- UI에서 `N개 통과`처럼 잘못 표시하지 않는가
- DONE 이벤트가 유실되어도 DB polling으로 최종 결과를 확인하는가

## Runner 확인

확인 항목:

- Runner가 `/internal/runner-executions`를 수신하는가
- Runner 요청이 `testCases` 배열을 사용하는가
- Runner가 DB/Redis 없이 실행되는가
- Runner가 Docker daemon에 접근할 수 있는가
- Docker socket mount가 정상인가
- workspace mount가 정상인가
- Warm Container Pool이 enabled 상태인가
- Warm Container fallback이 비정상적으로 자주 발생하지 않는가

## Docker 정리 확인

확인 항목:

- timeout 이후 container가 남지 않는가
- 임시 workspace가 제거되는가
- 실패한 제출도 `submissions.status=DONE`으로 종료되는가
- 사용자 코드가 host network에 접근하지 못하는가

## 배포 검증

확인 항목:

- 운영 환경의 `ddl-auto`가 `validate` 또는 `none`인가
- Orchestrator가 Runner base URL에 접근할 수 있는가
- Runner host에 Docker daemon이 있는가
- Runner image에 C++ / Java / Python runtime 도구가 포함되어 있는가
- `/internal/judge-executions`, `/internal/runner-executions`, `/internal/judge-progress`를 공개 사용자 API처럼 취급하지 않는가
- Cloud Run Orchestrator single instance 설정이 현재 SSE 정책과 맞는가
- Scale-out이 필요할 경우 Redis Pub/Sub 전환 계획이 있는가
