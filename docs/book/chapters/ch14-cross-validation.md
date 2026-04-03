# Ch14: 교차 검증 — 세 개의 목격자가 일치할 때 믿어라

> **이 장에서 배울 것**: 세 탐지 레이어를 하나의 위험도 점수(0~100)로 합치는 CrossValidator 설계를 배웁니다. 가중치 동적 조정, 보정 계수 설계 근거, 실제 탐지 시나리오 3가지를 통해 교차 검증 엔진의 전체 작동 방식을 이해합니다.

---

## 도입

법원에서 한 명의 목격자 증언으로 유죄를 선고하는 건 위험합니다. 하지만 독립적인 세 명의 목격자가 같은 내용을 증언한다면 이야기가 달라집니다. 각자 다른 방향에서, 다른 방법으로, 같은 결론에 도달했다면 — 신뢰할 수 있습니다.

SearCam의 세 탐지 레이어가 바로 이 세 명의 목격자입니다. Wi-Fi 스캔, 렌즈 감지, EMF — 각각 완전히 다른 물리적 원리로 동작합니다. 이 세 레이어가 동시에 "의심스럽다"고 말한다면, 그건 우연이 아닙니다.

CrossValidator는 이 세 목격자의 증언을 종합해서 판사 역할을 합니다.

---

## 14.1 세 레이어를 어떻게 합치는가 — CrossValidator 설계

### 기본 가중치 모델

각 레이어는 독립적으로 0~100 점수를 생성합니다. CrossValidator는 이 점수에 가중치를 곱해 최종 점수를 계산합니다.

```
기본 가중치:
  Layer 1 (Wi-Fi)   = 50%  → 0~50점 기여
  Layer 2 (렌즈)    = 35%  → 0~35점 기여
  Layer 3 (EMF)     = 15%  → 0~15점 기여

기본 계산:
  기반 점수 = (Wi-Fi 점수 × 0.5) + (렌즈 점수 × 0.35) + (EMF 점수 × 0.15)
```

하지만 이것만으로는 부족합니다. 레이어들이 서로를 확인해주는 **교차 검증 효과**를 반영해야 합니다.

### CrossValidator 핵심 설계

```kotlin
// data/analysis/CrossValidator.kt
class CrossValidator @Inject constructor() {

    companion object {
        // 기본 가중치 (레이어 사용 가능 시)
        const val WEIGHT_WIFI = 0.50f
        const val WEIGHT_LENS = 0.35f
        const val WEIGHT_EMF = 0.15f

        // 교차 보정 계수 — 몇 개의 레이어가 양성인지에 따라 달라집니다
        const val CORRECTION_SINGLE = 0.7f    // 1개 양성: ×0.7 (신뢰 하향)
        const val CORRECTION_DOUBLE = 1.2f    // 2개 양성: ×1.2 (교차 확인)
        const val CORRECTION_TRIPLE = 1.5f    // 3개 양성: ×1.5 (강력한 증거)

        // 위험도 등급 임계값
        const val THRESHOLD_SAFE = 20
        const val THRESHOLD_INTEREST = 40
        const val THRESHOLD_CAUTION = 60
        const val THRESHOLD_DANGER = 80
    }

    // 세 레이어의 결과를 종합해 위험도를 산출합니다
    fun validate(
        wifiResult: ScanResult?,
        lensResult: ScanResult?,
        emfResult: ScanResult?
    ): ValidationResult {

        // 사용 가능한 레이어만 동적으로 가중치를 조정합니다
        val adjustedWeights = calculateAdjustedWeights(
            hasWifi = wifiResult != null,
            hasLens = lensResult != null,
            hasEmf = emfResult != null
        )

        // 각 레이어의 가중 점수를 계산합니다
        val wifiScore = (wifiResult?.score ?: 0) * adjustedWeights.wifi
        val lensScore = (lensResult?.score ?: 0) * adjustedWeights.lens
        val emfScore = (emfResult?.score ?: 0) * adjustedWeights.emf

        // 기반 점수 합산
        val baseScore = wifiScore + lensScore + emfScore

        // 양성 레이어 수 카운트 (임계값 30 이상 = 양성으로 판정)
        val positiveCount = listOfNotNull(wifiResult, lensResult, emfResult)
            .count { it.score >= 30 }

        // 교차 보정 계수 적용
        val correctionFactor = when (positiveCount) {
            0 -> 1.0f
            1 -> CORRECTION_SINGLE   // 단일 탐지 — 신뢰도 낮춤
            2 -> CORRECTION_DOUBLE   // 이중 탐지 — 신뢰도 높임
            else -> CORRECTION_TRIPLE  // 삼중 탐지 — 강력한 신호
        }

        val finalScore = (baseScore * correctionFactor)
            .toInt()
            .coerceIn(0, 100)

        return ValidationResult(
            overallRisk = finalScore,
            riskLevel = scoreToRiskLevel(finalScore),
            positiveLayerCount = positiveCount,
            wifiContribution = wifiScore.toInt(),
            lensContribution = lensScore.toInt(),
            emfContribution = emfScore.toInt(),
            correctionFactor = correctionFactor,
            findings = buildFindings(wifiResult, lensResult, emfResult)
        )
    }

    // 위험도 점수를 등급으로 변환합니다
    fun scoreToRiskLevel(score: Int): RiskLevel = when {
        score < THRESHOLD_SAFE -> RiskLevel.SAFE
        score < THRESHOLD_INTEREST -> RiskLevel.INTEREST
        score < THRESHOLD_CAUTION -> RiskLevel.CAUTION
        score < THRESHOLD_DANGER -> RiskLevel.DANGER
        else -> RiskLevel.CRITICAL
    }
}

// 조정된 가중치 — 불변 데이터 클래스
data class AdjustedWeights(
    val wifi: Float,
    val lens: Float,
    val emf: Float
)

// 검증 결과 — 불변 데이터 클래스
data class ValidationResult(
    val overallRisk: Int,
    val riskLevel: RiskLevel,
    val positiveLayerCount: Int,
    val wifiContribution: Int,
    val lensContribution: Int,
    val emfContribution: Int,
    val correctionFactor: Float,
    val findings: List<Finding>
)
```

