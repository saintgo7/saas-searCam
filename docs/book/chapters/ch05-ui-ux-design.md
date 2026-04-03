# Ch05: UI/UX 설계 — Jetpack Compose로 불안을 안심으로

> **이 장에서 배울 것**: 선언형 UI 패러다임이 왜 불안을 다루는 앱에 적합한지, 위험도 0~100을 어떻게 색상과 애니메이션으로 시각화하는지, 30초 스캔이라는 제약이 어떻게 UX 구조를 결정했는지를 배웁니다.

---

## 도입

응급실 의사는 환자를 보자마자 "생존 가능성 72%"라는 숫자를 머릿속에 떠올리지 않습니다. 그 대신 환자의 안색, 호흡, 땀 여부를 한눈에 읽어냅니다. 이것이 좋은 UI가 해야 할 일입니다. 숫자를 즉각적인 감각으로 변환하는 것.

SearCam은 숙소, 화장실, 탈의실에서 불안을 느끼는 사람이 30초 안에 "여기는 괜찮다" 또는 "여기는 의심스럽다"는 판단을 내릴 수 있도록 설계되었습니다. 이 목표가 모든 UI/UX 결정의 출발점입니다.

이 장에서는 Jetpack Compose의 선언형 패러다임이 이 목표를 어떻게 달성하는지, 그리고 위험도 시각화부터 30초 UX 흐름까지 설계 결정의 근거를 공개합니다.

---

## 5.1 왜 Jetpack Compose인가

### UI를 "어떻게" 그릴지가 아닌 "무엇을" 그릴지로

전통적인 View 시스템에서 개발자는 UI를 "조종"했습니다. 버튼을 숨기려면 `button.visibility = GONE`, 텍스트를 바꾸려면 `textView.text = newText`. 상태가 5개만 늘어나도 코드는 스파게티가 됩니다.

Compose는 발상을 뒤집습니다. "현재 상태가 이것이라면, UI는 이렇게 생겨야 한다"는 방식으로 씁니다.

```
[기존 View 방식]                    [Compose 방식]
상태 변경 → 뷰 참조 → 수동 업데이트    상태 변경 → UI 자동 재구성
button.isVisible = scanning          if (scanning) ScanningButton()
textView.text = count                else ReadyButton()
progressBar.progress = percent       Text("$count 기기 발견")
progressBar.isVisible = scanning     LinearProgressIndicator(percent)
```

SearCam에서 스캔 화면의 상태는 최소 6가지입니다: 대기, 스캔 중, 일시정지, 완료, 오류, 취소. Compose 없이 이 6가지 상태를 View로 관리하면 상태 전환 버그가 필연적으로 발생합니다.

### 선언형 UI가 불안을 다루는 앱에 적합한 이유

불안한 사용자는 빠른 피드백을 원합니다. "내가 버튼을 눌렀는데 왜 아무것도 안 바뀌지?"라는 의문이 드는 순간 불안은 배가됩니다.

Compose의 `recomposition`은 상태가 바뀌면 즉시 UI가 다시 그려짐을 보장합니다. 스캔 진행률이 43%에서 44%로 바뀌는 순간, 게이지도 함께 움직입니다. 개발자가 `postInvalidate()`를 호출할 필요가 없습니다.

```kotlin
// ScanProgressState가 바뀌면 이 컴포저블 전체가 자동으로 재구성됩니다
@Composable
fun ScanProgressScreen(state: ScanProgressState) {
    Column {
        CircularProgressIndicator(progress = { state.progress })
        Text("${state.devicesFound}대 발견")
        Text("남은 시간: ${state.remainingSeconds}초")
        state.layers.forEach { layer ->
            LayerStatusRow(layer)
        }
    }
}
```

---

## 5.2 4대 디자인 원칙과 그 이유

좋은 디자인은 원칙에서 나옵니다. SearCam의 4대 원칙은 개발 내내 모든 결정의 기준이 됩니다.

