# Ch06: 보안 설계 — 탐지 앱이 탐지 당하면 안 된다

> **이 장에서 배울 것**: 몰래카메라를 탐지하는 앱이 역으로 사용자 데이터를 수집하거나 악용될 가능성을 어떻게 차단하는지 배웁니다. SQLCipher 암호화, Android Keystore, 네트워크 스캔 범위 제한, 최소 권한 원칙, 카메라 프레임 메모리 전용 처리를 실제 코드와 함께 설명합니다.

---

## 도입

자물쇠 가게는 가장 튼튼한 자물쇠를 만들어야 합니다. 그런데 그 가게가 복사 열쇠를 마음대로 만들 수 있다면, 우리는 그 가게를 믿을 수 없습니다.

SearCam은 사용자의 가장 사적인 공간—숙소, 화장실, 탈의실—에서 실행되는 앱입니다. 카메라 권한, 위치 권한, 네트워크 스캔 권한을 모두 가진 앱이 만약 그 정보를 수집하거나 외부로 전송한다면, 그 앱 자체가 몰래카메라가 됩니다.

"탐지 앱이 탐지 당하면 안 된다"는 원칙이 SearCam 보안 설계의 전부입니다.

이 장에서는 4개 보안 레이어(권한, 네트워크, 데이터, 코드)를 계층적으로 설명하고, 각 레이어가 어떤 위협을 차단하는지 보여줍니다.

---

## 6.1 3대 핵심 원칙

보안 설계의 복잡성을 3가지 원칙으로 압축했습니다.

```
┌─────────────────────────────────────────────────────┐
│  원칙 1: 로컬 처리 우선                              │
│  모든 탐지 및 분석은 사용자 기기 내에서만 수행        │
│  → 서버로 전송하지 않으면 서버 해킹 피해가 없다      │
├─────────────────────────────────────────────────────┤
│  원칙 2: 최소 권한                                   │
│  앱 기능에 반드시 필요한 권한만 요청                  │
│  → 권한이 없으면 남용할 수 없다                      │
├─────────────────────────────────────────────────────┤
│  원칙 3: 데이터 미수집                               │
│  사용자 개인정보를 수집하거나 외부로 전송하지 않음    │
│  → 수집하지 않은 데이터는 유출될 수 없다             │
└─────────────────────────────────────────────────────┘
```

이 3원칙은 서로 보완합니다. 로컬 처리 우선이 외부 유출 경로를 차단하고, 최소 권한이 수집 범위를 제한하며, 데이터 미수집이 그 목적 자체를 없앱니다.

---

## 6.2 보안 계층 구조 (Defense in Depth)

군사 방어에서 "심층 방어(Defense in Depth)"란 외곽이 뚫려도 내부 방어선이 남아 있는 구조를 말합니다. SearCam의 보안도 4개 계층으로 구성됩니다.

```
┌─────────────────────────────────────────────┐
│           Layer 4: 코드 보안                 │
│  ProGuard/R8 난독화, 루팅 탐지, 빌드 분리   │
├─────────────────────────────────────────────┤
│           Layer 3: 데이터 보안               │
│  SQLCipher AES-256, Android Keystore,       │
│  MAC 주소 SHA-256 해시, 카메라 프레임 즉시 해제│
├─────────────────────────────────────────────┤
│           Layer 2: 네트워크 보안             │
│  로컬 네트워크 스캔 범위 제한, HTTPS + 인증서│
│  고정, RFC 1918 주소만 대상, TTL=1 격리      │
├─────────────────────────────────────────────┤
│           Layer 1: 권한 보안                 │
│  최소 권한 요청, 런타임 권한, 사용 후 즉시 해제│
└─────────────────────────────────────────────┘
```

외부에서 내부로 침투하려면 4개 계층을 모두 돌파해야 합니다. 하나의 계층이 실패해도 나머지가 보호합니다.

---

## 6.3 위협 모델링 — 적을 먼저 알아야 한다

STRIDE 프레임워크로 SearCam의 위협을 체계적으로 분석했습니다. STRIDE는 6가지 위협 유형의 약어입니다: Spoofing(위장), Tampering(변조), Repudiation(부인), Information Disclosure(정보 노출), Denial of Service(서비스 거부), Elevation of Privilege(권한 상승).

