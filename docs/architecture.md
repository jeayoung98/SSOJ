# Architecture

이 문서는 현재 SSOJ 채점 백엔드의 실제 구조를 설명합니다.

## 저장소 경계

이 저장소는 Spring Boot 기반 채점 백엔드입니다.

Next.js Web/API 코드는 이 저장소에 포함하지 않습니다. 전체 서비스에서는 Next.js가 사용자 화면, 제출 API, Supabase row 생성을 담당하고, 이 저장소의 Spring Boot 애플리케이션은 `submissionId`를 기준으로 채점 작업을 수행합니다.

## 전체 인프라 구조

```text
Frontend / Next.js
-> Supabase DB
-> Cloud Run Orchestrator
-> Compute Engine Runner VM
-> Docker Sandbox Containers
```

## 구성 요소

| 구성 요소 | 역할 |
|---|---|
| Frontend / Next.js | 사용자 코드 제출, `submissions` row 생성, Orchestrator 트리거, SSE 수신 |
| Supabase DB | 문제, 예제, hidden testcase, 제출, 최종 결과 저장 |
| Cloud Run Orchestrator | 제출 조회, 테스트케이스 조회, Runner 호출, 최종 결과 저장, SSE 중계 |
| Compute Engine Runner VM | 실제 코드 실행 요청 처리 |
| Docker Sandbox Containers | 사용자 코드 격리 실행 |

## 제출 생성 흐름

```text
1. 사용자가 프론트에서 코드를 제출한다.
2. Next.js가 Supabase `submissions` 테이블에 row를 생성한다.
3. DB에서 생성된 `submissionId`를 받는다.
4. `submissionId`를 Orchestrator에 전달한다.
```

`submissionId`는 한 번의 제출/채점 작업을 식별합니다.

`problemId`는 어떤 문제인지 식별합니다.

채점 실행은 `problemId`가 아니라 `submissionId` 기준으로 돌아갑니다.

## 채점 트리거 흐름

```text
POST /internal/judge-executions
-> submissionId로 submissions row 조회
-> PENDING이면 JUDGING으로 변경
-> problemId로 hidden testcase 조회
-> Runner VM에 /internal/runner-executions 요청
-> Runner 결과 수신
-> submissions row에 최종 결과 저장
-> DONE 이벤트를 SSE로 전달
```

정상 트리거 응답은 `202 Accepted`입니다.

## Orchestrator 책임

Orchestrator는 다음을 담당합니다.

1. `submissionId` 수신
2. `submissions.status`를 `PENDING`에서 `JUDGING`으로 변경
3. 제출 코드, 언어, 문제 제한, hidden testcase 조회
4. Runner VM으로 실제 실행 위임
5. Runner progress callback 수신
6. SSE client에게 progress 전달
7. 최종 결과를 `submissions`에 저장

Orchestrator는 사용자 코드를 직접 실행하지 않습니다.

## Runner 책임

Runner는 실행 전용 컴포넌트입니다.

```text
POST /internal/runner-executions
-> RunnerExecutionService
-> LanguageExecutor
-> DockerProcessExecutor
-> Docker sandbox 실행
-> RunnerExecutionResponse 반환
```

Runner는 DB를 직접 수정하지 않습니다.

Runner는 프론트가 직접 호출하는 API가 아닙니다.

## Docker 실행 모델

현재 실행 모델은 `PER_CASE_PROCESS`입니다.

| 언어 | 실행 방식 |
|---|---|
| C++ | `g++` 컴파일 1회, 실행은 testcase마다 1회 |
| Java | `javac` 컴파일 1회, `java Main`은 testcase마다 1회 |
| Python | `python3 main.py`를 testcase마다 1회 |

장점:

- 테스트케이스 간 상태 오염을 방지합니다.
- Java static 변수 오염을 막습니다.
- Python global state 오염을 막습니다.
- `System.exit` 또는 `sys.exit` 영향이 해당 testcase 프로세스 안으로 제한됩니다.
- timeout 제어가 단순합니다.

단점:

- Java / Python은 testcase 수가 많을수록 runtime startup 비용이 커집니다.

## Warm Container 구조

Runner는 Warm Container Pool을 사용할 수 있습니다.

```text
언어별 container를 미리 실행
-> 채점 요청 시 idle container acquire
-> docker exec로 run_all.sh 실행
-> 실행 후 release
```

Warm Container는 Docker container 생성 비용을 줄여줍니다.

다만 현재 병목은 Docker 생성보다 Java / Python runtime startup 비용에 더 가깝습니다.

## SSE Progress 구조

```text
Runner run_all.sh
-> progress.jsonl 기록
-> DockerProcessExecutor polling
-> Orchestrator /internal/judge-progress callback
-> SubmissionProgressHub
-> SSE EventSource client
```

진행률 이벤트의 핵심 필드:

| 필드 | 의미 |
|---|---|
| `phase` | `RUNNING` 또는 `DONE` |
| `completedTestcases` | 실행 완료 testcase 수 |
| `totalTestcases` | 전체 testcase 수 |
| `progressPercent` | 진행률 |
| `result` | 완료 시 최종 결과 |

`completedTestcases`는 통과 개수가 아닙니다.

## SSE 한계

현재 SSE hub는 메모리 기반입니다.

Cloud Run instance가 여러 개면 다음 상황이 발생할 수 있습니다.

```text
SSE 연결 -> instance A
Progress callback -> instance B
instance B에는 해당 SSE emitter 없음
progress 유실
```

현재 운영 판단은 다음과 같습니다.

- RUNNING progress 유실은 허용합니다.
- 최종 결과는 DB polling으로 보완합니다.
- MVP 단계에서는 Cloud Run Orchestrator를 single instance로 고정할 수 있습니다.

Scale-out이 필요해지면 Redis Pub/Sub 같은 외부 브로커가 필요합니다.

## DB 모델

현재 JPA 모델 기준 활성 테이블:

| Entity | Table | ID type |
|---|---|---|
| `User` | `users` | UUID |
| `Problem` | `problems` | Long |
| `ProblemExample` | `problem_examples` | Long |
| `ProblemTestcase` | `problem_testcases` | Long |
| `Submission` | `submissions` | Long |

제출 결과 필드:

- `submissions.status`
- `submissions.result`
- `submissions.failed_testcase_order`
- `submissions.execution_time_ms`
- `submissions.memory_kb`
- `submissions.submitted_at`
- `submissions.judged_at`

테스트케이스별 결과 row는 현재 핵심 모델이 아닙니다.

## 개선 후보

우선순위는 다음과 같습니다.

1. Progress callback throttle 적용
2. 프론트 DB polling fallback 강화
3. Redis Pub/Sub 기반 SSE scale-out 구조 도입
4. Java / Python 실행 모델 개선 검토
