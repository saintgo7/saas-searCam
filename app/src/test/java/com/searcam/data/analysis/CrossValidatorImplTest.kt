package com.searcam.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * CrossValidatorImpl 단위 테스트
 *
 * 가중 평균 방식으로 최종 위험도를 산출하는 로직을 검증한다.
 *
 * 정상 모드 (EMF 사용 가능):
 *   score = wifi * 0.50 + lens * 0.35 + emf * 0.15
 *
 * EMF 미지원 모드 (가중치 재분배):
 *   wifiAdj = 0.50 / (0.50 + 0.35) ≈ 0.5882
 *   lensAdj = 0.35 / (0.50 + 0.35) ≈ 0.4118
 */
class CrossValidatorImplTest {

    private lateinit var crossValidator: CrossValidatorImpl

    @Before
    fun setUp() {
        crossValidator = CrossValidatorImpl()
    }

    // ──────────────────────────────────────────────
    // 정상 모드 (EMF 사용 가능)
    // ──────────────────────────────────────────────

    @Test
    fun `모든 점수 0이면 결과는 0`() {
        val result = crossValidator.calculateRisk(
            wifiScore = 0,
            lensScore = 0,
            emfScore = 0,
            emfAvailable = true,
        )
        assertEquals(0, result)
    }

    @Test
    fun `모든 점수 100이면 결과는 100`() {
        val result = crossValidator.calculateRisk(
            wifiScore = 100,
            lensScore = 100,
            emfScore = 100,
            emfAvailable = true,
        )
        assertEquals(100, result)
    }

    @Test
    fun `Wi-Fi 점수만 100이면 결과는 50 근처`() {
        // 100 * 0.50 + 0 * 0.35 + 0 * 0.15 = 50
        val result = crossValidator.calculateRisk(
            wifiScore = 100,
            lensScore = 0,
            emfScore = 0,
            emfAvailable = true,
        )
        assertEquals(50, result)
    }

    @Test
    fun `렌즈 점수만 100이면 결과는 35`() {
        // 0 * 0.50 + 100 * 0.35 + 0 * 0.15 = 35
        val result = crossValidator.calculateRisk(
            wifiScore = 0,
            lensScore = 100,
            emfScore = 0,
            emfAvailable = true,
        )
        assertEquals(35, result)
    }

    @Test
    fun `EMF 점수만 100이면 결과는 15`() {
        // 0 * 0.50 + 0 * 0.35 + 100 * 0.15 = 15
        val result = crossValidator.calculateRisk(
            wifiScore = 0,
            lensScore = 0,
            emfScore = 100,
            emfAvailable = true,
        )
        assertEquals(15, result)
    }

    @Test
    fun `가중 평균 복합 점수를 정확히 계산한다`() {
        // 80 * 0.50 + 60 * 0.35 + 40 * 0.15 = 40 + 21 + 6 = 67
        val result = crossValidator.calculateRisk(
            wifiScore = 80,
            lensScore = 60,
            emfScore = 40,
            emfAvailable = true,
        )
        assertEquals(67, result)
    }

    // ──────────────────────────────────────────────
    // EMF 미지원 모드 (가중치 재분배)
    // ──────────────────────────────────────────────

    @Test
    fun `EMF 미지원 시 EMF 점수는 무시된다`() {
        val withEmf = crossValidator.calculateRisk(
            wifiScore = 80,
            lensScore = 60,
            emfScore = 100,   // 높은 EMF 점수
            emfAvailable = true,
        )
        val withoutEmf = crossValidator.calculateRisk(
            wifiScore = 80,
            lensScore = 60,
            emfScore = 100,   // 동일 점수지만 EMF 미지원
            emfAvailable = false,
        )

        // EMF 미지원 시 EMF 점수가 반영되지 않으므로 결과가 달라야 함
        assertTrue("EMF 유무에 따라 결과가 달라야 함", withEmf != withoutEmf)
    }

    @Test
    fun `EMF 미지원 시 모든 점수 0이면 결과는 0`() {
        val result = crossValidator.calculateRisk(
            wifiScore = 0,
            lensScore = 0,
            emfScore = 0,
            emfAvailable = false,
        )
        assertEquals(0, result)
    }

    @Test
    fun `EMF 미지원 시 Wi-Fi만 100이면 약 59`() {
        // wifiAdj = 0.50 / (0.50 + 0.35) = 0.5882...
        // 100 * 0.5882 ≈ 58.82 → 58 (int 변환)
        val result = crossValidator.calculateRisk(
            wifiScore = 100,
            lensScore = 0,
            emfScore = 0,
            emfAvailable = false,
        )
        // 0.50 / 0.85 * 100 ≈ 58 또는 59 (float 정밀도)
        assertTrue("Wi-Fi만 100점 EMF 미지원 시 결과는 58~59 범위", result in 58..59)
    }

    @Test
    fun `EMF 미지원 시 렌즈만 100이면 약 41`() {
        // lensAdj = 0.35 / (0.50 + 0.35) = 0.4118...
        // 100 * 0.4118 ≈ 41
        val result = crossValidator.calculateRisk(
            wifiScore = 0,
            lensScore = 100,
            emfScore = 0,
            emfAvailable = false,
        )
        assertTrue("렌즈만 100점 EMF 미지원 시 결과는 41~42 범위", result in 41..42)
    }

    @Test
    fun `결과는 항상 0~100 범위`() {
        val result = crossValidator.calculateRisk(
            wifiScore = 150, // 범위 초과 입력
            lensScore = 150,
            emfScore = 150,
            emfAvailable = true,
        )
        assertEquals(100, result)
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