### SearCam STRIDE 위협 매트릭스

| 위협 유형 | 시나리오 | 위험도 | SearCam 대응 |
|----------|---------|--------|-------------|
| **Spoofing** | 악성 앱이 SearCam UI를 위장 | 낮음 | Play Store 단일 배포 + 앱 서명 |
| **Spoofing** | 카메라 MAC을 일반 기기로 위장 | 중간 | OUI + 포트 + mDNS 교차 검증 |
| **Tampering** | 로컬 DB 스캔 리포트 변조 | 낮음 | SQLCipher 암호화 |
| **Tampering** | OUI DB를 변조하여 카메라를 안전 기기로 등록 | 중간 | 앱 내장 DB + 업데이트 서명 검증 |
| **Info Disclosure** | MAC 주소 외부 유출 | 중간 | 메모리 전용 처리, DB에는 SHA-256 해시만 저장 |
| **Info Disclosure** | 카메라 프레임 데이터 유출 | 높음 | 저장 안 함, 분석 후 즉시 메모리 해제 |
| **DoS** | 대량 기기로 스캔 지연/크래시 | 중간 | 기기 수 상한 254대, 타임아웃 적용 |
| **EoP** | 앱 권한으로 시스템 공격 | 낮음 | 최소 권한, 센서 사용 후 즉시 해제 |

가장 위험한 위협은 "카메라 프레임 데이터 유출"입니다. 사용자의 실시간 영상이 외부로 나가는 것은 앱이 몰래카메라가 되는 것이기 때문입니다. 이 위협에 대한 대응이 가장 철저해야 합니다.

---

## 6.4 최소 권한 원칙 — 필요한 것만 요청한다

### 요청하는 권한 (7개만)

| 권한 | 사용 이유 | 사용 시점 | 사용 후 처리 |
|------|---------|---------|------------|
| `ACCESS_FINE_LOCATION` | Wi-Fi 스캔 시 Android 필수 요구 | Quick/Full Scan | 스캔 완료 후 중단 |
| `ACCESS_WIFI_STATE` | Wi-Fi 연결 상태 확인 | 스캔 시작 전 | 상태 확인만 |
| `CHANGE_WIFI_STATE` | Wi-Fi 스캔 트리거 | Quick/Full Scan | 스캔 완료 후 비활성화 |
| `CAMERA` | 렌즈/IR 감지 | 렌즈 찾기, Full Scan | 분석 완료 후 즉시 해제 |
| `INTERNET` | OUI DB 업데이트, AdMob | 백그라운드 업데이트 | 업데이트 완료 후 종료 |
| `VIBRATE` | 의심 기기 발견 알림 | 스캔 중 | 설정으로 비활성화 가능 |
| `FLASHLIGHT` | Retroreflection 렌즈 감지 | 렌즈 찾기 모드 | 모드 종료 시 OFF |

### 의도적으로 요청하지 않는 권한

다음 권한은 일부 카메라 앱들이 불필요하게 요청하는 권한입니다. SearCam은 이를 명시적으로 배제합니다.

```
❌ READ_CONTACTS        — 연락처와 탐지 기능은 무관
❌ READ_PHONE_STATE     — 전화 기능 불필요
❌ RECORD_AUDIO         — 음성 녹음 불필요
❌ ACCESS_BACKGROUND_LOCATION — 백그라운드 위치 불필요
❌ BLUETOOTH_SCAN       — Phase 1에서 BLE 미구현
❌ READ_EXTERNAL_STORAGE — 외부 저장소 접근 불필요
❌ WRITE_EXTERNAL_STORAGE — PDF 내보내기는 SAF 사용
```

권한 개수가 적을수록 신뢰도는 높아집니다. Play Store의 "데이터 안전" 섹션에서 권한 목록을 본 사용자는 "이 앱이 최소한의 것만 요청한다"는 인상을 받습니다.

---

## 6.5 네트워크 스캔 범위 제한 — 울타리를 쳐라

포트 스캔은 SearCam에서 가장 민감한 기능입니다. 잘못 구현하면 해킹 도구가 됩니다. 다음 4가지 제한으로 스캔 범위를 철저히 통제합니다.

### 제한 1: RFC 1918 사설 주소만 대상

인터넷의 주소 체계에서 RFC 1918은 사설 네트워크(내부 네트워크)를 위한 주소 범위를 정의합니다.

