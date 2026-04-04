package com.searcam.domain.usecase

import com.searcam.domain.model.DeviceType
import com.searcam.domain.model.DiscoveryMethod
import com.searcam.domain.model.LayerResult
import com.searcam.domain.model.LayerType
import com.searcam.domain.model.NetworkDevice
import com.searcam.domain.model.ScanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CalculateRiskUseCase 단위 테스트
 *
 * 가중치 합산 + 보정 계수 로직을 검증한다.
 *
 * 보정 계수:
 *   양성 레이어 0개 → × 1.0
 *   양성 레이어 1개 → × 0.7
 *   양성 레이어 2개 → × 1.2
 *   양성 레이어 3개 → × 1.5
 */
class CalculateRiskUseCaseTest {

    private lateinit var useCase: CalculateRiskUseCase

    @Before
    fun setUp() {
        useCase = CalculateRiskUseCase()
    }

    // ──────────────────────────────────────────────
    // 빈 결과
    // ──────────────────────────────────────────────

    @Test
    fun `레이어 결과가 비어 있으면 0 반환`() {
        val result = useCase(emptyMap())
        assertEquals(0, result)
    }

    @Test
    fun `완료된 레이어가 없으면 0 반환`() {
        val layerResults = mapOf(
            LayerType.WIFI to makeLayerResult(LayerType.WIFI, ScanStatus.FAILED, score = 80),
        )
        val result = useCase(layerResults)
        assertEquals(0, result)
    }

    // ──────────────────────────────────────────────
    // 보정 계수 테스트 (Wi-Fi 연결 기준)
    // ──────────────────────────────────────────────

    @Test
    fun `양성 레이어 없으면 보정 계수 1-0 적용`() {
        // Wi-Fi 완료 + score=0 → 양성 아님
        val layerResults = mapOf(
            LayerType.WIFI to makeLayerResult(LayerType.WIFI, ScanStatus.COMPLETED, score = 0),
        )
        val result = useCase(layerResults)
        assertEquals(0, result) // 0 * 0.5 * 1.0 = 0
    }

    @Test
    fun `양성 레이어 1개이면 보정 계수 0-7 적용`() {
        // Wi-Fi score=100 → 양성 1개
        // weighted = 100 * 0.5 = 50
        // corrected = 50 * 0.7 = 35
        val layerResults = mapOf(
            LayerType.WIFI to makeLayerResult(LayerType.WIFI, ScanStatus.COMPLETED, score = 100),
        )
        val result = useCase(layerResults)
        assertEquals(35, result)
    }

    @Test
    fun `양성 레이어 2개이면 보정 계수 1-2 적용`() {
        // Wi-Fi score=100, LENS score=100 → 양성 2개
        // weighted = 100 * 0.5 + 100 * 0.2 = 70
        // corrected = 70 * 1.2 = 84
        val layerResults = mapOf(
            LayerType.WIFI to makeLayerResult(LayerType.WIFI, ScanStatus.COMPLETED, score = 100),
            LayerType.LENS to makeLayerResult(LayerType.LENS, ScanStatus.COMPLETED, score = 100),
        )
        val result = useCase(layerResults)
        assertEquals(84, result)
    }

    @Test
    fun `양성 레이어 3개 이상이면 보정 계수 1-5 적용`() {
        // Wi-Fi=100, LENS=100, MAGNETIC=100 → 양성 3개
        // weighted = 100*0.5 + 100*0.2 + 100*0.15 = 85
        // corrected = 85 * 1.5 = 127.5 → clamp(100)
        val layerResults = mapOf(
            LayerType.WIFI to makeLayerResult(LayerType.WIFI, ScanStatus.COMPLETED, score = 100),
            LayerType.LENS to makeLayerResult(LayerType.LENS, ScanStatus.COMPLETED, score = 100),
            LayerType.MAGNETIC to makeLayerResult(LayerType.MAGNETIC, ScanStatus.COMPLETED, score = 100),
        )
        val result = useCase(layerResults)
        assertEquals(100, result) // 클램핑
    }

