# SSOJ

SSOJ는 사용자가 제출한 C++ / Java / Python 코드를 Docker 환경에서 실행하고, 결과를 저장하는 온라인 저지 채점 백엔드입니다.

이 저장소는 **Spring Boot 기반 채점 백엔드**만 포함합니다. Next.js Web/API는 별도 계층으로 보고, 이 저장소는 제출 ID를 받아 채점 파이프라인을 수행하는 역할에 집중합니다.

## 현재 구현 범위

| 영역 | 현재 역할 |
| --- | --- |
| Spring Boot Orchestrator | 제출 상태 조회, 채점 시작, 테스트케이스 조회, Runner 호출, 최종 결과 저장 |
| Spring Boot Runner | Docker 기반 코드 컴파일/실행, 실행 결과 반환 |
| Cloud Tasks | 배포 환경에서 채점 작업을 비동기로 트리거 |
| Redis | 로컬 개발 환경에서만 사용하는 비동기 dispatch 경로 |
| Docker Sandbox | C++ / Java / Python 제출 코드 격리 실행 |
| PostgreSQL / Supabase | 문제, 예제, hidden testcase, 제출, 최종 채점 결과 저장 |

이 저장소에 포함하지 않는 영역:

- Next.js Web/API 구현
- 랭킹
- 표절 검사
- Special Judge
- 인터랙티브 문제 채점
- 다중 Runner 오토스케일링
- 고도화된 sandbox hardening

## 전체 구조

현재 포트폴리오 기준 전체 서비스 흐름은 다음과 같습니다.

```text
Browser
-> Next.js Web/API
-> Cloud Run Orchestrator
-> Cloud Tasks
-> Cloud Run Orchestrator /internal/judge-executions
-> GCE Runner VM /internal/runner-executions
-> Docker Sandbox
-> Cloud Run Orchestrator
-> PostgreSQL / Supabase
-> 사용자 결과 조회
```

저장소 내부 관점에서는 다음 구조로 나뉩니다.

```text
Orchestrator
-> submission + hidden testcase 조회
-> local Docker executor 또는 remote runner 호출
-> 첫 실패 테스트케이스에서 즉시 중단
-> submissions에 최종 결과 저장
```

## 핵심 설계 결정

### 1. 웹 요청과 코드 실행 분리

사용자 코드는 실행 시간이 예측되지 않고 CPU/메모리를 많이 사용할 수 있습니다. 따라서 Web/API가 직접 코드를 실행하지 않고, Orchestrator와 Runner를 분리했습니다.

### 2. Cloud Run Orchestrator + GCE Runner VM

Orchestrator는 Cloud Run에 배포해 요청 처리와 작업 조율을 담당합니다. Runner는 Docker daemon 접근이 필요하므로 GCE VM에서 실행하는 구조를 기준으로 합니다.

### 3. Cloud Tasks 기반 비동기 채점

배포 환경에서는 Cloud Tasks가 Orchestrator의 내부 채점 API를 호출합니다. 이를 통해 제출 요청과 실제 채점 실행을 분리하고, 실패 시 재시도와 dispatch 제어를 GCP 관리형 서비스에 위임합니다.

### 4. Docker 기반 격리 실행

C++ / Java / Python 코드는 Runner의 Docker executor를 통해 격리 실행합니다. 실행 시간, 메모리, 네트워크, 임시 작업 디렉터리 정리를 Runner 책임으로 둡니다.

### 5. 최종 결과 중심 저장

테스트케이스별 결과 row는 저장하지 않습니다. 대신 `submissions`에 최종 결과, 실행 시간, 메모리 사용량, 첫 실패 테스트케이스 순서를 저장합니다.

## 채점 정책

- Hidden testcase는 `testcase_order` 순서로 실행합니다.
- Runner 요청은 여러 testcase를 `testCases` 배열로 전달하는 batch 구조입니다.
- 첫 `WA`, `TLE`, `RE`, `MLE` 발생 시 즉시 중단합니다.
- `CE`는 컴파일 실패이므로 특정 testcase 번호와 연결하지 않습니다.
- `SYSTEM_ERROR`는 내부 오류이므로 특정 testcase 실패로 단정하지 않습니다.
- 제출 단위 `execution_time_ms`, `memory_kb`는 실행된 testcase 중 최댓값을 저장합니다.

## 결과 저장 모델

최종 결과는 `submissions` 테이블에 저장합니다.

| 컬럼 | 의미 |
| --- | --- |
| `status` | 작업 상태: `PENDING`, `JUDGING`, `DONE` |
| `result` | 채점 결과: `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR` |
| `execution_time_ms` | 실행된 testcase 중 최대 실행 시간 |
| `memory_kb` | 실행된 testcase 중 최대 메모리 사용량 |
| `failed_testcase_order` | 첫 실패 testcase 번호. `AC`, `CE`, `SYSTEM_ERROR`는 `null` |
| `submitted_at` | 제출 생성 시각 |
| `judged_at` | 채점 완료 시각 |

## DB 기준

현재 JPA 기준 활성 테이블은 다음과 같습니다.

| Entity | Table | ID type |
| --- | --- | --- |
| `User` | `users` | `UUID` |
| `Problem` | `problems` | `Long` |
| `ProblemExample` | `problem_examples` | `Long` |
| `ProblemTestcase` | `problem_testcases` | `Long` |
| `Submission` | `submissions` | `Long` |

현재 저장소에는 DDL 또는 migration 파일을 포함하지 않습니다. 외부 DB 스키마는 JPA 모델과 직접 맞춰야 합니다.

## Profile 구성

| Profile | 목적 | Dispatch | Execution |
| --- | --- | --- | --- |
| `local` | 로컬 개발/검증 | Redis polling | Local Docker executor |
| `remote` | 배포 Orchestrator | Cloud Tasks HTTP trigger | Remote Runner HTTP 호출 |
| `runner` | 실행 전용 Runner | 없음 | Docker executor |

## 로컬 실행

필수 조건:

- JDK 17
- PostgreSQL
- Redis
- Docker daemon

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue 예시:

```powershell
redis-cli LPUSH judge:queue 1
```

Redis payload는 단순한 `submissionId` Long 문자열입니다.

## 로컬 Orchestrator / Runner 분리 실행

Runner:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=runner --server.port=8081"
```

Orchestrator:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=remote --server.port=8080 --judge.execution.remote.base-url=http://localhost:8081"
```

Trigger:

```powershell
curl -X POST http://localhost:8080/internal/judge-executions ^
  -H "Content-Type: application/json" ^
  -d "{\"submissionId\":1}"
```

## 테스트 실행

```powershell
.\gradlew.bat test
```

테스트는 다음 내용을 검증합니다.

- 첫 실패 testcase에서 즉시 중단
- `failedTestcaseOrder` 저장
- 실행 시간/메모리 최댓값 저장
- `AC`, `CE`, `SYSTEM_ERROR`의 failed order 처리
- Runner profile의 DB/Redis 분리
- Remote Runner 요청/응답 매핑
- Cloud Tasks dispatch payload 생성

## 주요 문서

- [Architecture](docs/architecture.md)
- [Local Development](docs/local-development.md)
- [Deployment](docs/deployment.md)
- [Judging Model](docs/judging-model.md)
- [Validation Checklist](docs/validation-checklist.md)
