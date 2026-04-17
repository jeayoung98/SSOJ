# Judge Worker 검증 작업물

이 문서는 현재 프로젝트 구현 기준으로 바로 복붙해서 검증할 수 있도록 정리한 실전 체크리스트입니다.

기준:

- queue key: `judge:queue`
- payload: `submissionId`
- language 값:
  - `java`
  - `python`
  - `cpp`
- status enum:
  - `PENDING`
  - `JUDGING`
  - `AC`
  - `WA`
  - `CE`
  - `RE`
  - `TLE`
  - `MLE`
  - `SYSTEM_ERROR`

---

## 공통 준비

### 1. 문제 / hidden test case 준비

아래 SQL을 먼저 실행합니다.

```sql
insert into problem (id, title, description, time_limit_ms, memory_limit_mb)
values (100, 'A+B', '두 정수의 합', 3000, 256)
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

### 2. 공통 확인 SQL

submission 확인:

```sql
select id, language, status, started_at, finished_at
from submission
where id = :submission_id;
```

submission_case_result 확인:

```sql
select submission_id, test_case_id, status, execution_time_ms, memory_usage_kb
from submission_case_result
where submission_id = :submission_id
order by test_case_id;
```

### 3. 공통 Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue <submissionId>
```

### 4. 공통 기대 로그

- `Received submissionId=<id> from Redis queue judge:queue`
- `Submission <id> changed from PENDING to JUDGING`
- `Submission <id> finished with status=<status>`

---

## 1. Java 검증 작업물

### 1-1. Java WA

#### submission insert SQL

```sql
insert into submission (
    id, problem_id, language, source_code, status, created_at, started_at, finished_at
) values (
    2001, 100, 'java', $$
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int a = scanner.nextInt();
        int b = scanner.nextInt();
        System.out.println(a - b);
    }
}
$$, 'PENDING', now(), null, null
);
```

#### source_code 예시

```java
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int a = scanner.nextInt();
        int b = scanner.nextInt();
        System.out.println(a - b);
    }
}
```

#### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 2001
```

#### 기대 로그

- `Received submissionId=2001 from Redis queue judge:queue`
- `Submission 2001 changed from PENDING to JUDGING`
- `Submission 2001 finished with status=WA`

#### 기대 DB 결과

- `submission.status = WA`
- `started_at` 채워짐
- `finished_at` 채워짐
- `submission_case_result` row 생성
- 최소 1개 이상 `status = WA`

#### 확인용 SQL

```sql
select id, language, status, started_at, finished_at
from submission
where id = 2001;

select submission_id, test_case_id, status
from submission_case_result
where submission_id = 2001
order by test_case_id;
```

#### 검증 성공 기준

- 최종 `submission.status`가 `WA`
- `submission_case_result`에 `WA`가 저장됨

---

### 1-2. Java CE

#### submission insert SQL

```sql
insert into submission (
    id, problem_id, language, source_code, status, created_at, started_at, finished_at
) values (
    2002, 100, 'java', $$
public class Main {
    public static void main(String[] args) {
        System.out.println("missing semicolon")
    }
}
$$, 'PENDING', now(), null, null
);
```

#### source_code 예시

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("missing semicolon")
    }
}
```

#### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 2002
```

#### 기대 로그

- `Received submissionId=2002 from Redis queue judge:queue`
- `Submission 2002 changed from PENDING to JUDGING`
- `Submission 2002 finished with status=CE`

#### 기대 DB 결과

- `submission.status = CE`
- `started_at` 채워짐
- `finished_at` 채워짐
- `submission_case_result`에 `CE` 저장

#### 확인용 SQL

```sql
select id, status, started_at, finished_at
from submission
where id = 2002;

