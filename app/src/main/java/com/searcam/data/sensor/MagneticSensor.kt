package com.searcam.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.searcam.domain.model.EmfLevel
import com.searcam.domain.model.MagneticReading
import com.searcam.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Android SensorManager를 통해 TYPE_MAGNETIC_FIELD 센서를 구독하는 클래스
 *
 * 20Hz(50ms 간격)로 x, y, z 3축 자기장 측정값을 수집하고
 * magnitude를 계산하여 StateFlow로 방출한다.
 *
 * 에러 코드:
 *   E1001 — 자력계(Magnetometer) 미지원 기기
 *   E1002 — 센서 리스너 등록 실패
 */
class MagneticSensor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // 자력계 센서 (null이면 기기 미지원)
    private val magneticSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    // 최신 측정값 보관 (getCurrentReading()에서 사용)
    private val _currentReading = MutableStateFlow(createZeroReading())
    val currentReading: StateFlow<MagneticReading> = _currentReading.asStateFlow()

    // 현재 리스너 참조 (stopListening 시 해제)
    private var activeListener: SensorEventListener? = null

    /**
     * 자기장 측정값 Flow를 반환한다.
     *
     * 구독 시 SensorEventListener를 등록하고,
     * 구독 취소 시 자동으로 리스너를 해제한다.
     * SensorManager 콜백은 Main 스레드에서 수신되며,
     * magnitude 계산만 수행 후 즉시 emit한다.
     *
     * @return 20Hz로 MagneticReading을 emit하는 Flow
     * @throws IllegalStateException E1001: 자력계 미지원 기기
     * @throws IllegalStateException E1002: 센서 등록 실패
     */
    fun flow(): Flow<MagneticReading> = callbackFlow {
        // E1001: 자력계 미지원 기기 확인
        val sensor = magneticSensor ?: run {
            Timber.e("[${Constants.ErrorCode.E1001}] 자력계 센서를 지원하지 않는 기기입니다.")
            close(IllegalStateException("[${Constants.ErrorCode.E1001}] 자력계 센서 미지원"))
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val reading = buildReading(event)
                _currentReading.value = reading
                trySend(reading)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                Timber.d("자력계 정확도 변경: $accuracy")
            }
        }

        // 50,000μs(20Hz)로 등록하여 불필요한 CPU 소비 방지
        // SensorManager 콜백은 반드시 Main 스레드 컨텍스트에서 등록해야 함
        val registered = sensorManager.registerListener(
            listener,
            sensor,
            SENSOR_DELAY_20HZ_US,
        )

        if (!registered) {
            // E1002: 센서 리스너 등록 실패
            Timber.e("[${Constants.ErrorCode.E1002}] 자력계 센서 리스너 등록에 실패했습니다.")
            close(IllegalStateException("[${Constants.ErrorCode.E1002}] 센서 리스너 등록 실패"))
            return@callbackFlow
        }

        activeListener = listener
        Timber.d("자력계 센서 리스너 등록 완료 (20Hz 목표)")

        // Flow 구독 취소 시 리스너 자동 해제
        awaitClose {
            sensorManager.unregisterListener(listener)
            activeListener = null
            Timber.d("자력계 센서 리스너 해제 완료")
        }
    }

    /**
     * 자기장 센서 리스닝을 시작한다.
     *
     * flow()를 통해 이미 구독하고 있는 경우에는 별도로 호출할 필요가 없다.
     * 단발성 제어가 필요한 경우(예: 수동 수명 관리)에 사용한다.
     *
     * @return 등록 성공 여부
     */
    fun startListening(listener: SensorEventListener): Boolean {
        val sensor = magneticSensor ?: run {
            Timber.e("[${Constants.ErrorCode.E1001}] 자력계 미지원 기기 — startListening 실패")
            return false
        }

        val registered = sensorManager.registerListener(
            listener,
            sensor,
            SENSOR_DELAY_20HZ_US,
        )

        if (!registered) {
            Timber.e("[${Constants.ErrorCode.E1002}] 센서 리스너 등록 실패 — startListening")
        } else {
            activeListener = listener
            Timber.d("자력계 센서 수동 리스닝 시작")
        }

        return registered
    }

    /**
     * 자기장 센서 리스닝을 중단한다.
     *
     * @param listener 해제할 SensorEventListener
     */
    fun stopListening(listener: SensorEventListener) {
        sensorManager.unregisterListener(listener)
        if (activeListener === listener) activeListener = null
        Timber.d("자력계 센서 수동 리스닝 중단")
    }

    /**
     * 자력계 센서 탑재 여부를 반환한다.
     */
    fun isAvailable(): Boolean = magneticSensor != null

    // ────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────

    /**
     * SensorEvent로부터 MagneticReading을 생성한다.
     *
     * 초기 상태에서는 delta=0, level=NORMAL로 설정한다.
     * NoiseFilter와 MagneticRepositoryImpl에서 delta/level이 보정된다.
     */
    private fun buildReading(event: SensorEvent): MagneticReading {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        return MagneticReading(
            timestamp = System.currentTimeMillis(),
            x = x,
            y = y,
            z = z,
            magnitude = magnitude,
            delta = 0f,          // Repository에서 캘리브레이션 후 계산
            level = EmfLevel.NORMAL,
        )
    }

    /**
     * 초기 기본값 MagneticReading을 생성한다.
     */
    private fun createZeroReading(): MagneticReading = MagneticReading(
        timestamp = System.currentTimeMillis(),
        x = 0f,
        y = 0f,
        z = 0f,
        magnitude = 0f,
        delta = 0f,
        level = EmfLevel.NORMAL,
    )

    companion object {
        /** 20Hz 샘플링 = 50ms 간격 = 50,000μs */
        private const val SENSOR_DELAY_20HZ_US = 50_000
    }
}
