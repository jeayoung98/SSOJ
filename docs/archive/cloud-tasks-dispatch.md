# Cloud Tasks Dispatch

이 문서는 `judge.dispatch.mode=cloud-tasks`에서 사용하는 Cloud Tasks dispatch 경로를 현재 코드 기준으로 정리한다.

## 역할

`CloudTasksJudgeDispatchService`는 `JudgeDispatchCommand`를 받아 Cloud Tasks HTTP task를 생성한다.

현재 payload는 최소 필드만 포함한다.

```json
{
  "submissionId": "00000000-0000-0000-0000-000000000001"
}
```

`submissionId`는 현재 코드 기준 `UUID` 문자열이다.

## 호출 대상

Cloud Tasks task는 orchestrator의 내부 endpoint를 호출해야 한다.

```text
POST /internal/judge-executions
```

해당 endpoint는 다음 조건에서 활성화된다.

```properties
worker.role=orchestrator
worker.mode=http-trigger
```

## 필수 설정

`application-remote.properties` 기준 필수 설정:

- `judge.dispatch.cloud-tasks.project-id`
- `judge.dispatch.cloud-tasks.location`
- `judge.dispatch.cloud-tasks.queue-name`
- `judge.dispatch.cloud-tasks.target-url`

환경변수 이름:

- `JUDGE_DISPATCH_CLOUD_TASKS_PROJECT_ID`
- `JUDGE_DISPATCH_CLOUD_TASKS_LOCATION`
- `JUDGE_DISPATCH_CLOUD_TASKS_QUEUE_NAME`
- `JUDGE_DISPATCH_CLOUD_TASKS_TARGET_URL`

## 선택 설정

인증된 Cloud Run 호출을 사용할 때 설정한다.

- `JUDGE_DISPATCH_CLOUD_TASKS_SERVICE_ACCOUNT_EMAIL`
- `JUDGE_DISPATCH_CLOUD_TASKS_OIDC_AUDIENCE`

## 로컬 경로와의 차이

로컬 기본 경로는 Cloud Tasks를 사용하지 않는다.

```properties
judge.dispatch.mode=redis
```

이때는 `RedisJudgeDispatchService`가 Redis List `judge:queue`에 UUID 문자열을 push한다.

## 현재 구현 상태

- Cloud Tasks task 생성 코드: 구현됨
- HTTP payload 생성: 구현됨
- OIDC 관련 필드 전달: 구현됨
- 실제 GCP IAM, queue 생성, Cloud Run invoker 권한: 인프라 설정 영역이며 코드 저장소 안에서 자동 생성하지 않음
