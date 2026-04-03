# Appendix A: OUI 데이터베이스 구조

> **이 부록에서 배울 것**: MAC 주소의 OUI 구조, SearCam이 수록한 카메라 제조사 목록, oui.json 파일 포맷, OuiDatabase.kt의 동작 방식을 참조합니다.

---

## A.1 OUI(Organizationally Unique Identifier) 구조

MAC 주소(48비트)는 두 부분으로 나뉩니다.

```
MAC 주소 구조:

  28:57:BE : A1:23:4F
  ├───────┤   ├──────┤
   OUI          기기 고유 식별자
  (3바이트,     (3바이트,
   제조사 식별)   기기별 고유값)

예시:
  28:57:BE → Hikvision Digital Technology
  3C:EF:8C → Dahua Technology
  00:09:18 → Hanwha Techwin (Samsung)
```

IEEE Registration Authority가 OUI를 관리하며, 모든 할당 내역은 공개되어 있습니다. SearCam은 이 공개 데이터를 기반으로 카메라 제조사를 분류한 자체 데이터베이스를 구축했습니다.

---

## A.2 카메라 제조사 OUI 목록

### IP 카메라 제조사 (전문 보안/감시 카메라)

`risk_weight`는 해당 OUI 기기가 카메라일 확률을 0~1 범위로 나타냅니다.

| OUI Prefix | 제조사 | 제품군 | risk_weight |
|-----------|--------|--------|-------------|
| 28:57:BE | Hikvision | IP카메라, NVR, DVR | 0.95 |
| C0:56:E3 | Hikvision | 추가 OUI | 0.95 |
| 44:19:B6 | Hikvision | 추가 OUI | 0.95 |
| 54:C4:15 | Hikvision | 추가 OUI | 0.95 |
| 3C:EF:8C | Dahua Technology | IP카메라, PTZ, NVR | 0.95 |
| A0:BD:1D | Dahua Technology | 추가 OUI | 0.95 |
| E0:50:8B | Dahua Technology | 추가 OUI | 0.95 |
| 00:09:18 | Hanwha Techwin | IP카메라, PTZ | 0.90 |
| 00:09:19 | Hanwha Techwin | 추가 OUI | 0.90 |
| 00:80:F0 | Panasonic | 보안카메라, PTZ | 0.85 |
| 00:40:84 | Honeywell | 보안카메라, 출입통제 | 0.80 |
| 00:04:A3 | Axis Communications | IP카메라, 엔코더 | 0.95 |
| AC:CC:8E | Axis Communications | 추가 OUI | 0.95 |
| B8:A4:4F | Axis Communications | 추가 OUI | 0.95 |
| 00:0F:7C | ACTi | IP카메라 | 0.90 |
| 00:18:85 | Avigilon | HD카메라, 분석 | 0.90 |
| 00:1A:07 | Arecont Vision | 메가픽셀 카메라 | 0.90 |
| 00:30:53 | Vivotek | IP카메라, NVR | 0.90 |
| EC:71:DB | Reolink | IP카메라, PoE | 0.90 |
| 9C:8E:CD | Reolink | 추가 OUI | 0.90 |
| 00:62:6E | Uniview | IP카메라, NVR | 0.90 |
| 24:28:FD | Uniview | 추가 OUI | 0.90 |
| 00:1B:90 | GeoVision | IP카메라, DVR | 0.90 |
| 7C:DD:90 | IDIS | DVR, NVR, IP카메라 | 0.90 |
| F4:B5:AA | Pelco (Schneider) | PTZ, 고정카메라 | 0.90 |
| 00:0C:68 | Bosch Security | IP카메라, DVR | 0.85 |
| 00:18:17 | Apace Technology | IP카메라 | 0.85 |
| 38:D5:47 | Motorola/Avigilon | 보안카메라 | 0.85 |
| D4:20:B0 | Meritech (한화 계열) | IP카메라 | 0.85 |
| 00:40:8C | Milestone/Mobotix | IP카메라 | 0.90 |