```
사설 주소 범위 (로컬 네트워크):
  10.0.0.0/8      (10.x.x.x)
  172.16.0.0/12   (172.16.x.x ~ 172.31.x.x)
  192.168.0.0/16  (192.168.x.x)

공인 주소 (인터넷):
  위 범위에 포함되지 않는 모든 주소
  → SearCam에서 절대 스캔 대상으로 사용하지 않음
```

```kotlin
fun isPrivateAddress(ip: String): Boolean {
    val addr = InetAddress.getByName(ip)
    return addr.isSiteLocalAddress || addr.isLoopbackAddress
}

fun scanDevices(devices: List<NetworkDevice>): List<NetworkDevice> {
    // 사설 주소만 스캔, 공인 IP는 즉시 제외
    return devices.filter { isPrivateAddress(it.ipAddress) }
}
```

### 제한 2: 카메라 관련 포트만 (6개)

모든 포트를 스캔하면 해킹 행위와 구분이 없습니다. SearCam은 카메라와 직접 관련된 6개 포트만 스캔합니다.

| 포트 | 프로토콜 | 용도 | 카메라 연관성 |
|------|---------|------|-------------|
| 554 | RTSP | 스트리밍 | IP 카메라의 기본 스트리밍 포트 |
| 80 | HTTP | 웹 인터페이스 | IP 카메라 웹 뷰어 |
| 8080 | HTTP-alt | 대체 웹 | 일부 카메라 대체 포트 |
| 8888 | HTTP-alt | 대체 웹 | 소형 카메라 포트 |
| 3702 | ONVIF | 카메라 표준 | ONVIF IP 카메라 탐색 |
| 1935 | RTMP | 스트리밍 | 실시간 스트리밍 |

### 제한 3: 동시 연결 5개 제한

```kotlin
// 세마포어로 동시 연결 수를 5개로 제한
private val semaphore = Semaphore(5)

suspend fun scanPort(ip: String, port: Int, timeoutMs: Long = 2000): Boolean {
    return semaphore.withPermit {
        withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), timeoutMs.toInt())
                    true
                }
            } catch (e: IOException) {
                false
            }
        }
    }
}
```

5개 제한은 "충분히 빠르면서도 네트워크에 부하를 주지 않는" 균형점입니다. TCP Connect 방식(SYN 스캔이 아님)으로 비공격적입니다.

### 제한 4: 포트당 2초 타임아웃

응답 없는 포트를 무한정 기다리면 30초 스캔이 불가능합니다. 2초 타임아웃은 "응답 없음 = 포트 닫힘"으로 처리합니다.

---

## 6.6 카메라 프레임 메모리 전용 처리

이것이 SearCam 보안의 핵심입니다. 카메라로 촬영한 프레임은 단 1바이트도 디스크에 기록되지 않습니다.

### 프레임 생명주기

```
[카메라 센서]
    │
    ▼
[ImageAnalysis.Analyzer.analyze()] ← 이 함수 안에서만 존재
    │
    ├── 고휘도 포인트 추출 (Bitmap.getPixel)
    │
    ├── 렌즈 판정 (원형도, 크기, 안정성)
    │
    ├── 점수 계산
    │
    └── [ImageProxy.close()] ← 즉시 메모리 해제
         │
         ▼
    (프레임 데이터 소멸)
```

```kotlin
class LensAnalyzer(
    private val onResult: (LensDetectionResult) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        // analyze() 내부에서만 프레임 데이터 접근
        try {
            val bitmap = image.toBitmap()
            val result = detectLens(bitmap)
            onResult(result)
            // bitmap은 이 함수 스코프 내에서 소멸
        } finally {
            // 예외가 발생해도 반드시 해제
            image.close()
        }
    }

    private fun detectLens(bitmap: Bitmap): LensDetectionResult {
        val points = extractHighBrightnessPoints(bitmap)
        val suspiciousPoints = filterLensPoints(points)
        // bitmap 참조를 외부로 반환하지 않음
        return LensDetectionResult(suspiciousPoints, calculateScore(suspiciousPoints))
    }
}
```

`finally` 블록의 `image.close()`는 절대 생략할 수 없습니다. 예외가 발생한 경우에도 프레임이 메모리에 잔류하지 않도록 보장합니다.

