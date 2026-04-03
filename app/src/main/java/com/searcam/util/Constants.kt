package com.searcam.util

/**
 * 앱 전역 상수 정의
 *
 * 하드코딩 금지 원칙: 모든 매직 넘버와 문자열은 이 파일에 정의한다.
 * 변경이 필요한 경우 이 파일만 수정하면 전체에 반영된다.
 */
object Constants {

    // ──────────────────────────────────────────────
    // Wi-Fi / 네트워크 스캔
    // ──────────────────────────────────────────────

    /**
     * 몰래카메라 스트리밍에 자주 사용되는 포트 목록
     *
     * RTSP(554), HTTP(80/8080/8888), WS-Discovery(3702),
     * RTMP(1935), HTTPS(443/8443)
     */
    val CAMERA_PORTS = listOf(554, 80, 8080, 8888, 3702, 1935, 443, 8443)

    /** 포트 스캔 단일 연결 타임아웃 (밀리초) */
    const val PORT_SCAN_TIMEOUT_MS = 500L

    /** ARP 테이블 파일 경로 */
    const val ARP_TABLE_PATH = "/proc/net/arp"

    /** mDNS 서비스 탐색 타임아웃 (밀리초) */
    const val MDNS_DISCOVERY_TIMEOUT_MS = 5_000L

    // ──────────────────────────────────────────────
    // 탐지 레이어 가중치
    // ──────────────────────────────────────────────

    /** Layer 1 Wi-Fi 스캔 가중치 (50%) */
    const val WIFI_WEIGHT = 0.50f

    /** Layer 2 렌즈 감지 가중치 (35%) */
    const val LENS_WEIGHT = 0.35f

    /** Layer 3 EMF(자기장) 감지 가중치 (15%) */
    const val EMF_WEIGHT = 0.15f

    // ──────────────────────────────────────────────
    // 스캔 타임아웃
    // ──────────────────────────────────────────────

    /** Quick Scan 최대 시간 (30초) */
    const val QUICK_SCAN_TIMEOUT_MS = 30_000L

    /** Full Scan 최대 시간 (120초) */
    const val FULL_SCAN_TIMEOUT_MS = 120_000L

    // ──────────────────────────────────────────────
    // EMF(자기장) 센서 설정
    // ──────────────────────────────────────────────

    /** EMF 샘플링 주파수 (20Hz = 50ms 간격) */
    const val EMF_POLLING_HZ = 20

    /** EMF 이동 평균 윈도우 크기 (노이즈 필터) */
    const val EMF_MOVING_AVG_WINDOW = 10

    /** EMF 이상 임계값 (마이크로테슬라) - 이 값 이상이면 의심 */
    const val EMF_ANOMALY_THRESHOLD_UT = 50.0f

    /** EMF 베이스라인 측정 샘플 수 */
    const val EMF_BASELINE_SAMPLES = 30

    // ──────────────────────────────────────────────
    // 위험도 임계값 (RiskLevel 5단계와 동기화)
    // RiskLevel.kt 기준: SAFE(0~20), INTEREST(21~40),
    //   CAUTION(41~60), DANGER(61~80), CRITICAL(81~100)
    // ──────────────────────────────────────────────

    /** SAFE 최대 점수 (0~20) */
    const val RISK_SAFE_MAX = 20

    /** INTEREST 최대 점수 (21~40) */
    const val RISK_INTEREST_MAX = 40

    /** CAUTION 최대 점수 (41~60) */
    const val RISK_CAUTION_MAX = 60

    /** DANGER 최대 점수 (61~80) */
    const val RISK_DANGER_MAX = 80

    /** CRITICAL 최소 점수 (81~100) */
    const val RISK_CRITICAL_MIN = 81

    // ──────────────────────────────────────────────
    // 카메라 설정
    // ──────────────────────────────────────────────

    /** 렌즈 감지 분석 해상도 너비 (픽셀) */
    const val CAMERA_ANALYSIS_WIDTH = 1280

    /** 렌즈 감지 분석 해상도 높이 (픽셀) */
    const val CAMERA_ANALYSIS_HEIGHT = 720

    /** Retroreflection 분석 프레임 수 (평균화) */
    const val RETROREFLECTION_FRAME_COUNT = 10

    /** 렌즈 의심 반사 강도 임계값 (0~255) */
    const val RETROREFLECTION_THRESHOLD = 200

    // ──────────────────────────────────────────────
    // OUI 데이터베이스
    // ──────────────────────────────────────────────

    /** assets 폴더 내 OUI JSON 파일 경로 */
    const val OUI_JSON_ASSET_PATH = "oui.json"

    // ──────────────────────────────────────────────
    // 에러 코드 체계
    // ──────────────────────────────────────────────

    object ErrorCode {
        // E1xxx: 센서 오류
        /** 자력계(Magnetometer) 미지원 기기 */
        const val E1001 = "E1001"

        /** 자력계 정확도 불량 (ACCURACY_LOW) */
        const val E1002 = "E1002"

        /** 카메라 초기화 실패 */
        const val E1003 = "E1003"

        /** 플래시(토치) 미지원 기기 */
        const val E1004 = "E1004"

        /** 센서 샘플링 타임아웃 */
        const val E1005 = "E1005"

        // E2xxx: 네트워크 오류
        /** Wi-Fi 미연결 상태 */
        const val E2001 = "E2001"

        /** ARP 테이블 읽기 실패 */
        const val E2002 = "E2002"

        /** mDNS 서비스 탐색 실패 */
        const val E2003 = "E2003"

        /** 포트 스캔 타임아웃 */
        const val E2004 = "E2004"

        /** OUI 데이터베이스 로드 실패 */
        const val E2005 = "E2005"

        // E3xxx: 권한 오류
        /** 위치 권한 미승인 (Wi-Fi 스캔 불가) */
        const val E3001 = "E3001"

        /** 카메라 권한 미승인 */
        const val E3002 = "E3002"

        /** 알림 권한 미승인 (Android 13+) */
        const val E3003 = "E3003"
    }

    // ──────────────────────────────────────────────
    // SharedPreferences 키
    // ──────────────────────────────────────────────

    object PrefKey {
        /** 온보딩 완료 여부 */
        const val ONBOARDING_COMPLETED = "onboarding_completed"

        /** 경고음 활성화 여부 */
        const val SOUND_ENABLED = "sound_enabled"

        /** 진동 활성화 여부 */
        const val VIBRATION_ENABLED = "vibration_enabled"

        /** 마지막 스캔 타임스탬프 */
        const val LAST_SCAN_TIMESTAMP = "last_scan_timestamp"
    }
}
