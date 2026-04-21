# 제출 결과 모델과 채점 종료 규칙

이 문서는 현재 코드 기준의 채점 종료 규칙과 제출 결과 저장/조회 모델을 정리한다.

## 1. 문서 목적

이번 기준에서 중요한 변경점은 채점이 모든 hidden testcase를 끝까지 도는 구조가 아니라, 첫 실패 testcase에서 즉시 종료하는 구조라는 점이다.

이 문서는 다음 질문에 답한다.

- 언제 채점을 중단하는가
- 어떤 값을 `submissions`에 저장하는가
- 어떤 testcase 결과가 `submission_testcase_results`에 남는가
- 실행 시간과 메모리 사용량을 어떻게 해석하는가
- 제출 조회 응답에 어떤 값을 내려줄 수 있는가

SSE 알림은 현재 범위에서 제외한다.

## 2. 현재 채점 흐름

현재 orchestrator 기준 흐름:

```text
submissionId 수신
-> JudgePersistenceService.startJudging(...)
-> submissions.status = JUDGING
-> hidden problem_testcases 조회
-> testcase_order 오름차순 실행
-> testcase별 JudgeExecutionResult 판정
-> 첫 실패 결과에서 즉시 종료
-> 실행된 testcase까지만 submission_testcase_results 저장
-> submissions.status = DONE
-> submissions.result / failed_testcase_order / execution_time_ms / memory_kb / judged_at 저장
```

관련 코드:

- `JudgeService.runJudgeLogic(...)`
- `JudgePersistenceService.startJudging(...)`
- `JudgePersistenceService.saveResultsAndFinish(...)`
- `Submission.finish(...)`

## 3. 첫 실패 즉시 종료 규칙

`JudgeService`는 hidden testcase를 순서대로 실행하다가 `AC`가 아닌 결과가 나오면 반복을 중단한다.

실패 testcase 번호를 저장하는 결과:

- `WA`
- `TLE`
- `RE`
- `MLE`

실패 testcase 번호를 저장하지 않는 결과:

- `AC`: 실패 testcase가 없음
- `CE`: 현재 코드에서는 실행 결과에서 판정되지만 특정 testcase 실패로 노출하지 않음
- `SYSTEM_ERROR`: 시스템/실행 환경 오류로 특정 testcase 실패로 단정하지 않음

따라서 AC, CE, SYSTEM_ERROR의 `failedTestcaseOrder`는 `null`이다.

## 4. 제출 결과에 저장되는 정보

`submissions`에 저장되는 주요 결과:

| 필드 | 의미 |
| --- | --- |
| `status` | 작업 상태. 완료 시 `DONE` |
| `result` | 최종 채점 결과 |
| `failed_testcase_order` | 실패한 testcase 순서. AC/CE/SYSTEM_ERROR는 `null` |
| `execution_time_ms` | 실행된 testcase 중 최대 실행 시간 |
| `memory_kb` | 실행된 testcase 중 최대 메모리 사용량 |
| `judged_at` | 채점 완료 시각 |

주의: `failed_testcase_order`는 현재 코드에 추가된 매핑이다. 기존 Supabase DB에 컬럼이 없다면 DB 반영이 필요하다. 이 저장소에는 DDL/migration을 추가하지 않는다.

## 5. testcase 결과 저장 범위

`submission_testcase_results`에는 실제 실행된 testcase 결과만 저장된다.

예시:

- 1번 AC
- 2번 WA
- 3번 미실행

저장 결과:

- 1번 testcase 결과 row 저장
- 2번 testcase 결과 row 저장
- 3번 testcase 결과 row 없음
- `submissions.failed_testcase_order = 2`
- `submissions.result = WA`

이것은 누락이 아니라 의도된 동작이다.

## 6. 실행 시간과 메모리 사용량 기준

제출 단위의 `executionTimeMs`, `memoryKb`는 실행된 testcase 중 최대값을 저장한다.

이 기준을 사용하는 이유:

- 온라인 저지에서는 testcase별 제한을 넘는지가 중요하다.
- 마지막 실행값은 실패 위치에 따라 의미가 흔들린다.
- 합산 시간은 전체 처리 비용에는 유용하지만 per-testcase time limit 판정과는 다르다.
- 현재 코드와 테스트는 최대값 기준으로 검증한다.

현재 Docker executor는 실행 시간은 측정하지만 실제 메모리 사용량은 `null`로 반환한다. remote runner가 `memoryUsageKb`를 반환하거나 Docker 측정 로직이 추가되면 `memory_kb`에 저장될 수 있다.

## 7. 시간 제한과 메모리 제한 연결

`Problem`은 이미 다음 제한 값을 가진다.

- `time_limit_ms`
- `memory_limit_mb`

현재 연결 흐름:

```text
Problem.timeLimitMs / memoryLimitMb
-> StartedJudging
-> JudgeContext
-> DockerProcessExecutor 또는 remote runner
-> JudgeExecutionResult
-> JudgeService 판정
-> Submission 결과 저장
```

현재 구현:

- timeout이면 `TLE`
- `memoryUsageKb > memoryLimitMb * 1024`이면 `MLE`
- `exitCode=137`이면 Docker OOM 가능성으로 `MLE`

실제 메모리 측정의 정확도는 실행 backend에 따라 달라진다. 현재 local Docker executor에서 실제 사용량 측정은 확실하지 않다.

## 8. 제출 조회 응답 모델

현재 저장소에는 사용자용 제출 조회 controller가 없다. 대신 다음 DTO가 있다.

- `SubmissionResponse`

응답 필드:

- `id`
- `userId`
- `problemId`
- `language`
- `status`
- `result`
- `failedTestcaseOrder`
- `executionTimeMs`
- `memoryKb`
- `submittedAt`
- `judgedAt`

프론트에서는 다음처럼 사용할 수 있다.

- `result == "AC"`: 정답 표시
- `failedTestcaseOrder != null`: `N번째 테스트케이스에서 실패` 표시
- `executionTimeMs`: 실행 시간 표시
- `memoryKb`: 메모리 사용량 표시

## 9. 향후 확장 포인트

- 사용자용 `SubmissionController` 추가
- `SubmissionResponse.from(...)` 사용 시 lazy loading을 피하기 위한 fetch join 또는 projection 도입
- Docker executor의 실제 메모리 사용량 측정
- CE 판정 로직을 언어별 compile 단계와 더 명확히 분리
- SSE 또는 realtime 알림은 별도 범위로 분리

