# Judging Model

This document describes the current judging rules and result persistence model.

## Flow

```text
receive submissionId
-> startJudging
-> status=JUDGING
-> load hidden problem_testcases
-> execute by testcase_order
-> determine result for each executed testcase
-> stop immediately on first WA/TLE/RE/MLE
-> save final result to submissions
```

Relevant code:

- `JudgeService.runJudgeLogic(...)`
- `JudgePersistenceService.startJudging(...)`
- `JudgePersistenceService.saveResultsAndFinish(...)`
- `Submission.finish(...)`

## First Failure Policy

`JudgeService` stops when a testcase result is not `AC`.

`failedTestcaseOrder` is saved for:

- `WA`
- `TLE`
- `RE`
- `MLE`

`failedTestcaseOrder` is `null` for:

- `AC`
- `CE`
- `SYSTEM_ERROR`

`CE` is treated as compile failure and is not exposed as a testcase failure
number. `SYSTEM_ERROR` is not assumed to be caused by a specific testcase.

## Submission Result Storage

Only `submissions` stores judge results:

| Field | Meaning |
| --- | --- |
| `status` | Work state. Finished submissions use `DONE` |
| `result` | Final judge result |
| `failed_testcase_order` | First failed testcase order for WA/TLE/RE/MLE |
| `execution_time_ms` | Max execution time among executed testcases |
| `memory_kb` | Max memory usage among executed testcases |
| `submitted_at` | Submission creation time |
| `judged_at` | Judge completion time |

There is no active testcase-level result entity or repository.

## Time And Memory Basis

Submission-level `executionTimeMs` and `memoryKb` use the maximum value among
executed testcases.

Reason:

- Online judges usually compare each testcase against the same limits.
- The maximum value best represents whether the submission approached a limit.
- Last-run or summed values are less useful for limit-oriented judging.

Local Docker execution may return `null` for real memory usage. Remote runner
memory is saved when `memoryUsageKb` is provided.

## Submission Response

The current response DTO is `SubmissionResponse`.

It exposes:

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

Future controllers can build user-facing result screens from
`SubmissionResponse.from(submission)` without loading testcase result rows.
