package com.searcam.ui.navigation

/**
 * 앱 내 모든 화면의 라우트 정의
 *
 * sealed class를 사용해 컴파일 타임에 유효하지 않은 라우트를 방지한다.
 * NavHost에서 when 식으로 모든 케이스를 강제 처리할 수 있다.
 */
sealed class Screen(val route: String) {

    // ── 온보딩 ──
    data object Onboarding : Screen("onboarding")

    // ── 홈 ──
    data object Home : Screen("home")

    // ── 스캔 ──
    data object QuickScan : Screen("scan/quick")
    data object FullScan : Screen("scan/full")
    data object ScanResult : Screen("scan/result/{scanId}") {
        fun createRoute(scanId: Long) = "scan/result/$scanId"
    }

    // ── 렌즈 탐지 ──
    data object LensFinder : Screen("lens/finder")
    data object IrCamera : Screen("lens/ir")

    // ── 자기장 탐지 ──
    data object Magnetic : Screen("magnetic")

    // ── 체크리스트 ──
    data object ChecklistSelect : Screen("checklist/select")
    data object Checklist : Screen("checklist/{templateId}") {
        fun createRoute(templateId: String) = "checklist/$templateId"
    }

    // ── 리포트 ──
    data object ReportList : Screen("report/list")
    data object ReportDetail : Screen("report/detail/{reportId}") {
        fun createRoute(reportId: Long) = "report/detail/$reportId"
    }

    // ── 설정 ──
    data object Settings : Screen("settings")
}