---

## 14.2 가중치 동적 조정 — Wi-Fi 없을 때

### 레이어가 비활성화되는 상황

세 레이어가 항상 모두 사용 가능하지는 않습니다.

| 상황 | 비활성화 레이어 |
|------|--------------|
| Wi-Fi 꺼짐 또는 연결 없음 | Layer 1 (Wi-Fi 스캔) |
| 카메라 권한 거부 | Layer 2 (렌즈 감지) |
| 자력계 없는 기기 | Layer 3 (EMF) |
| 완전 오프라인 환경 | Layer 1 |

이때 단순히 해당 레이어를 0점으로 처리하면 최대 가능 점수가 줄어들어 비현실적인 결과가 나옵니다. 대신 나머지 레이어들에 가중치를 재분배합니다.

```kotlin
// 사용 가능한 레이어에 가중치를 동적으로 재분배합니다
private fun calculateAdjustedWeights(
    hasWifi: Boolean,
    hasLens: Boolean,
    hasEmf: Boolean
): AdjustedWeights {
    // 사용 가능한 레이어의 기본 가중치 합계 계산
    val totalWeight = (if (hasWifi) WEIGHT_WIFI else 0f) +
                      (if (hasLens) WEIGHT_LENS else 0f) +
                      (if (hasEmf) WEIGHT_EMF else 0f)

    if (totalWeight == 0f) {
        // 모든 레이어 비활성화 — 탐지 불가 상태
        return AdjustedWeights(0f, 0f, 0f)
    }

    // 사용 가능한 레이어끼리 100%가 되도록 정규화합니다
    return AdjustedWeights(
        wifi = if (hasWifi) WEIGHT_WIFI / totalWeight else 0f,
        lens = if (hasLens) WEIGHT_LENS / totalWeight else 0f,
        emf = if (hasEmf) WEIGHT_EMF / totalWeight else 0f
    )
}
```

### 동적 조정 예시

```
Wi-Fi 없음 (Layer 1 비활성화):
  기본: Wi-Fi 50% + 렌즈 35% + EMF 15% = 100%
  조정: (렌즈 35% + EMF 15%) = 50% → 정규화
  결과: Wi-Fi 0% + 렌즈 70% + EMF 30% = 100%

렌즈+Wi-Fi만 (EMF 없음):
  기본: Wi-Fi 50% + 렌즈 35% = 85% → 정규화
  결과: Wi-Fi 58.8% + 렌즈 41.2% + EMF 0%
```

