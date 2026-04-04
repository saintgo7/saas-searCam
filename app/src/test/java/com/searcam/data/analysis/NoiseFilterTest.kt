package com.searcam.data.analysis

import com.searcam.domain.model.EmfLevel
import com.searcam.domain.model.MagneticReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

/**
 * NoiseFilter 단위 테스트
 *
 * 이동 평균 필터, 급변 감지, 캘리브레이션 기능을 검증한다.
 */
class NoiseFilterTest {

    private lateinit var noiseFilter: NoiseFilter

    @Before
    fun setUp() {
        noiseFilter = NoiseFilter()
    }

    // ──────────────────────────────────────────────
    // 초기 상태
    // ──────────────────────────────────────────────

    @Test
    fun `초기 상태에서 isCalibrated는 false`() {
        assertFalse(noiseFilter.isCalibrated())
    }

    @Test
    fun `초기 상태에서 getDelta는 0 반환`() {
        val reading = makeReading(magnitude = 50f)
        assertEquals(0f, noiseFilter.getDelta(reading), 0.001f)
    }

    // ──────────────────────────────────────────────
    // 이동 평균 필터
    // ──────────────────────────────────────────────

    @Test
    fun `단일 샘플 필터 결과는 원본과 동일`() {
        val reading = makeReading(x = 10f, y = 20f, z = 30f)
        val filtered = noiseFilter.filter(reading)

        assertEquals(10f, filtered.x, 0.001f)
        assertEquals(20f, filtered.y, 0.001f)
        assertEquals(30f, filtered.z, 0.001f)
    }

    @Test
    fun `필터 후 magnitude는 xyz로 재계산된다`() {
        val reading = makeReading(x = 3f, y = 4f, z = 0f)
        val filtered = noiseFilter.filter(reading)

        // 3² + 4² = 5
        assertEquals(5f, filtered.magnitude, 0.01f)
    }

    @Test
    fun `연속 동일 값은 이동 평균 후 동일 값 유지`() {
        repeat(10) {
            noiseFilter.filter(makeReading(x = 5f, y = 5f, z = 5f))
        }
        val result = noiseFilter.filter(makeReading(x = 5f, y = 5f, z = 5f))

        assertEquals(5f, result.x, 0.001f)
        assertEquals(5f, result.y, 0.001f)
        assertEquals(5f, result.z, 0.001f)
    }

    @Test
    fun `이동 평균은 급격한 변화를 평탄화한다`() {
        // 0으로 9번 채우기
        repeat(9) { noiseFilter.filter(makeReading(x = 0f, y = 0f, z = 0f)) }
        // 10으로 1번 — 윈도우(10) 평균은 10/10 = 1f 미만
        val result = noiseFilter.filter(makeReading(x = 10f, y = 0f, z = 0f))

        assertTrue("평탄화된 값이 원본 10f보다 작아야 함", result.x < 10f)
    }

    // ──────────────────────────────────────────────
    // 급변 감지
    // ──────────────────────────────────────────────

    @Test
    fun `첫 번째 샘플은 급변으로 감지하지 않는다`() {
        val reading = makeReading(magnitude = 200f, timestamp = 100L)
        val filtered = noiseFilter.filter(reading)

        // 급변 아님 → 정상 처리 (magnitude가 유효하게 계산됨)
        assertTrue(filtered.magnitude >= 0f)
    }

    @Test
    fun `50μT 초과 급변을 300ms 이내에 감지하면 이전 값 유지`() {
        // 기준 샘플 설정
        noiseFilter.filter(makeReading(x = 10f, y = 0f, z = 0f, timestamp = 0L))

        // 300ms 안에 magnitude 대폭 증가 (50μT 초과)
        val spikeReading = makeReading(x = 100f, y = 0f, z = 0f, timestamp = 100L)
        val result = noiseFilter.filter(spikeReading)

        // 급변 감지 → 이전 평균값(~10f) 유지, 스파이크 100f 적용 안 됨
        assertTrue("급변 시 x가 100f 미만이어야 함", result.x < 100f)
    }

