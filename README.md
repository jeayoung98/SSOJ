# SSOJ

SSOJ는 온라인 저지 플랫폼 MVP 프로젝트입니다.

현재 저장소에는 Spring Boot 기반 judge worker가 구현되어 있으며, Redis queue를 통해 제출을 비동기로 받아 Docker 안에서 코드를 실행하고 결과를 PostgreSQL에 저장하는 구조를 사용합니다.

## 프로젝트 개요

현재 아키텍처는 아래와 같습니다.

- Web / API: Next.js
- Judge Worker: Spring Boot
- Queue: Redis queue
- DB: PostgreSQL
- Sandbox: Docker

역할 분리는 아래 기준입니다.

- Next.js
  - 문제 조회
  - 코드 제출
  - 제출 상태 / 결과 조회
  - 제출 후 `submissionId`를 Redis queue에 push
- Spring Boot Judge Worker
  - Redis에서 `submissionId` consume
  - `submission` 상태를 `PENDING -> JUDGING`로 변경
  - hidden test case 기준으로 채점 수행
  - 실행된 케이스 결과만 저장
  - 최종 상태 반영

## 현재 구현 범위

현재 worker는 아래 범위까지 구현되어 있습니다.

- Redis queue key `judge:queue` consume
- `submissionId` 기반 비동기 채점 시작
- JPA 엔티티 / 리포지토리 구성
- `submission`, `problem`, `test_case`, `submission_case_result` 사용
- hidden test case 순차 채점
- 첫 실패 시 즉시 중단
- 실행된 case까지만 `submission_case_result` 저장
- 최종 `submission.status` 저장
- `started_at`, `finished_at` 반영
- 동시성 제한: 최대 2개 채점
- Docker 기반 코드 실행
- timeout / memory limit / cpu limit 적용
- cleanup 보강

현재 상태 enum은 아래를 사용합니다.

- `PENDING`
- `JUDGING`
- `AC`
- `WA`
- `CE`
- `RE`
- `TLE`
- `MLE`
- `SYSTEM_ERROR`

## 현재 지원 언어

현재 worker 기준 지원 언어는 아래와 같습니다.

- `cpp`
- `java`
- `python`

언어별 실행 정책은 아래와 같습니다.

### C++

- 소스 파일명: `main.cpp`
- compile: `g++ main.cpp -O2 -std=c++17 -o main`
- run: `./main`
- Docker image: `gcc:13`

### Java

- 소스 파일명: `Main.java`
- compile: `javac Main.java`
- run: `java Main`
- Docker image: `eclipse-temurin:17-jdk`

### Python

- 소스 파일명: `main.py`
- run: `python3 main.py`
- Docker image: `python:3.11`

## 출력 비교 정책

현재 MVP 정책은 아래와 같습니다.

- 전체 출력 trim
- 줄 단위 분리
- 각 줄 trim
- line-by-line 비교
- trailing newline 차이는 무시

## 기술 스택

- Java 17
- Spring Boot
- Spring Data JPA
- Spring Data Redis
- PostgreSQL
- Redis
- Docker
- Gradle

## 실행 전 준비사항

아래 항목이 필요합니다.

- JDK 17 이상
- Docker
- Redis
- PostgreSQL

## 환경 변수

### DB

PostgreSQL 연결은 아래 환경 변수를 사용합니다.

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

