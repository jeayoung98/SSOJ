# Next.js와 Spring Worker 연동 체크리스트

이 문서는 현재 Docker 기반 로컬 개발/검증용 worker 기준 문서입니다. 운영 최종 구조 문서가 아니며, 현재 Next.js 제출 경로와 Spring worker 사이의 로컬 계약만 점검합니다. 운영 환경 적용 전에는 큐 계약, 장애 복구, 멀티 인스턴스 전략을 다시 검토해야 합니다.

## 문서 범위

- 구현 완료된 현재 Next.js-Spring worker 계약 확인
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
  - Spring worker가 Docker 기반 executor를 사용

## Redis 계약

- Next.js가 `judge:queue`에 push 하는지
- Spring worker가 `judge:queue`를 읽는지
- payload가 JSON이 아니라 `submissionId` plain value인지
- Next.js와 worker가 같은 Redis 인스턴스를 보는지

## Submission 생성 계약

- Next.js가 `submission`을 먼저 저장하는지
- 초기 상태를 `PENDING`으로 저장하는지
- `language` 값이 worker 기대값과 일치하는지

권장 `language` 값:

- `java`
- `python`
- `cpp`

## worker 기대 상태 전이

1. Next.js가 `submission` row 저장
2. Next.js가 Redis `judge:queue`에 `submissionId` push
3. worker가 queue consume
4. worker가 `PENDING -> JUDGING`
5. worker가 `started_at` 저장
6. worker가 hidden test case를 Docker에서 실행
7. 첫 실패 시 즉시 중단
8. 실행된 case까지만 `submission_case_result` 저장
9. worker가 최종 `submission.status`와 `finished_at` 저장

## 빠른 점검 목록

- Next.js가 `submissionId`만 enqueue 하는가
- queue key가 `judge:queue`와 일치하는가
- `submission.status` 초기값이 `PENDING`인가
- `language`가 `java`, `python`, `cpp` 중 하나인가
- worker 로그에 `Received submissionId=... from Redis queue judge:queue`가 보이는가
- 결과적으로 `started_at`, `finished_at`이 저장되는가

## 문제 발생 시 먼저 볼 것

- 제출이 계속 `PENDING`
  - enqueue 자체가 안 됨
  - Redis key 불일치
  - payload 파싱 실패
  - worker 비활성화
- 제출이 `JUDGING`에서 멈춤
  - Docker 실행 문제
  - 언어별 executor 문제
  - hidden test case 데이터 문제
- 최종 상태가 `SYSTEM_ERROR`
  - Docker 이미지 또는 Docker daemon 문제
  - 지원하지 않는 언어
  - executor 예외

## 빠른 수동 검증

```powershell
redis-cli LPUSH judge:queue <submissionId>
redis-cli LRANGE judge:queue 0 -1
```

DB에서 확인:

- `submission.status`
- `started_at`
- `finished_at`
- `submission_case_result`