---

## 14.3 보정 계수 설계 근거

### 왜 ×0.7, ×1.2, ×1.5인가

이 수치는 경험적으로 도출한 값입니다. 실제 IP 카메라와 일반 가전제품을 대상으로 반복 테스트한 결과입니다.

**단일 탐지 (×0.7)**: 하나의 레이어만 양성이면 다른 레이어가 침묵하는 겁니다. 즉, 두 개의 독립적인 검증이 "없음"이라고 말하는 상황입니다. 신뢰도를 낮추는 게 맞습니다.

```
예시: Wi-Fi 스캔만 양성 (카메라 OUI 발견, 점수 75)
  기반 점수: 75 × 0.5 = 37.5
  보정 적용: 37.5 × 0.7 = 26.25 → 약 26점 (INTEREST 등급)
  해석: "네트워크에 의심 기기가 있지만, 렌즈/EMF 미확인"
```

**이중 탐지 (×1.2)**: 두 개의 독립적인 물리 원리가 같은 방향을 가리킵니다. 우연의 일치가 아닐 가능성이 높습니다.

```
예시: Wi-Fi 양성 (75점) + 렌즈 양성 (60점), EMF 음성
  기반 점수: (75 × 0.5) + (60 × 0.35) = 37.5 + 21 = 58.5
  보정 적용: 58.5 × 1.2 = 70.2 → 약 70점 (DANGER 등급)
  해석: "네트워크 기기 + 렌즈 역반사 이중 확인"
```

**삼중 탐지 (×1.5)**: 세 레이어 모두 양성. 세 가지 독립적인 물리적 방법이 모두 같은 결론입니다. 강력한 증거입니다.

```
예시: 세 레이어 모두 양성 (Wi-Fi 80, 렌즈 70, EMF 50)
  기반 점수: (80 × 0.5) + (70 × 0.35) + (50 × 0.15) = 40 + 24.5 + 7.5 = 72
  보정 적용: 72 × 1.5 = 108 → 100점 (상한선, CRITICAL 등급)
  해석: "세 레이어 모두 양성 — 즉시 확인 필요"
```

### 상한선 100점의 의미

점수가 100을 넘어도 100으로 제한합니다. `coerceIn(0, 100)` 사용. 이유는 두 가지입니다.

첫째, UI에서 게이지(0~100%)로 표시하기 위한 정규화입니다. 둘째, "100% 확실"이라고 주장하지 않기 위해서입니다. 100점이라도 "높은 가능성"이지 "확정"이 아닙니다.

---

## 14.4 위험도 0~100 스코어 산출 알고리즘 — 전체 흐름

```kotlin
// domain/usecase/CalculateRiskUseCase.kt
class CalculateRiskUseCase @Inject constructor(
    private val crossValidator: CrossValidator
) {
    suspend operator fun invoke(
        wifiResult: ScanResult?,
        lensResult: ScanResult?,
        emfResult: ScanResult?
    ): RiskAssessment {

        // CrossValidator에 세 레이어 결과를 넘깁니다
        val validation = crossValidator.validate(wifiResult, lensResult, emfResult)

        // 위험도에 따른 권고 사항을 생성합니다
        val recommendations = buildRecommendations(
            validation.riskLevel,
            validation.findings
        )

        return RiskAssessment(
            score = validation.overallRisk,
            level = validation.riskLevel,
            validation = validation,
            recommendations = recommendations,
            timestamp = System.currentTimeMillis()
        )
    }

    // 위험 등급별 사용자 권고 사항
    private fun buildRecommendations(
        level: RiskLevel,
        findings: List<Finding>
    ): List<String> = buildList {
        when (level) {
            RiskLevel.SAFE -> {
                add("현재 환경에서 의심 신호가 발견되지 않았습니다.")
                add("정기적인 재스캔을 권장합니다 (30분마다).")
            }
            RiskLevel.INTEREST -> {
                add("약한 의심 신호가 있습니다. 추가 확인을 권장합니다.")
                findings.forEach { finding -> add("• ${finding.description}") }
            }
            RiskLevel.CAUTION -> {
                add("주의: 복수의 의심 신호가 감지되었습니다.")
                add("렌즈 찾기 모드로 육안 확인을 권장합니다.")
                findings.forEach { finding -> add("• ${finding.description}") }
            }
            RiskLevel.DANGER, RiskLevel.CRITICAL -> {
                add("경고: 강한 의심 신호가 감지되었습니다.")
                add("즉시 육안으로 확인하거나 다른 장소로 이동하세요.")
                add("의심 기기 발견 시 덮개를 씌우거나 관할 경찰에 신고하세요.")
                findings.forEach { finding -> add("• ${finding.description}") }
            }
        }
    }
}
```

