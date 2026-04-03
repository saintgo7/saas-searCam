# SearCam Book - 이미지 자산

## 디렉토리 구조

```
assets/images/
├── screenshots/     # 개발 중 캡처된 스크린샷 (자동)
│   ├── ch01/        # 챕터별 분류
│   ├── ch02/
│   └── ...
├── diagrams/        # 아키텍처/플로우 다이어그램 (수동)
└── ui-mockups/      # UI 목업/와이어프레임 (수동)
```

## 파일 네이밍 규칙

```
screenshots/chXX/YYYY-MM-DD_HH-MM-SS_설명.png
diagrams/chXX_설명.png
ui-mockups/chXX_화면명.png
```

## 챕터 내 삽입 방법

```markdown
![캡션 텍스트](../assets/images/screenshots/ch11/2026-04-04_wifi-scan-result.png)
```

## 자동 캡처 방법

```bash
# 현재 화면 전체 캡처
./capture.sh ch11 "wifi-scan-result"

# Android 에뮬레이터 캡처
./capture.sh ch11 "emulator-scan" --emulator

# 특정 영역 캡처 (인터랙티브)
./capture.sh ch11 "scan-button" --region
```
