# Judge Worker 검증 체크리스트

이 문서는 현재 코드 기준으로 judge worker를 수동 또는 테스트로 검증할 때 확인할 항목을 정리한다.

## 1. 검증 전제

현재 DB 매핑 기준:

- `problems.id`: 문자열
- `problem_testcases.id`: UUID
- `submissions.id`: UUID
- `submission_testcase_results.id`: UUID

상태/결과 기준:

- `submissions.status`: `PENDING`, `JUDGING`, `DONE`
- `submissions.result`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`
- `submissions.failed_testcase_order`: 실패 testcase 순서
- `submissions.execution_time_ms`: 실행된 testcase 중 최대 실행 시간
- `submissions.memory_kb`: 실행된 testcase 중 최대 메모리 사용량
- 완료 시각: `submissions.judged_at`

주의: `failed_testcase_order`가 실제 DB에 없다면 현재 코드와 DB 스키마가 충돌한다.

## 2. 로컬 Redis polling 검증

설정:

```properties
worker.role=orchestrator
worker.mode=redis-polling
judge.dispatch.mode=redis
judge.execution.mode=docker
```

실행:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

enqueue:

```powershell
redis-cli LPUSH judge:queue 00000000-0000-0000-0000-000000000001
```

확인:

- Redis payload가 UUID 문자열인가
- `submissions.status`가 `PENDING -> JUDGING -> DONE`으로 바뀌는가
- `submissions.result`가 기대 결과로 저장되는가
- `submissions.failed_testcase_order`가 실패 결과에서 저장되는가
- AC/CE/SYSTEM_ERROR의 `failed_testcase_order`가 `null`인가
- `submissions.execution_time_ms`, `submissions.memory_kb`가 저장되는가
- 실행된 testcase까지만 `submission_testcase_results`가 생성되는가

## 3. DB 확인 SQL

submission 확인:

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

testcase 결과 확인:

```sql
select submission_id,
       testcase_id,
       result,
       execution_time_ms,
       memory_kb,
       error_message
from submission_testcase_results
where submission_id = :submission_id;
```

hidden testcase 확인:

```sql
select id, problem_id, testcase_order, input_text, expected_output, is_hidden
from problem_testcases
where problem_id = :problem_id
order by testcase_order;
```

## 4. 첫 실패 즉시 종료 검증

예시:

- 1번 testcase: AC
- 2번 testcase: WA
- 3번 testcase: 실행되면 안 됨

기대 결과:

- `submissions.result = WA`
- `submissions.failed_testcase_order = 2`
- `submission_testcase_results` row는 1번, 2번만 존재
- 3번 testcase result row는 없음
- executor 실행 횟수는 2회

이 동작은 의도된 동작이다. 실패 이후 testcase 결과가 없는 것을 누락으로 보면 안 된다.

## 5. 실행 시간/메모리 검증

여러 testcase가 실행된 경우 제출 단위 저장값은 최대값이다.

예시:

| testcase | executionTimeMs | memoryKb |
| --- | ---: | ---: |
| 1 | 5 | 128 |
| 2 | 6 | 64 |

기대 저장값:

- `submissions.execution_time_ms = 6`
- `submissions.memory_kb = 128`

현재 local Docker executor는 실제 메모리 사용량을 `null`로 반환할 수 있다. 이 경우 `memory_kb`도 `null`일 수 있다.

## 6. 결과별 검증

- AC: 모든 hidden testcase가 AC이고 `failed_testcase_order`는 `null`
- WA: 출력 비교 실패 testcase에서 즉시 종료하고 해당 `testcase_order` 저장
- RE: 실행 실패 testcase에서 즉시 종료하고 해당 `testcase_order` 저장
- TLE: timeout testcase에서 즉시 종료하고 해당 `testcase_order` 저장
- MLE: 메모리 초과 testcase에서 즉시 종료하고 해당 `testcase_order` 저장
- CE: 현재 사용자에게 노출하는 실패 testcase order는 `null`
- SYSTEM_ERROR: `failed_testcase_order`는 `null`

## 7. Remote orchestrator/runner 검증

runner:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=runner --server.port=8081"
```

orchestrator:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=remote --server.port=8080 --judge.execution.remote.base-url=http://localhost:8081"
```

trigger:

```powershell
curl -X POST http://localhost:8080/internal/judge-executions ^
  -H "Content-Type: application/json" ^
  -d "{\"submissionId\":\"00000000-0000-0000-0000-000000000001\"}"
```

확인:

- orchestrator가 `/internal/judge-executions`를 받는가
- runner가 `/internal/runner-executions`를 받는가
- runner는 DB/Redis 없이 실행되는가
- 최종 결과는 orchestrator가 DB에 저장하는가
- remote runner의 `memoryUsageKb`가 있으면 `submissions.memory_kb`에 반영되는가

## 8. 완료 기준

- UUID submissionId queue/HTTP trigger 정상 동작
- `PENDING -> JUDGING -> DONE` 전환
- `result`, `judged_at`, `failed_testcase_order`, `execution_time_ms`, `memory_kb` 저장
- 첫 실패 이후 추가 testcase 미실행
- 실행된 testcase까지만 `submission_testcase_results` 저장
- Docker cleanup 확인

