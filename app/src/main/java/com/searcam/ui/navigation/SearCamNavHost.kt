package com.searcam.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.searcam.ui.checklist.ChecklistScreen
import com.searcam.ui.checklist.ChecklistSelectScreen
import com.searcam.ui.home.HomeScreen
import com.searcam.ui.lens.IrCameraScreen
import com.searcam.ui.lens.LensFinderScreen
import com.searcam.ui.magnetic.MagneticScreen
import com.searcam.ui.onboarding.OnboardingScreen
import com.searcam.ui.report.ReportDetailScreen
import com.searcam.ui.report.ReportListScreen
import com.searcam.ui.scan.FullScanScreen
import com.searcam.ui.scan.QuickScanScreen
import com.searcam.ui.settings.SettingsScreen

/**
 * SearCam 전체 Navigation 그래프
 *
 * Single Activity Pattern에서 모든 화면 전환을 담당한다.
 * 각 화면은 Phase 2에서 실제 구현체로 교체되었다.
 *
 * 참고: ScanResult는 현재 reportId(String)를 기반으로 동작하며,
 * Screen.ScanResult의 scanId(Long)과의 불일치는 추후 통일 예정이다.
 *
 * @param modifier 외부에서 전달되는 레이아웃 수정자
 * @param navController Navigation 컨트롤러 (기본값: 자체 생성)
 * @param startDestination 시작 화면 라우트 (온보딩 여부에 따라 변경)
 */
@Composable
fun SearCamNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {

        // ── 온보딩 ──
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }

        // ── 홈 ──
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToQuickScan = { navController.navigate(Screen.QuickScan.route) },
                onNavigateToFullScan = { navController.navigate(Screen.FullScan.route) },
                onNavigateToLensFinder = { navController.navigate(Screen.LensFinder.route) },
                onNavigateToIrCamera = { navController.navigate(Screen.IrCamera.route) },
                onNavigateToMagnetic = { navController.navigate(Screen.Magnetic.route) },
                onNavigateToReport = { reportId ->
                    // reportId가 String UUID이므로 Long 변환 시 hashCode 사용 (임시)
                    navController.navigate(Screen.ReportDetail.createRoute(reportId.hashCode().toLong()))
                },
            )
        }

        // ── 빠른 스캔 ──
        composable(Screen.QuickScan.route) {
            QuickScanScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToResult = { reportId ->
                    navController.navigate(Screen.ReportDetail.createRoute(reportId.hashCode().toLong())) {
                        popUpTo(Screen.QuickScan.route) { inclusive = true }
                    }
                },
            )
        }

        // ── 정밀 스캔 ──
        composable(Screen.FullScan.route) {
            FullScanScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToResult = { reportId ->
                    navController.navigate(Screen.ReportDetail.createRoute(reportId.hashCode().toLong())) {
                        popUpTo(Screen.FullScan.route) { inclusive = true }
                    }
                },
            )
        }

        // ── 스캔 결과 (ScanResult — 현재 ReportDetail로 통합 운영) ──
        composable(
            route = Screen.ScanResult.route,
            arguments = listOf(navArgument("scanId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getLong("scanId") ?: -1L
            // ScanResultScreen은 ScanReport 객체가 필요하므로 ReportDetail로 위임
            ReportDetailScreen(
                reportId = scanId.toString(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── 렌즈 탐지 ──
        composable(Screen.LensFinder.route) {
            LensFinderScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── IR 카메라 ──
        composable(Screen.IrCamera.route) {
            IrCameraScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── 자기장 탐지 ──
        composable(Screen.Magnetic.route) {
            MagneticScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── 체크리스트 선택 ──
        composable(Screen.ChecklistSelect.route) {
            ChecklistSelectScreen(
                onNavigateToChecklist = { placeType ->
                    navController.navigate(Screen.Checklist.createRoute(placeType.name))
                },
            )
        }

        // ── 체크리스트 수행 ──
        composable(
            route = Screen.Checklist.route,
            arguments = listOf(navArgument("templateId") { type = NavType.StringType }),
        ) {
            ChecklistScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── 리포트 목록 ──
        composable(Screen.ReportList.route) {
            ReportListScreen(
                onNavigateToDetail = { reportId ->
                    navController.navigate(Screen.ReportDetail.createRoute(reportId.hashCode().toLong()))
                },
            )
        }

        // ── 리포트 상세 ──
        composable(
            route = Screen.ReportDetail.route,
            arguments = listOf(navArgument("reportId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getLong("reportId") ?: -1L
            ReportDetailScreen(
                reportId = reportId.toString(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── 설정 ──
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
