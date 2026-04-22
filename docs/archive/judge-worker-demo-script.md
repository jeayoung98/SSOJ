# Judge Worker 데모 스크립트

이 문서는 현재 로컬 worker 동작을 짧게 시연하기 위한 진행 순서를 정리한다. 데모의 목적은 Redis queue, 비동기 채점, Docker 실행, DB 결과 저장이 연결되어 있음을 보여주는 것이다.

## 1. 데모 범위

보여줄 것:

- `submissions` row가 먼저 생성됨
- Redis `judge:queue`에는 UUID `submissionId`만 들어감
- worker가 비동기로 consume함
- `submissions.status`가 `PENDING -> JUDGING -> DONE`으로 변함
- `submissions.result`에 `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR` 중 하나가 저장됨
- `submission_testcase_results`가 생성됨

보여주지 않을 것:

- ranking
- plagiarism
- autoscaling
- 강한 sandbox hardening

## 2. 준비

필요한 프로세스:

- PostgreSQL
- Redis
- Docker
- Spring Boot 앱 `local` profile

준비할 데이터:

- `problems`
- `problem_testcases`
- `submissions`

## 3. 추천 화면 구성

- 터미널 1: Spring Boot worker 로그
- 터미널 2: Redis 명령
- DB 클라이언트: `submissions`, `submission_testcase_results`
- 선택: Next.js 또는 API 클라이언트

## 4. 진행 멘트

AC 제출:

- "제출은 먼저 `submissions`에 `PENDING`으로 저장됩니다."
- "queue에는 전체 소스 코드가 아니라 UUID `submissionId`만 들어갑니다."
- "worker가 Redis `judge:queue`를 polling해서 비동기로 채점합니다."
- "채점이 끝나면 `status=DONE`, `result=AC`처럼 작업 상태와 판정이 분리되어 저장됩니다."

WA/CE 제출:

- "WA는 출력 비교 실패가 `result=WA`로 저장되는지 확인합니다."
- "CE는 컴파일 실패가 `result=CE`로 저장되는지 확인합니다."
- "실패한 testcase의 상세 결과는 `submission_testcase_results`에서 확인합니다."

동시성 확인:

- "여러 제출을 queue에 넣으면 worker 설정의 동시성 기준에 따라 순차 또는 제한 병렬로 처리됩니다."

## 5. Redis 명령 예시

```powershell
redis-cli LPUSH judge:queue 018f2f1e-8d2f-7a44-9f2e-efb0c8a33f11
redis-cli LPUSH judge:queue 018f2f1e-8d2f-7a44-9f2e-efb0c8a33f12
redis-cli LPUSH judge:queue 018f2f1e-8d2f-7a44-9f2e-efb0c8a33f13
```

## 6. 로그에서 볼 내용

확인할 로그:

- Redis queue에서 `submissionId`를 읽었는지
- 제출이 `PENDING`에서 `JUDGING`으로 바뀌었는지
- Docker executor가 실행되었는지
- 최종 저장 시 `status=DONE`, `result=...`가 반영되었는지

## 7. 마무리 멘트

현재 구현은 로컬 검증 가능한 Docker 기반 worker와 배포용 orchestrator/runner 분리를 모두 포함한다. 로컬에서는 Redis와 Docker를 직접 사용하고, 배포에서는 Cloud Tasks와 runner HTTP 호출을 사용한다.