---

## 14.5 실제 탐지 시나리오 3가지

### 시나리오 1: 숙박시설 (모텔/에어비앤비)

```
환경: 에어비앤비 원룸 체크인 직후 스캔
      같은 Wi-Fi 연결, 플래시 켜고 렌즈 스캔, EMF 스캔

탐지 결과:
  Layer 1 Wi-Fi:
    - ARP 기기 3개: 라우터(ASUS), 스마트TV(Samsung), 의심 기기(Hikvision OUI)
    - 의심 기기 포트: 554(RTSP) OPEN, 8080(HTTP) OPEN
    - Wi-Fi 점수: 85

  Layer 2 렌즈:
    - 에어컨 리모컨 방향에서 역반사 포인트 1개 발견
    - 원형도 0.82, 밝기 240, 플래시 OFF 시 소멸 확인
    - 렌즈 점수: 78

  Layer 3 EMF:
    - 리모컨 방향 자기장 기준선 대비 +35μT 상승
    - EMF 점수: 45

교차 검증:
  기반 점수: (85 × 0.5) + (78 × 0.35) + (45 × 0.15)
           = 42.5 + 27.3 + 6.75 = 76.55
  양성 레이어: 3개 → 보정 계수 ×1.5
  최종 점수: 76.55 × 1.5 = 114.8 → 100 (CRITICAL)

결과:
  🔴 CRITICAL — 위험도 100/100
  "에어컨 리모컨 방향에서 강한 의심 신호:
   카메라 제조사 기기(Hikvision) 네트워크 감지,
   RTSP 포트 활성, 렌즈 역반사 확인, 자기장 이상"
```

### 시나리오 2: 공중화장실

```
환경: 대형 마트 화장실
      Wi-Fi 연결 없음(공용 Wi-Fi 미연결), 플래시 렌즈 스캔

탐지 결과:
  Layer 1 Wi-Fi: 비활성화 (네트워크 미연결)
  → 가중치 재분배: 렌즈 70%, EMF 30%

  Layer 2 렌즈:
    - 환기구 방향에서 역반사 포인트 1개
    - 원형도 0.91, 밝기 245, 플래시 OFF 소멸 확인
    - 렌즈 점수: 88

  Layer 3 EMF:
    - 기준선 정상 범위 내
    - EMF 점수: 8

교차 검증:
  기반 점수: (88 × 0.7) + (8 × 0.3)
           = 61.6 + 2.4 = 64
  양성 레이어: 1개(렌즈만) → 보정 계수 ×0.7
  최종 점수: 64 × 0.7 = 44.8 → 44 (INTEREST)

결과:
  🟡 INTEREST — 위험도 44/100
  "환기구 방향 렌즈 역반사 감지
   (Wi-Fi 연결 없어 네트워크 확인 불가)
   육안으로 환기구를 직접 확인하세요"

  해석: 렌즈 점수가 높지만 Wi-Fi로 교차 확인이 안 됨.
        단독 탐지이므로 보정으로 낮춤.
        실제로는 화장실 조명 LED였을 가능성도 있음.
```

### 시나리오 3: 탈의실

```
환경: 헬스장 탈의실
      Wi-Fi 연결됨, 렌즈 스캔 어려움 (조명 밝음)

탐지 결과:
  Layer 1 Wi-Fi:
    - ARP 기기: 라우터만 발견, 카메라 의심 기기 없음
    - Wi-Fi 점수: 5

  Layer 2 렌즈:
    - 조명이 밝아 역반사 구별 어려움 — 오탐 포인트 3개
    - 플래시 OFF 검증: 3개 모두 지속 (가짜 반사)
    - 렌즈 점수: 10 (플래시 검증으로 대폭 하향)

  Layer 3 EMF:
    - 헤어드라이어 방향 자기장 강함 (+80μT) — 명확한 발생원
    - 탐지 임계값 초과하지만 방향 명확
    - EMF 점수: 20

교차 검증:
  기반 점수: (5 × 0.5) + (10 × 0.35) + (20 × 0.15)
           = 2.5 + 3.5 + 3 = 9
  양성 레이어: 0개 → 보정 계수 ×1.0
  최종 점수: 9 → 9 (SAFE)

결과:
  🟢 SAFE — 위험도 9/100
  "현재 탐지된 의심 신호 없음.
   Wi-Fi 네트워크 정상, 렌즈 역반사 미탐지"
```

