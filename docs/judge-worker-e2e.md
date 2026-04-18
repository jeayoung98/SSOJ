# Judge Worker E2E 시나리오

이 문서는 현재 Docker 기반 로컬 개발/검증용 worker 기준 문서입니다. 운영 최종 구조 문서가 아니며, 현재 코드로 직접 확인 가능한 end-to-end 흐름만 정리합니다. 운영 환경 적용 전에는 실행 방식과 검증 방법을 다시 설계해야 합니다.

## 문서 범위

- 구현 완료된 현재 worker E2E 확인
- 로컬 개발과 수동 검증 기준
- 운영 구조로는 재검토 필요

## 공통 기준

- Redis queue key: `judge:queue`
- Redis payload: `submissionId`
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
  - Docker 컨테이너 안에서 컴파일 또는 실행
- 채점 정책:
  - hidden test case 순차 실행
  - 첫 실패 시 즉시 중단
  - `started_at`, `finished_at`, 최종 `submission.status` 저장

## 가정하는 문제

- 문제 유형: A + B
- hidden test case 예시:
  - `1 2 -> 3`
  - `10 20 -> 30`
  - `100 -5 -> 95`

## 기대 채점 흐름

1. `submission` row를 `PENDING`으로 저장
2. `problem`에 hidden `test_case` row 저장
3. Redis `judge:queue`에 `submissionId` push
4. worker가 queue에서 id를 읽음
5. `submission.status`를 `JUDGING`로 변경하고 `started_at` 저장
6. hidden test case를 Docker 기반 executor로 순서대로 실행
7. 첫 실패 전까지의 결과만 `submission_case_result`에 저장
8. 최종 `submission.status`와 `finished_at` 저장

## 언어별 대표 시나리오

### Java

- AC: `samples/submissions/java/ac/Main.java`
- WA: `samples/submissions/java/wa/Main.java`
- CE: `samples/submissions/java/ce/Main.java`
- RE: `samples/submissions/java/re/Main.java`
- TLE: `samples/submissions/java/tle/Main.java`

### Python

- AC: `samples/submissions/python/ac/main.py`
- WA: `samples/submissions/python/wa/main.py`
- RE: `samples/submissions/python/re/main.py`
- TLE: `samples/submissions/python/tle/main.py`

메모:

- Python은 별도 compile 단계가 없습니다.
- Python 문법 오류와 런타임 오류는 현재 worker 기준으로 실행 실패 계열로 처리됩니다.

### C++

- AC: `samples/submissions/cpp/ac/main.cpp`
- WA: `samples/submissions/cpp/wa/main.cpp`
- CE: `samples/submissions/cpp/ce/main.cpp`
- RE: `samples/submissions/cpp/re/main.cpp`
- TLE: `samples/submissions/cpp/tle/main.cpp`

메모:

- 현재 `CppExecutor`가 구현되어 있어 로컬 검증 범위에 포함됩니다.
- C++ compile error 분류 결과는 현재 구현을 그대로 확인하는 기준으로 검증합니다.

## 수동 검증 순서

1. PostgreSQL, Redis, Docker, worker를 실행합니다.
2. 문제와 hidden test case를 저장합니다.
3. `submission.status='PENDING'`인 row를 만듭니다.
4. `redis-cli LPUSH judge:queue <submissionId>`로 enqueue 합니다.
5. worker 로그를 확인합니다.
6. DB에서 `submission`과 `submission_case_result`를 확인합니다.

## DB에서 확인할 항목

`submission`

- `status`
- `started_at`
- `finished_at`

`submission_case_result`

- 실행된 test case까지만 row 생성
- 첫 실패 이후 hidden test case는 row가 없어야 함
- status는 executor 결과와 출력 비교 결과를 반영

## 샘플별 기대 포인트

- AC:
  - 모든 실행된 case가 `AC`
  - 최종 `submission.status=AC`
- WA:
  - 첫 오답 case에서 중단
  - 그 시점까지의 결과만 저장
  - 최종 `submission.status=WA`
- CE:
  - compile 실패가 분류되면 최종 상태 반영
  - `finished_at` 저장
- RE:
  - 실행 실패 시 최종 `submission.status=RE`
- TLE:
  - timeout 시 최종 `submission.status=TLE`
  - timeout case까지만 저장