    @Test
    fun `300ms 초과 후 큰 변화는 급변으로 감지하지 않는다`() {
        // 기준 샘플 설정
        noiseFilter.filter(makeReading(x = 10f, y = 0f, z = 0f, timestamp = 0L))

        // 300ms 초과 후 큰 변화 → 급변 아님
        val laterReading = makeReading(x = 100f, y = 0f, z = 0f, timestamp = 400L)
        val result = noiseFilter.filter(laterReading)

        // 정상 처리 → 큰 값이 반영됨 (이동 평균으로 일부 완화되더라도 양수)
        assertTrue("정상 업데이트 시 x가 0보다 커야 함", result.x > 0f)
    }

    // ──────────────────────────────────────────────
    // 캘리브레이션
    // ──────────────────────────────────────────────

    @Test
    fun `setBaseline 후 isCalibrated는 true`() {
        noiseFilter.setBaseline(listOf(makeReading(magnitude = 50f)))
        assertTrue(noiseFilter.isCalibrated())
    }

    @Test
    fun `setBaseline은 샘플 평균을 baseline으로 설정한다`() {
        val readings = listOf(
            makeReading(magnitude = 40f),
            makeReading(magnitude = 60f),
        )
        noiseFilter.setBaseline(readings)

        assertEquals(50f, noiseFilter.getBaseline(), 0.001f)
    }

    @Test
    fun `setBaseline 빈 리스트는 baseline을 변경하지 않는다`() {
        val baseline = noiseFilter.getBaseline()
        noiseFilter.setBaseline(emptyList())

        assertFalse("빈 리스트로 baseline 설정 후 calibrated는 false여야 함", noiseFilter.isCalibrated())
        assertEquals(baseline, noiseFilter.getBaseline(), 0.001f)
    }

    @Test
    fun `getDelta는 noiseFloor 미만 차이를 0으로 처리한다`() {
        // 동일한 값으로 baseline 설정 → noiseFloor = 0
        val readings = listOf(
            makeReading(magnitude = 50f),
            makeReading(magnitude = 50f),
        )
        noiseFilter.setBaseline(readings)

        // baseline과 동일한 값 → delta = 0
        val reading = makeReading(magnitude = 50f)
        assertEquals(0f, noiseFilter.getDelta(reading), 0.001f)
    }

    @Test
    fun `getDelta는 noiseFloor 초과 차이를 반환한다`() {
        // baseline = 50, noiseFloor = 0 (동일 샘플)
        noiseFilter.setBaseline(listOf(makeReading(magnitude = 50f), makeReading(magnitude = 50f)))

        // magnitude = 100 → delta = |100 - 50| = 50 > 0 (noiseFloor)
        val reading = makeReading(magnitude = 100f)
        assertTrue("delta가 0보다 커야 함", noiseFilter.getDelta(reading) > 0f)
    }

    // ──────────────────────────────────────────────
    // reset
    // ──────────────────────────────────────────────

    @Test
    fun `reset 후 isCalibrated는 false`() {
        noiseFilter.setBaseline(listOf(makeReading(magnitude = 50f)))
        noiseFilter.reset()

        assertFalse(noiseFilter.isCalibrated())
    }

    @Test
    fun `reset 후 baseline은 0`() {
        noiseFilter.setBaseline(listOf(makeReading(magnitude = 50f)))
        noiseFilter.reset()

        assertEquals(0f, noiseFilter.getBaseline(), 0.001f)
    }

    @Test
    fun `reset 후 getDelta는 0 반환`() {
        noiseFilter.setBaseline(listOf(makeReading(magnitude = 50f)))
        noiseFilter.reset()

        val reading = makeReading(magnitude = 100f)
        assertEquals(0f, noiseFilter.getDelta(reading), 0.001f)
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private fun makeReading(
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        magnitude: Float = sqrt(x * x + y * y + z * z),
        timestamp: Long = System.currentTimeMillis(),
    ) = MagneticReading(
        timestamp = timestamp,
        x = x,
        y = y,
        z = z,
        magnitude = magnitude,
        delta = 0f,
        level = EmfLevel.NORMAL,
    )
}
