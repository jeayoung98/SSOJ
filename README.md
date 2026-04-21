# SSOJ

SSOJ는 온라인 저지 MVP를 위한 Spring Boot 기반 judge worker 저장소다. 현재 저장소에는 Next.js Web/API 소스가 없고, Spring worker와 실행/배포 설정만 포함한다.

## 현재 구현 요약

- Web/API는 저장소 밖에 있으며, `submissions` row를 만든 뒤 `submissionId`를 Redis 또는 Cloud Tasks로 전달하는 상위 계층으로 전제한다.
- Spring Boot는 `orchestrator`와 `runner` 역할을 profile/property로 나눈다.
- 로컬 검증은 Redis polling과 Docker executor를 사용한다.
- 배포형 orchestrator는 Cloud Tasks HTTP trigger와 remote runner 호출을 사용한다.
- runner는 단일 실행 요청을 받고 Docker 기반 executor로 코드를 실행한다.
- SSE는 현재 구현 범위에 없다.

## 핵심 흐름

```text
submission 생성
-> submissionId 전달
-> JudgeService
-> hidden problem_testcases를 testcase_order 순서로 실행
-> 첫 실패(WA/TLE/RE/MLE)에서 즉시 종료
-> 실행된 testcase까지만 submission_testcase_results 저장
-> submissions.status=DONE
-> result, failed_testcase_order, execution_time_ms, memory_kb, judged_at 저장
```

AC인 경우 `failed_testcase_order`는 `null`이다. CE와 SYSTEM_ERROR도 현재 코드 기준으로 특정 testcase 실패로 노출하지 않으므로 `failed_testcase_order=null`이다.

## DB 기준

현재 JPA 매핑은 기존 Supabase PostgreSQL 스키마를 따르는 것을 전제로 한다.

- `users`
- `problems`
- `problem_examples`
- `problem_testcases`
- `submissions`
- `submission_testcase_results`

중요 타입:

- `problems.id`: `String`
- `submissions.id`: `UUID`
- `problem_testcases.id`: `UUID`
- `submission_testcase_results.id`: `UUID`

주의: 현재 코드에는 `submissions.failed_testcase_order` 매핑이 있다. 기존 DB에 이 컬럼이 없다면 `spring.jpa.hibernate.ddl-auto=validate`에서 실패한다. 이 저장소에서는 DDL/migration을 작성하지 않는다.

## 실행 모드

| 목적 | profile/property | 설명 |
| --- | --- | --- |
| 로컬 worker | `SPRING_PROFILES_ACTIVE=local` | Redis polling + Docker execution |
| 배포 orchestrator | `SPRING_PROFILES_ACTIVE=remote` | HTTP trigger + Cloud Tasks + remote runner |
| 실행 runner | `SPRING_PROFILES_ACTIVE=runner` | DB/Redis 없이 단일 실행 요청 처리 |

## 빠른 실행

로컬:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Redis enqueue:

```powershell
redis-cli LPUSH judge:queue 00000000-0000-0000-0000-000000000001
```

테스트:

```powershell
.\gradlew.bat test
```

## 문서 구조

실사용 문서는 아래 5개를 기준으로 본다.

- [아키텍처](docs/architecture.md)
- [로컬 개발](docs/local-development.md)
- [배포](docs/deployment.md)
- [채점 모델](docs/judging-model.md)
- [검증 체크리스트](docs/validation-checklist.md)

기존 세부 메모와 이전 점검 문서는 [docs/archive](docs/archive/)에 보관한다. archive 문서는 이력 참고용이며, 현재 기준은 위 핵심 문서가 우선이다.
