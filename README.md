# lumi-codex-bridge

Little LUMI(꼬미)를 Codex와 연결하는 비공식 모드입니다.

## 기능

- 꼬미와 대화하기, 답변 생성 취소
- 모델·추론 강도 선택과 캐릭터별 페르소나 설정
- 화면 같이 보기와 자동 화면 보기 간격 설정
- 루미 로컬 TTS와 음량·ON/OFF 설정

## 준비

Windows 11, Little LUMI, 로그인된 Codex가 필요합니다.
빌드에는 JDK 25(`.tools/jdk-25`), 실행에는 Python 3.12 가상환경(`.venv/bin/python.exe`)을 사용합니다.

## 설치

꼬미를 종료한 뒤 레포 루트에서 실행하세요.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-plugin.ps1 -Build
```

다른 폴더에 꼬미가 설치되어 있다면 `-LumiHome "설치 경로"`를 추가하세요.
설치 후 꼬미를 다시 실행하고 **설정 → 모드 → Lumi Codex**를 켜세요.

## 사용

- **캐릭터 우클릭:** 대화하기, 생성 취소, 화면 같이 보기
- **트레이 아이콘 우클릭:** 모델·자동 화면 보기 설정, 페르소나 설정, TTS 설정
- **TTS:** 한국어 루미 보이스팩을 설치한 뒤, TTS 설정에서 로컬 TTS를 설치하고 켜세요. 필요한 파일은 설치 버튼을 눌렀을 때만 다운로드합니다.

대화에는 계정의 Codex 사용량이 적용됩니다. 화면 같이 보기는 캡처한 화면을 OpenAI 서버로 전송합니다.
현재 대화 내용은 앱 재시작 후 복원되지 않습니다.
