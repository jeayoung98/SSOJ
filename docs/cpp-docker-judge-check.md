# C++ Docker 채점 검증

## 목표

현재 구현 기준으로 아래 흐름이 실제로 동작하는지 검증합니다.

- Redis enqueue
- worker consume
- Docker 안에서 C++ compile
- Docker 안에서 실행
- DB에 결과 저장

문제는 가장 단순한 A+B 형태를 기준으로 합니다.

## 1. 사용할 C++ 정답 코드 샘플

사용 파일:

- `samples/submissions/cpp/ac/main.cpp`

코드:

```cpp
#include <iostream>

int main() {
    int a, b;
    std::cin >> a >> b;
    std::cout << a + b << '\n';
    return 0;
}
```

이 코드는 현재 C++ executor 기준으로 아래 파일명으로 저장되어 실행됩니다.

- 소스 파일명: `main.cpp`

현재 Docker 내부 명령:

- compile:
  - `g++ main.cpp -O2 -std=c++17 -o main`
- run:
  - `./main`

현재 Docker 이미지:

- `gcc:13`

## 2. 테스트 입력 / 기대 출력 예시

추천 hidden test case:

- 테스트케이스 1
  - input: `1 2`
  - output: `3`
- 테스트케이스 2
  - input: `10 20`
  - output: `30`
- 테스트케이스 3
  - input: `100 -5`
  - output: `95`

최소 검증만 할 경우:

- input: `1 2`
- output: `3`

## 3. 로컬 검증 절차

### 준비

- [ ] PostgreSQL 실행
- [ ] Redis 실행
- [ ] Docker 실행
- [ ] `gcc:13` 이미지 pull
- [ ] Spring worker 실행

Docker 이미지 준비:

```powershell
docker pull gcc:13
```

worker 실행:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat bootRun
```

### DB 준비

1. `problem` row 생성
2. 해당 problem에 hidden `test_case` row 생성
3. `submission` row 생성

`submission`의 핵심 값:

- `problem_id`: 준비한 problem id
- `language`: `cpp`
- `source_code`: `samples/submissions/cpp/ac/main.cpp` 내용
- `status`: `PENDING`

### Redis enqueue

```powershell
redis-cli LPUSH judge:queue <submissionId>
```

## 4. 어떤 로그를 보면 Docker 실행 성공을 확인할 수 있는지

아래 로그 순서가 핵심입니다.

### queue consume 확인

- `Received submissionId=... from Redis queue judge:queue`

### C++ executor 진입 확인

- `Executing C++ submission ... with image=gcc:13, compileCommand=g++ main.cpp -O2 -std=c++17 -o main, runCommand=./main`

### Docker 실행 시작 확인

- `Starting Docker execution for submission ... with image=gcc:13 command=g++ main.cpp -O2 -std=c++17 -o main && ./main`

### Docker 실행 종료 확인

- `Docker execution finished for submission ... with image=gcc:13 exitCode=0`

### 최종 채점 완료 확인

- `Submission ... finished with status=AC`

위 로그가 보이면 아래 사실을 확인한 것입니다.

- Redis consume 성공
- C++ executor 선택 성공
- Docker `run` 실행 성공
- Docker 내부 compile 성공
- Docker 내부 run 성공
- 프로세스 종료 코드 `0`

## 5. DB에서 확인할 항목

### submission

아래를 확인합니다.

- `status`
- `started_at`
- `finished_at`

기대 상태 변화:

- `PENDING -> JUDGING -> AC`

### submission_case_result

아래를 확인합니다.

- `submission_id`
- `test_case_id`
- `status`
- `execution_time_ms`

기대 결과:

- hidden test case 개수만큼 row 생성
- 각 row의 `status`가 `AC`

## 6. 실패 시 어디를 먼저 의심해야 하는지

### Docker 실행 자체가 안 됨

먼저 확인:

- [ ] Docker Desktop이 실행 중인가
- [ ] `docker version`이 정상 동작하는가
- [ ] `gcc:13` 이미지가 존재하는가
- [ ] worker 로그에 `Failed to start Docker ... image=gcc:13`가 있는가

확인 명령:

```powershell
docker version
docker images
```

### C++ executor가 선택되지 않음

먼저 확인:

- [ ] `submission.language` 값이 정확히 `cpp`인가
- [ ] 대소문자 불일치가 없는가

### compile 실패

먼저 확인:

- [ ] `source_code`가 실제로 `main.cpp` 기준 C++17 문법에 맞는가
- [ ] worker 로그의 Docker command가 기대값과 같은가
- [ ] `stderr`에 compile 에러가 남았는가

### run 실패 또는 오답

먼저 확인:

- [ ] `test_case.input` 값이 기대 형식과 맞는가
- [ ] `test_case.output`이 실제 정답과 같은가
- [ ] 출력 비교 정책이 trim + line compare 기준인 점을 감안했는가

### 결과가 DB에 안 남음

먼저 확인:

- [ ] `submission_case_result` 테이블이 있는가
- [ ] hidden test case가 실제로 존재하는가
- [ ] `problem_id` 연결이 맞는가

## 7. 수동 확인용 명령 예시

Docker 이미지 확인:

```powershell
docker images
```

Redis enqueue:

```powershell
redis-cli LPUSH judge:queue 123
```

Redis queue 길이 확인:

```powershell
redis-cli LLEN judge:queue
```

남아 있는 컨테이너 확인:

```powershell
docker ps -a
```

## 8. 단계별 검증 순서

1. Docker, Redis, PostgreSQL, worker를 실행합니다.
2. `docker pull gcc:13`으로 이미지가 준비됐는지 확인합니다.
3. A+B용 `problem`과 hidden `test_case`를 DB에 넣습니다.
4. `language='cpp'`, `status='PENDING'`인 `submission`을 생성합니다.
5. `source_code`에는 `samples/submissions/cpp/ac/main.cpp` 내용을 넣습니다.
6. `redis-cli LPUSH judge:queue <submissionId>`로 queue에 넣습니다.
7. worker 로그에서 queue consume, C++ executor 진입, Docker 시작/종료 로그를 확인합니다.
8. DB에서 `submission.status`가 `PENDING -> JUDGING -> AC`로 바뀌는지 확인합니다.
9. `submission_case_result`가 hidden test case 개수만큼 생성되었는지 확인합니다.
10. 필요하면 `docker ps -a`로 컨테이너가 남지 않는 것도 확인합니다.