select submission_id, test_case_id, status
from submission_case_result
where submission_id = 2002
order by test_case_id;
```

#### 검증 성공 기준

- 최종 `submission.status`가 `CE`
- `submission_case_result`에도 `CE`가 저장됨

---

### 1-3. Java RE

#### submission insert SQL

```sql
insert into submission (
    id, problem_id, language, source_code, status, created_at, started_at, finished_at
) values (
    2003, 100, 'java', $$
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int a = scanner.nextInt();
        int b = scanner.nextInt();
        int crash = 1 / 0;
        System.out.println(a + b + crash);
    }
}
$$, 'PENDING', now(), null, null
);
```

#### source_code 예시

```java
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int a = scanner.nextInt();
        int b = scanner.nextInt();
        int crash = 1 / 0;
        System.out.println(a + b + crash);
    }
}
```

#### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 2003
```

#### 기대 로그

- `Submission 2003 finished with status=RE`

#### 기대 DB 결과

- `submission.status = RE`
- `started_at`, `finished_at` 채워짐
- `submission_case_result`에 `RE` 저장

#### 확인용 SQL

```sql
select id, status, started_at, finished_at
from submission
where id = 2003;

select submission_id, test_case_id, status
from submission_case_result
where submission_id = 2003
order by test_case_id;
```

#### 검증 성공 기준

- 최종 `submission.status`가 `RE`

---

### 1-4. Java TLE

#### submission insert SQL

```sql
insert into submission (
    id, problem_id, language, source_code, status, created_at, started_at, finished_at
) values (
    2004, 100, 'java', $$
public class Main {
    public static void main(String[] args) {
        while (true) {
        }
    }
}
$$, 'PENDING', now(), null, null
);
```

#### source_code 예시

```java
public class Main {
    public static void main(String[] args) {
        while (true) {
        }
    }
}
```

#### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 2004
```

#### 기대 로그

- `Docker execution timed out for submission 2004`
- `Submission 2004 finished with status=TLE`

#### 기대 DB 결과

- `submission.status = TLE`
- `started_at`, `finished_at` 채워짐
- `submission_case_result`에 `TLE` 저장

#### 확인용 SQL

```sql
select id, status, started_at, finished_at
from submission
where id = 2004;

select submission_id, test_case_id, status
from submission_case_result
where submission_id = 2004
order by test_case_id;
```

#### 검증 성공 기준

- 최종 `submission.status`가 `TLE`
- timeout 로그가 남음

---

## 2. Python 검증 작업물

### 2-1. Python WA

#### submission insert SQL

```sql
insert into submission (
    id, problem_id, language, source_code, status, created_at, started_at, finished_at
) values (
    3001, 100, 'python', $$
a, b = map(int, input().split())
print(a - b)
$$, 'PENDING', now(), null, null
);
```

#### source_code 예시

```python
a, b = map(int, input().split())
print(a - b)
```

#### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 3001
```

#### 기대 로그

- `Submission 3001 finished with status=WA`

#### 기대 DB 결과

- `submission.status = WA`
- `submission_case_result`에 `WA` 저장

#### 확인용 SQL

```sql
select id, status, started_at, finished_at
from submission
where id = 3001;

select submission_id, test_case_id, status
from submission_case_result
where submission_id = 3001
order by test_case_id;
```

#### 검증 성공 기준

- 최종 `submission.status`가 `WA`

---

### 2-2. Python RE

#### submission insert SQL

```sql
insert into submission (
    id, problem_id, language, source_code, status, created_at, started_at, finished_at
) values (
    3002, 100, 'python', $$
a, b = map(int, input().split())
print((a + b) // 0)
$$, 'PENDING', now(), null, null
);
```

#### source_code 예시

```python
a, b = map(int, input().split())
print((a + b) // 0)
```

#### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 3002
```

#### 기대 로그

- `Submission 3002 finished with status=RE`

#### 기대 DB 결과

- `submission.status = RE`
- `submission_case_result`에 `RE` 저장

#### 확인용 SQL

```sql
select id, status, started_at, finished_at
from submission
where id = 3002;