---

## 14.6 교차 검증 결과 ViewModel 연동

```kotlin
// ui/scan/ScanViewModel.kt (관련 부분)
class ScanViewModel @Inject constructor(
    private val runFullScanUseCase: RunFullScanUseCase,
    private val calculateRiskUseCase: CalculateRiskUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun startFullScan() {
        viewModelScope.launch {
            _uiState.value = ScanUiState.Scanning(progress = 0)

            try {
                // 세 레이어를 병렬로 실행합니다
                val wifiResult = async { runFullScanUseCase.scanWifi() }
                val lensResult = async { runFullScanUseCase.scanLens() }
                val emfResult = async { runFullScanUseCase.scanEmf() }

                // 모두 완료될 때까지 대기
                val results = awaitAll(wifiResult, lensResult, emfResult)

                // CrossValidator로 최종 위험도 산출
                val riskAssessment = calculateRiskUseCase(
                    wifiResult = results[0],
                    lensResult = results[1],
                    emfResult = results[2]
                )

                _uiState.value = ScanUiState.Complete(
                    riskScore = riskAssessment.score,
                    riskLevel = riskAssessment.level,
                    recommendations = riskAssessment.recommendations,
                    validation = riskAssessment.validation
                )
            } catch (e: Exception) {
                _uiState.value = ScanUiState.Error(
                    message = "스캔 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
}

// 스캔 UI 상태 — 불변 sealed class
sealed class ScanUiState {
    object Idle : ScanUiState()
    data class Scanning(val progress: Int) : ScanUiState()
    data class Complete(
        val riskScore: Int,
        val riskLevel: RiskLevel,
        val recommendations: List<String>,
        val validation: ValidationResult
    ) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}
```

---

## 실습

> **실습 14-1**: 세 가지 시나리오의 숫자를 직접 계산해보세요. 보정 계수를 0.7/1.2/1.5 대신 0.6/1.3/1.8로 바꾸면 각 시나리오의 최종 점수가 어떻게 달라지는지 비교해보세요.

> **실습 14-2**: `CrossValidator`의 양성 판정 임계값을 30에서 50으로 올려보세요. 어떤 시나리오에서 양성 레이어 수가 달라지는지, 결과적으로 최종 점수가 어떻게 변하는지 분석해보세요.

---

## 핵심 정리

| 개념 | 내용 |
|------|------|
| 기본 가중치 | Wi-Fi 50%, 렌즈 35%, EMF 15% |
| 동적 가중치 | 비활성 레이어 비중을 나머지가 나눠 가짐 |
| 단독 탐지 ×0.7 | 교차 미확인 → 신뢰도 하향 |
| 이중 탐지 ×1.2 | 독립 교차 확인 → 신뢰도 상향 |
| 삼중 탐지 ×1.5 | 강력한 증거 → 최고 신뢰도 |

- 교차 검증은 오탐률을 결정적으로 낮추는 핵심 설계다
- 단독 탐지 신호는 신뢰도를 낮춰 사용자에게 과도한 공포를 주지 않는다
- 삼중 탐지는 세 개의 독립적 물리 원리가 같은 결론 — 가장 신뢰할 수 있는 신호다
- 100점이라도 "확정"이 아니라 "강한 가능성" — UI에서 솔직하게 전달해야 한다

---

## 다음 장 예고

탐지 엔진의 핵심이 완성되었습니다. Ch15에서는 이 결과를 사용자에게 어떻게 전달할지 — ScanResult 화면, RiskGauge 컴포넌트, 리포트 저장 — Compose UI 레이어를 구현합니다.

---
*참고 문서: docs/02-TRD.md, docs/03-TDD.md, docs/04-system-architecture.md*
