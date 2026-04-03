# 27. OUI 데이터베이스 관리 계획서

> 버전: v1.0
> 작성일: 2026-04-03
> 관련 문서: project-plan.md (v3.1)

---

## 1. OUI(Organizationally Unique Identifier) 개요

### 1.1 IEEE OUI 데이터베이스란

OUI는 IEEE(Institute of Electrical and Electronics Engineers)가 네트워크 장비 제조사에 부여하는 고유 식별자이다. 모든 네트워크 인터페이스(Wi-Fi, 이더넷 등)는 고유한 MAC 주소를 가지며, 이 MAC 주소의 앞 3바이트(24비트)가 제조사를 식별하는 OUI에 해당한다.

```
MAC 주소 구조 (48비트 = 6바이트):

  28:57:BE:A1:23:4F
  ├──────┤ ├──────┤
   OUI     기기 고유
  (3바이트) (3바이트)

  28:57:BE = Hikvision Digital Technology
  → 이 MAC을 가진 기기는 Hikvision이 제조한 네트워크 장비
```

IEEE는 공개적으로 OUI 할당 목록을 유지관리하며, 누구나 조회할 수 있다. SearCam은 이 공개 데이터를 기반으로, Wi-Fi 네트워크에 연결된 기기의 MAC 주소에서 OUI를 추출하여 카메라 제조사 여부를 판별한다.

### 1.2 SearCam에서의 활용

```
Wi-Fi 스캔 파이프라인에서의 OUI 역할:

  ARP 테이블 → IP + MAC 목록
       │
       ▼
  MAC 앞 3바이트 추출 (OUI)
       │
       ▼
  OUI DB 조회
       │
       ├── 카메라 제조사 매칭 → risk_weight 부여 (+40점)
       ├── 안전 기기 매칭 → 낮은 risk (포트 스캔 스킵)
       └── 미확인 제조사 → 중간 risk (포트 스캔으로 추가 판별)
```

### 1.3 OUI 기반 탐지의 한계

| 한계 | 설명 | 대응 |
|------|------|------|
| MAC 랜덤화 | Android 10+, iOS 14+에서 Wi-Fi 접속 시 랜덤 MAC 사용 | ARP에서 읽은 MAC은 실제 MAC → 영향 제한적 |
| OUI 미등록 제조사 | 소규모 중국 제조사는 OUI 미등록 | 포트 스캔으로 보완 |
| 공유 OUI | 한 OUI를 여러 제품군에 사용 | 포트+mDNS 교차 확인 |
| MAC 스푸핑 | 의도적 MAC 변경 | 포트 스캔 + 호스트명 확인으로 보완 |

---

## 2. 카메라 제조사 OUI 목록

### 2.1 IP 카메라 제조사 (50개+)

전문 보안/감시 카메라 제조사 목록이다. `risk_weight`는 해당 OUI가 카메라일 확률을 0~1 범위로 나타낸다.

