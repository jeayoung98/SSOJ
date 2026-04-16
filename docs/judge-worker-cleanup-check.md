# Judge Worker cleanup 검증

## 범위

이 문서는 현재 구현 기준으로 작성되었습니다.

현재 worker의 cleanup 관련 동작:

- executor는 로컬 OS temp 경로 아래에 임시 디렉터리를 만듭니다.
- executor는 Docker를 `--rm`으로 실행합니다.
- executor는 `finally`에서 임시 디렉터리를 삭제합니다.
- executor는 `finally`에서 프로세스가 살아 있으면 강제 종료합니다.
- `JudgeService`는 `finally`에서 submission 최종 상태를 정리합니다.

현재 실제 실행 가능한 언어:

- `java`
- `python`

## 목표

아래 항목을 확인합니다.

- 채점 후 임시 디렉터리가 삭제되는가
- 채점 후 Docker 컨테이너가 남지 않는가
- 성공, compile/runtime 실패, timeout, 예외 경로에서도 cleanup이 수행되는가

## 1. 성공 케이스에서 확인할 항목

사용 샘플:

- Java AC 샘플
  - `samples/submissions/java/ac/Main.java`
- Python AC 샘플
  - `samples/submissions/python/ac/main.py`

체크리스트:

- [ ] Submission이 정상적으로 채점 시작
- [ ] 최종 `submission.status`가 `AC`
- [ ] `started_at`이 채워짐
- [ ] `finished_at`이 채워짐
- [ ] `submission_case_result` row가 저장됨
- [ ] executor가 만든 temp directory가 채점 후 삭제됨
- [ ] 실행 후 Docker 컨테이너가 남지 않음

## 2. CE / RE / TLE 발생 시 확인할 항목

### CE 케이스

현재 실질적으로 확인 가능한 범위:

- Java CE
  - `samples/submissions/java/ce/Main.java`

체크리스트:

- [ ] 최종 `submission.status`가 `CE`
- [ ] temp directory 삭제됨
- [ ] Docker 컨테이너가 남지 않음
- [ ] 이후 submission도 계속 처리 가능함

### RE 케이스

사용 샘플:

- Java RE
  - `samples/submissions/java/re/Main.java`
- Python RE
  - `samples/submissions/python/re/main.py`

체크리스트:

- [ ] 최종 `submission.status`가 `RE`
- [ ] temp directory 삭제됨
- [ ] Docker 컨테이너가 남지 않음
- [ ] `finished_at`이 채워짐

### TLE 케이스

사용 샘플:

- Java TLE
  - `samples/submissions/java/tle/Main.java`
- Python TLE
  - `samples/submissions/python/tle/main.py`

체크리스트:

- [ ] 최종 `submission.status`가 `TLE`
- [ ] timeout 된 프로세스가 종료됨
- [ ] temp directory 삭제됨
- [ ] Docker 컨테이너가 남지 않음
- [ ] `finished_at`이 채워짐

## 3. 임시 디렉터리 확인 방법

### 현재 temp directory prefix

- Java executor:
  - `judge-java-`
- Python executor:
  - `judge-python-`

### Windows PowerShell 확인

테스트 전:

```powershell
Get-ChildItem $env:TEMP -Directory | Where-Object { $_.Name -like 'judge-java-*' -or $_.Name -like 'judge-python-*' }
```

submission 실행 후 종료를 기다린 다음 다시 확인:

```powershell
Get-ChildItem $env:TEMP -Directory | Where-Object { $_.Name -like 'judge-java-*' -or $_.Name -like 'judge-python-*' }
```

기대 결과:

- [ ] 종료된 실행의 temp directory가 남아 있지 않음

### 반복 관찰용 예시

```powershell
1..10 | ForEach-Object {
    Get-ChildItem $env:TEMP -Directory | Where-Object { $_.Name -like 'judge-java-*' -or $_.Name -like 'judge-python-*' }
    Start-Sleep -Seconds 1
}
```

## 4. Docker 컨테이너 잔존 여부 확인 방법

현재 executor는 `docker run --rm`을 쓰므로, 실행이 끝난 컨테이너는 남지 않아야 합니다.

### 실행 중 컨테이너 확인

```powershell
docker ps
```

### 종료된 것 포함 전체 컨테이너 확인

```powershell
docker ps -a
```

### 추천 관찰 방식

1. 테스트 전 컨테이너 목록 확인
2. submission 하나 실행
3. 실행 중 잠깐 컨테이너가 보일 수 있음
4. 실행이 끝난 뒤 다시 확인

기대 결과:

- [ ] judge run에서 생성된 컨테이너가 종료 후 남아 있지 않음

### 간단한 반복 확인

```powershell
1..10 | ForEach-Object {
    docker ps -a
    Start-Sleep -Seconds 1
}
```

## 5. 예외 발생 시에도 cleanup가 수행되는지 검증

현재 구현은 executor 실패를 `SYSTEM_ERROR`로 처리합니다.
또한 `JudgeService`는 `finally`로 `JUDGING` 상태 방치를 막습니다.

### 예외에 가까운 경로를 만드는 쉬운 방법

#### Docker 실행 실패

방법 예시:

- Docker Desktop 중지
- `application.properties`에 잘못된 이미지명 설정

예시:

- `worker.executor.java.image=not-found-image`
- 또는 Docker 자체를 종료

기대 결과:

- [ ] 최종 `submission.status`가 `SYSTEM_ERROR`
- [ ] `finished_at`이 채워짐
- [ ] temp directory가 남지 않음
- [ ] Docker 컨테이너가 남지 않음

#### executor 없음

방법 예시:

- `submission.language=cpp`로 저장

기대 결과:

- [ ] 최종 `submission.status`가 `SYSTEM_ERROR`
- [ ] `finished_at`이 채워짐

메모:

- 이건 Docker 예외 경로는 아니지만, submission 마무리 로직 검증에는 유용합니다.

## 6. cleanup 실패 시 의심 포인트

임시 디렉터리가 남아 있으면:

- [ ] `finally`가 돌기 전에 프로세스가 외부에서 강제 종료됨
- [ ] worker 프로세스가 채점 중 비정상 종료됨
- [ ] temp directory 내부 파일이 OS에 의해 lock 되어 있음
- [ ] 백신 또는 다른 프로세스가 파일 삭제를 방해함
- [ ] temp path 권한이 비정상적임

Docker 컨테이너가 남아 있으면:

- [ ] 컨테이너가 `--rm` 없이 실행되었음
- [ ] Docker daemon이 실행 중간에 끊김
- [ ] Docker cleanup 전에 worker 프로세스가 종료됨
- [ ] 로컬에서 수동 실행한 다른 Docker 컨테이너와 혼동하고 있음

Submission이 `JUDGING`에 머물면:

- [ ] worker 프로세스가 transaction commit 전에 죽었음
- [ ] DB 연결 문제로 최종 업데이트가 실패했음
- [ ] 예상한 service 흐름 밖에서 예외가 발생했음
- [ ] 여러 worker 버전이 동시에 떠서 간섭 중임

## 7. 추천 수동 검증 순서

1. 기존 temp directory 확인
2. 기존 Docker 컨테이너 확인
3. AC submission 실행
4. 최종 DB 상태와 cleanup 확인
5. CE 또는 RE submission 실행
6. 최종 DB 상태와 cleanup 확인
7. TLE submission 실행
8. 최종 DB 상태와 cleanup 확인
9. Docker 종료 또는 잘못된 이미지 설정으로 executor 실패 유도
10. `SYSTEM_ERROR`, `finished_at`, cleanup 동작 확인