select submission_id, test_case_id, status
from submission_case_result
where submission_id = 3002
order by test_case_id;
```

#### 검증 성공 기준

- 최종 `submission.status`가 `RE`

---

### 2-3. Python TLE

#### submission insert SQL

```sql
insert into submission (
    id, problem_id, language, source_code, status, created_at, started_at, finished_at
) values (
    3003, 100, 'python', $$
while True:
    pass
$$, 'PENDING', now(), null, null
);
```

#### source_code 예시

```python
while True:
    pass
```

#### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 3003
```

#### 기대 로그

- `Docker execution timed out for submission 3003`
- `Submission 3003 finished with status=TLE`

#### 기대 DB 결과

- `submission.status = TLE`
- `submission_case_result`에 `TLE` 저장

#### 확인용 SQL

```sql
select id, status, started_at, finished_at
from submission
where id = 3003;

select submission_id, test_case_id, status
from submission_case_result
where submission_id = 3003
order by test_case_id;
```

#### 검증 성공 기준

- 최종 `submission.status`가 `TLE`

---

## 3. C++ 검증 작업물

### 3-1. C++ AC

#### submission insert SQL

```sql
insert into submission (
    id, problem_id, language, source_code, status, created_at, started_at, finished_at
) values (
    4001, 100, 'cpp', $$
#include <iostream>

int main() {
    int a, b;
    std::cin >> a >> b;
    std::cout << a + b << '\n';
    return 0;
}
$$, 'PENDING', now(), null, null
);
```

#### source_code 예시

```cpp
#include <iostream>

int main() {
    int a, b;
    std::cin >> a >> b;
    std::cout << a + b << '\n';
    return 0;
}
```

#### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 4001
```

#### 기대 로그

- `Executing C++ submission 4001`
- `Starting Docker execution for submission 4001`
- `Docker execution finished for submission 4001 with image=gcc:13 exitCode=0`
- `Submission 4001 finished with status=AC`

#### 기대 DB 결과

- `submission.status = AC`
- 모든 `submission_case_result.status = AC`

#### 확인용 SQL

```sql
select id, status, started_at, finished_at
from submission
where id = 4001;

select submission_id, test_case_id, status
from submission_case_result
where submission_id = 4001
order by test_case_id;
```

#### 검증 성공 기준

- 최종 `submission.status`가 `AC`

---

### 3-2. C++ WA

#### submission insert SQL

```sql
insert into submission (
    id, problem_id, language, source_code, status, created_at, started_at, finished_at
) values (
    4002, 100, 'cpp', $$
#include <iostream>

int main() {
    int a, b;
    std::cin >> a >> b;
    std::cout << a - b << '\n';
    return 0;
}
$$, 'PENDING', now(), null, null
);
```

#### source_code 예시

```cpp
#include <iostream>

int main() {
    int a, b;
    std::cin >> a >> b;
    std::cout << a - b << '\n';
    return 0;
}
```

#### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 4002
```

#### 기대 로그

- `Submission 4002 finished with status=WA`

#### 기대 DB 결과

- `submission.status = WA`
- `submission_case_result`에 `WA` 저장

#### 확인용 SQL

```sql
select id, status, started_at, finished_at
from submission
where id = 4002;

select submission_id, test_case_id, status
from submission_case_result
where submission_id = 4002
order by test_case_id;
```

#### 검증 성공 기준

- 최종 `submission.status`가 `WA`

---

### 3-3. C++ RE

#### submission insert SQL

```sql
insert into submission (
    id, problem_id, language, source_code, status, created_at, started_at, finished_at
) values (
    4003, 100, 'cpp', $$
#include <iostream>

int main() {
    int a, b;
    std::cin >> a >> b;
    int zero = 0;
    std::cout << (a + b) / zero << '\n';
    return 0;
}
$$, 'PENDING', now(), null, null
);
```

#### source_code 예시

```cpp
#include <iostream>

int main() {
    int a, b;
    std::cin >> a >> b;
    int zero = 0;
    std::cout << (a + b) / zero << '\n';
    return 0;
}
```

#### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 4003
```

#### 기대 로그

- `Submission 4003 finished with status=RE`

#### 기대 DB 결과

- `submission.status = RE`
- `submission_case_result`에 `RE` 저장

#### 확인용 SQL

```sql
select id, status, started_at, finished_at
from submission
where id = 4003;

