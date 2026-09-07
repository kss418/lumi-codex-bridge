# lumi-codex-bridge

Little LUMI(꼬미)와 Codex를 연결하는 Java 25 플러그인과 Python 3.12 브릿지 프로젝트입니다.

현재 Python Codex 클라이언트와 Java 모델 설정창을 구현했습니다. Java 대화창과 Python 파이프 통신을 연결했습니다.

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
- `bridge/`: Python 브릿지 소스. `main.py`는 실행 진입점, `codex_client.py`는 Codex 통신, `stdio_bridge.py`는 Java용 JSON 입출력을 담당합니다.
- `scripts/build-plugin.ps1`: Java 소스를 컴파일하고 플러그인 JAR을 만드는 스크립트
- `tests/`: 모델과 추론 강도 선택 기능 테스트
- `dist/`: 빌드 결과물. Git 추적에서 제외됩니다.

레포 루트에서 다음 명령으로 Java 플러그인을 빌드합니다.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-plugin.ps1
```

## Codex 클라이언트 실행

먼저 `codex login`으로 로그인해야 합니다. 아래 명령은 레포 루트에서 실행합니다.

```powershell
# 통신 초기화와 로그인 상태만 확인합니다. 모델 답변은 생성하지 않습니다.
.\.venv\bin\python.exe .\bridge\main.py --check

# 터미널에서 대화합니다. /quit을 입력하면 종료합니다.
.\.venv\bin\python.exe .\bridge\main.py

# 같은 대화에서 메시지 두 개를 순서대로 보냅니다.
.\.venv\bin\python.exe .\bridge\main.py --message "안녕하세요" --message "방금 제가 뭐라고 했나요?"
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
.\.venv\bin\python.exe .\bridge\main.py --list-models

# 모델과 추론 강도를 지정해 대화합니다.
.\.venv\bin\python.exe .\bridge\main.py --model gpt-5.6-luna --effort low

# 지정한 설정으로 메시지 하나를 보냅니다.
.\.venv\bin\python.exe .\bridge\main.py --model gpt-5.6-luna --effort low --message "안녕하세요"
```

모델 목록과 지원 추론 강도는 실행 중인 Codex 서버에서 조회합니다. 옵션을 생략하면 기존 Codex 설정의 기본값을 유지합니다. 지원하지 않는 모델이나 추론 강도 조합은 모델 답변 생성을 시작하기 전에 오류로 안내합니다.

선택값은 별도 파일에 저장하지 않으며 해당 실행 동안만 사용합니다. 향후 Java 파이프 연동에서도 사용할 수 있도록 `start_thread(model=...)`와 `send_message(thread_id, text, effort=...)` 메서드를 제공합니다.

## 테스트

```powershell
.\.venv\bin\python.exe -m unittest discover -s tests -v
```

모델과 추론 강도 전달, 기본값 유지, 지원하지 않는 선택 거부, 모델 목록 페이지 처리를 검증합니다. 이 테스트는 실제 모델을 호출하지 않습니다.

## Java용 JSON 표준입출력 모드

```powershell
.\.venv\bin\python.exe .\bridge\main.py --stdio
```

Java가 위 프로세스를 실행하고 stdin에 UTF-8 JSON을 한 줄씩 보냅니다. Python은 stdout에 한 줄 JSON으로 응답합니다. stderr는 별도 스레드에서 계속 읽어 로그로 처리해야 합니다. stdout과 stderr를 합치지 마세요.

초기화와 로그인 확인이 성공하면 `{"event":"ready","protocol_version":1}`이 출력됩니다. 초기화 실패 시 `event:error`가 출력되고 종료합니다.

요청 예시:

```json
{"id":1,"method":"chat","params":{"text":"안녕","model":"gpt-5.6-luna","effort":"low"}}
{"id":2,"method":"chat","params":{"text":"방금 뭐라고 했지?"}}
{"id":3,"method":"model/list"}
{"id":4,"method":"status"}
{"id":5,"method":"shutdown"}
```

응답 예시:

```json
{"id":1,"result":{"text":"안녕하세요!","thread_id":"대화 ID"}}
{"id":9,"error":{"code":-32601,"message":"Unknown method: example"}}
```

- `id`는 문자열 또는 정수이며, 응답에 같은 값이 돌아옵니다.
- 요청은 입력 순서대로 처리하며, 현재 답변이 끝난 뒤 다음 요청을 처리합니다.
- 첫 `chat`에서 대화를 만들고 이후에는 같은 대화를 사용합니다.
- `model`과 `effort`를 생략하면 앞서 선택한 값 또는 실행 옵션을 사용합니다. 처음부터 지정하지 않았다면 Codex 기본값을 사용합니다.
- 현재 모델 변경은 새 브릿지 프로세스에서 해야 합니다. 추론 강도는 다음 `chat`에서 변경할 수 있습니다.
- `shutdown` 응답 후 종료합니다. stdin의 EOF로도 종료할 수 있습니다. 생성 중 즉시 취소 기능은 아직 없습니다.
- 세션 저장, Java 플러그인 연결, 오류 후 자동 재연결은 아직 없습니다.
- 배포 시 `main.py`, `codex_client.py`, `stdio_bridge.py`를 같은 `tools/` 폴더에 함께 넣습니다.

### 요청 대기열

Java 입력은 전용 스레드에서 읽고, Codex 요청은 단일 처리 루프에서 실행합니다. 직접 대화와 자동 혼잣말 모두 `chat` 요청으로 보내면 도착 순서대로 처리됩니다. 요청마다 서로 다른 `id`를 사용하고, Java에서도 JSON 한 줄 전체를 한 번에 기록하도록 쓰기를 동기화해야 합니다.

- 대기열은 기본 64개입니다. 처리 중인 요청과 입력 스레드에서 읽은 한 줄은 별도입니다.
- 대기열이 가득 차면 입력을 잠시 기다리며, 수락한 요청을 버리지 않습니다.
- 응답은 요청 순서와 원래 ID를 유지합니다. 직접 대화 우선 처리나 혼잣말 합치기는 아직 없습니다.
- `shutdown` 이전에 받은 요청을 처리한 뒤 종료합니다. 생성 중 즉시 취소는 하지 않습니다.
- stdin이 닫혀도 이미 받은 요청을 모두 처리한 뒤 종료합니다.
- 이 순차 처리 보장은 `--stdio` 경로에 적용됩니다. `CodexClient` 메서드를 여러 스레드에서 직접 동시에 호출하면 안 됩니다.

## Java 모델 설정창

플러그인 설치 후 트레이의 **Codex 모델 설정** 또는 설정 → 모드의 해당 설정 버튼으로 엽니다.

- 모델과 추론 강도를 선택하고 **저장**을 누릅니다.
- 모델별 지원 추론 강도만 표시합니다. 모델을 바꾸면 추론 강도는 기본값으로 돌아갑니다.
- **Codex 기본값 사용**을 선택하면 빈 문자열로 저장합니다.
- 저장은 `PluginContext.prefs()`의 `model`, `effort` 키를 사용합니다. 본체 공용 설정이나 Codex 전역 설정은 수정하지 않습니다.
- 설정창을 열 때 Python 브릿지의 `model/list`로 현재 모델 목록을 조회합니다. **목록 새로고침**으로 다시 조회할 수 있습니다. 조회 실패 시 기존 저장값을 유지하고 재시도할 수 있습니다.
- 목록에 없는 저장 모델은 다른 모델을 선택하기 전까지 저장 버튼을 비활성화합니다.
- 대화창을 새로 열면 저장한 모델·추론 강도를 사용합니다. 로컬 설치는 아래 설치 스크립트를 사용합니다.

플러그인 및 Python 브릿지 빌드:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-plugin.ps1
```

