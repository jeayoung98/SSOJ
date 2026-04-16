# Judge Worker 로컬 실행 가이드
## 1. 필수 실행 환경

### JAVA_HOME

로컬에서 Gradle과 Spring Boot를 실행하려면 JDK 17 이상이 필요합니다.

Windows 예시:

```C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot```

# 2. 로컬 의존성
## PostgreSQL

Worker는 다음 테이블을 사용합니다.

- submission
- problem
- test_case
- submission_case_result

기본 DB 연결 정보는 src/main/resources/application.properties에 설정되어 있습니다.

```
spring.datasource.url=jdbc:postgresql://localhost:5432/ssoj
spring.datasource.username=postgres
spring.datasource.password=postgres
```

## Redis

기본 Redis 연결 정보는 다음과 같습니다.
```
spring.data.redis.host=localhost
spring.data.redis.port=6379
```
사용하는 큐 키는 아래와 같습니다.

`judge:queue`

## Docker

Java 및 Python 실행기는 사용자 제출 코드를 Docker 컨테이너 내부에서 실행하므로 Docker가 반드시 필요합니다.

현재 설정된 실행 이미지:

- eclipse-temurin:17-jdk
- python:3.11
- 
## 3. Worker 실행 방법

- PostgreSQL, Redis, Docker를 먼저 실행합니다.
- 데이터베이스 스키마가 미리 생성되어 있어야 합니다.
- JAVA_HOME을 JDK 17 이상으로 설정합니다.
- 아래 명령으로 Worker를 실행합니다.
```
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat bootRun
```
## 4. 큐 동작 테스트

Redis에 테스트용 submission id를 넣어 Worker가 채점을 시작하는지 확인할 수 있습니다.

`redis-cli LPUSH judge:queue 123`

Worker는 Redis 큐를 polling 방식으로 조회하며, 사용 가능한 슬롯이 있으면 채점을 시작합니다.

현재 최대 동시 채점 수는 2개입니다.

## 5. 검증 포인트

### 5.1 Redis 큐 소비 확인

Worker 로그에 아래와 같은 메시지가 출력되어야 합니다.
```
Received submissionId=123 from Redis queue judge:queue.
```
### 5.2 제출 시작 처리

채점이 시작되면 다음이 반영되어야 합니다.

submission.status: PENDING → JUDGING
submission.started_at: 채점 시작 시각 저장
### 5.3 테스트케이스 실행

해당 제출이 속한 문제의 hidden test case를 순서대로 실행해야 합니다.

또한 hidden test case마다 `submission_case_result` 테이블에 1개의 결과 행이 저장되어야 합니다.

### 5.4 최종 결과 반영
모든 테스트케이스를 통과하면 `submission.status`는 `AC`가 되어야 합니다.
하나라도 실패하면 최종 상태는 가장 먼저 발생한 실패 상태가 되어야 합니다.
`submission.finished_at`에는 채점 종료 시각이 저장되어야 합니다.

### 5.5 실패 처리

다음과 같은 경우 최종 상태는 `SYSTEM_ERROR`가 되어야 합니다.
- Docker 실행 실패
- 채점 중 예상하지 못한 예외 발생