| # | OUI Prefix | 제조사 | 제품군 | risk_weight | 비고 |
|---|-----------|--------|--------|-------------|------|
| 1 | 28:57:BE | Hikvision | IP카메라, NVR, DVR | 0.95 | 전 세계 1위 CCTV 점유율 |
| 2 | C0:56:E3 | Hikvision | 추가 OUI | 0.95 | |
| 3 | 44:19:B6 | Hikvision | 추가 OUI | 0.95 | |
| 4 | 54:C4:15 | Hikvision | 추가 OUI | 0.95 | |
| 5 | 3C:EF:8C | Dahua Technology | IP카메라, PTZ, NVR | 0.95 | 전 세계 2위 점유율 |
| 6 | A0:BD:1D | Dahua Technology | 추가 OUI | 0.95 | |
| 7 | E0:50:8B | Dahua Technology | 추가 OUI | 0.95 | |
| 8 | 00:09:18 | Hanwha Techwin (Samsung) | IP카메라, PTZ | 0.90 | 한국 대표 CCTV |
| 9 | 00:09:19 | Hanwha Techwin | 추가 OUI | 0.90 | |
| 10 | 00:80:F0 | Panasonic | 보안카메라, PTZ | 0.85 | 일본 대표 |
| 11 | 00:40:84 | Honeywell | 보안카메라, 출입통제 | 0.80 | |
| 12 | 00:04:A3 | Axis Communications | IP카메라, 엔코더 | 0.95 | 네트워크 카메라 원조 |
| 13 | AC:CC:8E | Axis Communications | 추가 OUI | 0.95 | |
| 14 | B8:A4:4F | Axis Communications | 추가 OUI | 0.95 | |
| 15 | 00:0F:7C | ACTi | IP카메라 | 0.90 | |
| 16 | 00:18:85 | Avigilon | HD카메라, 분석 | 0.90 | Motorola 계열 |
| 17 | 00:1A:07 | Arecont Vision | 메가픽셀 카메라 | 0.90 | |
| 18 | 00:12:18 | Arecont Vision | 추가 OUI | 0.90 | |
| 19 | 00:30:53 | Vivotek | IP카메라, NVR | 0.90 | |
| 20 | 00:02:D1 | Vivotek | 추가 OUI | 0.90 | |
| 21 | EC:71:DB | Reolink | IP카메라, PoE | 0.90 | 가성비 카메라 |
| 22 | 9C:8E:CD | Reolink | 추가 OUI | 0.90 | |
| 23 | 00:62:6E | Uniview | IP카메라, NVR | 0.90 | 중국 3위 |
| 24 | 24:28:FD | Uniview | 추가 OUI | 0.90 | |
| 25 | 00:01:F4 | Enterasys (Extreme) | 네트워크 카메라 | 0.70 | 복합 벤더 |
| 26 | 00:E0:8F | Cisco (Meraki) | 보안카메라 | 0.60 | 네트워크 장비도 생산 |
| 27 | 00:1B:90 | GeoVision | IP카메라, DVR | 0.90 | |
| 28 | 00:13:E2 | GeoVision | 추가 OUI | 0.90 | |
| 29 | B0:C5:54 | D-Link | IP카메라 | 0.70 | 공유기도 생산 |
| 30 | 28:10:7B | D-Link | 추가 OUI | 0.70 | |
| 31 | 00:1E:58 | D-Link | 추가 OUI | 0.70 | |
| 32 | 7C:DD:90 | IDIS | DVR, NVR, IP카메라 | 0.90 | 한국 CCTV |
| 33 | 00:11:C6 | IDIS (Webgate) | 추가 OUI | 0.90 | |
| 34 | F4:B5:AA | Pelco (Schneider) | PTZ, 고정카메라 | 0.90 | |
| 35 | 00:20:4A | Pelco | 추가 OUI | 0.90 | |
| 36 | 00:0C:68 | Bosch Security | IP카메라, DVR | 0.85 | |
| 37 | 00:07:5F | Bosch Security | 추가 OUI | 0.85 | |
| 38 | 00:1C:B0 | Cisco/Tandberg | 화상회의 카메라 | 0.65 | |
| 39 | 00:0E:56 | 4Sight Imaging | 산업용 카메라 | 0.80 | |
| 40 | 00:18:17 | Apace Technology | IP카메라 | 0.85 | |
| 41 | 00:1A:93 | ERECA | IP카메라 | 0.85 | |
| 42 | 38:D5:47 | Motorola/Avigilon | 보안카메라 | 0.85 | |
| 43 | 00:0B:82 | Grandstream | IP카메라, 인터폰 | 0.75 | VoIP 기기도 생산 |
| 44 | C0:74:AD | Grandstream | 추가 OUI | 0.75 | |
| 45 | 00:04:F2 | Polycom (HP Poly) | 화상회의 카메라 | 0.60 | 회의실 카메라 |
| 46 | 64:16:7F | Polycom | 추가 OUI | 0.60 | |
| 47 | 00:30:91 | CNSystems | IP카메라 | 0.85 | |
| 48 | 00:0D:5A | Tiesse | IP카메라, 라우터 | 0.70 | |
| 49 | 74:DA:38 | Edimax | IP카메라 | 0.70 | 네트워크 장비도 생산 |
| 50 | 00:40:8C | Milestone/Mobotix | IP카메라 | 0.90 | |
| 51 | 00:0B:2B | HOSTNET | IP카메라 보드 | 0.85 | |
| 52 | D4:20:B0 | Meritech (한화 계열) | IP카메라 | 0.85 | 한국 제조사 |

