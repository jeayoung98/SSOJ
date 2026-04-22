# 현재 아키텍처와 목표 아키텍처

이 문서는 현재 구현된 구조와 앞으로 운영 관점에서 확장할 수 있는 목표 구조를 구분한다. 현재 코드에 없는 기능은 구현 완료처럼 표현하지 않는다.

## 1. 현재 구현된 구조

현재 Spring Boot 코드는 judge worker 역할을 중심으로 구성되어 있다.

구성 요소:

- PostgreSQL
  - `users`
  - `problems`
  - `problem_examples`
  - `problem_testcases`
  - `submissions`
  - `submission_testcase_results`
- Redis
  - local mode queue
  - key: `judge:queue`
  - payload: UUID `submissionId`
- Spring Boot orchestrator
  - submission 조회
  - hidden testcase 조회
  - 채점 상태/결과 저장
- Spring Boot runner
  - 단일 실행 요청 처리
  - Docker 기반 실행
- Docker executor
  - C++/Java/Python 실행

## 2. 로컬 현재 흐름

```text
Next.js 또는 수동 입력
-> submissions row 생성
-> Redis judge:queue에 submissionId push
-> JudgeQueueConsumer consume
-> JudgeService 실행
-> Docker executor 실행
-> submissions / submission_testcase_results 저장
```

상태 저장 기준:

- `submissions.status`: `PENDING -> JUDGING -> DONE`
- `submissions.result`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`

## 3. 배포 현재 흐름

현재 코드가 지원하는 배포형 흐름:

```text
submissionId
-> Cloud Tasks
-> orchestrator /internal/judge-executions
-> runner /internal/runner-executions
-> orchestrator DB 저장
```

이 구조에서 orchestrator는 Cloud Run에 잘 맞는다. runner는 현재 Docker daemon 의존이 있으므로 표준 Cloud Run에서 실제 실행 가능한지는 확실하지 않다.

## 4. 목표 아키텍처 방향

목표 방향:

- orchestrator는 stateless HTTP service로 유지
- 비동기 trigger는 Cloud Tasks 사용
- runner는 격리된 실행 환경에서 단일 testcase 실행만 담당
- DB schema는 Supabase PostgreSQL 기존 테이블을 그대로 사용
- 상태와 결과는 계속 분리

아직 미확정인 부분:

- runner를 Cloud Run에서 그대로 운영할지
- Docker 가능한 VM을 runner host로 둘지
- Docker executor를 Cloud Run 호환 실행 백엔드로 바꿀지
- 강한 sandbox hardening을 어떤 방식으로 구현할지

## 5. 현재와 목표 비교

| 구분 | 현재 코드 | 목표 방향 |
| --- | --- | --- |
| queue | local은 Redis, remote는 Cloud Tasks | remote 운영은 Cloud Tasks 중심 |
| orchestrator | 구현됨 | Cloud Run 배포 가능 |
| runner | 구현됨 | 실행 host 전략 확정 필요 |
| DB schema | Supabase 기존 테이블 매핑 | DB 변경 없이 유지 |
| status/result | 분리됨 | 유지 |
| sandbox | Docker 기반 MVP | 운영 수준 hardening 필요 |
| Next.js | 저장소에 없음 | 외부 web/API가 submission 저장 후 enqueue |

## 6. 핵심 구분

현재 완료된 것은 "Spring Boot judge worker가 로컬/원격 실행 모드를 설정으로 나눠 동작할 수 있는 구조"다. 아직 완료되지 않은 것은 "표준 Cloud Run만으로 안전하게 사용자 코드를 실행하는 최종 sandbox 구조"다.
