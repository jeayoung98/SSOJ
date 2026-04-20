# Cloud Tasks 디스패치

원격 오케스트레이터 모드에서는 이제 Cloud Tasks를 통해 `submissionId`를 디스패치할 수 있습니다.

- `CloudTasksJudgeDispatchService`는 `/internal/judge-executions`를 호출하는 HTTP 작업을 생성합니다.
- 작업 payload는 `{"submissionId":123}`처럼 최소한으로 유지됩니다.
- 소스 코드와 테스트 케이스는 작업 본문에 포함되지 않습니다.
- 로컬 모드에서는 계속 `RedisJudgeDispatchService`를 사용합니다.

필수 속성:

- `judge.dispatch.cloud-tasks.project-id`
- `judge.dispatch.cloud-tasks.location`
- `judge.dispatch.cloud-tasks.queue-name`
- `judge.dispatch.cloud-tasks.target-url`

선택 속성:

- `judge.dispatch.cloud-tasks.service-account-email`
- `judge.dispatch.cloud-tasks.oidc-audience`