### 2.2 스마트 카메라 / IoT 카메라 제조사 (30개+)

소비자용 스마트홈 카메라. 숙소에서 발견 시 의심 대상이 될 수 있다.

| # | OUI Prefix | 제조사 | 제품군 | risk_weight | 비고 |
|---|-----------|--------|--------|-------------|------|
| 1 | 2C:AA:8E | Wyze Labs | Wyze Cam 시리즈 | 0.80 | 미국 저가 카메라 |
| 2 | 7C:78:B2 | Wyze Labs | 추가 OUI | 0.80 | |
| 3 | 18:B4:30 | Nest (Google) | Nest Cam Indoor/Outdoor | 0.80 | Google Home 연동 |
| 4 | 64:16:66 | Nest (Google) | 추가 OUI | 0.80 | |
| 5 | 98:DA:C4 | TP-Link | Tapo C200/C310 등 | 0.75 | 공유기와 동일 OUI 주의 |
| 6 | 5C:A6:E6 | TP-Link (Kasa) | Kasa Spot/KC카메라 | 0.75 | |
| 7 | 50:C7:BF | TP-Link | 추가 OUI | 0.75 | 공유기일 수도 있음 |
| 8 | FC:65:DE | Amazon (Ring) | Ring Indoor/Outdoor Cam | 0.80 | Alexa 연동 |
| 9 | 34:D2:70 | Amazon (Blink) | Blink Mini/Outdoor | 0.80 | 배터리 카메라 |
| 10 | 68:37:E9 | Amazon (Ring) | Ring Doorbell 시리즈 | 0.75 | 도어벨 |
| 11 | CC:50:E3 | Arlo Technologies | Arlo Pro/Ultra | 0.85 | 무선 카메라 전문 |
| 12 | 20:47:DA | Arlo | 추가 OUI | 0.85 | |
| 13 | 44:61:32 | ecobee | ecobee SmartCamera | 0.75 | 스마트홈 |
| 14 | 78:11:DC | Xiaomi | Mi Home 카메라 시리즈 | 0.75 | 스마트홈 기기도 포함 |
| 15 | 64:CC:2E | Xiaomi | 추가 OUI | 0.75 | |
| 16 | F8:4D:89 | Tuya Smart | 다수 화이트레이블 카메라 | 0.80 | 중국 IoT 플랫폼 |
| 17 | D8:1F:12 | Tuya Smart | 추가 OUI | 0.80 | |
| 18 | 28:6D:97 | Imou (Dahua 소비자) | Imou Cue/Ranger | 0.85 | Dahua 소비자 브랜드 |
| 19 | 40:AE:30 | Ezviz (Hikvision 소비자) | C6N/C3W 등 | 0.85 | Hikvision 소비자 브랜드 |
| 20 | 50:01:D9 | Ezviz | 추가 OUI | 0.85 | |
| 21 | 00:26:73 | Logitech | Circle View/C930 | 0.60 | 웹캠도 생산 |
| 22 | 04:69:F8 | Logitech | 추가 OUI | 0.60 | |
| 23 | 38:01:46 | YI Technology | YI Home Camera | 0.80 | 샤오미 계열 |
| 24 | 90:8D:78 | D-Link (mydlink) | DCS 시리즈 | 0.75 | |
| 25 | B4:A2:EB | Samsung SmartThings | SmartCam | 0.70 | |
| 26 | A4:34:D9 | Eufy (Anker) | eufyCam/Indoor Cam | 0.80 | 로컬 저장 강조 |
| 27 | 7C:C2:C6 | Eufy | 추가 OUI | 0.80 | |
| 28 | E8:AB:F3 | Wansview | Wi-Fi 카메라 | 0.80 | 아마존 인기 |
| 29 | CC:32:E5 | EBITCAM | Wi-Fi 카메라 | 0.80 | |
| 30 | D4:35:1D | YooSee | P2P 카메라 | 0.85 | 중국산 P2P 카메라 |
| 31 | 00:26:87 | VTech | 베이비 모니터 카메라 | 0.70 | |
| 32 | 3C:84:6A | TP-Link (Tapo) | Tapo C시리즈 신형 | 0.75 | |

