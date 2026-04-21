# 검증 체크리스트

이 문서는 현재 코드 기준의 수동 검증과 테스트 관점을 한 곳에 모은다.

## 기본 실행 검증

로컬 실행:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue:

```powershell
redis-cli LPUSH judge:queue 00000000-0000-0000-0000-000000000001
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

## 채점 결과 검증

| 시나리오 | 기대값 |
| --- | --- |
| 모든 testcase AC | `result=AC`, `failed_testcase_order=null` |
| 1번 WA | `result=WA`, `failed_testcase_order=1`, 2번 이후 미실행 |
| 중간 testcase WA/TLE/RE/MLE | 해당 order 저장, 이후 testcase 미실행 |
| CE | `result=CE`, `failed_testcase_order=null` |
| SYSTEM_ERROR | `result=SYSTEM_ERROR`, `failed_testcase_order=null` |

실행된 testcase까지만 `submission_testcase_results`가 생긴다.

## 실행 시간/메모리 검증

여러 testcase가 실행되면 제출 단위 값은 최대값이다.

예시:

| testcase | executionTimeMs | memoryKb |
| --- | ---: | ---: |
| 1 | 5 | 128 |
| 2 | 6 | 64 |

기대:

- `submissions.execution_time_ms = 6`
- `submissions.memory_kb = 128`

local Docker executor는 실제 메모리 사용량을 `null`로 반환할 수 있다.

## Remote 검증

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
- runner가 DB/Redis 없이 실행되는가
- 결과 저장은 orchestrator가 수행하는가

## Docker cleanup 검증

확인:

- timeout 후 container가 남지 않는가
- temp workspace가 삭제되는가
- `.container.cid` 파일이 남지 않는가
- 실패해도 `submissions.status=DONE`으로 마감되는가

## 배포 전 검증

- DB에 `failed_testcase_order` 컬럼이 있는가
- 운영에서 `ddl-auto`가 `validate` 또는 `none`인가
- Cloud Tasks payload가 UUID JSON인가
- Redis payload는 로컬에서 plain UUID 문자열인가
- runner host에 Docker daemon이 있는가
- 표준 Cloud Run runner 실행 가능 여부를 확정된 것으로 문서화하지 않았는가

## Archive 참고

이전 개별 E2E, demo, cleanup, concurrency, C++ 검증 문서는 `docs/archive/`에 있다. 현재 검증 기준은 이 문서를 우선한다.
