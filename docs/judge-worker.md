# Judge Worker 문서

이 문서는 현재 Docker 기반 로컬 개발/검증용 worker 기준 문서입니다. 운영 최종 구조 문서가 아니며, 현재 코드 기준으로 구현 완료된 범위만 설명합니다. 운영 환경 적용 전에는 아키텍처, 보안, 배포 방식, 관측성을 다시 검토해야 합니다.

## 문서 범위

- 구현 완료된 현재 Spring judge worker 기준
- 로컬 개발과 수동 검증 기준
- 운영 구조로는 재검토 필요

## 현재 고정 계약

- Redis queue key: `judge:queue`
- Redis payload: `submissionId` 문자열 1개
- 상태값:
  - `PENDING`
  - `JUDGING`
  - `AC`
  - `WA`
  - `CE`
  - `RE`
  - `TLE`
  - `MLE`
  - `SYSTEM_ERROR`
- 현재 실행 가능한 언어:
  - `java`
  - `python`
  - `cpp`
- 실행 방식:
  - 각 언어 executor가 Docker 컨테이너 안에서 사용자 코드를 실행
  - 네트워크 차단, 메모리 제한, CPU 제한, timeout 적용
- 채점 정책:
  - hidden test case를 id 오름차순으로 실행
  - 첫 실패 시 즉시 중단
  - `started_at`, `finished_at`, 최종 `submission.status` 저장

## 필요한 로컬 환경

- JDK 17 이상
- PostgreSQL
- Redis
- Docker

기본 설정은 [application.properties](/C:/Users/user/OneDrive/Desktop/JeaYoung/프로젝트/SSOJ/SSOJ/src/main/resources/application.properties:1)에 있습니다.

## 현재 로컬 기본 설정

- DB:
  - `spring.datasource.url=jdbc:postgresql://localhost:5432/ssoj`
  - `spring.datasource.username=postgres`
  - `spring.datasource.password=postgres`
- Redis:
  - `spring.data.redis.host=localhost`
  - `spring.data.redis.port=6379`
- Worker:
  - `worker.poll-delay-ms=1000`
  - `worker.max-concurrency=2`
- Docker 이미지:
  - `worker.executor.java.image=eclipse-temurin:17-jdk`
  - `worker.executor.python.image=python:3.11`
  - `worker.executor.cpp.image=gcc:13`

## 로컬 실행

1. PostgreSQL, Redis, Docker를 실행합니다.
2. DB 스키마가 준비되어 있는지 확인합니다.
3. `JAVA_HOME`을 JDK 17 이상으로 맞춥니다.
4. worker를 실행합니다.

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat bootRun
```

## 가장 짧은 큐 확인

```powershell
redis-cli LPUSH judge:queue 123
```

정상 동작 시 worker는 Redis polling 후 `submissionId=123`을 읽고 비동기 채점을 시작합니다.

## 현재 검증 포인트

- queue consume:
  - `Received submissionId=123 from Redis queue judge:queue`
- 시작 처리:
  - `submission.status`가 `PENDING`에서 `JUDGING`로 변경
  - `submission.started_at` 저장
- hidden test case 실행:
  - hidden test case를 순서대로 실행
  - 첫 실패 시 즉시 중단
- 결과 저장:
  - 실행된 test case까지만 `submission_case_result` 저장
  - `submission.finished_at` 저장
  - 최종 `submission.status` 저장
- 예외 처리:
  - executor 예외 또는 지원하지 않는 언어는 `SYSTEM_ERROR`

## 현재 문서가 다루지 않는 범위

- 운영 배포 구조
- worker 다중 인스턴스 운영 전략
- 고급 sandbox hardening
- autoscaling
- 실시간 push
- 운영 모니터링과 장애 대응 체계