```
┌─────────────────────────────────────────────────────┐
│              SearCam 4대 디자인 원칙                   │
├───────────────┬─────────────────────────────────────┤
│ 30초 스캔     │ 버튼 1번으로 결과까지                │
│ 원클릭        │ 스캔 시작까지 최대 2탭               │
│ 공포 방지     │ "위험" 대신 "확인 필요", 근거 제공   │
│ 거짓 안심 방지│ 한계 고지 상시 표시                  │
└───────────────┴─────────────────────────────────────┘
```

### 원칙 1: 30초 스캔

스캔이 30초라는 숫자는 임의로 정한 것이 아닙니다. 사용자 리서치 결과, 사람이 "기다려줄 수 있는" 한계가 30초임을 확인했습니다. 30초를 넘어가면 "이게 작동하는 건지" 의심하기 시작합니다.

이 제약이 기술 설계를 결정했습니다. Wi-Fi 스캔(10초) + 기기 분석(15초) + 결과 정리(5초)라는 타임박스가 생겼고, 각 레이어는 병렬로 실행됩니다.

### 원칙 2: 공포 방지

`"위험"` 한 단어는 사람을 얼어붙게 만듭니다. SearCam의 모든 결과 표현은 "행동 가능한 정보"를 함께 제공합니다.

| 기존 앱 표현 | SearCam 표현 |
|------------|-------------|
| 위험! | 확인이 필요합니다 (67점) |
| 카메라 발견 | Wi-Fi에 연결된 기기 중 카메라 가능성이 있는 기기가 발견되었습니다 |
| 안전 | 이번 스캔에서 이상 징후를 발견하지 못했습니다 |

### 원칙 3: 거짓 안심 방지

"탐지되지 않음 = 안전"이라는 오해가 가장 위험합니다. SearCam은 결과 화면 최하단에 항상 이 문구를 표시합니다:

> "이 결과는 참고용입니다. 전원이 꺼진 카메라나 LTE/5G 카메라는 탐지할 수 없습니다. 의심스러우면 112에 신고하세요."

---

## 5.3 화면 구조와 네비게이션

SearCam은 18개 화면으로 구성됩니다. 핵심 경로는 단 4단계입니다.

```
앱 실행
  │
  ├─ [최초] → 온보딩 3단계 → 권한 요청 → 홈
  │
  └─ [재실행] ─────────────────────────→ 홈
                                          │
                              Quick Scan 탭
                                          │
                                    스캔 진행 (30초)
                                          │
                                    결과 화면
                                          │
                                    리포트 저장
```

### Bottom Navigation 설계

4개 탭으로 구성된 Bottom Navigation은 사용자가 어느 화면에서든 주요 기능에 1탭으로 접근할 수 있게 합니다.

```
┌─────────────────────────────────────────────────┐
│  [홈]      [리포트]    [체크리스트]    [설정]    │
│   홈         리포트       체크           설정    │
│  (활성)    (비활성)    (비활성)      (비활성)   │
└─────────────────────────────────────────────────┘
```