### 검증: 카메라 프레임이 정말 저장 안 되나?

릴리스 전 검증 방법:

```bash
# 루팅 기기에서 앱 데이터 디렉토리 확인
adb shell run-as com.searcam.app ls -la /data/data/com.searcam.app/

# 예상 결과: 이미지 파일 (.jpg, .png, .bmp) 0개
# 허용 파일: databases/ (SQLCipher 암호화 DB), shared_prefs/
```

---

## 6.7 SQLCipher + Android Keystore 암호화

금고의 내용물이 중요하면 금고도 튼튼해야 합니다. SearCam의 로컬 DB는 SQLCipher로 암호화됩니다.

### 암호화 스펙

```
┌───────────────────────────────────────────────┐
│              Room DB + SQLCipher               │
│                                               │
│  알고리즘: AES-256-CBC                         │
│  키 파생: PBKDF2-HMAC-SHA512                   │
│           반복 횟수: 256,000회                 │
│  페이지 크기: 4096 bytes                       │
│  키 저장: Android Keystore (TEE/SE 지원)       │
│                                               │
│  테이블:                                       │
│  ├── reports (암호화) — 스캔 리포트            │
│  └── settings (암호화) — 앱 설정              │
└───────────────────────────────────────────────┘
```

### Android Keystore를 쓰는 이유

일반 SharedPreferences에 DB 키를 저장하면, 루팅된 기기에서 앱 데이터 디렉토리를 열어보면 키가 그대로 노출됩니다. Android Keystore는 키를 TEE(Trusted Execution Environment) 또는 SE(Secure Element) 하드웨어에 저장합니다. 소프트웨어가 키에 직접 접근할 수 없고, "이 앱이 이 키로 암호화/복호화해달라"는 요청만 가능합니다.

```kotlin
object DatabaseKeyManager {

    private const val KEY_ALIAS = "searcam_db_key"
    private const val PREFS_NAME = "searcam_secure_prefs"
    private const val KEY_PREF = "encrypted_db_passphrase"

    fun getOrCreateKey(context: Context): ByteArray {
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existing = prefs.getString(KEY_PREF, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.DEFAULT)
        }

        // 최초 실행: 256비트 랜덤 키 생성
        val newKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PREF, Base64.encodeToString(newKey, Base64.DEFAULT))
            .apply()
        return newKey
    }
}
```

```kotlin
// Room DB에 SQLCipher 적용
@Database(entities = [ReportEntity::class, SettingEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao

    companion object {
        fun create(context: Context): AppDatabase {
            val passphrase = DatabaseKeyManager.getOrCreateKey(context)
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "searcam.db"
            )
            .openHelperFactory(factory)
            .build()
        }
    }
}
```

### MAC 주소 SHA-256 해시 처리

MAC 주소는 기기를 식별할 수 있는 준개인정보입니다. SearCam은 원본 MAC 주소를 DB에 저장하지 않습니다.

```kotlin
fun hashMacAddress(mac: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(mac.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}

// 저장: 원본 "AA:BB:CC:DD:EE:FF" → 해시 "a1b2c3..."
// 원본은 메모리에서 즉시 삭제
```

---

## 6.8 네트워크 통신 보안 — 단 하나의 예외

Phase 1 SearCam은 외부 서버와 단 하나의 이유로만 통신합니다: OUI 데이터베이스 업데이트. 나머지 모든 기능은 100% 온디바이스로 처리됩니다.

```
SearCam이 외부와 통신하는 것:
  ✅ OUI DB 업데이트 (HTTPS + 인증서 고정)
  ✅ AdMob 광고 (Google SDK 관리)

SearCam이 외부와 통신하지 않는 것:
  ❌ 탐지 결과 전송
  ❌ 사용자 행동 분석
  ❌ 스캔 데이터 업로드
  ❌ 사용자 계정 서버
```

### Certificate Pinning (인증서 고정)

OUI DB 업데이트 시 MITM(중간자 공격)으로 가짜 OUI 데이터를 주입하면, 카메라 MAC 주소가 안전 기기로 위장될 수 있습니다. 인증서 고정으로 이를 방지합니다.

```kotlin
// OkHttp에 인증서 고정 적용
val certificatePinner = CertificatePinner.Builder()
    .add("oui.searcam.app", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build()

val okHttpClient = OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()
```

