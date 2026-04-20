# 로컬 오케스트레이터와 러너

두 가지 로컬 역할을 지원합니다.

- `orchestrator`: DB 기반 채점 흐름, hidden 테스트 케이스 반복, 최종 상태 저장을 담당합니다.
- `runner`: 실행만 담당하며 `POST /internal/runner-executions`를 제공합니다.

예시 포트:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=runner --server.port=8081"
```

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=remote --server.port=8080 --judge.execution.remote.base-url=http://localhost:8081"
```

엔드 투 엔드 검증을 위한 오케스트레이터 직접 트리거:

```powershell
curl -X POST http://localhost:8080/internal/judge-executions ^
  -H "Content-Type: application/json" ^
  -d "{\"submissionId\":123}"
```
