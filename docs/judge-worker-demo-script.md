# Judge Worker 데모 스크립트

이 문서는 현재 Docker 기반 로컬 개발/검증용 worker 기준 문서입니다. 운영 최종 구조 소개 문서가 아니며, 현재 구현 완료 범위를 짧게 시연하는 목적에 맞춰 작성했습니다. 운영 환경 설명이 필요하면 별도 자료로 다시 구성해야 합니다.

## 문서 범위

- 구현 완료된 현재 worker 데모
- 로컬 개발과 검증 시연 기준
- 운영 구조로는 재검토 필요

## 공통 기준

- Redis queue key: `judge:queue`
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
  - `started_at`, `finished_at`, 최종 상태 저장

## 3~5분 데모에서 보여줄 것

- `submissionId`만 Redis에 enqueue 되는 점
- worker가 비동기로 consume 하는 점
- `PENDING -> JUDGING -> 최종 상태`
- `submission_case_result` 저장
- 최대 동시성 `2`

## 시연 전 준비

- PostgreSQL 실행
- Redis 실행
- Docker 실행
- Spring worker 실행
- hidden test case가 있는 problem 준비
- 샘플 submission 준비

추천 샘플:

- AC: `samples/submissions/java/ac/Main.java`
- WA: `samples/submissions/java/wa/Main.java`
- CE: `samples/submissions/java/ce/Main.java`
- 동시성 확인: 각 언어의 TLE 샘플

## 추천 화면 구성

- 터미널 1: worker 로그
- 터미널 2: Redis 명령
- DB 클라이언트: `submission`, `submission_case_result`
- 선택: Next.js 제출 화면 또는 API 클라이언트

## 가장 짧은 데모 흐름

1. AC 제출
2. WA 또는 CE 제출
3. 느린 submission 3건 enqueue

## AC 데모 멘트

- "제출은 먼저 `PENDING`으로 저장됩니다."
- "큐에는 `submissionId`만 들어갑니다."
- "worker가 Redis `judge:queue`를 polling 해서 비동기로 채점합니다."
- "채점은 Docker 안에서 실행되고, `started_at`과 `finished_at`이 남습니다."

## WA 또는 CE 데모 포인트

- WA:
  - 첫 실패 시 즉시 중단
  - 실패 지점까지만 `submission_case_result` 저장
- CE:
  - compile 실패 분류 확인
  - 최종 상태와 `finished_at` 저장

## 동시성 데모 포인트

- 처음 2개만 `JUDGING`
- 나머지는 잠시 `PENDING`
- 하나가 끝난 뒤 다음 submission 시작

사용 예시:

```powershell
redis-cli LPUSH judge:queue 201
redis-cli LPUSH judge:queue 202
redis-cli LPUSH judge:queue 203
```

## 로그에서 보여줄 부분

- `Received submissionId=... from Redis queue judge:queue`
- `Submission ... changed from PENDING to JUDGING`
- `Submission ... finished with status=...`

## 정리 멘트

- "현재 구현은 로컬 검증용 Docker 기반 worker입니다."
- "Redis queue key는 `judge:queue`이고 payload는 `submissionId` 하나입니다."
- "현재 실행 가능한 언어는 `java`, `python`, `cpp`입니다."
- "운영 구조로 쓰기 전에는 분산 처리, 보안, 관측성을 다시 검토해야 합니다."