select submission_id, test_case_id, status
from submission_case_result
where submission_id = 4003
order by test_case_id;
```

#### 검증 성공 기준

- 최종 `submission.status`가 `RE`

---

### 3-4. C++ TLE

#### submission insert SQL

```sql
insert into submission (
    id, problem_id, language, source_code, status, created_at, started_at, finished_at
) values (
    4004, 100, 'cpp', $$
int main() {
    while (true) {
    }
}
$$, 'PENDING', now(), null, null
);
```

#### source_code 예시

```cpp
int main() {
    while (true) {
    }
}
```

#### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 4004
```

#### 기대 로그

- `Docker execution timed out for submission 4004`
- `Submission 4004 finished with status=TLE`

#### 기대 DB 결과

- `submission.status = TLE`
- `submission_case_result`에 `TLE` 저장

#### 확인용 SQL

```sql
select id, status, started_at, finished_at
from submission
where id = 4004;

select submission_id, test_case_id, status
from submission_case_result
where submission_id = 4004
order by test_case_id;
```

#### 검증 성공 기준

- 최종 `submission.status`가 `TLE`

---

### 3-5. C++ CE

#### submission insert SQL

```sql
insert into submission (
    id, problem_id, language, source_code, status, created_at, started_at, finished_at
) values (
    4005, 100, 'cpp', $$
#include <iostream>

int main() {
    std::cout << "compile error" << std::endl
    return 0;
}
$$, 'PENDING', now(), null, null
);
```

#### source_code 예시

```cpp
#include <iostream>

int main() {
    std::cout << "compile error" << std::endl
    return 0;
}
```

#### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 4005
```

#### 기대 로그

- compile error는 Docker stderr에 남을 가능성이 큼
- 현재 구현은 Java만 `CE`를 별도 분류함

#### 기대 DB 결과

- 현재 구현 기준 예상 최종 상태:
  - `RE` 가능성 높음
  - 경우에 따라 `SYSTEM_ERROR` 가능성도 배제할 수 없음

#### 확인용 SQL

```sql
select id, status, started_at, finished_at
from submission
where id = 4005;

select submission_id, test_case_id, status
from submission_case_result
where submission_id = 4005
order by test_case_id;
```

#### 검증 성공 기준

- 현재 단계 목표는 "실제 저장 결과 확인"입니다.
- 정확히 `CE`가 아니어도 현재 구현 결과를 확인하면 검증 완료로 봅니다.

#### 추가 수정 필요

- C++ compile error를 `CE`로 고정하려면 C++용 stderr 분류 로직 추가가 필요합니다.

---

## 4. 동시성 제한 검증 작업물

### 목적

- 동시에 최대 2개만 채점되는지 확인

### submission insert SQL

```sql
insert into submission (id, problem_id, language, source_code, status, created_at, started_at, finished_at)
values
(5001, 100, 'java', $$
public class Main {
    public static void main(String[] args) {
        while (true) {
        }
    }
}
$$, 'PENDING', now(), null, null),
(5002, 100, 'java', $$
public class Main {
    public static void main(String[] args) {
        while (true) {
        }
    }
}
$$, 'PENDING', now(), null, null),
(5003, 100, 'java', $$
public class Main {
    public static void main(String[] args) {
        while (true) {
        }
    }
}
$$, 'PENDING', now(), null, null);
```

### source_code 예시

```java
public class Main {
    public static void main(String[] args) {
        while (true) {
        }
    }
}
```

### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 5001
redis-cli LPUSH judge:queue 5002
redis-cli LPUSH judge:queue 5003
```

### 기대 로그

- 처음 2개 submission만 먼저 consume / start
- 3번째는 바로 실행되지 않고 대기
- 하나가 끝난 뒤 다음 하나 시작

### 기대 DB 결과

- 어느 시점에도 `status='JUDGING'`는 최대 2개

### 확인용 SQL

