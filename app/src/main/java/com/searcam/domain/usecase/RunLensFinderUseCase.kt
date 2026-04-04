package com.searcam.domain.usecase

import javax.inject.Inject

import com.searcam.domain.model.RetroreflectionPoint
import com.searcam.domain.repository.IrDetectionRepository
import com.searcam.domain.repository.LensDetectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 렌즈 찾기(Lens Finder) UseCase
 *
 * Retroreflection(역반사) + IR LED 감지를 통합하여 실시간으로 의심 포인트를 반환한다.
 * 사용자가 수동으로 종료할 때까지 무한히 동작하는 수동 모드다.
 *
 * 흐름:
 *   1. LensDetectionRepository — 후면 카메라 + 플래시, Retroreflection 감지
 *   2. IrDetectionRepository — 전면 카메라, IR LED 감지
 *   3. 두 Flow를 병합하여 Retroreflection 포인트 목록으로 emit
 *
 * 주의: 이 UseCase는 시작(startDetection) 및 종료(stopDetection)를 호출하지 않는다.
 *       LensViewModel이 생명주기에 맞춰 직접 관리해야 한다.
 */
class RunLensFinderUseCase @Inject constructor(
    private val lensDetectionRepository: LensDetectionRepository,
    private val irDetectionRepository: IrDetectionRepository,
) {

    /**
     * Retroreflection 의심 포인트를 실시간으로 emit하는 Flow를 반환한다.
     *
     * LensDetectionRepository의 observeRetroreflections()와
     * IrDetectionRepository의 observeIrPoints()를 통합한다.
     * IR 포인트는 현재 RetroreflectionPoint 형식으로 변환하여 함께 emit한다.
     *
     * @return List<RetroreflectionPoint>를 실시간으로 emit하는 Flow
     */
    operator fun invoke(): Flow<List<RetroreflectionPoint>> {
        val retroFlow = lensDetectionRepository.observeRetroreflections()
        val irFlow = irDetectionRepository.observeIrPoints()

        // 두 Flow를 결합하여 통합 포인트 목록 생성
        return retroFlow.combine(irFlow) { retroPoints, irPoints ->
            val irAsRetroPoints = irPoints.map { irPoint ->
                // IR 포인트를 RetroreflectionPoint 형식으로 변환
                // flashDependency = false — IR은 플래시 없이도 감지됨
                RetroreflectionPoint(
                    x = irPoint.x,
                    y = irPoint.y,
                    radius = IR_POINT_DEFAULT_RADIUS,
                    brightness = irPoint.intensity,
                    circularity = IR_POINT_DEFAULT_CIRCULARITY,
                    isStable = irPoint.isStable,
                    flashDependency = false,
                    riskScore = irPoint.riskScore,
                    detectedAt = irPoint.timestamp,
                )
            }
            retroPoints + irAsRetroPoints
        }
    }

    companion object {
        /** IR 포인트를 RetroreflectionPoint로 변환 시 기본 반지름 (pixel) */
        private const val IR_POINT_DEFAULT_RADIUS = 3.0f

        /** IR 포인트의 기본 원형도 — 불규칙한 형태로 가정 */
        private const val IR_POINT_DEFAULT_CIRCULARITY = 0.5f
    }
}
