# Local Orchestrator Runner

Two local roles are supported.

- `orchestrator`: owns DB-backed judging flow, hidden test case iteration, and final status persistence.
- `runner`: owns execution only and serves `POST /internal/runner-executions`.

Example ports:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=runner --server.port=8081"
```

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=remote --server.port=8080 --judge.execution.remote.base-url=http://localhost:8081"
```

Direct orchestrator trigger for end-to-end verification:

```powershell
curl -X POST http://localhost:8080/internal/judge-executions ^
  -H "Content-Type: application/json" ^
  -d "{\"submissionId\":123}"
```