```sql
select count(*)
from submission
where status = 'JUDGING';

select id, status, started_at, finished_at
from submission
where id in (5001, 5002, 5003)
order by id;
```

### 검증 성공 기준

- `JUDGING` 개수가 2를 넘지 않음
- 3번째 submission은 슬롯이 날 때 시작

---

## 5. cleanup 검증 작업물

### 목적

- 성공 / 실패 / timeout 후 temp directory와 Docker 컨테이너가 정리되는지 확인

### 추천 실행 케이스

- AC: `4001`
- RE: `4003`
- TLE: `4004`

### Redis enqueue 명령

```powershell
redis-cli LPUSH judge:queue 4001
redis-cli LPUSH judge:queue 4003
redis-cli LPUSH judge:queue 4004
```

### 기대 로그

- `Deleted temp directory ...`
- 필요 시:
  - `Destroyed running Docker client process ...`
  - `Cleaned Docker container ...`

### 기대 DB 결과

- 실패 케이스여도 `finished_at` 채워짐
- `JUDGING`에 방치되지 않음

### 확인용 명령

임시 디렉터리:

```powershell
Get-ChildItem $env:TEMP -Directory | Where-Object { $_.Name -like 'judge-*' }
```

컨테이너 잔존 여부:

```powershell
docker ps -a
```

DB 확인:

```sql
select id, status, started_at, finished_at
from submission
where id in (4001, 4003, 4004)
order by id;
```

### 검증 성공 기준

- `judge-*` temp directory가 남지 않음
- judge 실행 컨테이너가 남지 않음
- 각 submission이 최종 상태로 종료됨

---

## 6. Next.js 연동 체크리스트

### 확인 항목

- [ ] Next.js가 Redis `judge:queue`에 push 하는가
- [ ] payload가 `submissionId` 하나인가
- [ ] JSON payload를 쓰지 않는가
- [ ] `language` 값이 아래 중 하나인가
  - `java`
  - `python`
  - `cpp`
- [ ] status enum이 아래와 일치하는가
  - `PENDING`
  - `JUDGING`
  - `AC`
  - `WA`
  - `CE`
  - `RE`
  - `TLE`
  - `MLE`
  - `SYSTEM_ERROR`
- [ ] Next.js가 `submission`을 먼저 저장하고, 그 다음 enqueue 하는가

### submission insert SQL

이 항목은 Next.js가 직접 수행하는 동작 확인이 목적이므로, 수동 insert 대신 실제 Next.js 제출 API 호출을 우선합니다.

수동 비교 기준 SQL:

```sql
select id, language, status, created_at, started_at, finished_at
from submission
where id = :submission_id;
```

### source_code 예시

실제 Next.js 제출 화면/API에서 아래 예시 중 하나를 사용합니다.

- `java` 예시: Java AC / WA / CE / RE / TLE 샘플
- `python` 예시: Python AC / WA / RE / TLE 샘플
- `cpp` 예시: C++ AC / WA / RE / TLE 샘플

### Redis enqueue 명령

직접 enqueue 대신, Next.js 제출 후 Redis에 아래와 같은 형태로 들어가는지 확인합니다.

```powershell
redis-cli LRANGE judge:queue 0 -1
```

### 기대 로그

- `Received submissionId=<id> from Redis queue judge:queue`

### 기대 DB 결과

- `submission.id = <id>` row가 존재
- worker가 그 row를 조회하고 상태를 갱신

### 확인용 SQL

```sql
select id, language, status
from submission
where id = :submission_id;
```

### 검증 성공 기준

- Next.js 제출 1건이 worker까지 정상 전달됨
- queue key / payload / language / status enum 불일치가 없음

---

## 최종 실행 순서

1. 공통 problem / hidden test case 생성
2. Java WA / CE / RE / TLE 검증
3. Python WA / RE / TLE 검증
4. C++ AC / WA / RE / TLE 검증
5. C++ CE 실제 결과 확인
6. 동시성 2개 제한 검증
7. cleanup 검증
8. Next.js 계약 검증