예시:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://YOUR_DB_HOST:5432/ssoj"
$env:SPRING_DATASOURCE_USERNAME="YOUR_DB_USERNAME"
$env:SPRING_DATASOURCE_PASSWORD="YOUR_DB_PASSWORD"
```

### Redis

Redis 연결은 아래 환경 변수를 사용합니다.

- `SPRING_DATA_REDIS_HOST`
- `SPRING_DATA_REDIS_PORT`

기본값:

- `SPRING_DATA_REDIS_HOST=localhost`
- `SPRING_DATA_REDIS_PORT=6379`

예시:

```powershell
$env:SPRING_DATA_REDIS_HOST="localhost"
$env:SPRING_DATA_REDIS_PORT="6379"
```

## 로컬 실행 방법

### 1. Redis 실행

예시:

```powershell
docker run --rm -p 6379:6379 redis:7-alpine
```

### 2. 필요한 환경 변수 설정

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://YOUR_DB_HOST:5432/ssoj"
$env:SPRING_DATASOURCE_USERNAME="YOUR_DB_USERNAME"
$env:SPRING_DATASOURCE_PASSWORD="YOUR_DB_PASSWORD"
$env:SPRING_DATA_REDIS_HOST="localhost"
$env:SPRING_DATA_REDIS_PORT="6379"
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

### 3. 애플리케이션 실행

```powershell
.\gradlew.bat bootRun
```

### 4. 시작 후 기본 확인

기동 후에는 worker가 오류 없이 올라오는지 확인하고, enqueue 후 아래 로그 흐름이 보이는지 확인합니다.

- `Received submissionId=123 from Redis queue judge:queue`
- `Submission 123 changed from PENDING to JUDGING`
- `Submission 123 finished with status=...`

## Redis queue 테스트

Redis queue key는 아래를 사용합니다.

- `judge:queue`

Payload는 아래 형식입니다.

- `submissionId` 하나만 사용

예시:

```powershell
redis-cli LPUSH judge:queue 123
```

## Docker 실행 정책

현재 Docker 실행 시 아래 옵션을 사용합니다.

- `--rm`
- `--network none`
- `-m <memoryLimitMb>m`
- `--cpus 1`
- `-v <host-workspace>:/workspace`
- `-w /workspace`

## 현재 제약 사항

현재 프로젝트는 MVP 단계이며, 아래는 아직 범위 밖입니다.

- ranking
- plagiarism check
- multi-region deployment
- advanced sandbox hardening
- autoscaling
- SSE / realtime push

또한 현재 구현은 worker 중심이며, 전체 Next.js 서비스와의 실제 통합 검증은 별도 확인이 필요합니다.

## Docker Compose

현재 저장소에는 아래 파일이 포함되어 있습니다.

- `Dockerfile`
- `docker-compose.yml`

`docker-compose.yml`은 Redis와 judge worker를 함께 띄우는 용도로 사용할 수 있습니다.

예시:

```powershell
docker compose up --build
```

## 관련 문서

상세 문서는 `docs/` 아래에 정리되어 있습니다.

- [Judge Worker 문서](./docs/judge-worker.md)
- [현재 아키텍처 vs 검토 중 아키텍처](./docs/current-vs-target-architecture.md)
- [Cloud Run 기반 채점 구조 검토](./docs/cloud-run-architecture-review.md)
- [JudgeService 개선 정리](./docs/judge-service-improvements.md)
- [Judge Worker E2E 시나리오](./docs/judge-worker-e2e.md)
- [Judge Worker 검증 체크리스트](./docs/worker-validation-checklist.md)
- [Next.js와 Spring Worker 연동 체크리스트](./docs/nextjs-spring-worker-checklist.md)
- [Judge Worker 동시성 점검](./docs/judge-worker-concurrency-check.md)
- [Judge Worker Cleanup 점검](./docs/judge-worker-cleanup-check.md)
- [Judge Worker 데모 스크립트](./docs/judge-worker-demo-script.md)
- [C++ Docker 채점 검증](./docs/cpp-docker-judge-check.md)

## 현재 상태 요약

현재 judge worker는 로컬 MVP 검증이 가능한 수준까지 구현되어 있습니다.

다만 아래는 추가 확인이 필요합니다.

- Java / Python / C++ 실제 end-to-end 상태별 검증
- Next.js 제출 API와의 실제 연동 검증
- PostgreSQL / Redis / Docker Compose 기준 실제 배포 환경 검증