### 스마트 카메라 / IoT 카메라 제조사

| OUI Prefix | 제조사 | 제품군 | risk_weight |
|-----------|--------|--------|-------------|
| 2C:AA:8E | Wyze Labs | Wyze Cam 시리즈 | 0.80 |
| 18:B4:30 | Nest (Google) | Nest Cam Indoor/Outdoor | 0.80 |
| 98:DA:C4 | TP-Link (Tapo) | Tapo C200/C310 | 0.75 |
| FC:65:DE | Amazon (Ring) | Ring Indoor/Outdoor Cam | 0.80 |
| 34:D2:70 | Amazon (Blink) | Blink Mini/Outdoor | 0.80 |
| CC:50:E3 | Arlo Technologies | Arlo Pro/Ultra | 0.85 |
| 78:11:DC | Xiaomi | Mi Home 카메라 시리즈 | 0.75 |
| F8:4D:89 | Tuya Smart | 화이트레이블 카메라 | 0.80 |
| D8:1F:12 | Tuya Smart | 추가 OUI | 0.80 |
| 28:6D:97 | Imou (Dahua 소비자) | Imou Cue/Ranger | 0.85 |
| 40:AE:30 | Ezviz (Hikvision 소비자) | C6N/C3W | 0.85 |
| 38:01:46 | YI Technology | YI Home Camera | 0.80 |
| A4:34:D9 | Eufy (Anker) | eufyCam/Indoor Cam | 0.80 |
| E8:AB:F3 | Wansview | Wi-Fi 카메라 | 0.80 |
| D4:35:1D | YooSee | P2P 카메라 | 0.85 |
| 3C:84:6A | TP-Link (Tapo) | Tapo C시리즈 신형 | 0.75 |

### 초소형/위장 카메라 (Wi-Fi 모듈 기반)

> 주의: 아래 OUI는 카메라 외 IoT 기기에서도 사용됩니다. OUI 단독으로 카메라 판정하지 않으며, 포트 스캔(RTSP, HTTP)으로 교차 확인합니다.

| OUI Prefix | 칩셋/모듈 | 설명 | risk_weight |
|-----------|---------|------|-------------|
| 18:FE:34 | Espressif ESP8266 | 중국산 초소형 카메라 다수 사용 | 0.50 |
| 24:0A:C4 | Espressif ESP32 | ESP32-CAM 보드 | 0.50 |
| A4:CF:12 | Espressif | 추가 OUI | 0.50 |
| 48:3F:DA | Espressif | 추가 OUI | 0.50 |
| B0:A7:32 | Realtek RTL8xxx | Wi-Fi 칩셋, 다용도 | 0.40 |

---

## A.3 안전 기기 화이트리스트

숙소에서 정상적으로 발견되는 기기들. 이 OUI가 감지되면 포트 스캔을 스킵하여 스캔 시간을 단축합니다.

| 제조사 | 기기 유형 | risk_weight |
|--------|----------|-------------|
| Apple | iPhone, iPad, MacBook | 0.05 |
| Samsung (비카메라) | Galaxy, TV, 가전 | 0.05 |
| LG | 스마트폰, TV | 0.05 |
| Google | Pixel, Chromecast | 0.05 |
| Huawei | 스마트폰, 태블릿 | 0.05 |
| Microsoft | Surface, Xbox | 0.05 |
| ipTIME (EFM) | 공유기 | 0.05 |
| ASUS | 공유기, RT 시리즈 | 0.05 |
| Netgear | 공유기, 스위치 | 0.05 |

---

## A.4 oui.json 파일 포맷

`assets/oui.json` 파일의 구조입니다.

