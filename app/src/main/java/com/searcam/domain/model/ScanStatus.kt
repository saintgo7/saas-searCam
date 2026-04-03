package com.searcam.domain.model

/**
 * 탐지 레이어 실행 상태 열거형
 *
 * 각 레이어의 진행 상태를 나타낸다.
 * LayerResult.status 필드에서 사용된다.
 */
enum class ScanStatus {
    /** 대기 중 — 아직 시작되지 않은 레이어 */
    PENDING,

    /** 실행 중 — 현재 분석 진행 중 */
    RUNNING,

    /** 완료 — 분석 결과 있음 */
    COMPLETED,

    /** 건너뜀 — 조건 미충족으로 해당 레이어 비활성화 (예: Wi-Fi 미연결 시 WIFI 레이어) */
    SKIPPED,

    /** 실패 — 오류 발생으로 결과 없음 */
    FAILED,
}
