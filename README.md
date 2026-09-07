# lumi-codex-bridge

Little LUMI(꼬미)를 Codex와 연결하는 비공식 모드입니다.

## 기능

- 꼬미와 대화하기, 답변 생성 취소
- 모델·추론 강도 선택과 캐릭터별 페르소나 설정
- 화면을 참고한 자연스러운 대화와 자동 화면 보기 간격 설정
- 문장별로 읽는 루미 로컬 TTS, 미리듣기·음량·ON/OFF 설정

## 설치

Windows 11, Little LUMI와 로그인된 Codex가 필요합니다.

꼬미를 종료한 뒤 CMD에 아래 명령 한 줄을 복사해 **CMD 창에 붙여넣고 Enter**를 누르면 다운로드와 설치가 진행됩니다.

```cmd
powershell -NoProfile -ExecutionPolicy Bypass -Command "$installer = Join-Path $env:TEMP 'install-lumi-codex.ps1'; Invoke-WebRequest -Uri 'https://github.com/kss418/lumi-codex-bridge/releases/latest/download/install-lumi-codex.ps1' -OutFile $installer -UseBasicParsing -ErrorAction Stop; & $installer"
```

설치 후 꼬미를 다시 실행하고 **설정 → 모드 → Lumi Codex**를 켜세요. 같은 명령으로 업데이트할 수 있습니다.

설치 경로를 찾지 못하면 명령 끝의 `& $installer` 뒤, 마지막 큰따옴표 안에 `-LumiHome '꼬미 설치 경로'`를 추가하세요.

## 사용

- **캐릭터 우클릭:** 대화하기, 생성 취소, 화면 같이 보기
- **트레이 아이콘 우클릭 → Lumi Codex 설정:** 모델·자동 화면 보기, 페르소나, TTS를 탭으로 설정
- **TTS:** 한국어 루미 보이스팩을 설치한 뒤 TTS 설정에서 로컬 TTS를 설치하고 켜세요. 의존성은 설치 버튼을 눌렀을 때만 다운로드합니다.

대화에는 계정의 Codex 사용량이 적용됩니다. 화면 같이 보기는 캡처한 화면을 OpenAI 서버로 전송합니다.
현재 대화 내용은 앱 재시작 후 복원되지 않습니다.