    // ──────────────────────────────────────────────
    // Wi-Fi 미연결 가중치
    // ──────────────────────────────────────────────

    @Test
    fun `Wi-Fi 미연결 시 weightNoWifi 사용`() {
        // Wi-Fi 없음 → LENS.weightNoWifi = 0.45 사용
        // LENS score=100 양성 1개
        // weighted = 100 * 0.45 = 45
        // corrected = 45 * 0.7 = 31
        val layerResults = mapOf(
            LayerType.LENS to makeLayerResult(LayerType.LENS, ScanStatus.COMPLETED, score = 100),
        )
        val result = useCase(layerResults)
        assertEquals(31, result)
    }

    // ──────────────────────────────────────────────
    // 점수 클램핑
    // ──────────────────────────────────────────────

    @Test
    fun `최종 점수는 0~100 범위로 제한`() {
        val layerResults = mapOf(
            LayerType.WIFI to makeLayerResult(LayerType.WIFI, ScanStatus.COMPLETED, score = 100),
            LayerType.LENS to makeLayerResult(LayerType.LENS, ScanStatus.COMPLETED, score = 100),
            LayerType.IR to makeLayerResult(LayerType.IR, ScanStatus.COMPLETED, score = 100),
            LayerType.MAGNETIC to makeLayerResult(LayerType.MAGNETIC, ScanStatus.COMPLETED, score = 100),
        )
        val result = useCase(layerResults)
        assertTrue("점수가 100 이하여야 함", result <= 100)
        assertTrue("점수가 0 이상이어야 함", result >= 0)
    }

    // ──────────────────────────────────────────────
    // invokeWithCorrection
    // ──────────────────────────────────────────────

    @Test
    fun `invokeWithCorrection은 빈 결과에서 (0, 1-0f) 반환`() {
        val (score, factor) = useCase.invokeWithCorrection(emptyMap())
        assertEquals(0, score)
        assertEquals(1.0f, factor, 0.001f)
    }

    @Test
    fun `invokeWithCorrection은 양성 1개에서 factor 0-7f 반환`() {
        val layerResults = mapOf(
            LayerType.WIFI to makeLayerResult(LayerType.WIFI, ScanStatus.COMPLETED, score = 100),
        )
        val (score, factor) = useCase.invokeWithCorrection(layerResults)
        assertEquals(35, score)
        assertEquals(0.7f, factor, 0.001f)
    }

    @Test
    fun `invokeWithCorrection은 양성 2개에서 factor 1-2f 반환`() {
        val layerResults = mapOf(
            LayerType.WIFI to makeLayerResult(LayerType.WIFI, ScanStatus.COMPLETED, score = 100),
            LayerType.LENS to makeLayerResult(LayerType.LENS, ScanStatus.COMPLETED, score = 100),
        )
        val (_, factor) = useCase.invokeWithCorrection(layerResults)
        assertEquals(1.2f, factor, 0.001f)
    }

    @Test
    fun `invokeWithCorrection은 양성 3개에서 factor 1-5f 반환`() {
        val layerResults = mapOf(
            LayerType.WIFI to makeLayerResult(LayerType.WIFI, ScanStatus.COMPLETED, score = 100),
            LayerType.LENS to makeLayerResult(LayerType.LENS, ScanStatus.COMPLETED, score = 100),
            LayerType.MAGNETIC to makeLayerResult(LayerType.MAGNETIC, ScanStatus.COMPLETED, score = 100),
        )
        val (_, factor) = useCase.invokeWithCorrection(layerResults)
        assertEquals(1.5f, factor, 0.001f)
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private fun makeLayerResult(
        layerType: LayerType,
        status: ScanStatus,
        score: Int,
    ) = LayerResult(
        layerType = layerType,
        status = status,
        score = score,
        devices = emptyList(),
        durationMs = 0L,
        findings = emptyList(),
    )
}
