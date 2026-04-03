# Ch12: 렌즈 감지 — 빛이 돌아오는 길을 막아라

> **이 장에서 배울 것**: 탐지 가중치 35%를 차지하는 렌즈 감지 레이어의 원리와 구현을 배웁니다. 역반사(Retroreflection) 물리 원리부터 CameraX 5단계 분석 파이프라인까지, 스마트폰 플래시로 카메라 렌즈를 찾는 방법을 다룹니다.

---

## 도입

야간 운전 중 도로 표지판에 헤드라이트를 비추면 표지판이 눈부시게 빛납니다. 옆에서 보면 별로 밝지 않은데, 운전자 위치에서만 유독 밝게 보입니다. 이것이 역반사(Retroreflection)입니다. 빛이 들어온 방향 그대로 되돌아가는 현상이죠.

카메라 렌즈는 같은 원리로 동작합니다. 렌즈는 빛을 굴절시키고 반사시키도록 설계된 광학 소자입니다. 스마트폰 플래시를 켜고 방을 천천히 돌아보면, 숨겨진 카메라 렌즈는 다른 표면보다 유독 밝게, 그리고 원형으로 빛납니다.

SearCam은 이 물리 법칙을 알고리즘으로 구현합니다.

---

## 12.1 빛의 역반사(Retroreflection) 원리

### 왜 렌즈만 특별하게 반사하는가

일반 표면(벽, 천장, 가구)에 빛을 비추면 **난반사(Diffuse Reflection)**가 일어납니다. 빛이 여러 방향으로 퍼져나가서, 어느 방향에서 봐도 비슷한 밝기로 보입니다.

카메라 렌즈는 다릅니다. 렌즈 내부의 광학 소자들이 **코너 반사체(Corner Reflector)** 역할을 합니다. 빛이 들어온 방향으로 정확히 되돌아가는 거죠.

```
일반 표면 (난반사):
  광원 →    표면    → 여러 방향으로 산란
            ↑
          관찰자 (어두움)

카메라 렌즈 (역반사):
  광원 → 렌즈 → 광원 방향으로 되돌아옴
    ↑                         ↓
  관찰자 (밝게 보임!) ← 반사광
```

스마트폰에서 플래시와 카메라 센서는 아주 가까이 있습니다. 따라서 플래시를 켜고 카메라로 촬영하면 역반사 지점이 극도로 밝게 찍힙니다.

### 역반사의 특성

역반사 포인트는 세 가지 특성을 가집니다.

1. **고휘도**: 주변보다 현저히 밝습니다 (픽셀값 200+ / 255)
2. **원형**: 렌즈는 원형이므로 반사 패턴도 원형입니다
3. **안정성**: 카메라를 조금 움직여도 같은 자리에서 계속 빛납니다

이 세 조건을 동시에 만족하는 포인트를 찾는 것이 렌즈 감지 알고리즘의 핵심입니다.

---

## 12.2 CameraX ImageAnalysis 프레임 처리

### CameraX 설정

렌즈 감지는 후면 카메라 + 플래시 조합을 사용합니다. CameraX의 `ImageAnalysis` use case를 통해 매 프레임을 분석합니다.

```kotlin
// data/sensor/LensDetector.kt
class LensDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val retroreflectionAnalyzer: RetroreflectionAnalyzer,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null

    // CameraX 파이프라인을 구성하고 분석을 시작합니다
    fun startAnalysis(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): Flow<List<RetroreflectionPoint>> = callbackFlow {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview — 사용자가 카메라 화면을 볼 수 있게 합니다
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // ImageAnalysis — 30fps로 프레임을 분석합니다
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))  // 720p로 분석
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                // KEEP_ONLY_LATEST: 분석이 느려도 최신 프레임만 처리 (실시간성 유지)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        ContextCompat.getMainExecutor(context)
                    ) { imageProxy ->
                        // 각 프레임을 분석하고 결과를 Flow로 내보냅니다
                        val points = retroreflectionAnalyzer.analyze(imageProxy)
                        trySend(points)
                        imageProxy.close()  // 반드시 close() 호출 — 메모리 누수 방지
                    }
                }

            // 후면 카메라 선택
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                // 플래시를 토치 모드로 켭니다
                camera?.cameraControl?.enableTorch(true)
            } catch (e: Exception) {
                close(e)
            }
        }, ContextCompat.getMainExecutor(context))

        awaitClose {
            // Flow가 끝나면 플래시 끄기
            camera?.cameraControl?.enableTorch(false)
            imageAnalysis?.clearAnalyzer()
        }
    }
}
```

