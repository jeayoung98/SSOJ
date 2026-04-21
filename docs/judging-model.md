# 채점 모델

이 문서는 현재 코드 기준의 채점 규칙과 제출 결과 저장 모델을 설명한다.

## 채점 흐름

```text
submissionId 수신
-> startJudging
-> status=JUDGING
-> hidden problem_testcases 조회
-> testcase_order 순서로 실행
-> testcase별 결과 판정
-> 첫 실패에서 즉시 종료
-> 실행된 testcase 결과만 저장
-> submission 최종 결과 저장
```

관련 코드:

- `JudgeService.runJudgeLogic(...)`
- `JudgePersistenceService.startJudging(...)`
- `JudgePersistenceService.saveResultsAndFinish(...)`
- `Submission.finish(...)`

## 첫 실패 즉시 종료

`JudgeService`는 testcase 결과가 `AC`가 아니면 이후 testcase를 실행하지 않는다.

`failedTestcaseOrder`를 저장하는 결과:

- `WA`
- `TLE`
- `RE`
- `MLE`

`failedTestcaseOrder`가 `null`인 결과:

- `AC`
- `CE`
- `SYSTEM_ERROR`

CE는 현재 코드에서 실행 결과로 판정되지만, 사용자에게 특정 testcase 실패 번호로 노출하지 않는다.

## 제출 결과 저장

`submissions`:

| 필드 | 의미 |
| --- | --- |
| `status` | 작업 상태. 완료 시 `DONE` |
| `result` | 최종 채점 결과 |
| `failed_testcase_order` | 실패 testcase 순서 |
| `execution_time_ms` | 실행된 testcase 중 최대 실행 시간 |
| `memory_kb` | 실행된 testcase 중 최대 메모리 사용량 |
| `judged_at` | 채점 완료 시각 |

`submission_testcase_results`:

| 필드 | 의미 |
| --- | --- |
| `submission_id` | 제출 ID |
| `testcase_id` | 실행된 testcase ID |
| `result` | testcase 결과 |
| `execution_time_ms` | testcase 실행 시간 |
| `memory_kb` | testcase 메모리 사용량 |
| `error_message` | stderr 또는 오류 메시지 |

첫 실패 이후 실행하지 않은 testcase 결과 row는 생성하지 않는다.

## 실행 시간과 메모리 기준

제출 단위 `executionTimeMs`, `memoryKb`는 실행된 testcase 중 최대값이다.

이유:

- 온라인 저지는 보통 testcase별 제한을 기준으로 판단한다.
- 마지막 실행값은 실패 위치에 따라 의미가 흔들린다.
- 합산값은 전체 처리 비용에는 유용하지만 현재 제한 판정 기준과 다르다.

현재 local Docker executor는 실행 시간은 측정하지만 실제 메모리 사용량은 `null`일 수 있다. remote runner가 `memoryUsageKb`를 반환하면 저장될 수 있다.

## 시간/메모리 제한 연결

`Problem` 제한값:

- `time_limit_ms`
- `memory_limit_mb`

전달 흐름:

```text
Problem
-> StartedJudging
-> JudgeContext
-> DockerProcessExecutor 또는 remote runner
-> JudgeExecutionResult
-> JudgeService 판정
```

현재 판정:

- timeout이면 `TLE`
- `memoryUsageKb > memoryLimitMb * 1024`이면 `MLE`
- exit code `137`이면 `MLE`

실제 메모리 측정 정확도는 실행 backend에 따라 달라진다.

## 제출 조회 응답

사용자용 제출 조회 controller는 현재 저장소에 없다. 응답 DTO는 있다.

- `SubmissionResponse`

포함 필드:

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

향후 controller에서 `SubmissionResponse.from(submission)`을 사용할 수 있다. 단, `user`와 `problem`은 lazy 관계이므로 transaction 안에서 변환하거나 fetch join/projection을 쓰는 편이 안전하다.

## 현재 범위 밖

- SSE/realtime 알림
- special judge
- ranking
- plagiarism check
- 강한 sandbox hardening
