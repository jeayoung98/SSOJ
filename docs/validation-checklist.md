# Validation Checklist

이 문서는 현재 SSOJ 채점 백엔드의 수동 검증 항목과 자동 테스트 기대값을 정리합니다.

## 기본 실행

로컬 실행:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue:

```powershell
redis-cli LPUSH judge:queue 1
```

테스트:

```powershell
.\gradlew.bat test
```

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

## ID 확인

| Table | 기대 ID 타입 |
| --- | --- |
| `users` | UUID |
| `problems` | Long / identity |
| `problem_examples` | Long / identity |
| `problem_testcases` | Long / identity |
| `submissions` | Long / identity |

Redis와 Cloud Tasks payload에는 Long 타입 `submissionId`가 들어가야 합니다.

## 채점 결과 기대값

| 상황 | 기대값 |
| --- | --- |
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
| --- | ---: | ---: |
| 1 | 5 | 128 |
| 2 | 6 | 64 |

기대값:

- `submissions.execution_time_ms = 6`
- `submissions.memory_kb = 128`

Docker executor는 환경과 이미지 지원 여부에 따라 실제 memory usage를 `null`로 반환할 수 있습니다.

## Remote 검증

Runner:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=runner --server.port=8081"
```

Orchestrator:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=remote --server.port=8080 --judge.execution.remote.base-url=http://localhost:8081"
```

Orchestrator trigger:

```powershell
curl -X POST http://localhost:8080/internal/judge-executions -H "Content-Type: application/json" -d "{\"submissionId\":1}"
```

확인 항목:

- Orchestrator가 `/internal/judge-executions`를 수신하는가
- Runner가 `/internal/runner-executions`를 수신하는가
- Runner 요청이 `testCases` 배열을 사용하는가
- Runner가 DB/Redis 없이 실행되는가
- Orchestrator가 최종 결과를 `submissions`에 저장하는가

## Docker 정리 확인

확인 항목:

- timeout 이후 container가 남지 않는가
- 임시 workspace가 제거되는가
- 실패한 제출도 `submissions.status=DONE`으로 종료되는가

## 배포 검증

- DB에 `Submission`에서 사용하는 결과 컬럼이 있는가
- 운영 환경의 `ddl-auto`가 `validate` 또는 `none`인가
- Cloud Tasks payload에 Long `submissionId`가 들어가는가
- Redis 로컬 payload가 단순 Long 문자열인가
- Runner host에 Docker daemon이 있는가
- Runner image에 필요한 runtime 도구가 포함되어 있는가
- Orchestrator가 Runner base URL에 접근할 수 있는가
- `/internal/judge-executions`, `/internal/runner-executions`를 공개 API로 취급하지 않는가