---

## 12.3 5단계 분석 파이프라인

### 파이프라인 개요

```
[카메라 프레임 (YUV 720p)]
        ↓
  1단계: 전처리 (그레이스케일 변환)
        ↓
  2단계: 고휘도 영역 추출 (임계값 필터링)
        ↓
  3단계: 원형도 분석 (컨투어 circularity 계산)
        ↓
  4단계: 시간축 안정성 검증 (연속 5프레임 지속)
        ↓
  5단계: 플래시 OFF 동적 검증 (플래시 끄면 사라지는지)
        ↓
[RetroreflectionPoint 목록]
```

```kotlin
// data/analysis/RetroreflectionAnalyzer.kt
class RetroreflectionAnalyzer @Inject constructor() {

    // 최근 N 프레임의 감지 기록 (안정성 검증용)
    private val frameHistory = ArrayDeque<Set<Point>>(maxSize = 5)

    fun analyze(imageProxy: ImageProxy): List<RetroreflectionPoint> {
        // 1단계: YUV 이미지를 그레이스케일 Bitmap으로 변환합니다
        val grayBitmap = imageProxy.toGrayBitmap()

        // 2단계: 고휘도 영역만 추출합니다 (임계값 200/255)
        val highBrightnessRegions = extractHighBrightness(
            bitmap = grayBitmap,
            threshold = 200
        )
        if (highBrightnessRegions.isEmpty()) return emptyList()

        // 3단계: 원형도 검사 — 렌즈는 원형입니다
        val circularRegions = highBrightnessRegions.filter { region ->
            region.circularity > 0.7f  // 1.0 = 완전한 원, 0.7 이상만 통과
        }
        if (circularRegions.isEmpty()) return emptyList()

        // 4단계: 최근 5프레임 연속 등장 여부 확인 (안정성)
        val currentPoints = circularRegions.map { it.center }.toSet()
        frameHistory.addLast(currentPoints)
        if (frameHistory.size > 5) frameHistory.removeFirst()

        val stableRegions = circularRegions.filter { region ->
            // 최소 3프레임 이상 같은 위치에서 감지된 포인트만 통과
            frameHistory.count { frame ->
                frame.any { point -> point.distanceTo(region.center) < 10f }
            } >= 3
        }

        // 5단계: 결과를 RetroreflectionPoint로 변환합니다
        return stableRegions.map { region ->
            RetroreflectionPoint(
                x = region.center.x,
                y = region.center.y,
                size = region.size,
                circularity = region.circularity,
                brightness = region.avgBrightness,
                contrastRatio = region.avgBrightness / grayBitmap.averageBrightness(),
                isStable = true,
                flashDependency = false,  // 5단계에서 플래시 OFF 검증 후 업데이트
                riskScore = calculateRisk(region)
            )
        }
    }

    // 고휘도 영역 추출 — 임계값 이상의 밝은 픽셀 클러스터를 찾습니다
    private fun extractHighBrightness(
        bitmap: Bitmap,
        threshold: Int
    ): List<BrightRegion> {
        val regions = mutableListOf<BrightRegion>()
        val visited = Array(bitmap.height) { BooleanArray(bitmap.width) }

        for (y in 0 until bitmap.height step 2) {  // 2픽셀 간격으로 탐색 (성능 최적화)
            for (x in 0 until bitmap.width step 2) {
                if (visited[y][x]) continue

                val brightness = bitmap.getBrightness(x, y)
                if (brightness < threshold) continue

                // BFS로 연결된 고휘도 픽셀 클러스터를 찾습니다
                val cluster = floodFillBFS(bitmap, x, y, threshold, visited)
                if (cluster.size >= 9) {  // 최소 9픽셀 (노이즈 제거)
                    regions.add(analyzeClusters(cluster))
                }
            }
        }

        return regions
    }

    // 클러스터의 원형도를 계산합니다
    // circularity = 4π × 면적 / 둘레² (완전한 원 = 1.0)
    private fun calculateCircularity(cluster: List<Point>): Float {
        val area = cluster.size.toFloat()
        val perimeter = calculatePerimeter(cluster).toFloat()
        if (perimeter == 0f) return 0f
        return (4f * Math.PI.toFloat() * area) / (perimeter * perimeter)
    }

    // 역반사 포인트의 위험도 점수를 계산합니다
    private fun calculateRisk(region: BrightRegion): Int {
        var score = 0
        if (region.circularity > 0.85f) score += 30  // 높은 원형도
        if (region.avgBrightness > 230f) score += 25  // 극도로 높은 밝기
        if (region.contrastRatio > 5f) score += 25    // 주변 대비 5배 이상
        if (region.size in 16f..400f) score += 20     // 적정 크기 (너무 크거나 작으면 감점)
        return score.coerceAtMost(100)
    }
}
```

