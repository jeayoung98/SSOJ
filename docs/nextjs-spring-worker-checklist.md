# Next.js/API와 Spring Worker 연동 체크리스트

이 문서는 전체 서비스에서 Web/API 계층이 Spring judge worker와 연동할 때 지켜야 할 계약을 정리한다.

현재 이 저장소에는 Next.js 소스가 포함되어 있지 않다. 따라서 이 문서는 실제 Next.js 코드 검증 문서가 아니라, Spring worker가 기대하는 외부 계약을 현재 코드 기준으로 정리한 문서다.

## 1. DB 저장 계약

Web/API는 enqueue 전에 `submissions` row를 먼저 저장해야 한다.

필수 기준:

- `submissions.id`: UUID
- `submissions.user_id`: UUID
- `submissions.problem_id`: `problems.id`와 같은 문자열
- `submissions.language`: `cpp`, `java`, `python` 중 하나
- `submissions.source_code`: 제출 코드
- `submissions.status`: `PENDING`
- `submissions.submitted_at`: 제출 시각

채점 전에는 일반적으로 다음 값이 비어 있을 수 있다.

- `submissions.result`
- `submissions.execution_time_ms`
- `submissions.memory_kb`
- `submissions.judged_at`

## 2. Redis queue 계약

로컬 Redis polling 경로에서 Web/API는 다음 queue에 UUID 문자열만 push한다.

- key: `judge:queue`
- value: `submissionId` UUID 문자열

예:

```powershell
redis-cli LPUSH judge:queue 00000000-0000-0000-0000-000000000001
```

JSON payload가 아니라 plain UUID 문자열이다.

## 3. Cloud Tasks 계약

배포 remote 경로에서 Cloud Tasks HTTP payload는 다음 형태다.

```json
{
  "submissionId": "00000000-0000-0000-0000-000000000001"
}
```

호출 대상:

```text
POST /internal/judge-executions
```

## 4. Worker 처리 흐름

1. Web/API가 `submissions` row를 `PENDING`으로 저장
2. Web/API 또는 dispatch 계층이 `submissionId` 전달
3. worker가 submission을 조회
4. worker가 `PENDING -> JUDGING`
5. worker가 `problem_testcases`의 hidden testcase를 순서대로 실행
6. 첫 실패 시 중단
7. 실행된 testcase 결과만 `submission_testcase_results`에 저장
8. worker가 `submissions.status=DONE`, `submissions.result=<결과>`, `submissions.judged_at=<완료시각>` 저장

## 5. 빠른 점검 목록

- `submissionId`가 UUID 문자열인가
- queue key가 `judge:queue`인가
- Redis payload가 JSON이 아니라 plain UUID 문자열인가
- 초기 `submissions.status`가 `PENDING`인가
- `language`가 `cpp`, `java`, `python` 중 하나인가
- `problem_id`가 `problems.id` 문자열과 일치하는가
- 해당 problem에 hidden `problem_testcases`가 있는가
- worker 처리 후 `submissions.status=DONE`이 되는가
- worker 처리 후 `submissions.result`가 저장되는가
- testcase별 결과가 `submission_testcase_results`에 저장되는가

## 6. 문제 발생 시 먼저 볼 곳

submission이 계속 `PENDING`:

- enqueue가 안 됨
- Redis key 불일치
- UUID payload 파싱 실패
- worker 비활성화
- remote 경로에서 Cloud Tasks 미전달

submission이 `JUDGING`에서 멈춤:

- executor 예외
- Docker daemon 문제
- runner 호출 실패
- DB 저장 실패

`SYSTEM_ERROR`:

- 지원하지 않는 언어
- Docker image/daemon 문제
- remote runner 장애
- executor 예외

## 7. 이 저장소에서 확인할 코드

- `JudgeQueueConsumer`: Redis queue consume
- `JudgeExecutionController`: Cloud Tasks/HTTP trigger endpoint
- `JudgeService`: 채점 흐름
- `JudgePersistenceService`: DB 상태 전환과 결과 저장
- `RedisJudgeDispatchService`: Redis dispatch
- `CloudTasksJudgeDispatchService`: Cloud Tasks dispatch
