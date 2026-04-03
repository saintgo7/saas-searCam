# Appendix C: 에러 코드 레퍼런스

> **이 부록에서 배울 것**: SearCam의 E1xxx(센서), E2xxx(네트워크), E3xxx(권한) 에러 코드 전체 목록과 각 에러의 원인, 대응 방법을 참조합니다.

---

## C.1 에러 코드 체계

SearCam은 에러를 세 도메인으로 분류합니다.

| 범위 | 도메인 | 예시 |
|------|--------|------|
| E1xxx | 센서 오류 | 자력계 미지원, 카메라 초기화 실패 |
| E2xxx | 네트워크 오류 | Wi-Fi 미연결, ARP 테이블 읽기 실패 |
| E3xxx | 권한 오류 | 위치 권한 미승인, 카메라 권한 미승인 |

에러 코드는 `Constants.ErrorCode` 객체에 정의되며, 로그와 사용자 화면에 모두 표시됩니다.

---

## C.2 E1xxx - 센서 오류

| 에러 코드 | 상수명 | 발생 원인 | 사용자 메시지 | 대응 방법 |
|---------|--------|---------|------------|---------|
| E1001 | E1001 | 자력계(Magnetometer) 미지원 기기 | "이 기기는 자기장 센서를 지원하지 않습니다" | EMF 탐지 레이어 비활성화, Layer 1+2로 대체 |
| E1002 | E1002 | 자력계 정확도 불량 (ACCURACY_LOW) | "자기장 센서 정확도가 낮습니다. 8자 모양으로 기기를 움직여 캘리브레이션해주세요" | 사용자에게 캘리브레이션 안내 후 재시도 |
| E1003 | E1003 | 카메라 초기화 실패 | "카메라를 시작할 수 없습니다. 앱을 재시작해주세요" | CameraX 세션 재초기화 시도 |
| E1004 | E1004 | 플래시(토치) 미지원 기기 | "이 기기는 플래시를 지원하지 않습니다. 렌즈 역반사 감지는 밝은 곳에서 사용하세요" | 플래시 없이 렌즈 감지 진행 (정확도 저하 안내) |
| E1005 | E1005 | 센서 샘플링 타임아웃 | "센서 응답이 없습니다. 잠시 후 다시 시도해주세요" | 5초 후 자동 재시도 |
| E1010 | E1010 | 자기장 센서 스트림 오류 | "자기장 측정 중 오류가 발생했습니다" | Flow 재구독 시도, 실패 시 EMF 레이어 비활성화 |
| E1011 | E1011 | 자기장 캘리브레이션 실패 | "자기장 베이스라인 측정에 실패했습니다. 금속 물체에서 멀리 이동해주세요" | 베이스라인 재측정 요청 |

### E1002 캘리브레이션 안내 화면

```
[캘리브레이션 안내]

자기장 센서의 정확도가 낮습니다.
더 정확한 측정을 위해 다음 동작을 해주세요:

  ①  스마트폰을 들고
  ②  공중에서 8자 모양으로
  ③  3회 천천히 움직이세요

[완료]  [건너뛰기]

건너뛰면 자기장 탐지 정확도가 낮아질 수 있습니다.
```

---

## C.3 E2xxx - 네트워크 오류

| 에러 코드 | 상수명 | 발생 원인 | 사용자 메시지 | 대응 방법 |
|---------|--------|---------|------------|---------|
| E2001 | E2001 | Wi-Fi 미연결 상태 | "Wi-Fi에 연결되어 있지 않습니다. Wi-Fi를 연결한 후 다시 시도해주세요" | Wi-Fi 설정 화면으로 이동 안내 |
| E2002 | E2002 | ARP 테이블 읽기 실패 | "네트워크 기기 목록을 가져오지 못했습니다" | `/proc/net/arp` 접근 실패. mDNS 탐색으로 대체 시도 |
| E2003 | E2003 | mDNS 서비스 탐색 실패 | "네트워크 서비스 탐색에 실패했습니다" | mDNS NsdManager 오류. Layer 1 결과 부분적으로만 표시 |
| E2004 | E2004 | 포트 스캔 타임아웃 | "일부 기기의 응답 시간이 초과되었습니다" | 타임아웃 기기는 "응답 없음"으로 처리, 스캔 계속 진행 |
| E2005 | E2005 | OUI 데이터베이스 로드 실패 | "카메라 제조사 데이터베이스를 불러오지 못했습니다. 앱을 재설치해주세요" | Assets 파일 손상. 제조사 분류 없이 포트 기반으로만 스캔 |

### E2002 대체 전략

