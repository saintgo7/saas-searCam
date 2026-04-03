package com.searcam.data.repository

import com.searcam.domain.model.IrPoint
import com.searcam.domain.repository.IrDetectionRepository
import com.searcam.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

/**
 * IrDetectionRepository 구현체
 *
 * 전면 카메라로 적외선 LED를 감지한다.
 * IR 필터가 약한 전면 카메라는 가시광선 차단 IR LED를 감지할 수 있다.
 *
 * Phase 1: 기본 골격만 구현. CameraX 이미지 분석은 Phase 2에서 구현.
 */
class IrDetectionRepositoryImpl @Inject constructor() : IrDetectionRepository {

    override fun observeIrPoints(): Flow<List<IrPoint>> = flow {
        // TODO Phase 2: CameraX ImageAnalysis로 IR 밝기 임계값 초과 포인트 감지
        Timber.d("IR 감지 Flow 구독 (Phase 1 스텁)")
        emit(emptyList())
    }

    override suspend fun startDetection(): Result<Unit> {
        // TODO Phase 2: CameraX 전면 카메라 바인딩
        Timber.d("IR 감지 시작 (Phase 1 스텁)")
        return Result.success(Unit)
    }

    override suspend fun stopDetection(): Result<Unit> {
        // TODO Phase 2: CameraX 바인딩 해제
        Timber.d("IR 감지 중단 (Phase 1 스텁)")
        return Result.success(Unit)
    }
}
