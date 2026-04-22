# 로컬 개발

이 문서는 로컬에서 현재 worker를 실행하고 검증하는 방법을 정리한다.

## 기본 조합

```properties
SPRING_PROFILES_ACTIVE=local
worker.role=orchestrator
worker.mode=redis-polling
judge.dispatch.mode=redis
judge.execution.mode=docker
```

흐름:

```text
submissionId(UUID)
-> Redis judge:queue
-> JudgeQueueConsumer
-> JudgeService
-> DockerExecutionGateway
-> DB 저장
```

## 필요 구성

- JDK 17
- PostgreSQL
- Redis
- Docker daemon

Docker daemon이 없으면 local Docker execution 경로는 동작하지 않는다.

## 실행

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue:

```powershell
redis-cli LPUSH judge:queue 00000000-0000-0000-0000-000000000001
```

queue payload는 JSON이 아니라 UUID 문자열이다.

## 로컬 orchestrator/runner 분리 검증

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

이 방식은 배포 전 HTTP 분리 구조를 로컬에서 확인하기 위한 용도다.

## 테스트

```powershell
.\gradlew.bat test
```

현재 테스트는 다음을 검증한다.

- 첫 실패 testcase에서 즉시 종료
- 실패 이후 testcase 미실행
- `failedTestcaseOrder` 저장
- 실행 시간/메모리 최대값 저장
- AC/CE/SYSTEM_ERROR의 실패 testcase order 처리

일부 real Docker/Testcontainers 테스트는 system property가 있어야 실행된다.

## 로컬에서 자주 확인할 DB 값

```sql
select id,
       status,
       result,
       failed_testcase_order,
       execution_time_ms,
       memory_kb,
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

## 주의

- `failed_testcase_order` 컬럼이 실제 DB에 없으면 현재 entity와 DB가 충돌한다.
- Docker executor는 현재 실제 메모리 사용량을 `null`로 반환할 수 있다.
- SSE는 로컬 실행 흐름에 포함되지 않는다.