서버 인증서의 공개키 해시를 앱에 내장합니다. 서버 인증서가 변경되거나 프록시가 개입하면 연결을 즉시 거부합니다.

---

## 6.9 코드 보안 — 릴리스 빌드는 다르다

### ProGuard/R8 난독화

릴리스 APK를 jadx로 디컴파일하면 Kotlin 코드를 복원할 수 있습니다. ProGuard/R8 난독화를 적용하면 클래스명, 메서드명이 `a`, `b`, `c`로 변환되어 역공학이 어려워집니다.

```
# proguard-rules.pro

# 탐지 알고리즘 핵심 클래스 보호
-keep class com.searcam.data.analysis.** { *; }
-keepclassmembers class com.searcam.data.analysis.CrossValidator {
    private <fields>;
}

# Room DB 엔티티 유지 (리플렉션 사용)
-keep class com.searcam.data.local.** { *; }

# 릴리스에서 로그 완전 제거
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
}
```

`-assumenosideeffects` 설정으로 릴리스 빌드에서 `Log.d()`, `Log.v()` 등이 완전히 제거됩니다. 로그에 민감한 정보가 포함되어 있어도 릴리스 APK에서는 나타나지 않습니다.

### 디버그 vs 릴리스 분리

| 항목 | 디버그 빌드 | 릴리스 빌드 |
|------|-----------|-----------|
| 난독화 | 비활성화 | R8 full mode |
| 로그 | Timber.d() 활성화 | 완전 제거 |
| SQLCipher 키 | "debug" 고정 비밀번호 | Android Keystore |
| 인증서 검증 | 시스템 CA 신뢰 | 인증서 고정 |
| 디버거 연결 | 허용 | 거부 (isDebuggable=false) |
| ADB 백업 | 허용 | 거부 (allowBackup=false) |

`allowBackup=false`는 AndroidManifest.xml에 한 줄이지만, 이 설정 없이는 `adb backup` 명령으로 앱의 모든 데이터(암호화된 DB 포함)를 추출할 수 있습니다.

```xml
<!-- AndroidManifest.xml -->
<application
    android:allowBackup="false"
    android:fullBackupContent="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    ... >
```

### 경로 순회(Path Traversal) 방지

PDF 내보내기 기능에서 파일명을 사용자 입력으로 받으면 위험합니다.

```kotlin
// WRONG: 경로 순회 취약점
fun exportPdf(fileName: String) {
    val file = File(exportDir, fileName)  // "../../../etc/passwd" 입력 가능
    writePdf(file)
}

// CORRECT: 파일명만 추출, 경로 구분자 제거
fun exportPdf(fileName: String) {
    val safeName = File(fileName).name  // 파일명만 추출
        .replace(Regex("[^a-zA-Z0-9가-힣_\\-.]"), "_")  // 위험 문자 치환
        .take(100)  // 길이 제한
    val file = File(exportDir, safeName)
    writePdf(file)
}
```

SearCam은 PDF 내보내기에 SAF(Storage Access Framework)를 사용합니다. SAF는 사용자가 직접 저장 위치를 선택하므로 앱이 파일시스템에 임의 접근하는 것 자체가 불가능합니다.

---

## 6.10 루팅 기기 대응

루팅된 기기는 앱 샌드박스를 무력화할 수 있습니다. Android Keystore가 소프트웨어 수준에서만 구현된 기기라면 루트 권한으로 키를 추출할 수도 있습니다.

```
루팅 기기 탐지 흐름:
  │
  ├─ su 바이너리 존재 확인
  ├─ /system/app/Superuser.apk 확인
  ├─ Build.TAGS 확인 (test-keys)
  │
  ▼
경고 다이얼로그 표시:
"이 기기는 루팅되어 있습니다.
 데이터 보호가 약화될 수 있습니다.
 계속 사용하시겠습니까?"
  │
  ├─ [계속] → 기능 제한 없이 사용 (사용자 선택 존중)
  └─ [취소] → 앱 종료
```

기능을 완전히 차단하지 않는 이유는 보안 연구자, 개발자, 고급 사용자의 권리를 존중하기 때문입니다. 경고만 표시하고 판단은 사용자에게 맡깁니다.

---

## 6.11 릴리스 전 보안 체크리스트