---

## 12.4 플래시 OFF 동적 검증 — 오탐 방지 핵심

역반사를 이용하는 분석의 가장 큰 약점은 **가짜 양성(False Positive)**입니다. 유리창 반사, 금속 장식품, 광택 있는 플라스틱도 비슷하게 밝게 반사될 수 있습니다. 이를 걸러내는 가장 확실한 방법은 플래시를 잠깐 끄는 것입니다.

진짜 카메라 렌즈: 플래시를 끄면 밝기가 급격히 떨어집니다 (역반사 의존)
일반 반사 표면: 플래시를 꺼도 주변 조명이 있으면 여전히 보입니다

```kotlin
// 플래시 ON/OFF 동적 검증
suspend fun verifyWithFlashToggle(
    points: List<RetroreflectionPoint>
): List<RetroreflectionPoint> = withContext(dispatcher) {

    if (points.isEmpty()) return@withContext points

    // 플래시 OFF 후 같은 위치의 밝기를 비교합니다
    camera?.cameraControl?.enableTorch(false)
    delay(300)  // 300ms 대기 — 프레임이 안정화될 시간

    val darkFramePoints = captureFramePoints()  // 플래시 OFF 상태의 포인트

    camera?.cameraControl?.enableTorch(true)  // 플래시 다시 ON

    // 플래시 OFF 시 사라진 포인트만 진짜 역반사로 판정합니다
    return@withContext points.map { point ->
        val stilPresentWhenDark = darkFramePoints.any { darkPoint ->
            darkPoint.distanceTo(point) < 15f &&
            darkPoint.brightness > point.brightness * 0.6f
        }

        point.copy(
            // 플래시 꺼도 밝으면 flashDependency=false (가짜 반사)
            // 플래시 끄면 사라지면 flashDependency=true (진짜 역반사)
            flashDependency = !stilPresentWhenDark,
            // 플래시 의존 = 렌즈일 가능성 높음 → 위험도 상향
            riskScore = if (!stilPresentWhenDark) {
                (point.riskScore * 1.3f).toInt().coerceAtMost(100)
            } else {
                (point.riskScore * 0.5f).toInt()  // 가짜 반사 → 위험도 하향
            }
        )
    }
}
```

---

## 12.5 IR 감지 — 전면 카메라의 적외선 필터 활용

### 스마트폰 카메라와 IR 필터

