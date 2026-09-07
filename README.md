# lumi-codex-bridge

Little LUMI(꼬미)를 Codex와 연결하는 비공식 모드입니다.

## 기능

- 꼬미와 대화하기, 답변 생성 취소
- 모델·추론 강도 선택과 캐릭터별 페르소나 설정
- 화면을 참고한 자연스러운 대화와 자동 화면 보기 간격 설정
- 문장별로 읽는 루미 로컬 TTS, 미리듣기·음량·ON/OFF 설정

## 설치

Windows 11, Little LUMI와 로그인된 Codex가 필요합니다. Python이 없으면 설치 스크립트가 모드 전용으로 자동 설치합니다.
꼬미를 종료한 뒤 아래 명령을 실행하세요. **배포 릴리스가 게시된 후 사용할 수 있습니다.**

```powershell
Invoke-WebRequest "https://github.com/kss418/lumi-codex-bridge/releases/latest/download/install-lumi-codex.ps1" -OutFile install-lumi-codex.ps1
powershell -ExecutionPolicy Bypass -File .\install-lumi-codex.ps1
```

설치 후 꼬미를 다시 실행하고 **설정 → 모드 → Lumi Codex**를 켜세요. 같은 명령으로 업데이트할 수 있습니다.
설치 경로를 찾지 못하면 `-LumiHome "꼬미 설치 경로"`, 특정 Python을 사용하려면 `-PythonPath "python.exe 경로"`를 추가하세요. 시스템 PATH는 변경하지 않습니다.

## 사용

- **캐릭터 우클릭:** 대화하기, 생성 취소, 화면 같이 보기
- **트레이 아이콘 우클릭:** 모델·자동 화면 보기 설정, 페르소나 설정, TTS 설정
- **TTS:** 한국어 루미 보이스팩을 설치한 뒤 TTS 설정에서 로컬 TTS를 설치하고 켜세요. 의존성은 설치 버튼을 눌렀을 때만 다운로드합니다.

대화에는 계정의 Codex 사용량이 적용됩니다. 화면 같이 보기는 캡처한 화면을 OpenAI 서버로 전송합니다.
현재 대화 내용은 앱 재시작 후 복원되지 않습니다.

## 직접 빌드·배포

개발용 JDK 25(`.tools/jdk-25`)와 Python 가상환경(`.venv/bin/python.exe`)을 준비한 뒤 실행합니다.

```powershell
# 내 PC의 꼬미에 빌드·설치
powershell -ExecutionPolicy Bypass -File scripts/install-plugin.ps1 -Build

# GitHub Release에 올릴 배포 파일 생성
powershell -ExecutionPolicy Bypass -File scripts/package-release.ps1
```

`dist/releases/<버전>`에 생성된 ZIP, 설치 스크립트, SHA256SUMS.txt를 같은 GitHub Release에 올리세요. 스크립트는 자동 게시하지 않습니다.