주요 파일:

- `CodexPlugin.java`: 메뉴 등록과 창 수명 관리
- `SettingsWindow.java`: 선택창과 모드 전용 설정 저장
- `plugin.json`: 플러그인 등록 정보
- `ModelCatalog.java`: Python을 실행해 모델 목록을 비동기로 조회
- 빌드 결과: `dist/lumi-codex/plugins/lumi-codex.jar`

설정창은 모델 답변을 생성하지 않습니다. 모델 목록 조회를 위해 Codex App Server에 연결하므로 `network`는 true입니다.

## 로컬 꼬미에 설치

꼬미를 완전히 종료한 뒤 레포 루트에서 실행하세요.

```powershell
# 빌드하고 설치
powershell -ExecutionPolicy Bypass -File scripts/install-plugin.ps1 -Build

# 기존 빌드 결과만 설치
powershell -ExecutionPolicy Bypass -File scripts/install-plugin.ps1

# 다른 Steam 라이브러리에 설치된 경우
powershell -ExecutionPolicy Bypass -File scripts/install-plugin.ps1 -Build -LumiHome "D:\SteamLibrary\steamapps\common\Little LUMI"

# 파일을 변경하지 않고 설치 대상 확인
powershell -ExecutionPolicy Bypass -File scripts/install-plugin.ps1 -WhatIf
```

`dist/lumi-codex/plugins/lumi-codex.jar`를 `<꼬미 설치 폴더>/mods/lumi-codex/plugins/lumi-codex.jar`에 복사합니다. 기존 JAR은 레포의 `build/install-backup/lumi-codex.jar.bak`에 최근 한 개를 보관합니다. 복사 후 SHA-256이 일치하는지 확인합니다.

설치 후 꼬미를 다시 실행하고 설정 → 모드에서 **Lumi Codex**를 켜세요. 스크립트는 꼬미를 강제 종료하거나 자동 실행하지 않으며, 기존 모드의 활성화 상태와 사용자 설정을 변경하지 않습니다. 실행 중인 꼬미 프로세스를 찾거나 JAR이 잠겨 있으면 설치를 중단합니다. 설치 폴더 쓰기 권한이 없으면 관리자 권한 터미널에서 실행하세요.