```kotlin
// ARP 테이블 실패 시 mDNS로 대체
suspend fun discoverDevices(): List<NetworkDevice> {
    return try {
        arpTableScanner.scan()
    } catch (e: IOException) {
        logger.error(Constants.ErrorCode.E2002, e)
        // 대체: mDNS 서비스 탐색
        mdnsScanner.discover()
    }
}
```

---

## C.4 E3xxx - 권한 오류

| 에러 코드 | 상수명 | 발생 원인 | 사용자 메시지 | 대응 방법 |
|---------|--------|---------|------------|---------|
| E3001 | E3001 | 위치 권한 미승인 (Wi-Fi 스캔 불가) | "Wi-Fi 스캔에는 위치 권한이 필요합니다" | 권한 요청 다이얼로그 표시. 거부 시 렌즈+자기장 모드로 계속 |
| E3002 | E3002 | 카메라 권한 미승인 | "렌즈 감지와 IR 탐지에는 카메라 권한이 필요합니다" | 권한 요청 다이얼로그 표시. 거부 시 Wi-Fi+자기장 모드로 계속 |
| E3003 | E3003 | 알림 권한 미승인 (Android 13+) | "스캔 완료 알림을 받으려면 알림 권한이 필요합니다" | 권한 요청. 거부해도 앱 핵심 기능에 영향 없음 |

### 권한 거부 시 대체 모드

```
권한 거부 조합 → 사용 가능한 레이어

위치 O + 카메라 O = 3레이어 풀 스캔 (권장)
위치 X + 카메라 O = Layer 2 (렌즈) + Layer 3 (자기장)
위치 O + 카메라 X = Layer 1 (Wi-Fi) + Layer 3 (자기장)
위치 X + 카메라 X = Layer 3 (자기장) 만 사용
```

모든 조합에서 앱이 동작하며, 사용 가능한 레이어를 최대한 활용합니다. 단, 커버리지가 줄어드는 것을 사용자에게 명확히 안내합니다.

---

## C.5 에러 처리 구현 패턴

### sealed class 기반 결과 처리

```kotlin
sealed class ScanError {
    data class SensorError(val code: String, val message: String) : ScanError()
    data class NetworkError(val code: String, val message: String) : ScanError()
    data class PermissionError(val code: String, val requiredPermission: String) : ScanError()
}

// UseCase에서 결과 반환
sealed class ScanResult<out T> {
    data class Success<T>(val data: T) : ScanResult<T>()
    data class Failure(val error: ScanError) : ScanResult<Nothing>()
}
```

### ViewModel에서 에러 상태 처리

```kotlin
class ScanViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun handleError(error: ScanError) {
        _uiState.value = when (error) {
            is ScanError.SensorError -> ScanUiState.Error(
                code = error.code,
                message = error.message,
                canContinue = error.code != Constants.ErrorCode.E1003
            )
            is ScanError.PermissionError -> ScanUiState.PermissionRequired(
                code = error.code,
                permission = error.requiredPermission
            )
            is ScanError.NetworkError -> ScanUiState.Error(
                code = error.code,
                message = error.message,
                canContinue = true  // 네트워크 오류는 대체 모드로 계속
            )
        }
    }
}
```

---

## C.6 에러 코드 전체 요약표

| 코드 | 도메인 | 심각도 | 앱 계속 실행 여부 |
|------|--------|--------|---------------|
| E1001 | 센서 | 중간 | 가능 (EMF 레이어 비활성화) |
| E1002 | 센서 | 낮음 | 가능 (정확도 저하 안내) |
| E1003 | 센서 | 높음 | 카메라 레이어 불가 |
| E1004 | 센서 | 낮음 | 가능 (플래시 없이 진행) |
| E1005 | 센서 | 중간 | 재시도 후 가능 |
| E1010 | 센서 | 중간 | 가능 (EMF 레이어 재시도) |
| E1011 | 센서 | 낮음 | 가능 (캘리브레이션 재시도) |
| E2001 | 네트워크 | 높음 | Wi-Fi 레이어 불가 |
| E2002 | 네트워크 | 중간 | 가능 (mDNS 대체) |
| E2003 | 네트워크 | 낮음 | 가능 (부분 결과) |
| E2004 | 네트워크 | 낮음 | 가능 (타임아웃 기기 스킵) |
| E2005 | 네트워크 | 높음 | 가능 (포트 기반만) |
| E3001 | 권한 | 높음 | Wi-Fi 레이어 불가 |
| E3002 | 권한 | 높음 | 카메라 레이어 불가 |
| E3003 | 권한 | 낮음 | 가능 (알림만 불가) |
