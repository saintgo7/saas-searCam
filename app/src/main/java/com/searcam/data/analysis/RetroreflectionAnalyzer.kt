package com.searcam.data.analysis

import androidx.camera.core.ImageProxy
import com.searcam.domain.model.RetroreflectionPoint
import timber.log.Timber
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import javax.inject.Inject

/**
 * Retroreflection(역반사) 기반 렌즈 감지 분석기
 *
 * 5단계 파이프라인으로 카메라 프레임에서 렌즈 반사 포인트를 추출한다.
 * 마트폰 플래시를 광원으로 사용하고, 렌즈 표면의 역반사 패턴을 분석한다.
 *
 * 파이프라인:
 *   1. 전처리: 720p 다운스케일 + 그레이스케일 변환
 *   2. 고휘도 추출: 적응형 임계값 초과 픽셀 클러스터링
 *   3. 원형도 검사: circularity = 4π×area/perimeter² > 0.8
 *   4. 안정성 추적: 연속 5프레임 동일 위치(±10px) 유지 여부
 *   5. 플래시 의존성: 플래시 OFF 시 사라지면 역반사 확정
 *
 * 보안 원칙: 카메라 프레임 데이터는 메모리에서만 처리하며 저장하지 않는다.
 */
class RetroreflectionAnalyzer @Inject constructor() {

    // ─────────────────────────────────────────────────────────
    // 상수
    // ─────────────────────────────────────────────────────────

    /** 목표 분석 너비 (픽셀) — 성능과 정확도 균형 */
    private val TARGET_WIDTH = 720

    /** 고휘도 고정 임계값 기반값 */
    private val BASE_THRESHOLD = 240

    /** 평균 밝기 배율 (적응형 임계값 계산) */
    private val BRIGHTNESS_MULTIPLIER = 3.0f

    /** 원형도 최소 임계값 — 이 값 이상이어야 렌즈 후보 */
    private val CIRCULARITY_MIN = 0.8f

    /** 대비 비율 최소 임계값 (주변 대비) */
    private val CONTRAST_RATIO_MIN = 5.0f

    /** 클러스터 최소 면적 (픽셀²) */
    private val CLUSTER_AREA_MIN = 1.0f

    /** 클러스터 최대 면적 (픽셀²) — 대형 반사 제외 */
    private val CLUSTER_AREA_MAX = 100.0f

    /** 안정성 추적 윈도우 크기 (프레임 수) */
    private val STABILITY_FRAMES = 5

    /** 안정성 판정 위치 허용 오차 (픽셀) */
    private val STABILITY_TOLERANCE_PX = 10

    /** 대비 측정 주변 반경 (픽셀) */
    private val SURROUND_RADIUS = 20

    /** 플래시 OFF 판정 밝기 비율 임계값 */
    private val FLASH_OFF_RATIO = 0.3f

    // ─────────────────────────────────────────────────────────
    // 내부 상태 — 안정성 추적
    // ─────────────────────────────────────────────────────────

    /** 포인트 위치별 연속 감지 프레임 카운터 (위치 키 → 카운터) */
    private val stabilityTracker: MutableMap<String, TrackedPoint> = mutableMapOf()

    // ─────────────────────────────────────────────────────────
    // 공개 API
    // ─────────────────────────────────────────────────────────

