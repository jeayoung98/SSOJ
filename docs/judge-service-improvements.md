# JudgeService 개선 반영 현황

이 문서는 `JudgeService` 주변 구조에서 현재 코드에 반영된 개선 사항을 정리한다. 과거 개선 메모가 아니라 현재 동작 기준의 설명 문서다.

## 1. 현재 책임 분리

`JudgeService`는 채점 흐름을 조율한다.

- 제출 시작 처리
- hidden testcase 실행
- 첫 실패 시 조기 종료
- 결과 저장 호출

`JudgePersistenceService`는 DB 상태 변경을 담당한다.

- `PENDING -> JUDGING`
- testcase result 저장
- `status=DONE`
- `result=AC/WA/...`
- `judged_at`, 실행 시간, 메모리 저장

실제 코드 실행은 executor 또는 remote runner가 담당한다.

## 2. 상태와 결과 분리

현재 구조에서는 작업 상태와 채점 결과를 분리한다.

- `SubmissionStatus`: `PENDING`, `JUDGING`, `DONE`
- `SubmissionResult`: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR`

따라서 예전 방식처럼 `submission.status=AC`로 마감하지 않는다. 정상 채점 완료 예시는 `status=DONE`, `result=AC`다.

## 3. 첫 실패 시 조기 종료

현재 채점 로직은 hidden testcase를 순서대로 실행하다가 `AC`가 아닌 결과가 나오면 이후 testcase 실행을 중단한다.

기대 효과:

- 불필요한 Docker 실행 감소
- 평균 채점 시간 감소
- 실패 지점까지의 결과만 저장

## 4. transaction 경계

DB 작업은 시작과 종료 저장에 집중하고, Docker 실행처럼 오래 걸릴 수 있는 작업은 DB transaction 밖에서 수행하는 방향이다.

의도:

- DB connection 점유 시간 감소
- long-running executor와 persistence 책임 분리
- 동시 처리 안정성 향상

## 5. 저장 대상

`submissions`:

- `status`
- `result`
- `execution_time_ms`
- `memory_kb`
- `submitted_at`
- `judged_at`

`submission_testcase_results`:

- `submission_id`
- `testcase_id`
- `result`
- `execution_time_ms`
- `memory_kb`
- `error_message`

현재 구조에서는 `started_at`, `finished_at`, `submission_case_result`를 사용하지 않는다.

## 6. 로컬과 원격 실행 차이

local profile:

- Redis queue consume
- local Docker executor 직접 실행

remote profile:

- Cloud Tasks HTTP trigger
- remote runner HTTP 호출

runner profile:

- DB 없이 단일 실행 요청 처리

`JudgeService`의 핵심 판단 로직은 공통이고, 실행 방식은 설정과 port 구현체로 갈라진다.
