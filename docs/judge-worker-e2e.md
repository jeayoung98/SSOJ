# Judge Worker End-to-End 시나리오

## 범위

이 문서는 현재 구현 기준으로 작성되었습니다.

- 지금 바로 E2E 실행 가능한 언어:
  - Java
  - Python
- 현재는 샘플 코드만 제공하는 언어:
  - C++
  - 이유: 현재 worker에는 `CppExecutor`가 구현되어 있지 않습니다.

## 가정하는 문제

- 문제 유형: A+B
- 입력:
  - 한 줄에 정수 두 개
- 기대 출력:
  - 두 수의 합

예시:

- 입력: `1 2`
- 출력: `3`

## 기대 채점 흐름

1. `submission` row를 `PENDING` 상태로 저장
2. 대상 `problem`에 hidden `test_case` row 저장
3. Redis 큐 `judge:queue`에 `submissionId` push
4. worker가 해당 id를 consume
5. worker가 `submission.status`를 `JUDGING`로 변경
6. worker가 hidden test case마다 Docker 내부에서 executor 실행
7. worker가 hidden test case마다 `submission_case_result` row 저장
8. worker가 최종 `submission.status` 저장
9. worker가 `submission.finished_at` 저장

## 추천 hidden test case

- 테스트케이스 1
  - input: `1 2`
  - output: `3`
- 테스트케이스 2
  - input: `10 20`
  - output: `30`
- 테스트케이스 3
  - input: `100 -5`
  - output: `95`

## 상태별 시나리오

### Java

- `samples/submissions/java/ac/Main.java`
  - 기대 최종 상태: `AC`
- `samples/submissions/java/wa/Main.java`
  - 기대 최종 상태: `WA`
- `samples/submissions/java/ce/Main.java`
  - 기대 최종 상태: `CE`
- `samples/submissions/java/re/Main.java`
  - 기대 최종 상태: `RE`
- `samples/submissions/java/tle/Main.java`
  - 기대 최종 상태: `TLE`

### Python

- `samples/submissions/python/ac/main.py`
  - 기대 최종 상태: `AC`
- `samples/submissions/python/wa/main.py`
  - 기대 최종 상태: `WA`
- `samples/submissions/python/re/main.py`
  - 기대 최종 상태: `RE`
- `samples/submissions/python/tle/main.py`
  - 기대 최종 상태: `TLE`

메모:

- 현재 Python executor에는 compile 단계가 없습니다.
- Python은 문법 오류와 런타임 오류 모두 실행 실패로 처리됩니다.
- 현재 구현에서는 Python 문법 오류도 실질적으로 `RE`로 보이게 됩니다.

### C++

- `samples/submissions/cpp/ac/main.cpp`
  - `CppExecutor` 추가 후 기대 상태: `AC`
- `samples/submissions/cpp/wa/main.cpp`
  - `CppExecutor` 추가 후 기대 상태: `WA`
- `samples/submissions/cpp/ce/main.cpp`
  - `CppExecutor` 추가 후 기대 상태: `CE`
- `samples/submissions/cpp/re/main.cpp`
  - `CppExecutor` 추가 후 기대 상태: `RE`
- `samples/submissions/cpp/tle/main.cpp`
  - `CppExecutor` 추가 후 기대 상태: `TLE`

메모:

- C++ 샘플 코드는 이후 검증을 위해 포함했습니다.
- 현재 worker는 `CppExecutor`가 없어서 C++를 end-to-end로 실행할 수 없습니다.

## 수동 검증 순서

### 1. 인프라 준비

- PostgreSQL 실행
- Redis 실행
- Docker 실행
- worker 실행

참고:

- [judge-worker.md](/C:/Users/SSAFY/IdeaProjects/SSOJ/docs/judge-worker.md:1)

### 2. DB 데이터 준비

문제 1개와 hidden test case를 넣습니다.

현재 worker가 사용하는 테이블:

- `problem`
- `submission`
- `test_case`
- `submission_case_result`

최소 데이터 형태:

- `problem`
  - `id`
  - `title`
  - `description`
  - `time_limit_ms`
  - `memory_limit_mb`
- `test_case`
  - `problem_id`
  - `input`
  - `output`
  - `is_hidden=true`

### 3. submission 생성

- `submission` row를 하나 생성합니다.
- 아래 값을 설정합니다.
  - `problem_id`: 준비한 problem id
  - `language`: `java` 또는 `python`
  - `source_code`: 샘플 파일 내용
  - `status`: `PENDING`

### 4. submission enqueue

```powershell
redis-cli LPUSH judge:queue <submissionId>
```

### 5. worker 로그 확인

기대 로그 순서:

- queue consume 로그
- `PENDING -> JUDGING`
- 최종 상태 로그

### 6. DB 결과 확인

`submission` 확인:

- `status`가 `PENDING`에서 아래 중 하나로 바뀌어야 합니다.
  - `AC`
  - `WA`
  - `CE`
  - `RE`
  - `TLE`
- `started_at`이 채워져야 합니다.
- `finished_at`이 채워져야 합니다.

`submission_case_result` 확인:

- hidden test case 개수만큼 row가 생겨야 합니다.
- `status`는 executor 결과와 출력 비교 결과에 맞아야 합니다.

## 샘플별 확인 포인트

- AC 샘플
  - 모든 `submission_case_result.status`가 `AC`
  - 최종 `submission.status`가 `AC`
- WA 샘플
  - 첫 오답 출력에서 `WA`
  - 최종 `submission.status`가 `WA`
- CE 샘플
  - 현재 E2E 가능 범위에서는 Java만 해당
  - 최종 `submission.status`가 `CE`
- RE 샘플
  - 프로세스가 실패 종료
  - 최종 `submission.status`가 `RE`
- TLE 샘플
  - 프로세스가 `time_limit_ms`를 초과
  - 최종 `submission.status`가 `TLE`
