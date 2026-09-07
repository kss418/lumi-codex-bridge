# lumi-codex-bridge

Little LUMI(꼬미)와 Codex를 연결하는 Java 25 플러그인과 Python 3.12 브릿지 프로젝트입니다.

현재 Python Codex 클라이언트를 구현했으며, Java 플러그인 연동은 아직 구현하지 않았습니다.

## 개발 환경

- **JDK:** 레포 내부의 `.tools/jdk-25`에 설치한 Eclipse Temurin 25를 사용합니다.
- **Python:** 기존 MSYS2 Python 3.12로 만든 가상환경 `.venv/bin/python.exe`를 사용합니다.
- **Python 의존성:** 표준 라이브러리만 사용하므로 추가 pip 패키지는 필요 없습니다.
- **Java 컴파일 의존성:** 설치된 Little LUMI의 `app/Shimeji-ee.jar`와 `app/lib/*.jar`를 참조합니다. 해당 파일은 재배포하지 않습니다.
- **Codex:** 설치된 Codex CLI를 사용합니다. 계정 로그인은 개발 환경 설치와 별도로 진행해야 합니다.

레포의 `.vscode/settings.json`에 Java와 Python 경로가 설정되어 있습니다. VS Code 터미널을 새로 열면 적용됩니다.

## 폴더 구조

- `plugin/src/main/java/`: Java 플러그인 소스
- `plugin/src/main/resources/`: `plugin.json`과 `META-INF/services` 등록 파일
- `bridge/`: Python 브릿지 소스
- `scripts/build-plugin.ps1`: Java 소스를 컴파일하고 플러그인 JAR을 만드는 스크립트
- `tests/`: 모델과 추론 강도 선택 기능 테스트
- `dist/`: 빌드 결과물. Git 추적에서 제외됩니다.

Java 소스를 추가한 뒤 레포 루트에서 다음 명령으로 빌드합니다.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-plugin.ps1
```

## Codex 클라이언트 실행

먼저 `codex login`으로 로그인해야 합니다. 아래 명령은 레포 루트에서 실행합니다.

```powershell
# 통신 초기화와 로그인 상태만 확인합니다. 모델 답변은 생성하지 않습니다.
.\.venv\bin\python.exe .\bridge\codex_client.py --check

# 터미널에서 대화합니다. /quit을 입력하면 종료합니다.
.\.venv\bin\python.exe .\bridge\codex_client.py

# 같은 대화에서 메시지 두 개를 순서대로 보냅니다.
.\.venv\bin\python.exe .\bridge\codex_client.py --message "안녕하세요" --message "방금 제가 뭐라고 했나요?"
```

클라이언트는 별도 창 없이 Codex App Server를 자식 프로세스로 실행하고, 표준입출력 파이프로 JSON을 주고받습니다. 초기화 후 임시 대화 하나를 생성하며, 프로그램 실행 중에는 같은 대화를 이어갑니다. 답변 생성에는 로그인한 계정의 Codex 사용량이 적용됩니다.

현재 동작과 제한은 다음과 같습니다.

- 한 번에 하나의 호출과 대화 턴만 처리합니다.
- 완료 이벤트에서 최종 답변을 수집해 한 번에 반환합니다.
- 도구 승인 등 서버에서 보내는 대화형 요청은 지원하지 않습니다.
- 대화 생성 시 읽기 전용 샌드박스를 요청하고, 도구를 사용하지 말라는 지시를 전달합니다.
- 시간 초과 시 요청을 중복 실행하지 않도록 자동 재시도 없이 연결을 종료합니다.
- 재시작 후 대화 복원, 로그인 UI, Java 연동은 아직 구현하지 않았습니다.

Codex 실행 파일을 자동으로 찾지 못하면 `CODEX_EXECUTABLE` 환경변수에 실제 Codex 실행 파일 경로를 지정하세요. 표준출력(stdout)은 UTF-8을 사용하고, Codex 진단 로그는 표준오류(stderr)로 출력합니다. 클라이언트 종료 시 자신이 실행한 Codex 프로세스도 정리합니다.

### 모델과 추론 강도 선택

```powershell
# 사용 가능한 모델과 모델별 지원 추론 강도를 조회합니다.
.\.venv\bin\python.exe .\bridge\codex_client.py --list-models

# 모델과 추론 강도를 지정해 대화합니다.
.\.venv\bin\python.exe .\bridge\codex_client.py --model gpt-5.6-luna --effort low

# 지정한 설정으로 메시지 하나를 보냅니다.
.\.venv\bin\python.exe .\bridge\codex_client.py --model gpt-5.6-luna --effort low --message "안녕하세요"
```

모델 목록과 지원 추론 강도는 실행 중인 Codex 서버에서 조회합니다. 옵션을 생략하면 기존 Codex 설정의 기본값을 유지합니다. 지원하지 않는 모델이나 추론 강도 조합은 모델 답변 생성을 시작하기 전에 오류로 안내합니다.

선택값은 별도 파일에 저장하지 않으며 해당 실행 동안만 사용합니다. 향후 Java 파이프 연동에서도 사용할 수 있도록 `start_thread(model=...)`와 `send_message(thread_id, text, effort=...)` 메서드를 제공합니다.

## 테스트

```powershell
.\.venv\bin\python.exe -m unittest discover -s tests -v
```

모델과 추론 강도 전달, 기본값 유지, 지원하지 않는 선택 거부, 모델 목록 페이지 처리를 검증합니다. 이 테스트는 실제 모델을 호출하지 않습니다.