### 2.3 초소형/위장 카메라 제조사 (위험도 최고)

합법적 OUI를 등록하지 않거나, 범용 Wi-Fi 모듈 OUI를 사용하는 경우가 많다.

| # | OUI Prefix | 제조사/모듈 | 설명 | risk_weight | 비고 |
|---|-----------|-----------|------|-------------|------|
| 1 | 18:FE:34 | Espressif (ESP8266) | 중국산 초소형 카메라 다수 사용 | 0.50 | IoT 모듈, 비카메라 기기도 사용 |
| 2 | 24:0A:C4 | Espressif (ESP32) | ESP32 기반 카메라 모듈 | 0.50 | ESP32-CAM 보드 |
| 3 | A4:CF:12 | Espressif | 추가 OUI | 0.50 | |
| 4 | 48:3F:DA | Espressif | 추가 OUI | 0.50 | |
| 5 | 7C:9E:BD | Espressif | 추가 OUI | 0.50 | |
| 6 | 10:52:1C | Espressif | 추가 OUI | 0.50 | |
| 7 | C4:4F:33 | Shenzhen C-Data | 저가 Wi-Fi 카메라 | 0.60 | |
| 8 | B0:A7:32 | Realtek | RTL8xxx 기반 카메라 | 0.40 | Wi-Fi 칩셋, 다용도 |

> **참고**: Espressif(ESP32/ESP8266) OUI는 IoT 기기 전반에서 사용되므로 OUI 단독으로 카메라 판정하지 않는다. 포트 스캔(RTSP, HTTP)과 교차 확인이 필수이다.

---

## 3. 안전 기기 화이트리스트

### 3.1 일반 소비자 기기 제조사

숙소/호텔에서 정상적으로 발견되는 기기들이다. 이 기기들은 포트 스캔을 스킵하여 스캔 시간을 단축한다.

| # | OUI Prefix (대표) | 제조사 | 기기 유형 | risk_weight |
|---|-------------------|--------|----------|-------------|
| 1 | 00:1A:2B, 3C:22:FB | Apple | iPhone, iPad, MacBook, ATV | 0.05 |
| 2 | 00:15:5D, 8C:F8:C5 | Samsung | Galaxy, TV, 냉장고 | 0.05 |
| 3 | 00:1E:75, 10:68:3F | LG | 스마트폰, TV, 가전 | 0.05 |
| 4 | 00:09:2D, 28:6C:07 | HTC | 스마트폰 | 0.05 |
| 5 | A0:8C:FD, D0:C5:D3 | Xiaomi (비카메라) | 스마트폰, 공기청정기 | 0.10 |
| 6 | FC:A1:83, B4:EE:B4 | Huawei | 스마트폰, 태블릿 | 0.05 |
| 7 | 00:BB:3A, 94:65:2D | Google | Pixel, Chromecast | 0.05 |
| 8 | 00:50:F2, 00:17:FA | Microsoft | Surface, Xbox | 0.05 |
| 9 | 74:40:BB, 00:E0:4C | Sony | PlayStation, TV | 0.05 |
| 10 | A8:66:7F, 00:25:D3 | Intel | 노트북 Wi-Fi | 0.05 |

### 3.2 네트워크 장비 제조사

라우터, 공유기, 스위치 등 네트워크 인프라 장비이다.

| # | OUI Prefix (대표) | 제조사 | 기기 유형 | risk_weight |
|---|-------------------|--------|----------|-------------|
| 1 | 50:C7:BF, 98:DA:C4 | TP-Link | 공유기, 메시 | 0.10 |
| 2 | 28:80:88, 4C:ED:FB | Netgear | 공유기, 스위치 | 0.05 |
| 3 | 1C:87:2C, 04:D9:F5 | ASUS | 공유기, RT 시리즈 | 0.05 |
| 4 | E0:63:DA | Huawei (네트워크) | 라우터 | 0.05 |
| 5 | 00:26:F2, 00:17:9A | Cisco (소비자) | Linksys 공유기 | 0.05 |
| 6 | 88:36:6C | EFM Networks | ipTIME 공유기 | 0.05 |
| 7 | 00:08:9F | EFM Networks | ipTIME 추가 OUI | 0.05 |
| 8 | 00:27:1C, B8:76:3F | Ubiquiti | UniFi AP, 라우터 | 0.05 |

