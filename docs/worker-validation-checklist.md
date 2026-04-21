# Judge Worker 검증 체크리스트

이 문서는 현재 코드 기준으로 judge worker를 수동 검증할 때 확인할 항목을 정리한다.

## 1. 검증 전제

현재 DB 매핑 기준:

- `problems.id`: 문자열
- `problem_testcases.id`: UUID
- `submissions.id`: UUID
- `submission_testcase_results.id`: UUID

상태/결과 기준:

- `submissions.status`: `PENDING`, `JUDGING`, `DONE`
- `submissions.result`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`
- 완료 시각: `submissions.judged_at`

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
- worker log에 `Received submissionId=... from Redis queue judge:queue`가 보이는가
- `submissions.status`가 `PENDING -> JUDGING -> DONE`으로 바뀌는가
- `submissions.result`가 기대 결과로 저장되는가
- `submissions.judged_at`이 저장되는가
- 실행된 testcase까지만 `submission_testcase_results`가 생성되는가

## 3. DB 확인 SQL

submission 확인:

```sql
select id, problem_id, language, status, result, execution_time_ms, memory_kb, submitted_at, judged_at
from submissions
where id = :submission_id;
```

testcase 결과 확인:

```sql
select submission_id, testcase_id, result, execution_time_ms, memory_kb, error_message
from submission_testcase_results
where submission_id = :submission_id
order by testcase_id;
```

hidden testcase 확인:

```sql
select id, problem_id, testcase_order, input_text, expected_output, is_hidden
from problem_testcases
where problem_id = :problem_id
order by testcase_order;
```

## 4. 언어별 검증

샘플 파일:

- C++: `samples/submissions/cpp/*/main.cpp`
- Java: `samples/submissions/java/*/Main.java`
- Python: `samples/submissions/python/*/main.py`

확인 결과:

- AC 샘플은 `submissions.result=AC`
- WA 샘플은 `submissions.result=WA`
- RE 샘플은 `submissions.result=RE`
- TLE 샘플은 `submissions.result=TLE`
- Java compile error는 현재 코드에서 `stderr`에 `error:`가 포함되면 `CE`

C++ compile error의 CE 분류는 별도 보강이 필요할 수 있다. 현재 코드는 Java compile error를 명시적으로 CE로 매핑한다.

## 5. Remote orchestrator/runner 검증

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

## 6. 동시성 검증

기본값:

```properties
worker.max-concurrency=2
```

확인:

- 동시에 실행 중인 Redis polling worker 작업이 2개를 넘지 않는가
- 세 번째 submission은 semaphore가 풀린 뒤 처리되는가
- 실패/예외 상황에서도 semaphore가 release되는가

## 7. Cleanup 검증

확인:

- timeout 후 Docker process/container가 정리되는가
- temp workspace가 삭제되는가
- `.container.cid` 파일이 남지 않는가
- 실패해도 `submissions.status=DONE`, `submissions.result=SYSTEM_ERROR` 또는 해당 결과로 마감되는가

## 8. 완료 기준

- UUID submissionId queue/HTTP trigger가 정상 동작
- `PENDING -> JUDGING -> DONE` 전환 확인
- `result`, `judged_at`, `execution_time_ms`, `memory_kb` 저장 확인
- `submission_testcase_results` 저장 확인
- 첫 실패 이후 추가 testcase를 실행하지 않음
- Docker cleanup 확인
