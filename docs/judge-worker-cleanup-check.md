# Judge Worker Cleanup 점검

이 문서는 현재 Docker 기반 로컬 개발/검증용 worker 기준 문서입니다. 운영 최종 구조 문서가 아니며, 로컬 executor cleanup 동작 확인에만 초점을 둡니다. 운영 환경 적용 전에는 임시 파일 관리, 컨테이너 정리, 장애 복구 전략을 다시 검토해야 합니다.

## 문서 범위

- 구현 완료된 현재 cleanup 동작 확인
- 로컬 개발과 수동 검증 기준
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
  - `finished_at` 저장

## 현재 cleanup 동작

- executor는 OS temp 경로 아래에 임시 디렉터리를 생성
- Docker는 `--rm`으로 실행
- executor는 `finally`에서 temp directory 삭제 시도
- executor는 살아 있는 프로세스를 `finally`에서 종료
- `JudgeService`는 `finally`에서 최종 상태 저장 시도

## 언어별 temp prefix

- Java: `judge-java-`
- Python: `judge-python-`
- C++: `judge-cpp-`

## 목표

- 채점 후 temp directory가 남지 않는지 확인
- 채점 후 Docker 컨테이너가 남지 않는지 확인
- 성공, 실패, timeout, 예외 경로에서 `finished_at`과 최종 상태가 저장되는지 확인

## 추천 점검 시나리오

- AC:
  - `samples/submissions/java/ac/Main.java`
  - `samples/submissions/python/ac/main.py`
  - `samples/submissions/cpp/ac/main.cpp`
- RE:
  - `samples/submissions/java/re/Main.java`
  - `samples/submissions/python/re/main.py`
  - `samples/submissions/cpp/re/main.cpp`
- TLE:
  - `samples/submissions/java/tle/Main.java`
  - `samples/submissions/python/tle/main.py`
  - `samples/submissions/cpp/tle/main.cpp`

## temp directory 확인

```powershell
Get-ChildItem $env:TEMP -Directory | Where-Object {
    $_.Name -like 'judge-java-*' -or
    $_.Name -like 'judge-python-*' -or
    $_.Name -like 'judge-cpp-*'
}
```

## Docker 컨테이너 잔존 확인

```powershell
docker ps
docker ps -a
```

## 기대 결과

- 정상 종료 후 temp directory가 남지 않음
- 종료 후 judge 실행 컨테이너가 남지 않음
- `submission.finished_at` 저장
- `submission.status`가 최종 상태로 마감
- 첫 실패 시 뒤 hidden test case는 실행되지 않음

## 예외 경로 점검

- Docker 실행 실패
  - Docker Desktop 중지
  - 잘못된 Docker 이미지 설정
- unsupported language
  - 현재 지원 언어 외 값 저장

기대 결과:

- 최종 `submission.status=SYSTEM_ERROR`
- `finished_at` 저장
- 가능한 범위의 temp 정리 수행

## cleanup 실패 시 의심 포인트

- worker 프로세스가 채점 중 비정상 종료
- OS lock 또는 권한 문제
- Docker daemon 중단
- 다른 로컬 컨테이너와 혼동