> **주의**: TP-Link는 카메라(Tapo)와 공유기를 동일 OUI로 사용할 수 있다. TP-Link OUI 발견 시 포트 스캔으로 RTSP 확인이 필수이다.

### 3.3 IoT 기기 (비카메라)

스마트 스피커, 스마트 TV, 기타 IoT 기기이다.

| # | OUI Prefix (대표) | 제조사 | 기기 유형 | risk_weight |
|---|-------------------|--------|----------|-------------|
| 1 | F0:D2:F1 | Amazon | Echo, Fire TV | 0.10 |
| 2 | 88:71:B1 | Amazon (Alexa) | Echo Dot 등 | 0.10 |
| 3 | 30:FD:38, 20:DF:B9 | Google (Home) | Nest Mini, Hub | 0.10 |
| 4 | 58:FD:B1 | Naver | Clova 스피커 | 0.05 |
| 5 | B4:F1:DA | LG (ThinQ) | 스마트 가전 | 0.05 |
| 6 | 00:04:4B | Roku | 스트리밍 기기 | 0.05 |
| 7 | 68:54:FD, 00:11:75 | Samsung (SmartThings) | 스마트홈 허브 | 0.05 |
| 8 | 3C:BD:D8 | LG (webOS) | 스마트 TV | 0.05 |
| 9 | F0:B4:29, 00:1D:C9 | Samsung (TV) | 스마트 TV | 0.05 |

> **참고**: Amazon Echo Show, Google Nest Hub 등 디스플레이가 있는 스마트 스피커는 카메라를 내장하고 있다. risk_weight 0.10으로 설정하되, 호텔/에어비앤비에서 정상적으로 제공하는 기기일 수 있으므로 "확인 필요" 수준으로 안내한다.

---

## 4. JSON 스키마 설계

### 4.1 전체 구조

```json
{
  "version": "1.0.0",
  "updated_at": "2026-04-03T00:00:00Z",
  "checksum": "sha256:abcdef1234567890...",
  "camera_vendors": [...],
  "safe_vendors": [...],
  "suspicious_modules": [...],
  "metadata": {
    "total_camera_vendors": 82,
    "total_safe_vendors": 45,
    "total_suspicious_modules": 8,
    "source": "IEEE OUI + manual curation",
    "next_update": "2026-05-01T00:00:00Z"
  }
}
```

### 4.2 camera_vendors 구조

