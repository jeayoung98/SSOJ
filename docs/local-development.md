# Local Development

이 문서는 현재 SSOJ 채점 백엔드를 로컬에서 실행하고 검증하는 방법을 정리합니다.

## 로컬 실행 방식

로컬에서는 두 가지 방식으로 검증할 수 있습니다.

1. 단일 local profile 실행
2. Orchestrator / Runner 분리 실행

배포 구조에 더 가까운 방식은 Orchestrator / Runner 분리 실행입니다.

## 필수 조건

- JDK 17
- PostgreSQL
- Docker daemon
- 필요한 경우 Redis

Docker daemon이 없으면 제출 코드를 실제로 실행할 수 없습니다.

## 단일 local profile 실행

```bash
# Spring Boot 애플리케이션을 local profile로 실행합니다.
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

로컬 queue가 Redis로 설정된 경우:

```bash
# Redis queue에 submissionId를 넣습니다.
redis-cli LPUSH judge:queue 1
```

Redis payload는 단순 Long 문자열입니다.

## Orchestrator / Runner 분리 실행

Runner:

```bash
# Runner 서버를 8081 포트로 실행합니다.
.\gradlew.bat bootRun --args="--spring.profiles.active=runner --server.port=8081"
```

Orchestrator:

```bash
# Orchestrator 서버를 8080 포트로 실행하고 Runner 주소를 localhost:8081로 지정합니다.
.\gradlew.bat bootRun --args="--spring.profiles.active=remote --server.port=8080 --judge.execution.remote.base-url=http://localhost:8081"
```

Orchestrator trigger:

```bash
# Orchestrator에 submissionId 기반 채점 실행을 요청합니다.
curl -X POST http://localhost:8080/internal/judge-executions -H "Content-Type: application/json" -d "{\"submissionId\":1}"
```

정상 응답은 `202 Accepted`입니다.

## Runner 직접 확인

Runner는 프론트가 직접 호출하는 API가 아닙니다.

다만 로컬 디버깅에서는 Runner API를 직접 호출해 실행기를 검증할 수 있습니다.

```bash
# Runner 실행 API를 직접 호출해 Docker executor를 검증합니다.
curl -X POST http://localhost:8081/internal/runner-executions -H "Content-Type: application/json" -d "{\"submissionId\":1,\"problemId\":1,\"language\":\"python\",\"sourceCode\":\"a,b=map(int,input().split())\\nprint(a+b)\",\"testCases\":[{\"testCaseOrder\":1,\"input\":\"1 2\\n\",\"expectedOutput\":\"3\\n\"}],\"timeLimitMs\":3000,\"memoryLimitMb\":128}"
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

## SSE 확인

Orchestrator가 SSE endpoint를 제공하는 경우 다음 주소로 확인합니다.

```http
GET /api/submissions/{submissionId}/events
```

브라우저 `EventSource` 또는 curl로 연결을 확인할 수 있습니다.

진행률은 실행 완료 testcase 수를 의미합니다.

통과 testcase 수로 표시하면 안 됩니다.

## 테스트 실행

```bash
# 자동 테스트를 실행합니다.
.\gradlew.bat test
```

테스트 확인 범위:

- 첫 실패 testcase에서 즉시 중단
- `failedTestcaseOrder` 저장
- 실행 시간과 메모리 최댓값 저장
- Runner profile의 DB/JPA/Redis 분리
- Remote Runner 요청/응답 매핑
- Progress callback이 채점 실패로 이어지지 않는지 확인

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
- testcase-level result table은 현재 핵심 모델이 아닙니다.
- SSE는 진행률 UX이며 최종 결과 보장은 DB polling으로 보완해야 합니다.