사람 눈은 가시광선(380~700nm)만 볼 수 있습니다. 하지만 스마트폰 카메라 센서는 적외선(IR, 700~1000nm)도 감지합니다. 제조사들이 IR 컷 필터(ICF)를 달아서 가시광선만 통과시키지만, 전면 카메라는 후면보다 IR 필터가 약한 경우가 많습니다.

TV 리모컨 버튼을 누르면서 전면 카메라로 촬영해보세요. 리모컨 끝에서 보라색/흰색 불빛이 보인다면 그 카메라는 IR에 민감합니다.

이를 이용합니다. 몰래카메라는 야간 감시를 위해 IR LED를 포함하는 경우가 많습니다. 어두운 환경에서 전면 카메라로 주변을 스캔하면 IR LED를 발견할 수 있습니다.

```kotlin
// data/sensor/IrDetector.kt
class IrDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    companion object {
        const val IR_BRIGHTNESS_THRESHOLD = 180  // IR 감지 밝기 임계값
        const val MIN_IR_DURATION_MS = 3000L      // 최소 3초 지속되어야 IR로 판정
        const val MAX_AMBIENT_LUX = 10f           // 10 lux 이하 암실에서만 신뢰
    }

    // 전면 카메라로 IR 발광체를 탐지합니다
    fun startIrDetection(
        lifecycleOwner: LifecycleOwner
    ): Flow<List<IrPoint>> = callbackFlow {

        // 전면 카메라 선택 — IR 필터가 약해 IR에 더 민감합니다
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(
            ContextCompat.getMainExecutor(context)
        ) { imageProxy ->
            val irPoints = detectIrPoints(imageProxy)
            trySend(irPoints)
            imageProxy.close()
        }

        // ... CameraX 바인딩 생략

        awaitClose { imageAnalysis.clearAnalyzer() }
    }

    private fun detectIrPoints(imageProxy: ImageProxy): List<IrPoint> {
        val bitmap = imageProxy.toBitmap()

        // IR은 특유의 보라색(Violet) 또는 흰색으로 나타납니다
        val irCandidates = findIrColorPixels(bitmap)

        return irCandidates.map { point ->
            IrPoint(
                x = point.x,
                y = point.y,
                intensity = point.brightness,
                duration = 0L,  // 지속 시간은 외부에서 누적
                isStable = false,  // 3초 지속 여부는 Flow 레이어에서 판정
                color = if (point.isViolet()) IrColor.VIOLET else IrColor.WHITE,
                riskScore = if (point.brightness > 220) 70 else 40
            )
        }
    }

    // IR 특유의 색상 필터 — 보라색(R>150, G<80, B>150) 또는 흰색(R>200, G>200, B>200)
    private fun findIrColorPixels(bitmap: Bitmap): List<ColorPoint> {
        val candidates = mutableListOf<ColorPoint>()

        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // 보라색 IR: 적색과 청색이 높고, 녹색이 낮은 경우
                val isViolet = r > 150 && g < 80 && b > 150
                // 흰색 IR: 모든 채널이 높은 경우 (일부 IR LED의 특성)
                val isWhite = r > 200 && g > 200 && b > 200

                if (isViolet || isWhite) {
                    candidates.add(ColorPoint(x, y, (r + g + b) / 3f, isViolet))
                }
            }
        }

        return candidates
    }
}
```

---

## 12.6 실전 오탐 사례와 보정 방법

### 오탐 사례 1: 야간 조명의 광원

호텔 방 조명 장식의 LED가 역반사와 비슷한 밝기로 찍힐 수 있습니다.

**보정**: 광원 특성 필터링. 진짜 조명은 발광 면적이 넓고 고르게 밝습니다. 카메라 렌즈의 역반사는 면적이 작고(직경 2~20mm 기준) 중심부가 극도로 밝습니다.

