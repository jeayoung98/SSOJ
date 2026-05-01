# Judging Model

이 문서는 현재 SSOJ의 채점 규칙과 결과 저장 모델을 설명합니다.

## 기본 흐름

```text
submissionId(Long) 수신
-> startJudging
-> status=JUDGING
-> submission, problem limit, hidden testcase 조회
-> testcase_order 순서로 실행
-> 첫 WA/TLE/RE/MLE 발생 시 즉시 중단
-> submissions에 최종 결과 저장
```

관련 코드:

- `JudgeService.runJudgeLogic(...)`
- `JudgePersistenceService.startJudging(...)`
- `JudgePersistenceService.saveResultsAndFinish(...)`
- `Submission.finish(...)`
- `RunnerExecutionService.executeSubmission(...)`
- `LanguageExecutor.executeSubmission(...)`

## 첫 실패 중단 정책

채점은 testcase 결과가 `AC`가 아닌 순간 중단합니다.

`failedTestcaseOrder`를 저장하는 결과:

- `WA`
- `TLE`
- `RE`
- `MLE`

`failedTestcaseOrder`가 `null`인 결과:

- `AC`
- `CE`
- `SYSTEM_ERROR`

`CE`는 컴파일 실패입니다. 특정 testcase 실패 번호로 노출하지 않습니다.

`SYSTEM_ERROR`는 내부 오류입니다. 특정 testcase 때문에 발생했다고 단정하지 않습니다.

## Batch 실행 정책

현재 Runner 계약은 여러 hidden testcase를 하나의 요청으로 전달합니다.

```json
{
  "submissionId": 1,
  "problemId": 1,
  "language": "python",
  "sourceCode": "print(1)",
  "testCases": [
    {
      "testCaseOrder": 1,
      "input": "",
      "expectedOutput": "1\n"
    }
  ],
  "timeLimitMs": 1000,
  "memoryLimitMb": 128
}
```

Runner는 제출 실행 batch에 대한 최종 결과 하나를 반환합니다.

```json
{
  "result": "AC",
  "executionTimeMs": 10,
  "memoryUsageKb": 128,
  "failedTestcaseOrder": null
}
```

## 제출 결과 저장

채점 결과는 `submissions`에 저장합니다.

| Field | 의미 |
| --- | --- |
| `status` | 작업 상태. 완료된 제출은 `DONE` |
| `result` | 최종 채점 결과 |
| `failed_testcase_order` | WA/TLE/RE/MLE의 첫 실패 testcase order |
| `execution_time_ms` | 실행된 testcase 중 최대 실행 시간 |
| `memory_kb` | 실행된 testcase 중 최대 메모리 사용량 |
| `submitted_at` | 제출 생성 시간 |
| `judged_at` | 채점 완료 시간 |

현재 활성화된 testcase-level result entity 또는 repository는 없습니다.

## 시간과 메모리 기준

제출 단위 `executionTimeMs`, `memoryKb`는 실행된 testcase 중 최댓값을 사용합니다.

이유:

- 온라인 저지는 보통 각 testcase를 동일한 제한 기준으로 판정합니다.
- 최댓값은 제출이 제한에 얼마나 근접했는지 보여줍니다.
- 마지막 실행값이나 합산값은 제한 판정 관점에서 덜 유용합니다.

Docker runtime 또는 language image 지원 여부에 따라 실제 memory usage는 `null`일 수 있습니다.

## 제출 응답 기준

현재 사용자에게 제공할 수 있는 결과 필드:

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

결과 화면은 testcase result row 없이 `submissions`만으로 구성할 수 있습니다.
