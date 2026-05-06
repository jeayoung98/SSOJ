# Judging Model

이 문서는 현재 SSOJ의 채점 규칙, 실행 모델, 결과 저장 모델을 설명합니다.

## 기본 흐름

```text
submissionId 수신
-> submissions row 조회
-> status=PENDING 확인
-> status=JUDGING 변경
-> submission, problem limit, hidden testcase 조회
-> testcase_order 순서로 실행
-> 첫 실패 결과 발생 시 중단
-> submissions에 최종 결과 저장
-> SSE DONE 이벤트 전송
```

채점은 `submissionId` 기준으로 실행합니다.

## 상태 모델

| 상태 | 의미 |
|---|---|
| `PENDING` | 제출은 생성되었지만 아직 채점 시작 전 |
| `JUDGING` | 채점 진행 중 |
| `DONE` | 채점 완료 |

## 결과 모델

| 결과 | 의미 |
|---|---|
| `AC` | 모든 testcase 정답 |
| `WA` | 오답 |
| `CE` | 컴파일 에러 |
| `RE` | 런타임 에러 |
| `TLE` | 시간 초과 |
| `MLE` | 메모리 초과 |
| `SYSTEM_ERROR` | 내부 시스템 오류 |

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

`CE`는 컴파일 실패이므로 특정 testcase 실패 번호로 노출하지 않습니다.

`SYSTEM_ERROR`는 내부 오류이므로 특정 testcase 때문에 발생했다고 단정하지 않습니다.

## Runner 요청 모델

Orchestrator는 여러 hidden testcase를 하나의 Runner 요청으로 전달합니다.

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

## 실행 모델

현재 실행 모델은 `PER_CASE_PROCESS`입니다.

| 언어 | 실행 방식 |
|---|---|
| C++ | 컴파일 1회, testcase마다 실행 1회 |
| Java | 컴파일 1회, testcase마다 JVM 실행 1회 |
| Python | testcase마다 Python 프로세스 실행 1회 |

이 방식은 빠르지는 않지만 안정적입니다.

상태 오염, static 변수 오염, global 변수 오염, 강제 종료의 영향을 testcase 단위로 제한할 수 있습니다.

## Progress 모델

Runner는 각 testcase 실행 완료 후 progress를 생성할 수 있습니다.

Progress 예시:

```json
{
  "phase": "RUNNING",
  "completedTestcases": 37,
  "totalTestcases": 94,
  "progressPercent": 39
}
```

`completedTestcases`는 실행 완료 개수입니다.

통과 개수가 아닙니다.

UI에서는 `37개 통과`가 아니라 `테스트 실행 중... 37 / 94`처럼 표현해야 합니다.

## 최종 결과 저장

채점 결과는 `submissions`에 저장합니다.

| Field | 의미 |
|---|---|
| `status` | 완료된 제출은 `DONE` |
| `result` | 최종 채점 결과 |
| `failed_testcase_order` | WA/TLE/RE/MLE의 첫 실패 testcase order |
| `execution_time_ms` | 실행된 testcase 중 최대 실행 시간 |
| `memory_kb` | 실행된 testcase 중 최대 메모리 사용량 |
| `submitted_at` | 제출 생성 시간 |
| `judged_at` | 채점 완료 시간 |

현재 활성화된 testcase-level result 저장 모델은 없습니다.

## 시간과 메모리 기준

제출 단위 `executionTimeMs`, `memoryKb`는 실행된 testcase 중 최댓값을 사용합니다.

이유:

- 온라인 저지는 보통 각 testcase를 동일한 제한 기준으로 판정합니다.
- 최댓값은 제출이 제한에 얼마나 근접했는지 보여줍니다.
- 마지막 실행값이나 합산값은 제한 판정 관점에서 덜 유용합니다.

Docker runtime 또는 language image 지원 여부에 따라 실제 memory usage는 `null`일 수 있습니다.
