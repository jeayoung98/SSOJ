# Cloud Run 러너

이 서비스는 실행 전용 러너 역할입니다.

러너의 책임:

- `POST /internal/runner-executions` 요청을 받습니다.
- 한 번의 요청에 대해 코드를 실행합니다.
- `RunnerExecutionResponse`를 반환합니다.

러너는 다음 작업을 하지 않습니다.

- 제출 상태를 읽거나 쓰지 않음
- DB에서 문제나 테스트 케이스를 조회하지 않음
- 최종 채점 상태를 결정하지 않음
- hidden 테스트 케이스 반복을 조정하지 않음

제출 상태에 대한 단일 진실 공급원은 오케스트레이터입니다.

## 현재 계약

오케스트레이터는 다음 주소로 러너를 호출합니다.

- `POST {JUDGE_EXECUTION_REMOTE_BASE_URL}/internal/runner-executions`

요청 본문:

```json
{
  "submissionId": 123,
  "problemId": 456,
  "language": "python",
  "sourceCode": "print(1)",
  "input": "",
  "timeLimitMs": 1000,
  "memoryLimitMb": 128
}
```

응답 본문:

```json
{
  "success": true,
  "stdout": "1\n",
  "stderr": "",
  "exitCode": 0,
  "executionTimeMs": 10,
  "memoryUsageKb": 128,
  "systemError": false,
  "timedOut": false
}
```

## 러너 설정

러너 프로필:

- `SPRING_PROFILES_ACTIVE=runner`
- `worker.role=runner`
- `judge.execution.mode=docker`

지원 언어:

- `cpp`
- `java`
- `python`

실행 제한:

- timeout은 요청별 `timeLimitMs`로 전달됩니다.
- 메모리 제한은 요청별 `memoryLimitMb`로 전달됩니다.
- CPU 제한은 현재 executor 구현에서 1 vCPU로 고정되어 있습니다.

Executor 이미지 설정:

- `worker.executor.cpp.image`
- `worker.executor.java.image`
- `worker.executor.python.image`

## 노출 엔드포인트

러너 역할은 다음만 노출해야 합니다.

- `/internal/runner-executions`
- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

`/internal/judge-executions`는 러너 역할에 포함되지 않습니다.

## 필수 환경 변수

필수:

- `SPRING_PROFILES_ACTIVE=runner`

선택적 executor 설정:

- `WORKER_EXECUTOR_CPP_IMAGE`
- `WORKER_EXECUTOR_JAVA_IMAGE`
- `WORKER_EXECUTOR_PYTHON_IMAGE`

런타임:

- `PORT`

## 중요한 Cloud Run 제약 사항

현재 러너 구현은 `DockerProcessExecutor`, `JavaExecutor`, `PythonExecutor`, `CppExecutor`를 재사용합니다.
이 executor들은 로컬에서 `docker run`을 호출합니다.

일반적인 Cloud Run 환경은 컨테이너 내부에 Docker 데몬을 제공하지 않습니다.
즉, 현재 러너는 별도 서비스로서 설정은 가능하지만, 샌드박스 백엔드가 Cloud Run 호환 실행 방식으로 교체되기 전까지는 표준 Cloud Run에서 실제 실행은 불가능합니다.

정리하면 다음과 같습니다.

- 역할 분리는 준비되어 있음
- 엔드포인트 계약은 준비되어 있음
- Cloud Run 배포 절차는 문서화할 수 있음
- 하지만 현재 Docker 기반 executor 설계로는 표준 Cloud Run에서 실제 코드 실행이 실패함

## 배포 흐름

빌드와 배포는 동일한 애플리케이션 이미지를 사용하되, 러너 프로필로 실행합니다.

```powershell
gcloud builds submit --tag asia-northeast3-docker.pkg.dev/PROJECT_ID/REPO/ssoj-runner
```

```powershell
gcloud run deploy ssoj-runner ^
  --image asia-northeast3-docker.pkg.dev/PROJECT_ID/REPO/ssoj-runner ^
  --region asia-northeast3 ^
  --platform managed ^
  --allow-unauthenticated=false ^
  --set-env-vars SPRING_PROFILES_ACTIVE=runner
```

나중에 Docker를 대체하는 Cloud Run 호환 실행 백엔드가 들어오더라도, 이 배포 형태는 그대로 유지할 수 있습니다.

## 보안 메모

- 러너는 공개 호출이 가능하면 안 됩니다.
- `/internal/runner-executions`는 오케스트레이터 서비스만 호출할 수 있어야 합니다.
- 전용 호출자 식별자를 사용한 인증된 서비스 간 호출을 우선해야 합니다.
- 러너는 신뢰할 수 없는 코드를 실행하므로 ingress를 가능한 한 엄격하게 제한해야 합니다.