```json
{
  "camera_vendors": [
    {
      "oui": "28:57:BE",
      "vendor": "Hikvision Digital Technology",
      "vendor_aliases": ["HIKVISION", "Hangzhou Hikvision"],
      "type": "ip_camera",
      "category": "professional_surveillance",
      "products": ["DS-2CD series", "IPC series", "NVR"],
      "risk_weight": 0.95,
      "common_ports": [554, 80, 8080, 3702],
      "common_hostnames": ["HIKVISION", "DS-", "IPC-"],
      "notes": "전 세계 1위 CCTV 점유율. RTSP 554 포트 활성이면 확실도 높음",
      "added_at": "2026-04-03",
      "source": "IEEE OUI + manual"
    }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| oui | string | Y | MAC OUI prefix ("XX:XX:XX") |
| vendor | string | Y | 제조사 정식 명칭 |
| vendor_aliases | string[] | N | 제조사 별칭 (검색용) |
| type | enum | Y | ip_camera, smart_camera, ptz_camera, dvr_nvr, conferencing |
| category | enum | Y | professional_surveillance, consumer_smart, industrial, conferencing |
| products | string[] | N | 대표 제품군 |
| risk_weight | float | Y | 0.0~1.0 (카메라일 확률) |
| common_ports | int[] | N | 자주 사용하는 포트 |
| common_hostnames | string[] | N | mDNS/호스트명 패턴 |
| notes | string | N | 참고 사항 |
| added_at | string | Y | 추가 날짜 (ISO 8601) |
| source | string | Y | 데이터 출처 |

### 4.3 safe_vendors 구조

```json
{
  "safe_vendors": [
    {
      "oui": "00:1A:2B",
      "vendor": "Apple, Inc.",
      "type": "consumer",
      "category": "smartphone",
      "risk_weight": 0.05,
      "skip_port_scan": true,
      "notes": "iPhone, iPad, MacBook 등 일반 소비자 기기",
      "added_at": "2026-04-03"
    }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| oui | string | Y | MAC OUI prefix |
| vendor | string | Y | 제조사 명칭 |
| type | enum | Y | consumer, networking, iot, enterprise |
| category | string | Y | smartphone, router, smart_speaker, tv, laptop |
| risk_weight | float | Y | 0.0~0.15 |
| skip_port_scan | boolean | Y | true이면 포트 스캔 스킵 |
| notes | string | N | 참고 사항 |
| added_at | string | Y | 추가 날짜 |

### 4.4 suspicious_modules 구조

범용 Wi-Fi 모듈이지만, 초소형 카메라에 자주 사용되는 모듈이다.

```json
{
  "suspicious_modules": [
    {
      "oui": "18:FE:34",
      "vendor": "Espressif Inc.",
      "module": "ESP8266",
      "risk_weight": 0.50,
      "reason": "중국산 초소형 카메라에 자주 사용되는 Wi-Fi 모듈",
      "requires_port_scan": true,
      "camera_indicators": {
        "ports": [554, 80, 8080, 81],
        "hostnames": ["ESP", "CAM", "IPCAM"],
        "mdns_services": ["_rtsp._tcp", "_http._tcp"]
      }
    }
  ]
}
```

### 4.5 버전 관리 필드

```json
{
  "version": "1.0.0",
  "updated_at": "2026-04-03T00:00:00Z",
  "checksum": "sha256:abcdef1234567890...",
  "min_app_version": "1.0.0",
  "changelog": [
    {
      "version": "1.0.0",
      "date": "2026-04-03",
      "changes": ["초기 릴리스: 카메라 82사, 안전 45사, 모듈 8개"]
    }
  ]
}
```

버전 규칙:
- Major (x.0.0): 스키마 구조 변경 (하위 호환 불가)
- Minor (0.x.0): 새 제조사 10개 이상 추가
- Patch (0.0.x): 제조사 수정, risk_weight 조정, 소수 추가

---

## 5. 업데이트 전략

### 5.1 Phase 1: 앱 번들 내장

```
초기 배포:
  ├── assets/oui_database.json (약 500KB)
  ├── 앱 설치 시 자동 포함
  ├── 앱 업데이트 시 새 DB로 교체
  └── 인터넷 없이도 기본 동작 보장

장점:
  ├── 오프라인 완전 동작
  ├── 서버 인프라 불필요
  └── 앱 시작 시 즉시 사용 가능

단점:
  ├── DB 업데이트 = 앱 업데이트 필요
  ├── Play Store 심사 대기 시간 (1~3일)
  └── 사용자가 업데이트 안 하면 구형 DB 유지
```

### 5.2 Phase 2: OTA 업데이트 (Firebase Remote Config 또는 CDN)

```
업데이트 흐름:

  앱 시작 시:
    1. 로컬 DB 버전 확인 (SharedPreferences)
    2. 서버에 최신 버전 조회 (GET /api/oui/version)
    3. 버전 비교
       ├── 동일 → 업데이트 불필요
       └── 새 버전 → 차분 업데이트 다운로드

  차분 업데이트:
    1. 서버: 변경분만 포함한 JSON Patch 제공
       {
         "from_version": "1.0.0",
         "to_version": "1.1.0",
         "added": [...],
         "removed": [...],
         "modified": [...]
       }
    2. 클라이언트: 기존 DB에 패치 적용
    3. checksum 검증 (SHA-256)
    4. 검증 성공 → 로컬 DB 교체
    5. 검증 실패 → 전체 DB 재다운로드

  무결성 검증:
    ├── SHA-256 체크섬으로 변조 방지
    ├── HTTPS 전송 (TLS 1.3)
    └── 검증 실패 시 기존 DB 유지 (안전 모드)
```

### 5.3 업데이트 주기

| 항목 | 주기 | 트리거 |
|------|------|--------|
| 정기 업데이트 | 월 1회 | IEEE OUI DB 반영 |
| 긴급 업데이트 | 즉시 | 신규 카메라 제조사 대량 발견 |
| 사용자 피드백 반영 | 2주 1회 | 오탐/미탐 신고 누적 |
| 앱 업데이트 동반 | 분기 1회 | 스키마 변경 시 |

---

## 6. 데이터 수집 파이프라인

### 6.1 IEEE OUI 공개 DB 크롤링

```
데이터 소스:
  ├── IEEE MA-L (MAC Address Block Large): 대형 블록
  │   URL: https://standards-oui.ieee.org/oui/oui.csv
  ├── IEEE MA-M: 중형 블록
  ├── IEEE MA-S: 소형 블록
  └── 총 약 30,000+ 엔트리

크롤링 파이프라인 (Python 스크립트):

  1. IEEE CSV 다운로드 (자동, 주 1회)
  2. CSV 파싱 → (OUI, 회사명, 주소) 추출
  3. 카메라 관련 키워드 자동 필터
     ├── "camera", "surveillance", "security", "vision"
     ├── "CCTV", "DVR", "NVR", "PTZ"
     ├── "imaging", "video", "streaming"
     └── 매칭된 엔트리 → 수동 검토 큐에 추가
  4. 기존 DB와 diff 생성
  5. 관리자 검토 후 반영

자동 필터 예시:
  "Hikvision Digital Technology" → camera 키워드 매칭
  "Apple, Inc." → 미매칭 → safe_vendors 후보
  "Shenzhen XYZ Technology" → 수동 검토 필요
```

### 6.2 카메라 제조사 수동 추가

```
수동 추가 기준:
  1. 알려진 카메라 브랜드이지만 IEEE 등록명이 다른 경우
     예: "Hangzhou Hikvision" → "Hikvision"으로 매핑
  2. OEM/화이트레이블 카메라 제조사
     예: Tuya 플랫폼 기반 카메라 (다수 브랜드, 동일 모듈)
  3. 신규 카메라 제품 출시 뉴스에서 확인
  4. 보안 커뮤니티 리포트에서 확인

수동 추가 프로세스:
  1. 제조사 확인 (공식 사이트, 제품 사양서)
  2. OUI prefix 확인 (IEEE 검색 또는 실기기 MAC 확인)
  3. 제품군 분류 (ip_camera / smart_camera / etc.)
  4. risk_weight 결정
     ├── 전문 CCTV 전용 = 0.90~0.95
     ├── 소비자 스마트카메라 = 0.75~0.85
     ├── 복합 제조사 (카메라+비카메라) = 0.60~0.75
     └── 범용 모듈 (ESP32 등) = 0.40~0.50
  5. JSON 엔트리 작성 → PR → 코드 리뷰 → 머지
```

### 6.3 Phase 3: 사용자 피드백 기반 추가

```
사용자 피드백 수집:
  1. "이 기기가 카메라인가요?" 사용자 리포트
     ├── 기기 정보: MAC OUI, 열린 포트, 호스트명
     ├── 사용자 판단: "카메라였다" / "카메라 아니었다"
     └── 위치 유형: 숙소 / 화장실 / 탈의실

  2. 데이터 수집 (익명화, 동의 후):
     ├── OUI prefix만 수집 (전체 MAC 수집 안 함)
     ├── 포트 스캔 결과
     └── 사용자 확인 결과 (true positive / false positive)

  3. 분석 파이프라인:
     ├── 동일 OUI에 대해 5건+ "카메라였다" 리포트 → 자동 추가 후보
     ├── 동일 OUI에 대해 5건+ "카메라 아니었다" → risk_weight 하향 후보
     └── 관리자 검토 후 DB 반영

  4. 프라이버시 보호:
     ├── 전체 MAC 주소 수집 금지 (OUI 3바이트만)
     ├── 위치 정보 수집 안 함
     ├── 개인 식별 불가 데이터만 수집
     └── 수집 전 명시적 동의 (opt-in)
```

---

## 7. 테스트 전략

### 7.1 알려진 카메라 MAC 매칭 검증

```
단위 테스트:

  테스트 케이스 1: 카메라 제조사 정확 매칭
    입력: MAC = "28:57:BE:A1:23:4F"
    기대: vendor = "Hikvision", type = "ip_camera", risk = 0.95

  테스트 케이스 2: 안전 기기 정확 매칭
    입력: MAC = "00:1A:2B:C3:D4:E5"
    기대: vendor = "Apple", type = "consumer", risk = 0.05

  테스트 케이스 3: 미확인 제조사
    입력: MAC = "FF:FF:FF:00:00:00"
    기대: vendor = "Unknown", risk = 0.30 (기본값)

  테스트 케이스 4: 의심 모듈
    입력: MAC = "18:FE:34:XX:XX:XX"
    기대: vendor = "Espressif (ESP8266)", risk = 0.50, requires_port_scan = true

  테스트 케이스 5: 대소문자 무관
    입력: MAC = "28:57:be:a1:23:4f"
    기대: 정상 매칭 (대소문자 구분 없음)

  테스트 케이스 6: 구분자 형식
    입력: MAC = "2857BEA1234F" (구분자 없음)
    입력: MAC = "28-57-BE-A1-23-4F" (하이픈)
    기대: 모두 정상 매칭
```

### 7.2 성능 테스트

```
  테스트 1: 로딩 시간
    조건: 전체 DB (500KB JSON)
    기대: < 300ms (중사양 기기)

  테스트 2: 조회 성능
    조건: 100개 MAC OUI 연속 조회
    기대: < 10ms (HashMap 조회)

  테스트 3: 메모리 사용량
    조건: 전체 DB 인메모리 로딩
    기대: < 3MB

  테스트 4: 업데이트 적용
    조건: 10개 엔트리 추가 패치
    기대: < 100ms
```

### 7.3 통합 테스트 (실기기)

```
  테스트 환경:
    ├── Hikvision DS-2CD 시리즈 (Wi-Fi 연결)
    ├── Wyze Cam v3 (Wi-Fi 연결)
    ├── Tapo C200 (Wi-Fi 연결)
    ├── ESP32-CAM 모듈 (자체 AP)
    └── 일반 기기 (iPhone, Galaxy, 공유기)

  검증 항목:
    ├── 카메라 기기: OUI 매칭 + 높은 risk_weight 확인
    ├── 일반 기기: safe vendor 매칭 + 낮은 risk_weight 확인
    ├── ESP32-CAM: suspicious_module 매칭 + 포트 스캔 트리거 확인
    └── 미등록 기기: Unknown 처리 + 포트 스캔으로 보완 확인
```

### 7.4 데이터 무결성 테스트

```
  테스트 1: JSON 구조 유효성
    ├── JSON Schema 검증 통과
    ├── 필수 필드 누락 없음
    └── risk_weight 범위: 0.0 ~ 1.0

  테스트 2: OUI 중복 검사
    ├── camera_vendors 내 OUI 중복 없음
    ├── safe_vendors 내 OUI 중복 없음
    └── camera_vendors와 safe_vendors 간 OUI 충돌 없음

  테스트 3: 체크섬 검증
    ├── 파일 SHA-256 해시 계산
    ├── 메타데이터 checksum 필드와 일치 확인
    └── 불일치 시 오류 처리

  테스트 4: 버전 일관성
    ├── version 필드 SemVer 형식 준수
    ├── changelog 최신 항목과 version 일치
    └── min_app_version이 현재 앱 버전 이하
```

---

*본 문서는 project-plan.md v3.1의 OUI 데이터베이스 설계를 상세화한 것입니다.*
*실제 OUI prefix 값은 IEEE 공개 데이터 및 실기기 검증을 거쳐 확정합니다.*
