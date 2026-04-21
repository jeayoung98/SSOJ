# C++ Docker 채점 검증

이 문서는 현재 코드 기준으로 C++ 제출이 Docker executor를 통해 채점되는지 확인하는 로컬 검증 문서다.

## 1. 목표

다음 흐름을 확인한다.

- Redis `judge:queue` enqueue
- worker consume
- Docker 안에서 C++ compile
- Docker 안에서 C++ run
- `submissions`, `submission_testcase_results` 저장

## 2. C++ 실행 기준

소스 파일명:

- `main.cpp`

compile:

```text
g++ main.cpp -O2 -std=c++17 -o main
```

run:

```text
./main
```

기본 Docker 이미지:

- `gcc:13`

## 3. 예시 문제

문제:

- A+B
- `problems.id`: `A_PLUS_B`

hidden testcase:

| input_text | expected_output |
| --- | --- |
| `1 2\n` | `3\n` |
| `10 20\n` | `30\n` |
| `100 -5\n` | `95\n` |

## 4. 로컬 준비

필요한 것:

- PostgreSQL
- Redis
- Docker
- Spring Boot 앱 `local` profile

Docker 이미지 준비:

```powershell
docker pull gcc:13
```

앱 실행 예시:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

## 5. DB 준비

현재 DB 기준으로 준비할 row:

- `problems`
- `problem_testcases`
- `submissions`

`submissions` 최소 기준:

- `id`: UUID
- `problem_id`: `A_PLUS_B`
- `language`: `cpp`
- `source_code`: C++ 정답 코드
- `status`: `PENDING`
- `result`: `NULL`

예전 문서의 `problem`, `test_case`, `submission`, `submission_case_result` 이름은 현재 기준이 아니다.

## 6. Redis enqueue

```powershell
redis-cli LPUSH judge:queue 018f2f1e-8d2f-7a44-9f2e-efb0c8a33f11
```

payload는 숫자가 아니라 UUID 문자열이다.

## 7. 로그 확인

확인할 로그:

- Redis queue에서 `submissionId`를 읽었는지
- C++ executor가 선택되었는지
- Docker 실행이 시작되었는지
- compile/run 결과가 반환되었는지
- 최종 저장이 수행되었는지

로그 문구는 구현 변경에 따라 달라질 수 있으므로, 정확한 문자열보다 흐름을 기준으로 확인한다.

## 8. DB 확인

`submissions`:

- `status`: `DONE`
- `result`: `AC`
- `execution_time_ms`
- `memory_kb`
- `judged_at`

`submission_testcase_results`:

- hidden testcase 수만큼 또는 실패 지점까지 row 생성
- 각 row의 `result`
- `execution_time_ms`
- `memory_kb`
- `error_message`

현재 구조에서는 `started_at`, `finished_at`을 확인하지 않는다.

## 9. 실패 시 확인 순서

Docker 자체 실패:

- Docker Desktop 또는 Docker daemon이 실행 중인지 확인
- `docker version` 확인
- `gcc:13` 이미지 존재 여부 확인

C++ executor 미선택:

- `submissions.language`가 `cpp`인지 확인
- 대소문자 또는 공백이 섞이지 않았는지 확인

compile 실패:

- `source_code`가 `main.cpp` 기준 C++17 문법에 맞는지 확인
- compile stderr가 `error_message` 또는 로그에 남는지 확인

WA:

- `problem_testcases.input_text`가 코드 입력 형식과 맞는지 확인
- `problem_testcases.expected_output`이 정답과 맞는지 확인
- 출력 비교 정책이 trim 기반임을 감안한다.

DB 저장 실패:

- `problem_id` 연결이 맞는지 확인
- `problem_testcases.is_hidden=true` 데이터가 있는지 확인
- `spring.jpa.hibernate.ddl-auto=validate`에서 schema mismatch가 발생하지 않는지 확인

## 10. 단계별 검증 요약

1. Docker, Redis, PostgreSQL, worker를 실행한다.
2. `docker pull gcc:13`으로 이미지가 준비되었는지 확인한다.
3. `problems`, `problem_testcases`, `submissions` 데이터를 넣는다.
4. UUID `submissionId`를 Redis `judge:queue`에 넣는다.
5. worker 로그에서 consume과 Docker 실행을 확인한다.
6. DB에서 `submissions.status=DONE`, `submissions.result=AC`를 확인한다.
7. `submission_testcase_results` row를 확인한다.
