# 현재 아키텍처 vs 검토 중 아키텍처

이 문서는 현재 구현된 구조와 검토 중인 구조를 섞지 않고 구분해서 설명하기 위한 요약 문서입니다. 면접이나 포트폴리오에서 "지금 구현한 것"과 "다음 단계로 검토하는 것"을 짧게 설명할 때 사용하는 용도를 기준으로 작성했습니다.

## 문서 범위

- 현재 구현된 아키텍처 요약
- 검토 중인 목표 아키텍처 방향 요약
- 현재 구현됨 / 검토 중 / 미구현 항목 구분

## 현재 아키텍처

현재 구현 기준 아키텍처는 아래와 같습니다.

- Next.js / API
  - 문제 조회
  - 코드 제출
  - 제출 상태 / 결과 조회
- PostgreSQL
  - 문제, 테스트케이스, 제출, 케이스별 결과 저장
- Redis queue
  - `judge:queue`
  - payload는 `submissionId`
- Spring Judge Worker
  - Redis를 polling 하며 submission을 비동기로 채점
  - `PENDING -> JUDGING -> 최종 상태` 처리
- Docker 기반 실행
  - worker 내부 executor가 직접 `docker run` 호출
  - Java / Python / C++ 채점 수행

현재 구조의 핵심은 "상시 떠 있는 Spring worker가 Redis queue를 읽고, Docker를 직접 실행해서 채점하는 구조"입니다.

## 검토 중 아키텍처

검토 중인 방향은 아래와 같습니다.

- 요청형 실행 구조
  - 항상 떠 있는 worker보다 요청 또는 job 단위로 채점 실행
- Cloud Run 기반 후보
  - scale-to-zero
  - 요청 기반 과금
  - 개인 프로젝트 초기 운영 비용 절감 기대
- 실행 방식 재설계 필요
  - 현재 `docker run` 기반 executor를 그대로 옮기는 방식이 아님
  - queue 소비 방식, 채점 시작 방식, sandbox 방식까지 다시 설계해야 함

검토 중 구조의 핵심은 "상시 worker를 유지하는 구조"가 아니라 "필요할 때만 실행되는 채점 구조"를 고려한다는 점입니다.

## 현재 구조와 검토 구조를 섞으면 안 되는 이유

현재 구조는 이미 동작하는 구현입니다.

- Next.js
- PostgreSQL
- Redis queue
- Spring Judge Worker
- Docker 기반 executor

반면 검토 중 구조는 아직 방향만 있는 상태입니다.

- 요청형 실행 구조
- Cloud Run 기반 후보
- 채점 실행 모델 재설계

즉, 지금 포트폴리오에서 말할 수 있는 것은 "현재는 Spring worker + Redis + Docker 구조를 구현했다"이고, 추가로 "비용과 운영 부담을 줄이기 위해 Cloud Run 기반 요청형 구조를 검토 중이다"까지가 정확한 설명입니다.

## 한 줄 비교

- 현재 아키텍처:
  - 상시 worker가 Redis queue를 읽고 Docker로 채점
- 검토 중 아키텍처:
  - 요청형 실행 구조로 전환하고 Cloud Run 같은 서버리스 후보를 검토

## 상태 구분표

| 구분 | 항목 | 설명 |
| --- | --- | --- |
| 현재 구현됨 | Next.js / API | 제출, 조회, 상태 확인을 담당하는 웹/API 계층 |
| 현재 구현됨 | PostgreSQL | 문제, 테스트케이스, 제출, 채점 결과 저장 |
| 현재 구현됨 | Redis queue | `judge:queue`에 `submissionId`를 넣고 worker가 consume |
| 현재 구현됨 | Spring Judge Worker | 상시 실행되며 Redis polling 후 채점 수행 |
| 현재 구현됨 | Docker 기반 실행 | executor가 직접 `docker run`을 호출해 Java / Python / C++ 실행 |
| 검토 중 | 요청형 실행 구조 | 상시 worker 대신 요청 또는 job 단위로 채점 실행 |
| 검토 중 | Cloud Run 기반 후보 | scale-to-zero, 요청 기반 과금, 초기 운영 비용 절감 목적 |
| 검토 중 | 실행 방식 재설계 | 현재 Docker executor와 queue 소비 모델을 그대로 쓰지 않는 방향 검토 |
| 미구현 | Cloud Run용 채점 아키텍처 | Cloud Run 제약에 맞는 실제 채점 실행 구조 |
| 미구현 | Docker 대체 또는 원격 sandbox 방식 | 현재 로컬 `docker run` 대신 쓸 실행 모델 |
| 미구현 | 요청형 queue / job orchestration | 요청 기반 채점 시작과 완료 처리 흐름 |
| 미구현 | 전환 후 운영 구조 | 배포, 확장, 장애 대응을 포함한 최종 운영 설계 |

## 면접용 설명 예시

"현재는 Next.js, PostgreSQL, Redis queue, Spring Judge Worker, Docker 기반 실행으로 온라인 저지 MVP를 구현했습니다.  
추가로 개인 프로젝트 비용과 운영 부담을 줄이기 위해 Cloud Run 기반 요청형 실행 구조를 검토하고 있지만, 이건 단순 인프라 교체가 아니라 채점 실행 방식 자체를 다시 설계해야 하는 단계라 아직 구현 전입니다."
