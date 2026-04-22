# Judge Worker Cleanup 점검

이 문서는 현재 Docker 기반 executor가 로컬 실행 후 임시 파일과 컨테이너를 정리하는지 확인하기 위한 문서다.

## 1. 범위

확인 대상:

- Java executor 임시 디렉토리
- Python executor 임시 디렉토리
- C++ executor 임시 디렉토리
- Docker 컨테이너 잔존 여부
- 실패/timeout/system error 후 DB 마감 여부

## 2. 현재 cleanup 기준

현재 executor는 언어별 임시 작업 디렉토리를 만들고, 실행 후 정리를 시도한다.

언어별 temp prefix:

- Java: `judge-java-`
- Python: `judge-python-`
- C++: `judge-cpp-`

Docker 실행은 `--rm` 기반으로 컨테이너 제거를 기대한다.

## 3. DB 마감 기준

채점이 끝나면 다음을 확인한다.

`submissions`:

- `status=DONE`
- `result` 저장
- `judged_at` 저장
- `execution_time_ms` 저장
- `memory_kb` 저장

예전 기준인 `finished_at`은 현재 DB/Entity 기준이 아니다.

## 4. 확인 명령

temp directory 확인:

```powershell
Get-ChildItem $env:TEMP -Directory | Where-Object {
    $_.Name -like 'judge-java-*' -or
    $_.Name -like 'judge-python-*' -or
    $_.Name -like 'judge-cpp-*'
}
```

Docker 컨테이너 확인:

```powershell
docker ps
docker ps -a
```

## 5. 검증 시나리오

AC:

- 정상 실행 후 temp directory가 남지 않는지 확인
- `submission_testcase_results.result=AC` 확인

RE:

- runtime error 후 temp directory가 남지 않는지 확인
- `submissions.status=DONE`
- `submissions.result=RE`

TLE:

- timeout 후 컨테이너가 종료되는지 확인
- `submissions.result=TLE`

SYSTEM_ERROR:

- Docker daemon 중단, 잘못된 이미지 등에서 가능한 범위의 cleanup이 수행되는지 확인
- `submissions.result=SYSTEM_ERROR`

## 6. 실패 시 의심 지점

- worker process가 실행 중 강제 종료됨
- OS file lock 또는 권한 문제
- Docker daemon 중단
- `--rm`이 적용되지 않은 컨테이너 실행
- executor 예외 처리 경로 누락
