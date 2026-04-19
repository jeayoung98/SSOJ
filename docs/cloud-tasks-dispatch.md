# Cloud Tasks Dispatch

Remote orchestrator mode can now dispatch `submissionId` through Cloud Tasks.

- `CloudTasksJudgeDispatchService` creates an HTTP task that calls `/internal/judge-executions`.
- Task payload stays minimal: `{"submissionId":123}`.
- Source code and test cases are not included in the task body.
- Local mode still uses `RedisJudgeDispatchService`.

Required properties:

- `judge.dispatch.cloud-tasks.project-id`
- `judge.dispatch.cloud-tasks.location`
- `judge.dispatch.cloud-tasks.queue-name`
- `judge.dispatch.cloud-tasks.target-url`

Optional properties:

- `judge.dispatch.cloud-tasks.service-account-email`
- `judge.dispatch.cloud-tasks.oidc-audience`
