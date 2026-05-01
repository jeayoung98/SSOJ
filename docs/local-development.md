# 로컬 개발

이 문서는 현재 worker를 로컬에서 실행하고 검증하는 방법을 정리합니다.

## 기본 로컬 설정

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
-> submissions update
```

## 필수 조건

- JDK 17
- PostgreSQL
- Redis
- Docker daemon

Docker daemon이 없으면 로컬 Docker 실행 경로로 제출 코드를 실행할 수 없습니다.

## 로컬 Orchestrator 실행

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue:

```powershell
redis-cli LPUSH judge:queue 1
```

queue payload는 단순한 Long 문자열입니다. JSON도 아니고 UUID도 아닙니다.

## 로컬 Orchestrator/Runner 분리 실행

배포 환경과 같은 경계를 검증할 때 사용합니다.

Runner:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=runner --server.port=8081"
```

Orchestrator:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=remote --server.port=8080 --judge.execution.remote.base-url=http://localhost:8081"
```

orchestrator trigger:

```powershell
curl -X POST http://localhost:8080/internal/judge-executions ^
  -H "Content-Type: application/json" ^
  -d "{\"submissionId\":1}"
```

runner 직접 smoke test:

```powershell
curl -X POST http://localhost:8081/internal/runner-executions ^
  -H "Content-Type: application/json" ^
  -d "{\"submissionId\":1,\"problemId\":1,\"language\":\"python\",\"sourceCode\":\"a,b=map(int,input().split())\nprint(a+b)\",\"testCases\":[{\"testCaseOrder\":1,\"input\":\"1 2\n\",\"expectedOutput\":\"3\n\"}],\"timeLimitMs\":3000,\"memoryLimitMb\":128}"
```

예상 runner 응답 예시:

```json
{
  "result": "AC",
  "executionTimeMs": 10,
  "memoryUsageKb": 128,
  "failedTestcaseOrder": null
}
```

실제 시간과 메모리 값은 Docker runtime 환경에 따라 달라질 수 있습니다.

## 테스트

```powershell
.\gradlew.bat test
```

테스트가 확인하는 내용:

- 첫 실패 테스트케이스에서 즉시 중단
- 실패 이후 테스트케이스 미실행
- `failedTestcaseOrder` 저장
- 실행 시간과 메모리 최댓값 저장
- AC/CE/SYSTEM_ERROR의 failed order 처리
- runner profile이 DB/JPA/Redis에서 분리되는지 확인
- remote runner 요청/응답 매핑
- Cloud Tasks dispatch payload 생성

## 유용한 DB 확인 쿼리

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

사용자에게 보여줄 제출 결과 조회에는 테스트케이스별 결과 테이블이 필요하지 않습니다.

## 참고 사항

- 이 저장소는 DDL/migration 파일을 추가하지 않습니다.
- 기존 DB에 `submission_testcase_results`가 남아 있다면, 해당 테이블 삭제는 코드 외부의 후속 DB 정리 작업입니다.
- Docker executor는 환경과 이미지 지원 여부에 따라 실제 memory usage를 `null`로 반환할 수 있습니다.
- SSE는 로컬 worker flow에 포함되지 않습니다.