```json
{
  "version": "1.0.0",
  "last_updated": "2026-04-01",
  "entries": [
    {
      "prefix": "28:57:BE",
      "manufacturer": "Hikvision Digital Technology",
      "product_category": "IP_CAMERA",
      "risk_weight": 0.95,
      "notes": "전 세계 1위 CCTV 점유율"
    },
    {
      "prefix": "3C:EF:8C",
      "manufacturer": "Dahua Technology",
      "product_category": "IP_CAMERA",
      "risk_weight": 0.95,
      "notes": "전 세계 2위 CCTV 점유율"
    },
    {
      "prefix": "18:FE:34",
      "manufacturer": "Espressif Systems (ESP8266)",
      "product_category": "IOT_MODULE",
      "risk_weight": 0.50,
      "notes": "IoT 모듈 범용, 카메라 아닐 수도 있음. 포트 스캔 필수."
    },
    {
      "prefix": "00:1A:2B",
      "manufacturer": "Apple Inc.",
      "product_category": "SAFE_DEVICE",
      "risk_weight": 0.05,
      "notes": "iPhone, MacBook 등 안전 기기"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `prefix` | String | MAC 주소 앞 3바이트 (대문자, 콜론 구분) |
| `manufacturer` | String | 제조사명 |
| `product_category` | Enum | `IP_CAMERA`, `SMART_CAMERA`, `IOT_MODULE`, `SAFE_DEVICE`, `NETWORK_DEVICE` |
| `risk_weight` | Float | 카메라일 확률 (0.0~1.0) |
| `notes` | String | 부가 설명 (선택) |

---

## A.5 OuiDatabase.kt 동작 방식

```kotlin
// 파일 위치: data/repository/OuiDatabase.kt (간략화)

class OuiDatabase(private val context: Context) {

    // assets/oui.json → 메모리 맵으로 로드
    // 키: OUI prefix (소문자 정규화), 값: OuiEntry
    private val ouiMap: Map<String, OuiEntry> by lazy {
        loadFromAssets()
    }

    // MAC 주소에서 OUI 추출 후 조회
    fun lookup(macAddress: String): OuiEntry? {
        val prefix = macAddress
            .take(8)           // "28:57:BE:..."에서 앞 8자리
            .uppercase()       // 대소문자 정규화
        return ouiMap[prefix]
    }

    // risk_weight 기반 카메라 여부 판별
    fun isCameraManufacturer(macAddress: String): Boolean {
        val entry = lookup(macAddress) ?: return false
        return entry.riskWeight >= 0.7f
    }

    // 안전 기기 화이트리스트 확인
    fun isSafeDevice(macAddress: String): Boolean {
        val entry = lookup(macAddress) ?: return false
        return entry.productCategory == ProductCategory.SAFE_DEVICE
            || entry.riskWeight <= 0.1f
    }
}
```

### 스캔 파이프라인에서의 OUI 활용

```
ARP 테이블 파싱
  → IP + MAC 목록 획득
  → MAC 앞 3바이트 추출

OuiDatabase.lookup(mac)
  → null        → 미등록 제조사 → 중간 위험도 + 포트 스캔
  → SAFE_DEVICE → 낮은 위험도  → 포트 스캔 스킵 (속도 최적화)
  → IP_CAMERA   → 높은 위험도  → risk_weight 적용 + 포트 스캔
  → IOT_MODULE  → 중간 위험도  → 포트 스캔으로 RTSP 확인 필수
```

---

## A.6 OUI DB 업데이트 방식

| 방식 | 대상 사용자 | 설명 |
|------|-----------|------|
| 앱 업데이트 내포 | 무료 사용자 | 앱 신버전에 최신 oui.json 포함 |
| OTA 업데이트 | 프리미엄 사용자 | 앱 실행 시 서버에서 최신 oui.json 다운로드 |
| 업데이트 주기 | 무료: 월 1~2회 / 프리미엄: 주 1회 | 신규 카메라 제조사 반영 |

> **저작권 안내**: OUI 데이터의 원본 출처는 IEEE Registration Authority입니다. SearCam은 IEEE 공개 데이터를 기반으로 카메라 제조사 분류를 추가하여 자체 편집한 데이터베이스입니다. IEEE OUI 원본 데이터에 대한 저작권은 IEEE에 있습니다.