```kotlin
// 광원과 렌즈를 구별하는 밝기 분포 분석
private fun isLightSource(region: BrightRegion): Boolean {
    // 광원: 중심과 가장자리 밝기 차이가 적음 (고른 발광)
    // 렌즈: 중심 극도로 밝고 가장자리로 갈수록 급격히 어두워짐
    val centerBrightness = region.getCenterBrightness()
    val edgeBrightness = region.getEdgeBrightness()
    val gradient = centerBrightness / (edgeBrightness + 1f)

    return gradient < 2.0f  // 기울기 2배 이하 = 광원으로 판정 (역반사 아님)
}
```

### 오탐 사례 2: 금속 장식품과 액자 유리

**보정**: 형태 분석 강화. 금속 장식은 불규칙한 형태, 카메라 렌즈는 원형입니다.

```kotlin
// 원형도 임계값을 높여 비원형 반사를 제거합니다
val strictCircularity = 0.75f  // 0.7 → 0.75로 상향 조정
```

### 오탐 사례 3: 안경 렌즈

사용자가 안경을 쓴 경우, 안경 렌즈도 역반사를 일으킵니다. 하지만 안경 렌즈는 크기가 매우 큽니다 (직경 50mm+).

**보정**: 크기 범위 필터링.

```kotlin
// 카메라 렌즈 크기 범위 (픽셀 단위, 720p 기준)
// 실제 직경 2mm~25mm 범위를 픽셀로 환산
private val LENS_MIN_AREA_PX = 4   // 너무 작으면 노이즈
private val LENS_MAX_AREA_PX = 800  // 너무 크면 안경/유리
```

### 오탐 보정 결과

실제 테스트에서 보정 전/후 오탐률:

| 조건 | 보정 전 | 보정 후 |
|------|---------|---------|
| 야간 LED 조명 | 탐지(오탐) | 미탐지 |
| 금속 장식품 | 탐지(오탐) | 미탐지 |
| 안경 렌즈 | 탐지(오탐) | 미탐지 |
| 실제 2mm 카메라 렌즈 | 탐지(정탐) | 탐지(정탐) |

---

## 실습

> **실습 12-1**: TV 리모컨의 IR LED를 스마트폰 전면 카메라로 촬영해보세요. 보라색 또는 흰색 불빛이 보이면 해당 카메라는 IR에 민감한 것입니다. 이 특성을 이용해 `IrDetector`가 어떤 색상 범위를 기준으로 삼는지 조정해보세요.

> **실습 12-2**: `RetroreflectionAnalyzer`의 원형도 임계값(0.7)을 0.5, 0.8, 0.9로 바꿔가며 동전, 반지, 안경 렌즈에 플래시를 비춰보세요. 임계값에 따라 오탐/미탐이 어떻게 달라지는지 분석해보세요.

---

## 핵심 정리

| 단계 | 목적 | 핵심 파라미터 |
|------|------|-------------|
| 전처리 | 그레이스케일로 처리 단순화 | 720p 다운스케일 |
| 고휘도 추출 | 밝은 반사 영역 격리 | 임계값 200/255 |
| 원형도 검사 | 렌즈 형태 특성 검증 | circularity > 0.7 |
| 안정성 검증 | 노이즈와 실제 신호 구분 | 5프레임 중 3회 |
| 플래시 검증 | 가짜 반사 최종 제거 | 밝기 60% 이하로 감소 |

- 역반사 원리는 물리 법칙 — 카메라 렌즈는 빛을 보낸 방향으로 되돌려보낸다
- 플래시 OFF 검증이 오탐률을 결정적으로 낮춘다
- 전면 카메라는 IR 필터가 약해 야간 IR 감지에 활용할 수 있다
- 오탐 방지는 하나의 필터가 아니라 크기+원형도+안정성의 복합 판단이다

---

## 다음 장 예고

두 개의 주요 레이어를 구현했습니다. Ch13에서는 마지막 보조 레이어 — 전자기장(EMF) 감지 — 를 다룹니다. 가중치 15%지만, 다른 두 레이어의 결과를 보강하는 중요한 역할을 합니다.

---
*참고 문서: docs/02-TRD.md, docs/03-TDD.md, docs/04-system-architecture.md*