### 동적 모델 목록 실행 환경

빌드 및 설치 스크립트는 JAR 외에 `tools/main.py`, `codex_client.py`, `stdio_bridge.py`, `runtime.json`도 복사합니다. `runtime.json`은 모델 목록이 아니라 로컬 개발용 Python 실행 경로만 담습니다. 배포할 때는 이 머신 전용 파일을 제외할 수 있습니다.

Python은 `LUMI_CODEX_PYTHON` 환경변수 → 유효한 `tools/runtime.json` 경로 → PATH의 `python` 순으로 선택합니다. 기존 Codex 로그인이 필요합니다. 조회용 Python은 목록 반환 후 종료하며, 창을 닫거나 조회 제한 시간(30초)을 넘으면 해당 조회 프로세스를 정리합니다. 네트워크 조회는 Swing 백그라운드 작업으로 실행합니다.

캐릭터 우클릭 → **대화하기**에서 텍스트를 입력할 수 있습니다. 전송하면 입력창은 숨고 답변은 해당 꼬미의 말풍선에 표시합니다.


## 꼬미와 텍스트 대화

- 캐릭터 우클릭 → **대화하기**를 선택하면 해당 캐릭터 근처에 작은 입력창이 나타납니다.
- 제목 표시줄 없는 둥근 창이며, 원본 LUMI Chat처럼 입력칸(260×34), 보내기, 설정 버튼을 제공합니다.
- Enter 또는 보내기로 전송하면 입력창을 숨기고, 답변은 꼬미 말풍선에 15초 동안 표시합니다. 별도 대화 기록 창은 없습니다.
- Esc 또는 다른 곳 클릭으로 입력창을 숨길 수 있습니다. 숨겨도 대화 세션은 유지됩니다.
- 첫 메시지에서 Python 브릿지를 시작하며, 캐릭터 인스턴스마다 대화를 분리합니다.
- 생성 중에는 전송을 잠시 막습니다. UI는 백그라운드 작업으로 계속 반응합니다.
- 저장된 모델·추론 강도가 바뀌면 다음 전송에서 새 세션을 시작합니다.
- 플러그인 종료 시 입력창과 Python/Codex 프로세스를 정리합니다. 재시작 후 대화 복원은 아직 없습니다.
- 실패 시 말풍선에 안내하고, 다음 전송에서 새 연결을 만듭니다. 실패한 메시지를 자동 재전송하지 않습니다.
- 현재는 일반 텍스트 대화이며, 캐릭터 페르소나를 전달합니다. 화면 인식·음성 합성은 아직 없습니다.



## 캐릭터별 페르소나

트레이 아이콘 우클릭 → **페르소나 설정**에서 성격과 말투를 편집하고 저장합니다.

- 저장 위치: 꼬미의 `app/plugindata/lumi.codex/personas.json` (`PluginContext.dataDir()` 기준).
- JSON 형식은 `{"version":1,"characters":{"Lumi":"페르소나 내용"}}`입니다. 캐릭터 이미지 세트 이름을 키로 사용하므로 같은 종류의 꼬미들은 페르소나를 공유합니다.
- 저장값이 없으면 `context.persona(imageSet)`의 캐릭터 기본값을 사용합니다.
- 빈 내용 저장은 사용자 페르소나 없이 일반 대화를 사용한다는 뜻입니다. **캐릭터 기본값 사용**은 저장된 덮어쓰기를 제거합니다.
- 파일은 원자적 쓰기로 저장하고, 손상된 파일은 자동 덮어쓰지 않습니다. 페르소나는 최대 20,000자입니다.
- Java가 매 전송마다 유효 페르소나를 읽어 Python `chat.params.persona`로 전달합니다. Python은 대화 생성 시 `developerInstructions`에 반영합니다.
- 동일한 내용이면 같은 대화를 유지하고, 내용이 변경되면 다음 메시지에서 새 대화를 생성합니다. 진행 중인 답변은 기존 페르소나로 완료됩니다.
- Python은 페르소나 파일을 저장하지 않으며, 모드가 보낸 값만 사용합니다. `persona` 생략은 현재 세션의 값을 유지하고 빈 문자열은 초기화합니다.
- 페르소나가 있으면 캐릭터 말투를 따릅니다. 페르소나가 없을 때만 기본 존댓말 지시를 사용합니다.

기본 페르소나는 캐릭터 내장 설정 → 내장 Lumi 설정 → 모드의 루미 기본 설정 순으로 선택합니다. 루미 기본값은 밝고 다정한 존댓말과 짧은 말풍선 대화를 사용합니다. 이미 저장한 사용자 설정(빈 문자열 포함)은 그대로 우선합니다. 기본값으로 돌아가려면 **캐릭터 기본값 사용**을 누르세요.

페르소나 설정은 트레이의 **Codex 모델 설정** 바로 아래에 표시됩니다. 여러 캐릭터가 활성화된 경우 대상을 선택한 뒤 열립니다.
