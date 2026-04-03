package com.searcam.data.repository

import androidx.lifecycle.LifecycleOwner
import com.searcam.data.sensor.LensDetector
import com.searcam.domain.model.RetroreflectionPoint
import com.searcam.domain.repository.LensDetectionRepository
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

/**
 * LensDetectionRepository 구현체
 *
 * LensDetector(CameraX + 플래시 Retroreflection)에 위임하여
 * 렌즈 반사 패턴을 분석한다.
 */
class LensDetectionRepositoryImpl @Inject constructor(
    private val lensDetector: LensDetector,
) : LensDetectionRepository {

    override fun observeRetroreflections(): Flow<List<RetroreflectionPoint>> =
        lensDetector.detectionPoints

    override suspend fun startDetection(lifecycleOwner: LifecycleOwner): Result<Unit> {
        Timber.d("렌즈 감지 시작")
        return lensDetector.startDetection(lifecycleOwner)
    }

    override suspend fun stopDetection(): Result<Unit> {
        Timber.d("렌즈 감지 중단")
        return lensDetector.stopDetection()
    }
}
