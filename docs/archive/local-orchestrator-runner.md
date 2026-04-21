# 로컬 Orchestrator/Runner 분리 실행

이 문서는 한 개발 머신에서 orchestrator와 runner를 HTTP로 분리해 검증하는 방법을 정리한다.

## 역할

- `orchestrator`
  - DB에서 submission과 hidden testcase를 읽는다.
  - testcase마다 runner를 호출한다.
  - 최종 `submissions.status=DONE`, `submissions.result=<AC/WA/...>`를 저장한다.
- `runner`
  - `POST /internal/runner-executions` 요청을 받는다.
  - 한 testcase 실행 결과만 반환한다.
  - DB/Redis에 접근하지 않는다.

## 실행 순서

### 1. Runner 실행

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=runner --server.port=8081"
```

### 2. Orchestrator 실행

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=remote --server.port=8080 --judge.execution.remote.base-url=http://localhost:8081"
```

`remote` profile은 `worker.mode=http-trigger`, `judge.execution.mode=remote`를 사용한다.

## 수동 trigger 예시

`submissionId`는 현재 코드 기준 UUID 문자열이어야 한다.

```powershell
curl -X POST http://localhost:8080/internal/judge-executions ^
  -H "Content-Type: application/json" ^
  -d "{\"submissionId\":\"00000000-0000-0000-0000-000000000001\"}"
```

## Runner 직접 호출 예시

```powershell
curl -X POST http://localhost:8081/internal/runner-executions ^
  -H "Content-Type: application/json" ^
  -d "{\"submissionId\":\"00000000-0000-0000-0000-000000000001\",\"problemId\":\"1000\",\"language\":\"python\",\"sourceCode\":\"print(1)\",\"input\":\"\",\"timeLimitMs\":1000,\"memoryLimitMb\":128}"
```

## 주의

- orchestrator는 DB 연결이 필요하다.
- runner는 Docker daemon이 필요하다.
- runner profile은 DB/JPA/Redis auto-configuration을 끈다.
- 이 문서는 로컬 HTTP 분리 검증용이며, 실제 배포 권한/IAM/ingress 설정은 `cloud-run-orchestrator.md`, `cloud-run-runner.md`, `deployment-readiness.md`를 따른다.