활성 탭은 Primary 색상(#2563EB)으로 강조, 비활성 탭은 Gray-400으로 표시합니다. 라벨은 활성 탭에만 표시해서 시각적 노이즈를 줄입니다.

---

## 5.4 색상 코드 시스템 — 위험도를 색으로 말하다

교통신호는 세계 어디서나 같은 의미입니다. 초록이면 가도 됩니다. SearCam의 색상 시스템은 이 보편적 직관을 빌려옵니다.

### 5단계 위험도 색상 스펙트럼

| 등급 | 점수 범위 | 색상 코드 | 의미 | 사용자 행동 |
|------|----------|---------|------|------------|
| 안전 | 0~19 | `#22C55E` (Green) | 이상 징후 없음 | 안심하고 사용 |
| 관심 | 20~39 | `#84CC16` (Lime) | 주의 관찰 권장 | 육안 확인 권장 |
| 주의 | 40~59 | `#EAB308` (Yellow) | 의심 기기 존재 | Full Scan 권장 |
| 위험 | 60~79 | `#F97316` (Orange) | 카메라 가능성 높음 | 즉시 점검 |
| 매우 위험 | 80~100 | `#EF4444` (Red) | 카메라 강력 의심 | 112 신고 고려 |

이 5단계는 의도적으로 "안전"과 "위험" 사이에 3단계의 중간 지대를 둡니다. 이진법(안전/위험)이 아닌 스펙트럼으로 표현하면 오탐으로 인한 공포를 줄일 수 있습니다.

### 브랜드 색상과의 분리

위험도 색상과 브랜드 색상은 명확히 분리됩니다.

```
브랜드 색상 (기능용)
  Primary: #2563EB (Blue-600) — 버튼, 활성 탭, CTA
  Surface: #1F2937 (Gray-800) — 카드 배경 (다크 모드)

위험도 색상 (의미용)
  위험도 게이지, 배지, 결과 배경에만 사용
  브랜드 Blue와 절대 혼용하지 않음
```

이 분리가 없으면 사용자는 "파란색 = 안전한가? 파란색 = 위험한가?"를 혼동합니다.

### 게이지 그라데이션 구현

원형 게이지에서 0~100 점수는 선형 보간으로 색상이 연속적으로 변합니다.

```kotlin
fun riskColor(score: Int): Color {
    return when {
        score < 20  -> Color(0xFF22C55E)  // 안전: Green
        score < 40  -> Color(0xFF84CC16)  // 관심: Lime
        score < 60  -> Color(0xFFEAB308)  // 주의: Yellow
        score < 80  -> Color(0xFFF97316)  // 위험: Orange
        else        -> Color(0xFFEF4444)  // 매우 위험: Red
    }
}

@Composable
fun RiskGauge(score: Int) {
    val color = riskColor(score)
    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 1000, easing = EaseOut),
        label = "riskGaugeProgress"
    )

    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            color = color,
            strokeWidth = 12.dp,
            modifier = Modifier.size(120.dp)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.displayLarge,
                color = color
            )
            Text(
                text = riskLabel(score),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
```

`animateFloatAsState`를 사용하면 스캔이 완료되어 점수가 표시될 때 게이지가 자연스럽게 채워지는 애니메이션이 자동으로 동작합니다. 1초에 걸쳐 0에서 최종 점수까지 부드럽게 증가합니다.

---

## 5.5 홈 화면 — 원클릭의 실현

홈 화면은 SearCam의 얼굴입니다. 처음 앱을 열었을 때 사용자가 보는 화면이 바로 이것입니다.

```
┌─────────────────────────┐
│ SearCam          [알림] │  ← AppBar (단순, 최소)
│                         │
│    ┌─────────────────┐  │
│    │                 │  │
│    │   ◎ Quick Scan  │  │  ← 메인 CTA: 120dp 원형 버튼
│    │   30초 점검     │  │     레이더 펄스 애니메이션
│    │                 │  │
│    └─────────────────┘  │
│                         │
│  ┌──────┐ ┌──────┐     │  ← 서브 모드: 80×80dp
│  │ Full │ │ 렌즈 │     │
│  │ Scan │ │ 찾기 │     │
│  └──────┘ └──────┘     │
│  ┌──────┐ ┌──────┐     │
│  │  IR  │ │ EMF  │     │
│  │ Only │ │ Only │     │
│  └──────┘ └──────┘     │
│                         │
│ ─── 마지막 스캔 결과 ── │  ← 컨텍스트 카드
│ ┌─────────────────────┐ │
│ │ 4/3 14:32  Quick    │ │
│ │ 안전 12/100      →  │ │
│ └─────────────────────┘ │
│                         │
├─────────────────────────┤
│ [홈] [리포트] [체크] [설정] │
└─────────────────────────┘
```

### Quick Scan 버튼의 물리적 크기

120dp 원형 버튼은 한 손으로 쥔 상태에서 엄지로 편안하게 닿을 수 있는 크기입니다. Android의 최소 터치 타깃 권장 크기는 48dp이지만, SearCam의 메인 CTA는 이보다 2.5배 큽니다. 이는 "빠르게 찾아서 누르는" 행동 패턴을 지원하기 위함입니다.

### 레이더 펄스 애니메이션 (Idle 상태)

스캔을 시작하지 않은 상태에서도 버튼은 "살아있음"을 표현합니다. 3초 주기로 원형 파동이 확장되며 사라집니다.

```kotlin
@Composable
fun QuickScanButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = EaseOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = EaseOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )

    Box(contentAlignment = Alignment.Center) {
        // 펄스 원
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
        )
        // 메인 버튼
        Button(
            onClick = onClick,
            modifier = Modifier.size(120.dp),
            shape = CircleShape
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Quick Scan", style = MaterialTheme.typography.titleMedium)
                Text("30초 점검", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

---

## 5.6 스캔 30초 UX 흐름

30초는 짧습니다. 하지만 아무것도 일어나지 않는 것처럼 느껴지면 30초는 3분처럼 느껴집니다. SearCam의 스캔 화면은 매초마다 사용자에게 "진행되고 있다"는 신호를 보냅니다.

### 스캔 진행 화면 구조

```
┌─────────────────────────┐
│ ←  Quick Scan            │
│                         │
│    ┌─────────────────┐  │
│    │                 │  │
│    │   ◎  15초       │  │  ← 원형 타이머 (카운트다운)
│    │   남은 시간      │  │
│    │                 │  │
│    └─────────────────┘  │
│                         │
│  Layer 1 Wi-Fi  ✅ 완료 │  ← 레이어별 실시간 상태
│  Layer 2 렌즈   ⏳ 진행  │
│  Layer 3 EMF    ○ 대기  │
│                         │
│  발견된 기기: 7대        │  ← 실시간 카운트
│                         │
│    [취소]               │
└─────────────────────────┘
```

### UX 설계 원칙: 정보의 밀도

스캔 중 화면에는 4가지 정보가 동시에 표시됩니다: 남은 시간, 레이어 상태, 발견 기기 수, 취소 옵션. 더 많은 정보를 넣으면 오히려 불안을 증가시킵니다. "왜 저 숫자가 저러지?"라는 의문이 생기기 때문입니다.

```kotlin
sealed class ScanProgressState {
    data object Idle : ScanProgressState()
    data class Scanning(
        val remainingSeconds: Int,
        val progress: Float,          // 0.0 ~ 1.0
        val layers: List<LayerState>,
        val devicesFound: Int
    ) : ScanProgressState()
    data class Completed(val result: ScanResult) : ScanProgressState()
    data class Error(val code: String, val message: String) : ScanProgressState()
    data object Cancelled : ScanProgressState()
}

data class LayerState(
    val name: String,
    val status: LayerStatus  // WAITING, RUNNING, COMPLETED, SKIPPED, ERROR
)
```

이 sealed class 하나가 스캔 화면의 모든 상태를 표현합니다. Compose에서 `when` 식으로 각 상태에 맞는 UI를 선언하면 됩니다.

### 시간 압박을 줄이는 심리적 설계

카운트다운 숫자만 표시하면 사용자는 "빨리 끝내야 한다"는 압박을 느낍니다. SearCam은 카운트다운 대신 "남은 시간"을 앞에 표시합니다.

- "15초" → 압박감: "이미 절반이 지났다"
- "남은 시간: 15초" → 안도감: "아직 15초가 남았다"

단어 하나의 차이가 감정을 바꿉니다.

---

## 5.7 결과 화면 — 불안에서 안심으로의 전환

스캔 결과 화면은 SearCam의 가장 중요한 화면입니다. 이 화면에서 사용자의 감정이 "불안"에서 "안심" 또는 "경계"로 전환됩니다.

```
┌─────────────────────────┐
│ ←  스캔 결과             │
│                         │
│    ┌─────────────────┐  │
│    │   ◎  12         │  │  ← RiskGauge (1초 애니메이션)
│    │      안전        │  │     초록색
│    └─────────────────┘  │
│                         │
│  "이번 스캔에서           │  ← 결과 메시지
│   이상 징후를 발견하지    │
│   못했습니다"             │
│                         │
│ ── 레이어별 결과 ──      │
│ Wi-Fi   0/7대 의심      │
│ 렌즈    감지 없음        │
│ 자기장  정상             │
│                         │
│ ── 발견된 기기 (7대) ── │
│ ┌─────────────────────┐ │  ← 기기 목록
│ │ 삼성 스마트폰        │ │
│ │ 안전 ██░░ 5/100     │ │
│ └─────────────────────┘ │
│                         │
│  [리포트 저장] [Full Scan]│
│                         │
│  ⚠ 이 결과는 참고용입니다 │  ← 한계 고지 (항상 표시)
└─────────────────────────┘
```

### 점진적 정보 공개

결과는 한꺼번에 쏟아내지 않습니다. 3단계로 공개됩니다.

1단계: 종합 위험도 (게이지 + 등급 텍스트)  
2단계: 레이어별 요약 (Wi-Fi / 렌즈 / 자기장)  
3단계: 기기 목록 (탭하면 상세 정보)

이 구조는 "결론 먼저, 근거 나중" 원칙을 UI로 구현한 것입니다.

---

## 5.8 다크 테마 설계

모텔 방에서 새벽에 앱을 쓰는 상황을 상상해보세요. 밝은 흰색 화면은 눈을 자극합니다. SearCam의 기본 테마는 다크 모드입니다.

### 다크 테마 색상 스펙트럼

| 역할 | 라이트 모드 | 다크 모드 |
|------|-----------|---------|
| 배경 | `#FFFFFF` (White) | `#111827` (Gray-900) |
| 카드/Surface | `#F9FAFB` (Gray-50) | `#1F2937` (Gray-800) |
| 본문 텍스트 | `#111827` (Gray-900) | `#F9FAFB` (Gray-50) |
| 보조 텍스트 | `#6B7280` (Gray-500) | `#9CA3AF` (Gray-400) |
| 구분선 | `#E5E7EB` (Gray-200) | `#374151` (Gray-700) |
| Primary | `#2563EB` (Blue-600) | `#3B82F6` (Blue-500) |

다크 모드에서도 위험도 색상(Green/Lime/Yellow/Orange/Red)은 변경하지 않습니다. 위험도 색상은 의미를 전달하는 신호이기 때문에 일관성이 최우선입니다.

### Material 3 Dynamic Color 비채택 이유

Android 12부터 지원하는 Dynamic Color(사용자 배경화면에서 색상 추출)는 SearCam에서 비채택했습니다. 위험도 색상(초록, 노랑, 빨강)이 사용자의 배경화면 색상에 따라 달라지면 의미 전달에 혼선이 생기기 때문입니다.

```kotlin
// MaterialTheme 설정 — Dynamic Color 비활성화
@Composable
fun SearCamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    // dynamicColor = false (의도적 비채택)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SearCamTypography,
        content = content
    )
}
```

---

## 5.9 타이포그래피 — 숫자가 주인공

SearCam의 타이포그래피 설계의 핵심은 숫자를 명확하게 표현하는 것입니다. 위험도 점수 "67"은 "6"과 "7"이 명확히 구분되어야 합니다.

### 텍스트 스케일

| 스타일 | 크기 | 무게 | 용도 |
|--------|------|------|------|
| Display Large | 36sp | Bold 700 | 위험도 점수 (메인) |
| Headline Large | 28sp | Bold 700 | 화면 제목 |
| Headline Medium | 24sp | SemiBold 600 | 섹션 제목 |
| Title Large | 20sp | SemiBold 600 | 카드 제목 |
| Body Large | 16sp | Regular 400 | 본문 |
| Body Small | 12sp | Regular 400 | 캡션, 타임스탬프 |
| Label Large | 14sp | Medium 500 | 버튼 텍스트 |

Pretendard Variable 서체를 사용하며, 숫자는 Tabular 설정(고정 폭)을 적용합니다. 위험도 점수가 "12"에서 "67"로 바뀔 때 텍스트가 좌우로 흔들리지 않게 하기 위함입니다.

---

## 5.10 접근성 고려사항

색맹 사용자를 위한 설계도 필수입니다. 위험도를 색상만으로 표현하면 적록색맹 사용자는 "안전"과 "주의"를 구분할 수 없습니다.

SearCam은 색상과 함께 반드시 텍스트 레이블을 병기합니다.

```
✅ 올바른 표현                ❌ 잘못된 표현
● 안전 (초록)                 ●
● 주의 (노랑)                 ●
● 위험 (빨강)                 ●
```

터치 타깃 최소 크기 48dp를 모든 인터랙티브 요소에 적용합니다. 화면 낭독기(TalkBack) 지원을 위해 모든 이미지와 아이콘에 contentDescription을 명시합니다.

```kotlin
Icon(
    imageVector = Icons.Outlined.Warning,
    contentDescription = "위험 경고",  // TalkBack이 읽음
    tint = Color(0xFFEF4444)
)
```

---

## 5.11 온보딩 — 신뢰를 먼저 쌓다

온보딩 3단계는 기능 설명이 아닙니다. 신뢰 구축입니다.

| 단계 | 제목 | 핵심 메시지 |
|------|------|-----------|
| 1/3 | 30초 안전 점검 | "숙소, 화장실을 스마트폰으로 빠르게 점검하세요" |
| 2/3 | 3중 교차 검증 | "Wi-Fi + 렌즈 감지 + 자기장 분석으로 정확도를 높입니다" |
| 3/3 | 솔직한 안내 | "전문 장비를 대체하지 않습니다. 의심 시 112에 신고하세요" |

3번째 화면이 핵심입니다. 대부분의 앱은 온보딩에서 한계를 숨깁니다. SearCam은 한계를 먼저 말합니다. 이 솔직함이 사용자의 신뢰를 만들고, 오탐 시 "앱이 잘못됐다"가 아닌 "원래 그렇다고 했지"라는 이해로 이어집니다.

---

## 5.12 체크리스트 화면 — 앱 없이도 쓸 수 있는 가이드

Wi-Fi가 없거나, 카메라 권한을 거부한 경우에도 SearCam은 유용해야 합니다. 체크리스트 화면은 숙소 유형별 육안 점검 가이드를 제공합니다.

```
체크리스트 유형 선택
├── 숙소 (모텔/호텔)
│   ├── 에어컨, 시계, 리모컨 확인
│   ├── TV 주변 확인
│   └── 욕실 환풍구, 샤워기 확인
├── 화장실 (공중)
│   ├── 칸막이 나사 확인
│   ├── 화장지 홀더 확인
│   └── 환풍구 확인
└── 탈의실
    ├── 거울 뒷면 확인
    └── 옷걸이 확인
```

각 항목은 체크박스로 완료를 표시합니다. 모든 항목을 완료하면 "점검 완료" 애니메이션이 표시됩니다.

---

## 정리: UX가 기술보다 먼저다

이 장에서 배운 핵심은 하나입니다. **좋은 UX는 기술적 정확도보다 중요할 때가 있습니다.**

SearCam이 탐지 정확도 85%를 달성해도, 결과 화면에서 사용자가 "무슨 뜻이지?"라고 혼란을 느낀다면 앱은 실패입니다. 반대로, 탐지 정확도가 80%이더라도 결과를 명확하게 이해하고 올바른 다음 행동을 취할 수 있다면 앱은 성공합니다.

Jetpack Compose는 이 목표를 달성하기 위한 도구입니다. 상태 기반의 선언형 UI, 부드러운 애니메이션, 일관된 색상 시스템이 합쳐져 "불안을 안심으로" 전환하는 경험을 만듭니다.

다음 장에서는 이 앱이 "탐지 앱이면서 탐지당하지 않는 방법", 즉 보안 설계를 다룹니다.

---

## 체크리스트

- [ ] 위험도 색상 5단계가 일관되게 적용되었는가
- [ ] 모든 터치 타깃이 48dp 이상인가
- [ ] 위험도 표현에 텍스트 레이블이 병기되었는가
- [ ] 결과 화면에 한계 고지 문구가 항상 표시되는가
- [ ] 스캔 화면에서 매초 피드백이 제공되는가
- [ ] 다크 테마에서 위험도 색상이 변경되지 않는가
- [ ] contentDescription이 모든 아이콘에 적용되었는가
