package com.searcam.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat

/**
 * 런타임 권한 관리 헬퍼
 *
 * Android 10+(API 29)부터 위치 권한 없이 Wi-Fi 스캔이 불가능하다.
 * 이 클래스는 SearCam에서 필요한 모든 권한의 상태를 관리한다.
 *
 * 비유: 경비원처럼 각 기능을 사용하기 전에 '출입증(권한)' 확인.
 */
object PermissionHelper {

    /**
     * Wi-Fi 스캔에 필요한 권한 목록 (Layer 1)
     *
     * Android 12+(API 31)부터 NEARBY_WIFI_DEVICES도 필요하지만,
     * 위치 권한으로 대체 가능한 경우가 많다.
     */
    val wifiScanPermissions: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE
    )

    /**
     * 카메라 권한 목록 (Layer 2: 렌즈 + IR 탐지)
     */
    val cameraPermissions: Array<String> = arrayOf(
        Manifest.permission.CAMERA
    )

    /**
     * 알림 권한 (Android 13+에서만 필요)
     */
    val notificationPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    /**
     * Quick Scan 실행에 필요한 최소 권한 전체 목록
     */
    val allRequiredPermissions: Array<String>
        get() = (wifiScanPermissions + cameraPermissions + notificationPermissions).distinct().toTypedArray()

    /**
     * 특정 권한이 허용되었는지 확인
     *
     * @param context 애플리케이션 컨텍스트
     * @param permission 확인할 권한 (Manifest.permission.*)
     * @return true: 허용됨, false: 거부됨
     */
    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Wi-Fi 스캔 권한이 모두 허용되었는지 확인
     */
    fun isWifiScanGranted(context: Context): Boolean =
        wifiScanPermissions.all { isGranted(context, it) }

    /**
     * 카메라 권한이 허용되었는지 확인
     */
    fun isCameraGranted(context: Context): Boolean =
        cameraPermissions.all { isGranted(context, it) }

    /**
     * 현재 권한 상태를 PermissionStatus로 반환
     *
     * @param context 애플리케이션 컨텍스트
     * @return 허용된 기능 목록이 담긴 PermissionStatus
     */
    fun getStatus(context: Context): PermissionStatus = PermissionStatus(
        wifiGranted = isWifiScanGranted(context),
        cameraGranted = isCameraGranted(context),
        locationGranted = isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    )
}

/**
 * 권한 상태를 나타내는 불변 데이터 클래스
 *
 * @param wifiGranted Wi-Fi 스캔 권한 허용 여부 (Layer 1 활성화 조건)
 * @param cameraGranted 카메라 권한 허용 여부 (Layer 2 활성화 조건)
 * @param locationGranted 위치 권한 허용 여부 (Wi-Fi 스캔 필수 선행 조건)
 */
data class PermissionStatus(
    val wifiGranted: Boolean = false,
    val cameraGranted: Boolean = false,
    val locationGranted: Boolean = false
) {
    /**
     * Quick Scan 실행 가능 여부
     * Wi-Fi + 카메라 권한이 모두 필요
     */
    val canRunQuickScan: Boolean
        get() = wifiGranted && cameraGranted

    /**
     * Layer 3(EMF)는 SensorManager 접근으로 권한 불필요
     * 항상 true 반환
     */
    val emfAvailable: Boolean = true
}

/**
 * Compose에서 사용하는 다중 권한 요청 런처 헬퍼
 *
 * @param onResult 권한 요청 결과 콜백 (Map<권한명, 허용여부>)
 * @return ManagedActivityResultLauncher 인스턴스
 */
@Composable
fun rememberMultiplePermissionsLauncher(
    onResult: (Map<String, Boolean>) -> Unit
): ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>> =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = onResult
    )
