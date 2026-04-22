# 문서 최신화 점검 보고서

이 문서는 README와 `docs` 폴더를 현재 코드 구조와 비교한 결과를 정리한다. 목적은 예전 구조 기준 문서를 그대로 신뢰하지 않도록 하고, 현재 저장소에서 로컬 실행용 코드/설정, 배포 환경용 코드/설정, 공통 코드를 구분하는 문서 체계를 유지하는 것이다.

## 1. 점검 기준

확인한 실제 코드 기준:

- Spring Boot source: `src/main/java/com/example/ssoj`
- test source: `src/test/java/com/example/ssoj`
- application 설정:
  - `src/main/resources/application.properties`
  - `src/main/resources/application-local.properties`
  - `src/main/resources/application-remote.properties`
  - `src/main/resources/application-runner.properties`
- Docker 설정:
  - `Dockerfile`
- 기존 문서:
  - `README.md`
  - `docs/*.md`

현재 코드 기준 핵심:

- DB table은 Supabase PostgreSQL의 복수형 이름을 따른다.
- `problems.id`는 문자열이다.
- `submissions.id`는 UUID다.
- Redis와 Cloud Tasks payload의 `submissionId`는 UUID 문자열이다.
- `submissions.status`는 `PENDING`, `JUDGING`, `DONE` 작업 상태다.
- `submissions.result`는 `AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `SYSTEM_ERROR` 채점 결과다.
- 현재 timestamp 기준은 `submitted_at`, `judged_at`이다.
- `started_at`, `finished_at`, `submission_case_result`, `test_case`는 현재 코드 기준이 아니다.

## 2. 기존 문서 상태 판단

기존 문서에는 다음 오래된 기준이 섞여 있었다.

- 숫자형 `submissionId`, `problemId` 예시
- 단수형 테이블명 `problem`, `test_case`, `submission`, `submission_case_result`
- `started_at`, `finished_at` 기준 설명
- `submission.status=AC`처럼 status가 최종 verdict를 겸하는 설명
- Cloud Tasks dispatch가 아직 구현 전이라는 이전 설명
- 깨진 인코딩으로 읽기 어려운 한국어 문서

이 중 현재 운영/검증에 계속 참고할 문서는 수정했고, 과거 검토 메모 성격이 강한 문서는 삭제 후보로 분류했다.

## 3. 최신화한 문서

이번 정리에서 최신화한 핵심 문서:

- `README.md`
  - 현재 Spring Boot judge worker 구조, DB table, ID 타입, profile, 실행 방식을 현재 코드 기준으로 정리했다.
- `docs/local-vs-deployment-code-guide.md`
  - 로컬/배포/공통 코드 구분을 저장소 구조 기준으로 새로 정리한 기준 문서다.
- `docs/worker-mode-configuration.md`
  - `worker.role`, `worker.mode`, `judge.dispatch.mode`, `judge.execution.mode` 차이를 현재 profile 기준으로 정리했다.
- `docs/judge-worker.md`
  - worker의 orchestrator/runner 역할, Redis queue, DB 저장 기준을 최신화했다.
- `docs/worker-validation-checklist.md`
  - 로컬/원격 검증 체크리스트를 UUID와 복수형 table 기준으로 정리했다.
- `docs/nextjs-spring-worker-checklist.md`
  - 이 저장소에 Next.js 소스가 없다는 점과 외부 Web/API가 지켜야 할 queue/DB 계약을 분리했다.
- `docs/local-orchestrator-runner.md`
  - 로컬에서 orchestrator와 runner를 HTTP로 분리 실행하는 예시를 UUID/String ID 기준으로 고쳤다.
- `docs/cloud-tasks-dispatch.md`
  - Cloud Tasks payload, 설정, 현재 구현 상태를 최신화했다.
- `docs/cloud-run-runner.md`
  - runner의 책임, HTTP 계약, Cloud Run 제약을 현재 코드 기준으로 다시 작성했다.
- `docs/deployment-readiness.md`
  - 배포 준비 체크리스트를 `status/result` 분리와 현재 profile 기준으로 다시 작성했다.
- `docs/judge-worker-e2e.md`
  - 로컬 E2E 검증 절차를 현재 DB table과 UUID queue 기준으로 다시 작성했다.
- `docs/judge-worker-demo-script.md`
  - 데모 진행 멘트를 현재 상태/결과 분리 기준으로 다시 작성했다.
- `docs/cpp-docker-judge-check.md`
  - C++ Docker 검증 절차를 현재 table, UUID, `status=DONE/result=AC` 기준으로 다시 작성했다.
- `docs/current-vs-target-architecture.md`
  - 현재 구현과 목표 구조를 구분해 다시 작성했다.
- `docs/judge-service-improvements.md`
  - 과거 개선 메모를 현재 `JudgeService` 책임 분리 설명으로 바꿨다.
- `docs/judge-worker-cleanup-check.md`
  - cleanup 점검 기준을 `judged_at`, `status/result` 구조로 고쳤다.
- `docs/judge-worker-concurrency-check.md`
  - 동시성 점검 SQL과 queue 예시를 `submissions`, UUID 기준으로 고쳤다.

## 4. 새로 생성한 문서

새로 생성한 문서:

- `docs/local-vs-deployment-code-guide.md`
  - 기존 문서들은 개별 실행/배포/검증 문서였고, 저장소 전체를 로컬용/배포용/공통 코드로 분류하는 기준 문서가 없었다.
- `docs/documentation-maintenance-report.md`
  - 이번 문서 최신화의 판단 근거와 삭제 후보를 남기기 위해 생성했다.

## 5. 삭제 후보 문서

아래 문서는 즉시 삭제하지 않고 후보로만 분류한다. 이유는 과거 검토 맥락이 남아 있어 참고 가치는 있지만, 운영자가 현재 실행 가이드로 오해할 수 있기 때문이다.

삭제 후보:

- `docs/cloud-run-architecture-review.md`
  - Cloud Run 검토 배경 메모 성격이 강하다.
  - 현재 실행 기준은 `deployment-readiness.md`, `cloud-run-orchestrator.md`, `cloud-run-runner.md`가 더 정확하다.
  - 유지한다면 파일 상단에 "과거 검토 메모"임을 더 강하게 표시하는 편이 좋다.

보류:

- `docs/current-vs-target-architecture.md`
  - 기존에는 과거 검토 문서에 가까웠지만, 이번에 현재/목표 구조 구분 문서로 재작성했으므로 삭제 후보에서 제외한다.
- `docs/judge-service-improvements.md`
  - 기존에는 과거 개선 메모였지만, 이번에 현재 책임 분리 설명으로 재작성했으므로 삭제 후보에서 제외한다.

## 6. 앞으로의 문서 유지 규칙

문서를 새로 쓰거나 수정할 때 다음 기준을 따른다.

- DB table 이름은 실제 Supabase table 이름만 쓴다.
- `submissionId` 예시는 UUID 문자열로 쓴다.
- `problemId` 예시는 문자열로 쓴다.
- `status`와 `result`를 섞어 쓰지 않는다.
- 로컬 실행은 `local`, 배포 orchestrator는 `remote`, 실행 전용 runner는 `runner` profile로 구분한다.
- Docker executor는 "로컬 전용"이 아니라 "Docker daemon이 있는 환경에서 쓰는 실행 경로"로 표현한다.
- 표준 Cloud Run runner 실행 가능 여부는 확정된 것처럼 쓰지 않고 "확실하지 않음"으로 표시한다.
- 과거 검토 메모는 파일 상단에 운영 기준 문서가 아님을 명시한다.

## 7. 다음으로 정리하면 좋은 문서

1. 환경변수 전체 목록 문서
   - `application*.properties`의 모든 환경변수, 필수 여부, local/remote/runner 사용 범위를 표로 정리한다.
2. 배포 아키텍처 문서
   - Cloud Run orchestrator, runner host, Cloud Tasks, DB, IAM 흐름을 한 장으로 정리한다.
3. DB 계약 문서
   - Supabase table, entity, repository, DTO가 어떤 컬럼과 타입을 기준으로 연결되는지 정리한다.

## 8. 한줄 요약

현재 문서 체계의 기준은 `local = Redis polling + Docker execution`, `remote orchestrator = Cloud Tasks + HTTP trigger + remote runner`, `runner = DB/Redis 없는 단일 실행 서비스`다.
