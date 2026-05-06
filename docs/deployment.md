# Deployment

이 문서는 현재 SSOJ 채점 백엔드의 배포 기준을 정리합니다.

## 권장 배포 구조

| 구성 요소 | 권장 위치 | 이유 |
|---|---|---|
| Orchestrator | Cloud Run | 요청 기반 실행, 관리형 배포, HTTPS endpoint 제공 |
| Runner | Compute Engine VM | Docker daemon 접근 필요 |
| DB | Supabase PostgreSQL | 문제, 테스트케이스, 제출, 결과 저장 |
| Docker runtime | Runner VM 내부 | 사용자 코드 격리 실행 |

Runner는 Docker daemon 접근이 필요합니다.

따라서 표준 Cloud Run 환경에서 Runner가 Docker executor를 그대로 실행할 수 있다고 전제하면 안 됩니다.

## 배포 흐름

```text
Next.js Web/API
-> Supabase submissions row 생성
-> Cloud Run Orchestrator /internal/judge-executions
-> GCE Runner VM /internal/runner-executions
-> Docker Sandbox
-> Runner progress callback
-> Orchestrator SSE emit
-> Orchestrator submissions 결과 저장
```

## Orchestrator profile

배포 Orchestrator는 `remote` profile을 사용합니다.

```text
SPRING_PROFILES_ACTIVE=remote
worker.role=orchestrator
worker.mode=http-trigger
judge.execution.mode=remote
```

Orchestrator 책임:

- `/internal/judge-executions` 수신
- `submissionId` 기준 제출 조회
- `PENDING -> JUDGING` 상태 변경
- hidden testcase 조회
- Runner 호출
- Runner progress callback 수신
- SSE progress 전달
- 최종 결과 저장

## Runner profile

Runner는 `runner` profile을 사용합니다.

```text
SPRING_PROFILES_ACTIVE=runner
worker.role=runner
worker.mode=runner
judge.execution.mode=docker
```

Runner 책임:

- `/internal/runner-executions` 수신
- 언어별 source file 생성
- C++ / Java 컴파일
- Python 실행
- Docker sandbox 실행
- progress 파일 생성
- progress callback 전송
- 최종 batch 결과 반환

Runner는 DB를 직접 수정하지 않습니다.

## 필수 환경변수

### Orchestrator

| 환경변수 | 설명 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `remote` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `JUDGE_EXECUTION_REMOTE_BASE_URL` | Runner base URL |

### Runner

| 환경변수 | 설명 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `runner` |
| `JUDGE_RUNNER_MAX_CONCURRENT_EXECUTIONS` | 동시 채점 수 |
| `WORKER_EXECUTOR_WARM_CONTAINER_ENABLED` | Warm Container 사용 여부 |
| `WORKER_EXECUTOR_WARM_CONTAINER_POOL_SIZE` | Warm Container Pool 크기 |
| `WORKER_EXECUTOR_PROGRESS_ENABLED` | Progress callback 사용 여부 |
| `WORKER_EXECUTOR_PROGRESS_CALLBACK_URL` | Orchestrator progress callback URL |
| `WORKER_EXECUTOR_PROGRESS_CALLBACK_TIMEOUT_MS` | Progress callback timeout |
| `WORKER_EXECUTOR_WORKSPACE_ROOT` | 임시 workspace root |

공개 저장소에는 실제 URL, 외부 IP, DB URL, 계정 정보를 적지 않습니다.

## 이미지 빌드

```bash
# 저장소 루트로 이동합니다.
cd ~/SSOJ

# 원격 main 브랜치의 최신 변경사항을 가져옵니다.
git pull origin main

# Cloud Build로 이미지를 빌드하고 Artifact Registry에 push합니다.
gcloud builds submit --tag <REGION>-docker.pkg.dev/<PROJECT_ID>/<REPOSITORY>/<IMAGE_NAME>:latest .
```

## Orchestrator 배포

```bash
# Cloud Run Orchestrator 서비스를 최신 이미지로 업데이트합니다.
gcloud run services update <ORCHESTRATOR_SERVICE_NAME> --region=<REGION> --image=<IMAGE_URI>

# 메모리 기반 SSE 유실 가능성을 줄이기 위해 MVP 단계에서는 단일 인스턴스로 고정합니다.
gcloud run services update <ORCHESTRATOR_SERVICE_NAME> --region=<REGION> --min-instances=1 --max-instances=1
```

## Runner VM 실행

```bash
# 최신 Runner 이미지를 가져옵니다.
docker pull <IMAGE_URI>

# 기존 Runner 컨테이너를 중지합니다.
docker stop ssoj-runner

# 기존 Runner 컨테이너를 삭제합니다.
docker rm ssoj-runner

# Runner 컨테이너를 실행합니다.
docker run -d --name ssoj-runner --restart unless-stopped -p 8081:8080 -e SPRING_PROFILES_ACTIVE=runner -e JUDGE_RUNNER_MAX_CONCURRENT_EXECUTIONS=5 -e WORKER_EXECUTOR_WARM_CONTAINER_ENABLED=true -e WORKER_EXECUTOR_WARM_CONTAINER_POOL_SIZE=5 -e WORKER_EXECUTOR_PROGRESS_ENABLED=true -e WORKER_EXECUTOR_PROGRESS_CALLBACK_URL=<ORCHESTRATOR_PROGRESS_CALLBACK_URL> -e WORKER_EXECUTOR_PROGRESS_CALLBACK_TIMEOUT_MS=500 -v /var/run/docker.sock:/var/run/docker.sock -v /tmp/ssoj-runner-workspaces:/tmp/ssoj-runner-workspaces <IMAGE_URI>
```

## 채점 트리거 계약

Endpoint:

```http
POST /internal/judge-executions
```

Request:

```json
{
  "submissionId": 222
}
```

Expected response:

```text
202 Accepted
```

응답 body가 없어도 정상입니다.

## Progress callback 계약

Runner가 Orchestrator로 progress를 전송합니다.

Endpoint:

```http
POST /internal/judge-progress
```

이 API는 사용자 공개 API가 아닙니다.

## SSE 계약

Frontend는 Orchestrator의 SSE endpoint에 연결합니다.

```http
GET /api/submissions/{submissionId}/events
```

SSE는 진행률 UX를 위한 보조 수단입니다.

최종 결과 보장은 DB polling으로 보완해야 합니다.

## 운영 주의사항

- Frontend는 Runner VM을 직접 호출하면 안 됩니다.
- Runner VM의 외부 노출은 최소화해야 합니다.
- `/internal/*` endpoint는 공개 사용자 API처럼 취급하면 안 됩니다.
- Progress callback은 testcase 수만큼 많이 발생할 수 있습니다.
- 동시 제출이 늘면 progress throttle이 필요합니다.
- Cloud Run scale-out이 필요하면 메모리 기반 SSE hub를 Redis Pub/Sub 등으로 바꿔야 합니다.
