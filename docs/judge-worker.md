# Judge Worker 로컬 실행

## 필요한 환경

- `JAVA_HOME`
  - 로컬에서 Gradle과 Spring Boot를 실행하려면 JDK 17 이상이 필요합니다.
  - Windows 예시:
    - `C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`

## 로컬 의존성

- PostgreSQL
  - worker는 `submission`, `problem`, `test_case`, `submission_case_result` 테이블을 읽고 씁니다.
  - 기본 연결 정보는 `src/main/resources/application.properties`에 있습니다.
    - `spring.datasource.url=jdbc:postgresql://localhost:5432/ssoj`
    - `spring.datasource.username=postgres`
    - `spring.datasource.password=postgres`
- Redis
  - 기본 연결 정보:
    - `spring.data.redis.host=localhost`
    - `spring.data.redis.port=6379`
  - 큐 키:
    - `judge:queue`
- Docker
  - Java, Python executor가 사용자 코드를 Docker 내부에서 실행하므로 필요합니다.
  - 현재 설정된 이미지:
    - `eclipse-temurin:17-jdk`
    - `python:3.11`

## Worker 실행

1. PostgreSQL, Redis, Docker를 실행합니다.
2. DB 스키마가 이미 준비되어 있어야 합니다.
3. `JAVA_HOME`을 JDK 17 이상으로 맞춥니다.
4. worker를 실행합니다.

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat bootRun
```

## 큐 테스트

Redis에 `submissionId`를 넣습니다.

```powershell
redis-cli LPUSH judge:queue 123
```

worker는 Redis를 polling 하다가 실행 가능한 슬롯이 있으면 채점을 시작합니다.
현재 최대 동시 실행 수는 `2`입니다.

## 검증 포인트

- Redis 큐 소비
  - worker 로그에 `Received submissionId=123 from Redis queue judge:queue`가 보여야 합니다.
- Submission 시작
  - `submission.status`가 `PENDING`에서 `JUDGING`로 바뀌어야 합니다.
  - `submission.started_at`이 채워져야 합니다.
- 테스트케이스 실행
  - 해당 submission의 problem에 연결된 hidden test case를 읽어 순서대로 실행해야 합니다.
  - hidden test case마다 `submission_case_result` row가 하나씩 저장되어야 합니다.
- 최종 결과
  - 모든 테스트케이스를 통과하면 `submission.status`는 `AC`가 되어야 합니다.
  - 하나라도 실패하면 최종 상태는 첫 실패 상태가 되어야 합니다.
  - `submission.finished_at`이 채워져야 합니다.
- 실패 처리
  - Docker 실행 실패나 채점 중 예외가 발생하면 최종 상태는 `SYSTEM_ERROR`가 되어야 합니다.

## 현재 범위 메모

- 현재 executor: `java`, `python`
- 현재 출력 비교 정책: trim 후 line-by-line 비교
- 현재 큐 소비 방식: polling
- 이 문서는 아직 구현되지 않은 API, controller endpoint, Docker sandbox hardening은 다루지 않습니다.