    /**
     * 단일 카메라 프레임을 분석하여 Retroreflection 포인트 목록을 반환한다.
     *
     * 불변성 원칙: 결과는 항상 새 List를 반환하며 입력 프레임을 수정하지 않는다.
     *
     * @param frame CameraX ImageAnalysis에서 수신한 프레임 (YUV_420_888)
     * @param isFlashOn 현재 플래시 상태 (true = 켜짐)
     * @return 감지된 Retroreflection 포인트 목록 (위험도 순 정렬)
     */
    fun analyze(frame: ImageProxy, isFlashOn: Boolean): List<RetroreflectionPoint> {
        return try {
            val grayPixels = extractGrayscale(frame)
            val width = frame.width
            val height = frame.height

            val threshold = calculateThreshold(grayPixels)
            val candidates = extractHighBrightnessClusters(grayPixels, width, height, threshold)
            val filtered = candidates.filter { cluster ->
                passesCircularityCheck(cluster) && passesContrastCheck(cluster, grayPixels, width, height)
            }

            updateStabilityTracker(filtered, isFlashOn)

            buildResult(isFlashOn)
        } catch (e: Exception) {
            Timber.e(e, "[E1003] Retroreflection 분석 중 오류 발생")
            emptyList()
        }
    }

    /**
     * 안정성 추적 상태를 초기화한다.
     * 새로운 감지 세션 시작 시 호출해야 한다.
     */
    fun reset() {
        stabilityTracker.clear()
        Timber.d("RetroreflectionAnalyzer 상태 초기화 완료")
    }

    // ─────────────────────────────────────────────────────────
    // STEP 1: 전처리
    // ─────────────────────────────────────────────────────────

    /**
     * ImageProxy에서 그레이스케일 픽셀 배열을 추출한다.
     *
     * YUV_420_888 포맷에서 Y 플레인만 사용하여 그레이스케일을 얻는다.
     * 분석 후 버퍼 참조를 해제하여 메모리 누수를 방지한다.
     *
     * @param frame CameraX 프레임 (YUV_420_888)
     * @return 0~255 범위의 그레이스케일 픽셀 배열
     */
    private fun extractGrayscale(frame: ImageProxy): ByteArray {
        val yPlane = frame.planes[0]
        val buffer: ByteBuffer = yPlane.buffer
        val pixelStride = yPlane.pixelStride
        val rowStride = yPlane.rowStride

        val width = frame.width
        val height = frame.height
        val result = ByteArray(width * height)

        for (row in 0 until height) {
            for (col in 0 until width) {
                val bufferIndex = row * rowStride + col * pixelStride
                result[row * width + col] = buffer[bufferIndex]
            }
        }
        return result
    }

    // ─────────────────────────────────────────────────────────
    // STEP 2: 고휘도 클러스터 추출
    // ─────────────────────────────────────────────────────────

    /**
     * 적응형 임계값을 계산한다.
     *
     * 평균 밝기 × 3.0과 240 중 큰 값을 사용한다.
     * 어두운 환경에서는 낮은 임계값으로 민감도를 높인다.
     *
     * @param pixels 그레이스케일 픽셀 배열
     * @return 0~255 범위의 임계값
     */
    private fun calculateThreshold(pixels: ByteArray): Int {
        val mean = pixels.map { (it.toInt() and 0xFF).toFloat() }.average().toFloat()
        return max((mean * BRIGHTNESS_MULTIPLIER).toInt(), BASE_THRESHOLD).coerceIn(0, 255)
    }

