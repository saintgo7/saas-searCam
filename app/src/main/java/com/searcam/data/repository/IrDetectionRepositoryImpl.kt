package com.searcam.data.repository

import androidx.lifecycle.LifecycleOwner
import com.searcam.data.sensor.IrDetector
import com.searcam.domain.model.IrPoint
import com.searcam.domain.repository.IrDetectionRepository
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

/**
 * IrDetectionRepository 구현체
 *
 * IrDetector(CameraX 전면 카메라 IR 감지)에 위임하여
 * IR LED 패턴을 분석한다.
 */
class IrDetectionRepositoryImpl @Inject constructor(
    private val irDetector: IrDetector,
) : IrDetectionRepository {

    override fun observeIrPoints(): Flow<List<IrPoint>> = irDetector.irPoints

    override suspend fun startDetection(lifecycleOwner: LifecycleOwner): Result<Unit> {
        Timber.d("IR 감지 시작")
        return irDetector.startDetection(lifecycleOwner)
    }

    override suspend fun stopDetection(): Result<Unit> {
        Timber.d("IR 감지 중단")
        return irDetector.stopDetection()
    }
}
