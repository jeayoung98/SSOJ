# Cloud Run Runner 문서

이 문서는 현재 코드 기준의 runner 역할과 실행 계약을 정리한다. runner는 채점 전체를 판단하는 서비스가 아니라, orchestrator가 넘긴 단일 테스트 입력을 실행하고 실행 결과만 반환하는 실행 전용 컴포넌트다.

## 1. runner 역할

runner가 하는 일:

- `POST /internal/runner-executions` 요청 수신
- 요청에 포함된 `language`, `sourceCode`, `input` 실행
- 실행 결과를 `RunnerExecutionResponse`로 반환

runner가 하지 않는 일:

- `submissions` 조회 또는 수정
- `problem_testcases` 조회
- `submission_testcase_results` 저장
- 최종 채점 결과 결정
- Redis 또는 Cloud Tasks 사용

## 2. 현재 HTTP 계약

orchestrator는 다음 주소로 runner를 호출한다.

```text
POST {JUDGE_EXECUTION_REMOTE_BASE_URL}/internal/runner-executions
```

요청 예시:

```json
{
  "submissionId": "018f2f1e-8d2f-7a44-9f2e-efb0c8a33f11",
  "problemId": "A_PLUS_B",
  "language": "python",
  "sourceCode": "print(sum(map(int, input().split())))",
  "input": "1 2\n",
  "timeLimitMs": 1000,
  "memoryLimitMb": 128
}
```

응답 예시:

```json
{
  "success": true,
  "stdout": "3\n",
  "stderr": "",
  "exitCode": 0,
  "executionTimeMs": 10,
  "memoryUsageKb": 128,
  "systemError": false,
  "timedOut": false
}
```

주의:

- `submissionId`는 `submissions.id`와 같은 UUID 문자열이다.
- `problemId`는 `problems.id`와 같은 문자열이다.
- runner 응답의 `memoryUsageKb`는 orchestrator가 `submissions.memory_kb`, `submission_testcase_results.memory_kb`로 저장할 수 있는 실행 메타데이터다.

## 3. 실행 설정

runner 프로필:

```properties
SPRING_PROFILES_ACTIVE=runner
worker.role=runner
worker.mode=runner
judge.execution.mode=docker
```

현재 `application-runner.properties`는 runner가 DB, JPA, Redis 자동 설정에 의존하지 않도록 제외한다. runner는 실행 요청 본문만으로 동작한다.

지원 언어:

- `cpp`
- `java`
- `python`

Docker executor 이미지 설정:

- `WORKER_EXECUTOR_CPP_IMAGE`
- `WORKER_EXECUTOR_JAVA_IMAGE`
- `WORKER_EXECUTOR_PYTHON_IMAGE`

## 4. Cloud Run 적용 시 주의점

현재 runner 구현은 내부에서 로컬 Docker daemon을 호출하는 `DockerProcessExecutor` 기반이다. 일반적인 Cloud Run 컨테이너 안에서는 Docker daemon이 제공되지 않는다.

따라서 현재 코드 기준의 정확한 판단은 다음과 같다.

- runner 역할 분리와 HTTP 계약은 준비되어 있다.
- runner 프로필 설정도 준비되어 있다.
- 하지만 표준 Cloud Run 환경에서 실제 사용자 코드를 Docker로 실행하는 것은 확실하지 않다.
- 실제 운영 실행이 필요하면 Docker 실행이 가능한 VM, 별도 sandbox host, 또는 Cloud Run 호환 실행 백엔드로 교체가 필요하다.

## 5. 노출 엔드포인트

runner에서 운영상 필요한 엔드포인트:

- `/internal/runner-executions`
- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

runner는 `/internal/judge-executions`를 처리하지 않는다. 해당 엔드포인트는 orchestrator 역할이다.

## 6. 배포 예시

실제 Cloud Run에서 Docker 실행이 가능한지는 별도 검증이 필요하다. 아래 명령은 runner 서비스 형태로 배포하는 예시일 뿐이다.

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

## 7. 보안 기준

- `/internal/runner-executions`는 공개 API가 아니다.
- orchestrator만 runner를 호출할 수 있어야 한다.
- runner는 신뢰할 수 없는 코드를 실행하므로 ingress, service account, 네트워크 권한을 최소화해야 한다.
- 현재 sandbox는 MVP 수준이며, 강한 격리 환경으로 표현하면 안 된다.