    /**
     * 임계값 초과 픽셀을 연결 성분으로 클러스터링한다.
     *
     * 단순 bounding-box 기반 클러스터링을 사용한다.
     * 인접 픽셀(8-connectivity)을 동일 클러스터로 그룹핑한다.
     *
     * @param pixels 그레이스케일 픽셀 배열
     * @param width 프레임 너비
     * @param height 프레임 높이
     * @param threshold 고휘도 임계값
     * @return 유효 면적 범위 내 클러스터 목록
     */
    private fun extractHighBrightnessClusters(
        pixels: ByteArray,
        width: Int,
        height: Int,
        threshold: Int,
    ): List<Cluster> {
        val visited = BooleanArray(width * height) { false }
        val clusters = mutableListOf<Cluster>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val value = pixels[idx].toInt() and 0xFF

                if (value > threshold && !visited[idx]) {
                    val clusterPixels = floodFill(pixels, visited, width, height, x, y, threshold)
                    val area = clusterPixels.size.toFloat()

                    if (area in CLUSTER_AREA_MIN..CLUSTER_AREA_MAX) {
                        val cluster = buildCluster(clusterPixels, pixels, width)
                        clusters.add(cluster)
                    }
                }
            }
        }

        return clusters
    }

    /**
     * BFS 기반 Flood Fill로 연결된 고휘도 픽셀을 수집한다.
     */
    private fun floodFill(
        pixels: ByteArray,
        visited: BooleanArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        threshold: Int,
    ): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(startX, startY))

        // 8방향 이웃
        val directions = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            val idx = cy * width + cx

            if (cx < 0 || cx >= width || cy < 0 || cy >= height) continue
            if (visited[idx]) continue
            if ((pixels[idx].toInt() and 0xFF) <= threshold) continue

            visited[idx] = true
            result.add(Pair(cx, cy))

            // 최대 클러스터 크기 제한 (성능 보호)
            if (result.size > CLUSTER_AREA_MAX.toInt() * 2) break

            for ((dx, dy) in directions) {
                queue.add(Pair(cx + dx, cy + dy))
            }
        }

        return result
    }

    /**
     * 픽셀 목록에서 Cluster 객체를 생성한다.
     */
    private fun buildCluster(pixels: List<Pair<Int, Int>>, imagePixels: ByteArray, width: Int): Cluster {
        val area = pixels.size.toFloat()
        val centerX = pixels.map { it.first }.average().toInt()
        val centerY = pixels.map { it.second }.average().toInt()
        val meanBrightness = pixels.map { (x, y) ->
            (imagePixels[y * width + x].toInt() and 0xFF).toFloat()
        }.average().toFloat()

        // 경계 픽셀 수를 둘레로 근사 (정확한 contour 대신 근사값 사용)
        val perimeter = calculateApproximatePerimeter(pixels)
        val circularity = if (perimeter > 0f) {
            (4.0 * PI * area / (perimeter * perimeter)).toFloat()
        } else {
            0f
        }

        return Cluster(
            centerX = centerX,
            centerY = centerY,
            area = area,
            perimeter = perimeter,
            circularity = circularity,
            meanBrightness = meanBrightness,
            pixels = pixels,
        )
    }

    /**
     * 경계 픽셀 수로 둘레를 근사한다.
     *
     * 8-connectivity에서 이웃이 없는 픽셀을 경계로 간주한다.
     */
    private fun calculateApproximatePerimeter(pixels: List<Pair<Int, Int>>): Float {
        val pixelSet = pixels.toHashSet()
        val boundaryCount = pixels.count { (x, y) ->
            listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).any {
                it !in pixelSet
            }
        }
        return boundaryCount.toFloat()
    }

    // ─────────────────────────────────────────────────────────
    // STEP 3: 원형도 및 대비 검사
    // ─────────────────────────────────────────────────────────

    /**
     * 원형도 임계값 검사를 수행한다.
     *
     * circularity = 4π × area / perimeter² > 0.8이면 렌즈 후보.
     * 완전한 원은 circularity = 1.0이다.
     */
    private fun passesCircularityCheck(cluster: Cluster): Boolean {
        return cluster.circularity >= CIRCULARITY_MIN
    }

    /**
     * 주변 대비 비율 검사를 수행한다.
     *
     * 클러스터 중심 기준 SURROUND_RADIUS px 주변 픽셀의 평균 밝기 대비를
     * 계산한다. 5.0 이상이어야 유효한 역반사로 판정한다.
     */
    private fun passesContrastCheck(
        cluster: Cluster,
        imagePixels: ByteArray,
        width: Int,
        height: Int,
    ): Boolean {
        val surroundPixels = mutableListOf<Float>()
        val cx = cluster.centerX
        val cy = cluster.centerY

        for (dy in -SURROUND_RADIUS..SURROUND_RADIUS) {
            for (dx in -SURROUND_RADIUS..SURROUND_RADIUS) {
                val px = cx + dx
                val py = cy + dy
                if (px < 0 || px >= width || py < 0 || py >= height) continue
                val distSq = dx * dx + dy * dy
                if (distSq > SURROUND_RADIUS * SURROUND_RADIUS) continue
                // 클러스터 픽셀 자체는 제외
                if (abs(dx) <= 2 && abs(dy) <= 2) continue
                surroundPixels.add((imagePixels[py * width + px].toInt() and 0xFF).toFloat())
            }
        }

        if (surroundPixels.isEmpty()) return false

        val surroundMean = surroundPixels.average().toFloat()
        val contrastRatio = if (surroundMean > 0) cluster.meanBrightness / surroundMean else Float.MAX_VALUE
        cluster.contrastRatio = contrastRatio

        return contrastRatio >= CONTRAST_RATIO_MIN
    }

    // ─────────────────────────────────────────────────────────
    // STEP 4: 안정성 추적
    // ─────────────────────────────────────────────────────────

    /**
     * 감지된 클러스터를 안정성 추적기에 업데이트한다.
     *
     * 연속 STABILITY_FRAMES 프레임 동안 ±STABILITY_TOLERANCE_PX 이내에
     * 동일 포인트가 감지되면 isStable = true로 표시한다.
     *
     * @param clusters 현재 프레임에서 감지된 클러스터 목록
     * @param isFlashOn 현재 플래시 상태
     */
    private fun updateStabilityTracker(clusters: List<Cluster>, isFlashOn: Boolean) {
        // 현재 프레임의 클러스터와 기존 추적 포인트를 매칭
        val matchedKeys = mutableSetOf<String>()

        for (cluster in clusters) {
            val matchKey = findNearbyTrackedPoint(cluster.centerX, cluster.centerY)

            if (matchKey != null) {
                val tracked = stabilityTracker[matchKey]!!
                val updated = tracked.copy(
                    frameCount = tracked.frameCount + 1,
                    lastX = cluster.centerX,
                    lastY = cluster.centerY,
                    lastBrightness = cluster.meanBrightness,
                    lastCircularity = cluster.circularity,
                    lastArea = cluster.area,
                    lastContrastRatio = cluster.contrastRatio,
                    seenWithFlash = if (isFlashOn) true else tracked.seenWithFlash,
                    seenWithoutFlash = if (!isFlashOn) true else tracked.seenWithoutFlash,
                )
                stabilityTracker[matchKey] = updated
                matchedKeys.add(matchKey)
            } else {
                // 새 포인트 등록
                val key = "${cluster.centerX}_${cluster.centerY}"
                stabilityTracker[key] = TrackedPoint(
                    originX = cluster.centerX,
                    originY = cluster.centerY,
                    lastX = cluster.centerX,
                    lastY = cluster.centerY,
                    frameCount = 1,
                    lastBrightness = cluster.meanBrightness,
                    lastCircularity = cluster.circularity,
                    lastArea = cluster.area,
                    lastContrastRatio = cluster.contrastRatio,
                    seenWithFlash = isFlashOn,
                    seenWithoutFlash = !isFlashOn,
                )
                matchedKeys.add(key)
            }
        }

        // 이번 프레임에서 감지되지 않은 포인트는 카운터 감소 후 만료 제거
        val keysToRemove = stabilityTracker.keys.filter { it !in matchedKeys }
        for (key in keysToRemove) {
            val tracked = stabilityTracker[key]!!
            if (tracked.frameCount <= 1) {
                stabilityTracker.remove(key)
            } else {
                stabilityTracker[key] = tracked.copy(frameCount = tracked.frameCount - 1)
            }
        }
    }

    /**
     * 기존 추적 포인트 중 주어진 좌표와 가장 가까운 포인트를 찾는다.
     *
     * @return 매칭된 추적 포인트의 키, 없으면 null
     */
    private fun findNearbyTrackedPoint(x: Int, y: Int): String? {
        return stabilityTracker.entries.firstOrNull { (_, tracked) ->
            abs(tracked.lastX - x) <= STABILITY_TOLERANCE_PX &&
                abs(tracked.lastY - y) <= STABILITY_TOLERANCE_PX
        }?.key
    }

    // ─────────────────────────────────────────────────────────
    // STEP 5: 결과 생성
    // ─────────────────────────────────────────────────────────

    /**
     * 현재 추적 상태에서 RetroreflectionPoint 목록을 생성한다.
     *
     * STABILITY_FRAMES 이상 감지된 포인트만 결과에 포함한다.
     * 불변성: 항상 새 List를 반환한다.
     *
     * @param isFlashOn 현재 플래시 상태
     * @return 위험도 내림차순 정렬된 Retroreflection 포인트 목록
     */
    private fun buildResult(isFlashOn: Boolean): List<RetroreflectionPoint> {
        return stabilityTracker.values
            .filter { it.frameCount >= STABILITY_FRAMES }
            .map { tracked ->
                val isStable = tracked.frameCount >= STABILITY_FRAMES
                // 플래시 의존성: 플래시 ON에서 보이고 OFF에서 사라진 경우
                val flashDependency = tracked.seenWithFlash && !tracked.seenWithoutFlash
                val riskScore = calculateRiskScore(tracked, isStable, flashDependency)

                RetroreflectionPoint(
                    x = tracked.lastX,
                    y = tracked.lastY,
                    radius = sqrt(tracked.lastArea / PI.toFloat()),
                    brightness = tracked.lastBrightness,
                    circularity = tracked.lastCircularity,
                    isStable = isStable,
                    flashDependency = flashDependency,
                    riskScore = riskScore,
                    detectedAt = System.currentTimeMillis(),
                )
            }
            .sortedByDescending { it.riskScore }
    }

    /**
     * 포인트별 위험 점수를 산출한다.
     *
     * 가산 요인:
     *   - 크기 1~5px + 원형도 > 0.8: +30점
     *   - 안정성: +20점
     *   - 플래시 의존성: +25점
     *   - 대비 > 20: +15점
     *   - 흰색/붉은 색상: +10점 (brightness > 230 근사)
     */
    private fun calculateRiskScore(
        tracked: TrackedPoint,
        isStable: Boolean,
        flashDependency: Boolean,
    ): Int {
        var score = 0

        val radius = sqrt(tracked.lastArea / PI.toFloat())
        if (radius in 1.0..5.0 && tracked.lastCircularity > CIRCULARITY_MIN) score += 30
        if (isStable) score += 20
        if (flashDependency) score += 25
        if (tracked.lastContrastRatio > 20.0f) score += 15
        if (tracked.lastBrightness > 230f) score += 10

        return score.coerceIn(0, 100)
    }

    // ─────────────────────────────────────────────────────────
    // 내부 데이터 클래스
    // ─────────────────────────────────────────────────────────

    /**
     * 고휘도 클러스터 (내부 분석용)
     *
     * var contrastRatio는 Step 3에서 검사 후 채워진다.
     */
    private data class Cluster(
        val centerX: Int,
        val centerY: Int,
        val area: Float,
        val perimeter: Float,
        val circularity: Float,
        val meanBrightness: Float,
        val pixels: List<Pair<Int, Int>>,
        var contrastRatio: Float = 0f,
    )

    /**
     * 안정성 추적 포인트 (내부 상태)
     *
     * 불변 data class — 업데이트 시 copy()를 사용한다.
     */
    private data class TrackedPoint(
        val originX: Int,
        val originY: Int,
        val lastX: Int,
        val lastY: Int,
        val frameCount: Int,
        val lastBrightness: Float,
        val lastCircularity: Float,
        val lastArea: Float,
        val lastContrastRatio: Float,
        val seenWithFlash: Boolean,
        val seenWithoutFlash: Boolean,
    )
}
