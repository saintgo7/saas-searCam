package com.searcam.data.repository

import com.searcam.domain.model.RetroreflectionPoint
import com.searcam.domain.repository.LensDetectionRepository
import com.searcam.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

/**
 * LensDetectionRepository 구현체
 *
 * 후면 카메라 + 플래시 Retroreflection으로 렌즈 반사 패턴을 분석한다.
 * 렌즈 표면에 플래시 빛이 맞으면 강한 역반사(Retroreflection) 패턴이 나타난다.
 *
 * Phase 1: 기본 골격만 구현. CameraX 플래시 제어는 Phase 2에서 구현.
 */
class LensDetectionRepositoryImpl @Inject constructor() : LensDetectionRepository {

    override fun observeRetroreflections(): Flow<List<RetroreflectionPoint>> = flow {
        // TODO Phase 2: CameraX + 플래시로 Retroreflection 포인트 감지
        Timber.d("Retroreflection 감지 Flow 구독 (Phase 1 스텁)")
        emit(emptyList())
    }

    override suspend fun startDetection(): Result<Unit> {
        // TODO Phase 2: 후면 카메라 바인딩 + 플래시 TORCH 모드 활성화
        Timber.d("렌즈 감지 시작 (Phase 1 스텁)")
        return Result.success(Unit)
    }

    override suspend fun stopDetection(): Result<Unit> {
        // TODO Phase 2: 플래시 끄기 + CameraX 바인딩 해제
        Timber.d("렌즈 감지 중단 (Phase 1 스텁)")
        return Result.success(Unit)
    }
}
