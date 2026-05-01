# Local Development

이 문서는 현재 SSOJ 채점 백엔드를 로컬에서 실행하고 검증하는 방법을 정리합니다.

## 로컬 기본 구조

로컬 기본 실행은 Redis polling과 Docker executor를 사용합니다.

```properties
SPRING_PROFILES_ACTIVE=local
worker.role=orchestrator
worker.mode=redis-polling
judge.dispatch.mode=redis
judge.execution.mode=docker
```

흐름:

```text
submissionId(Long)
-> Redis judge:queue
-> JudgeQueueConsumer
-> JudgeService
-> DockerExecutionGateway
-> LanguageExecutor
-> DockerProcessExecutor
-> submissions update
```

## 필수 조건

- JDK 17
- PostgreSQL
- Redis
- Docker daemon

Docker daemon이 없으면 제출 코드를 실제로 실행할 수 없습니다.

## 로컬 Orchestrator 실행

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue:

```powershell
redis-cli LPUSH judge:queue 1
```

queue payload는 단순한 Long 문자열입니다.

## 로컬 Orchestrator / Runner 분리 실행

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

Runner 직접 확인:

```powershell
curl -X POST http://localhost:8081/internal/runner-executions -H "Content-Type: application/json" -d "{\"submissionId\":1,\"problemId\":1,\"language\":\"python\",\"sourceCode\":\"a,b=map(int,input().split())\nprint(a+b)\",\"testCases\":[{\"testCaseOrder\":1,\"input\":\"1 2\n\",\"expectedOutput\":\"3\n\"}],\"timeLimitMs\":3000,\"memoryLimitMb\":128}"
```

예상 응답:

```json
{
  "result": "AC",
  "executionTimeMs": 10,
  "memoryUsageKb": 128,
  "failedTestcaseOrder": null
}
```

## 테스트 실행

```powershell
.\gradlew.bat test
```

테스트 확인 범위:

- 첫 실패 testcase에서 즉시 중단
- `failedTestcaseOrder` 저장
- 실행 시간과 메모리 최댓값 저장
- Runner profile의 DB/JPA/Redis 분리
- Remote Runner 요청/응답 매핑
- Cloud Tasks dispatch payload 생성

## DB 확인 쿼리

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

## 참고 사항

- 이 저장소는 DDL/migration 파일을 포함하지 않습니다.
- 외부 DB schema는 JPA entity와 맞아야 합니다.
- 현재 결과 조회는 `submissions` 중심입니다.
- 과거 testcase-level result table은 현재 핵심 모델이 아닙니다.
- SSE는 이 저장소의 worker flow에 포함하지 않습니다.
