# Judge Worker 검증 체크리스트

이 문서는 현재 Docker 기반 로컬 개발/검증용 worker 기준 문서입니다. 운영 최종 구조 문서가 아니며, 현재 구현 완료 범위를 수동으로 검증하기 위한 체크리스트입니다. 운영 환경 적용 전에는 계약과 장애 대응 방식을 다시 검토해야 합니다.

## 문서 범위

- 구현 완료된 현재 worker 검증
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
  - Docker 기반 실행
- 채점 정책:
  - hidden test case 순차 실행
  - 첫 실패 시 즉시 중단
  - 실행된 case까지만 결과 저장
  - `started_at`, `finished_at`, 최종 상태 저장

## 현재 구현됨

- Java, Python, C++ executor 존재
- Java compile error는 현재 구현상 `CE` 기대 가능
- C++ executor는 로컬 검증 범위에 포함
- 첫 실패 이후 뒤 test case는 실행되지 않음

## 추가 개선 필요

- C++ compile error를 Java처럼 확정적으로 `CE`로 분류하는 규칙은 아직 없음
- C++ compile error 검증은 현재 저장 결과를 확인하는 방식으로만 해석해야 함
- 언어별 세부 에러 매핑은 추가 정리 필요

## 공통 준비

문제와 hidden test case 준비:

```sql
insert into problem (id, title, description, time_limit_ms, memory_limit_mb)
values (100, 'A+B', 'sum two integers', 3000, 256)
on conflict (id) do update
set title = excluded.title,
    description = excluded.description,
    time_limit_ms = excluded.time_limit_ms,
    memory_limit_mb = excluded.memory_limit_mb;

delete from test_case where problem_id = 100;

insert into test_case (id, problem_id, input, output, is_hidden) values
  (1001, 100, '1 2', '3', true),
  (1002, 100, '10 20', '30', true),
  (1003, 100, '100 -5', '95', true);
```

공통 확인 SQL:

```sql
select id, language, status, started_at, finished_at
from submission
where id = :submission_id;

select submission_id, test_case_id, status, execution_time_ms, memory_usage_kb
from submission_case_result
where submission_id = :submission_id
order by test_case_id;
```

공통 enqueue:

```powershell
redis-cli LPUSH judge:queue <submissionId>
```

## Java 검증

- WA: `samples/submissions/java/wa/Main.java`
- CE: `samples/submissions/java/ce/Main.java`
- RE: `samples/submissions/java/re/Main.java`
- TLE: `samples/submissions/java/tle/Main.java`

확인:

- 최종 `submission.status`가 기대값인지
- `started_at`, `finished_at`이 저장됐는지
- 첫 실패 시 뒤 test case 결과가 저장되지 않는지

## Python 검증

- WA: `samples/submissions/python/wa/main.py`
- RE: `samples/submissions/python/re/main.py`
- TLE: `samples/submissions/python/tle/main.py`

확인:

- 최종 `submission.status`가 기대값인지
- `started_at`, `finished_at`이 저장됐는지
- 첫 실패 시 뒤 test case 결과가 저장되지 않는지

## C++ 검증

- AC: `samples/submissions/cpp/ac/main.cpp`
- WA: `samples/submissions/cpp/wa/main.cpp`
- CE 확인용: `samples/submissions/cpp/ce/main.cpp`
- RE: `samples/submissions/cpp/re/main.cpp`
- TLE: `samples/submissions/cpp/tle/main.cpp`

확인:

- AC, WA, RE, TLE가 현재 구현 결과와 일치하는지
- `started_at`, `finished_at`이 저장됐는지
- 첫 실패 시 뒤 test case 결과가 저장되지 않는지
- C++ compile error는 `CE`를 고정 기대하지 않고 실제 최종 상태를 확인하는지

## 동시성 검증

- `worker.max-concurrency=2` 확인
- 느린 submission 3개 이상 enqueue
- 어느 시점에도 `JUDGING`가 2개를 넘지 않는지 확인

## cleanup 검증

- temp directory prefix 확인:
  - `judge-java-*`
  - `judge-python-*`
  - `judge-cpp-*`
- `docker ps -a`에서 judge 컨테이너가 남지 않는지 확인
- 실패나 timeout 이후에도 `finished_at`이 저장되는지 확인

## Next.js 연동 검증

- Next.js가 `submission`을 먼저 저장하는지
- Redis `judge:queue`에 `submissionId`만 push 하는지
- worker가 같은 DB와 Redis를 보는지

## 완료 기준

- queue consume 확인
- `PENDING -> JUDGING -> 최종 상태` 확인
- `started_at`, `finished_at` 확인
- 실행된 case까지만 `submission_case_result` 저장 확인
- 첫 실패 즉시 중단 확인