이 체크리스트는 모든 릴리스 전에 반드시 완료해야 합니다.

### 데이터 보호

- [ ] MAC 주소 원본이 DB에 저장되지 않는가
- [ ] 카메라 프레임이 디스크에 기록되지 않는가
- [ ] SQLCipher 암호화가 정상 동작하는가
- [ ] 앱 삭제 시 모든 데이터가 제거되는가
- [ ] allowBackup=false가 설정되어 있는가

### 권한

- [ ] 불필요한 권한을 요청하지 않는가
- [ ] 런타임 권한 거부 시 앱이 크래시하지 않는가
- [ ] 권한 사용 후 센서/카메라를 해제하는가

### 네트워크

- [ ] 외부 네트워크 IP 접근 시도가 없는가
- [ ] HTTPS 통신만 사용하는가
- [ ] 인증서 고정이 동작하는가
- [ ] 포트 스캔이 사설 주소로만 제한되는가

### 코드

- [ ] 릴리스 빌드에서 로그가 완전 제거되는가
- [ ] ProGuard/R8 난독화가 적용되는가
- [ ] 하드코딩된 비밀(API 키, 비밀번호)이 없는가
- [ ] 경로 순회 취약점이 없는가

### 검증 도구

| 단계 | 도구 | 검증 항목 |
|------|------|---------|
| 정적 분석 | Android Lint, Detekt | 코드 취약점 |
| 의존성 취약점 | Gradle Dependency Check | CVE 포함 라이브러리 |
| 네트워크 검증 | Charles Proxy, mitmproxy | 외부 통신 없음 확인 |
| 데이터 검증 | adb + 루팅 기기 | 파일 미저장 확인 |
| 난독화 확인 | jadx | APK 역공학 난이도 |

---

## 6.12 보안과 기능성의 균형

보안이 강할수록 기능이 제한됩니다. 이 긴장을 SearCam은 어떻게 해결했을까요?

```
보안 강화 방향                    기능 강화 방향
──────────────────────────────────────────────────
카메라 프레임 즉시 삭제  ←→  렌즈 탐지 정확도
네트워크 스캔 제한      ←→  Wi-Fi 탐지 범위
최소 권한 요청          ←→  더 많은 센서 활용
로컬 처리만             ←→  클라우드 AI 분석
```

SearCam의 선택: **보안이 먼저, 기능이 나중**.

이유는 간단합니다. 탐지 정확도가 70%여도 사용자 데이터가 안전한 앱은 신뢰받습니다. 탐지 정확도가 90%여도 사용자 데이터가 외부로 나가는 앱은 신뢰받지 못합니다.

---

## 정리: 탐지 앱이 탐지당하지 않는 방법

SearCam의 보안 설계는 기술적 우수성보다 **설계 철학**이 핵심입니다.

1. 수집하지 않으면 유출되지 않는다 → 데이터 미수집
2. 저장하지 않으면 도난당하지 않는다 → 메모리 전용 처리
3. 요청하지 않으면 남용할 수 없다 → 최소 권한
4. 나가지 않으면 가로채이지 않는다 → 로컬 처리 우선

보안은 앱을 배포하기 전에 설계에서 시작해야 합니다. 나중에 "보안을 추가"하는 것은 집을 다 지은 후 지하실을 만드는 것과 같습니다.

다음 장에서는 이 설계가 실제로 올바르게 구현되었는지 증명하는 방법인 테스트 전략을 다룹니다.

---

## 보안 원칙 요약

| 위협 | 대응 | 구현 |
|------|------|------|
| 카메라 프레임 유출 | 메모리 전용, 즉시 해제 | `image.close()` in finally |
| DB 데이터 유출 | SQLCipher AES-256 | Android Keystore 키 |
| MAC 주소 노출 | SHA-256 해시 저장 | 원본 미저장 |
| 외부 네트워크 스캔 | RFC 1918 주소만 허용 | `isPrivateAddress()` 필터 |
| 포트 스캔 남용 | 6개 포트, 5개 동시 연결, 2초 타임아웃 | Semaphore + timeout |
| DB 백업 취약점 | ADB 백업 금지 | `allowBackup=false` |
| 리버스 엔지니어링 | R8 난독화 | ProGuard rules |
| MITM 공격 | 인증서 고정 | CertificatePinner |
