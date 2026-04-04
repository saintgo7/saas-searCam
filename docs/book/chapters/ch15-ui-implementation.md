# Ch15: UI 구현 — Jetpack Compose로 스캔 화면 만들기

> **이 장에서 배울 것**: Jetpack Compose의 선언형 UI 철학이 SearCam의 스캔 화면에서 어떻게 살아 숨 쉬는지 봅니다. Canvas로 그린 RiskGauge 애니메이션, StateFlow와 sealed class로 설계한 ScanViewModel, 레이더 펄스 효과의 구현 원리, Navigation Graph 구성, Hilt와 Compose의 연결 방법, 그리고 카메라 생명주기를 안전하게 관리하는 DisposableEffect까지 — 실제 코드를 중심으로 Compose UI 구현의 핵심을 배웁니다.

---

## 도입

레고 블록을 쌓는다고 상상해보세요. 기존 XML 레이아웃 방식은 설계도(XML)와 조립(Java/Kotlin)이 분리되어 있었습니다. 설계도를 보면서 실제 조립 상태를 머릿속으로 그려야 했죠. Jetpack Compose는 이 둘을 하나로 합쳤습니다. 코드를 읽으면 화면이 어떻게 생겼는지 바로 보입니다. 조립과 설계가 같은 언어로 이루어지는 셈입니다.

SearCam의 스캔 화면은 이 철학의 좋은 예입니다. 위험도를 나타내는 반원 게이지, 레이더처럼 퍼지는 펄스 애니메이션, 스캔 상태에 따라 동적으로 바뀌는 UI — 이 모든 것을 Compose의 선언형 방식으로 구현했습니다. 이 장에서는 그 구현 과정을 처음부터 함께 따라갑니다.

---

## 15.1 선언형 UI의 핵심 개념

### 상태가 UI를 만든다

식당의 칠판 메뉴를 생각해보세요. 주방에서 재료가 떨어지면 웨이터가 칠판을 지우고 새로 씁니다. 칠판(UI)은 항상 현재 상태(재료 현황)를 반영합니다. Compose가 바로 이 방식입니다.

전통적인 View 시스템에서는 상태 변경 시 개발자가 직접 `textView.text = "새 값"` 처럼 UI를 명령해야 했습니다. Compose에서는 상태가 바뀌면 Compose 런타임이 자동으로 필요한 부분만 다시 그립니다(Recomposition). 개발자는 "이 상태일 때 화면은 이렇게 생겼다"만 선언하면 됩니다.

```kotlin
// 명령형 방식 (View 시스템)
if (isScanning) {
    progressBar.visibility = View.VISIBLE
    scanButton.isEnabled = false
    statusText.text = "스캔 중..."
}

// 선언형 방식 (Compose)
// 상태가 바뀌면 Compose가 알아서 재구성합니다
when (uiState) {
    is ScanUiState.Scanning -> {
        LinearProgressIndicator()
        Text(text = "스캔 중...")
        // 버튼은 렌더링하지 않음 — 존재 자체가 없어짐
    }
    is ScanUiState.Idle -> {
        Button(onClick = { viewModel.startQuickScan() }) {
            Text("스캔 시작")
        }
    }
}
```

이 차이는 단순히 스타일의 문제가 아닙니다. 선언형 방식은 UI와 상태의 불일치(버튼은 비활성화됐는데 텍스트는 여전히 "시작"인 상황)를 구조적으로 방지합니다.

### Recomposition과 성능

Compose는 영리합니다. 상태가 바뀔 때 전체 화면을 다시 그리지 않습니다. 바뀐 상태를 읽는 Composable 함수만 선택적으로 재실행합니다. 이를 Recomposition이라 합니다.

성능을 위해 기억해야 할 원칙 두 가지:
1. Composable 함수는 순수 함수처럼 작성하세요. 같은 입력에 항상 같은 출력을 내야 합니다.
2. 비싼 계산은 `remember`로 캐시하세요. Recomposition 때마다 재실행되면 성능이 떨어집니다.

---

## 15.2 ScanViewModel — StateFlow와 sealed class UiState

### sealed class로 UI 상태 모델링

비행기 탑승 절차를 생각해보세요. "체크인 중", "탑승 대기", "탑승 완료", "지연" — 이 상태들은 동시에 존재할 수 없고, 각 상태마다 표시되는 정보가 다릅니다. SearCam의 스캔 화면도 마찬가지입니다. sealed class는 이런 "유한하고 배타적인 상태"를 표현하는 완벽한 도구입니다.

```kotlin
// ui/scan/ScanViewModel.kt

sealed class ScanUiState {
    /** 초기 대기 상태 */
    data object Idle : ScanUiState()

    /**
     * 스캔 진행 중
     *
     * @param elapsedSeconds 경과 시간 (초)
     * @param foundDevices 지금까지 발견된 기기 목록
     * @param progress 전체 진행률 (0.0 ~ 1.0)
     * @param currentStep 현재 단계 이름
     */
    data class Scanning(
        val elapsedSeconds: Int = 0,
        val foundDevices: List<NetworkDevice> = emptyList(),
        val progress: Float = 0f,
        val currentStep: String = "스캔 준비 중...",
    ) : ScanUiState()

    /** 스캔 완료 */
    data class Success(val report: ScanReport) : ScanUiState()

    /**
     * 스캔 오류
     *
     * @param code 오류 코드 (E1xxx: 센서, E2xxx: 네트워크, E3xxx: 권한)
     * @param message 사용자 표시 메시지
     */
    data class Error(val code: String, val message: String) : ScanUiState()
}
```

`data object`와 `data class`의 차이에 주목하세요. `Idle`처럼 추가 데이터가 없는 상태는 `data object`로, `Scanning`처럼 상태와 함께 데이터를 전달해야 하는 경우는 `data class`로 선언합니다. Kotlin 1.9부터 `data object`는 `equals()`와 `toString()`을 올바르게 구현해줍니다.

### StateFlow와 SharedFlow의 역할 분리

ScanViewModel에는 두 종류의 Flow가 있습니다. StateFlow와 SharedFlow입니다. 이 둘의 역할은 명확하게 분리됩니다.

```kotlin
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val runQuickScanUseCase: RunQuickScanUseCase,
    private val runFullScanUseCase: RunFullScanUseCase,
) : ViewModel() {

    // StateFlow: 현재 UI 상태. 항상 최신값을 가집니다.
    // 새로 구독해도 즉시 현재 상태를 받습니다 (replay = 1)
    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // SharedFlow: 일회성 이벤트. 화면 이동, 스낵바 등
    // replay = 0 — 구독 이전 이벤트를 받지 않습니다
    private val _events = MutableSharedFlow<ScanUiEvent>()
    val events: SharedFlow<ScanUiEvent> = _events.asSharedFlow()

    // 취소 가능한 스캔 Job
    private var scanJob: Job? = null
```

StateFlow를 텔레비전 채널의 현재 방송으로, SharedFlow를 알림 벨소리로 비유할 수 있습니다. 채널을 켜면(구독하면) 지금 방송 중인 내용(현재 상태)을 바로 볼 수 있습니다. 반면 알림은 울리는 순간에 자리에 있어야만 들을 수 있습니다. 놓친 알림은 다시 받을 수 없습니다.

이 구분이 중요한 이유: 화면 이동 이벤트를 StateFlow에 넣으면 화면 회전 후 재구독할 때 같은 이동이 다시 발생합니다. SharedFlow는 이 문제를 방지합니다.

### 스캔 취소와 Job 관리

```kotlin
fun startQuickScan() {
    // 이미 스캔 중이면 중복 실행 방지
    if (_uiState.value is ScanUiState.Scanning) return

    scanJob = viewModelScope.launch {
        _uiState.value = ScanUiState.Scanning(
            progress = 0f,
            currentStep = "Wi-Fi 네트워크 스캔 중...",
        )

        try {
            runQuickScanUseCase.invoke().collect { report ->
                _uiState.value = ScanUiState.Success(report)
                _events.emit(ScanUiEvent.NavigateToResult(report.id))
            }
        } catch (e: Exception) {
            _uiState.value = ScanUiState.Error(
                code = "E2001",
                message = "스캔 중 오류가 발생했습니다: ${e.message}",
            )
        }
    }
}

fun cancelScan() {
    scanJob?.cancel()  // 코루틴 취소는 구조화된 동시성으로 전파됩니다
    scanJob = null
    _uiState.value = ScanUiState.Idle
}

override fun onCleared() {
    super.onCleared()
    scanJob?.cancel()  // ViewModel 소멸 시 스캔 자동 취소
}
```

`scanJob?.cancel()`이 호출되면 코루틴 취소 신호가 계층 아래로 전파됩니다. UseCase 내부에서 `collect`를 실행 중이라면 `CancellationException`이 발생하고 코루틴이 즉시 종료됩니다. `try-catch`에서 `CancellationException`을 잡지 않도록 주의해야 합니다 — Kotlin 코루틴 규약상 취소는 예외로 처리하면 안 됩니다.

---

## 15.3 HomeScreen — 레이더 펄스 애니메이션 구현

### InfiniteTransition으로 무한 반복 애니메이션

HomeScreen의 Quick Scan 버튼은 레이더에서 전파가 퍼져나가는 것처럼 원형 펄스를 무한 반복합니다. 이 효과는 `rememberInfiniteTransition`으로 구현합니다.

```kotlin
// ui/home/HomeScreen.kt

@Composable
private fun QuickScanButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")

    // 크기: 1배 → 1.4배 (1.5초 주기로 Restart)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_scale",
    )

    // 투명도: 0.3 → 0 (크기와 동시에 페이드아웃)
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_alpha",
    )

    Box(contentAlignment = Alignment.Center) {
        // 펄스 원 — 실제 버튼 뒤에 배치
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)),
        )
        // 메인 버튼 (클릭 가능)
        Button(
            onClick = onClick,
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Quick Scan",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Quick Scan", fontSize = 12.sp, color = Color.White)
                Text(text = "30초 점검", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
```

`RepeatMode.Restart`와 `RepeatMode.Reverse`의 차이를 이해하는 것이 중요합니다. `Restart`는 1f에서 1.4f로 커진 뒤 즉시 1f로 리셋하고 다시 시작합니다. `Reverse`는 1.4f에서 다시 1f로 천천히 줄어듭니다. 레이더 펄스 효과는 `Restart`여야 자연스럽습니다 — 전파는 퍼졌다 다시 모이지 않고, 새 전파가 새로 시작되는 것처럼 보여야 하니까요.

### HomeScreen 전체 구조 — Scaffold와 상태 분리

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToQuickScan: () -> Unit,
    onNavigateToFullScan: () -> Unit,
    // ... 다른 네비게이션 콜백들
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // LaunchedEffect: 컴포저블 진입 시 한 번 실행, 이벤트 수집
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeUiEvent.NavigateToQuickScan -> onNavigateToQuickScan()
                is HomeUiEvent.NavigateToFullScan -> onNavigateToFullScan()
                // ...
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("SearCam", fontWeight = FontWeight.Bold) })
        },
    ) { paddingValues ->
        // 실제 내용은 별도 컴포저블로 분리 — 테스트 용이성 향상
        HomeContent(
            uiState = uiState,
            onQuickScanClick = viewModel::onQuickScanClick,
            // ...
            modifier = Modifier.padding(paddingValues),
        )
    }
}
```

`collectAsStateWithLifecycle()`은 `collectAsState()`의 개선된 버전입니다. 앱이 백그라운드에 있을 때 자동으로 수집을 멈추고, 포그라운드로 돌아오면 다시 시작합니다. 배터리와 리소스를 아끼는 라이프사이클 인식 수집 방식입니다.

`LaunchedEffect(viewModel)`에서 key를 `viewModel`로 지정한 이유도 중요합니다. `viewModel`은 Configuration Change(화면 회전) 시에도 살아남기 때문에, Recomposition 때마다 이벤트 수집을 재시작하지 않습니다. key로 `Unit`을 쓰면 컴포저블이 처음 진입할 때만 실행됩니다.

---

## 15.4 RiskGauge — Canvas 애니메이션으로 그린 위험도 게이지

### Canvas는 마우스 없는 그림판

`Canvas` composable은 Compose에서 픽셀 수준의 자유로운 그림을 그릴 수 있는 공간입니다. Material 컴포넌트로 표현하기 어려운 커스텀 시각화에 사용합니다. RiskGauge는 반원 호(arc)로 0~100 점수를 시각화하는 컴포넌트입니다.

```kotlin
// ui/components/RiskGauge.kt

@Composable
fun RiskGauge(
    score: Int,
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    animate: Boolean = true,
) {
    // Animatable: 외부에서 값을 바꾸면 지정한 easing으로 부드럽게 전환
    val animatedScore = remember { Animatable(0f) }
    LaunchedEffect(score) {
        if (animate) {
            animatedScore.animateTo(
                targetValue = score.toFloat(),
                animationSpec = tween(durationMillis = 1000, easing = EaseOut),
            )
        } else {
            animatedScore.snapTo(score.toFloat())
        }
    }

    val gaugeColor = riskLevelToColor(riskLevel)

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = size.toPx() * 0.1f
            val radius = (this.size.minDimension - strokeWidth) / 2f
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            val arcSize = Size(radius * 2, radius * 2)

            // 1. 회색 배경 트랙 — 전체 270도 호
            drawArc(
                color = trackColor,
                startAngle = 135f,    // 왼쪽 아래에서 시작
                sweepAngle = 270f,    // 오른쪽 아래까지
                useCenter = false,    // 중심점 연결 없이 호만 그림
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // 2. 점수 호 — 그라데이션, 애니메이션 값으로 너비 결정
            val sweepAngle = (animatedScore.value / 100f) * 270f
            if (sweepAngle > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF22C55E),  // 녹색 (안전)
                            0.2f to Color(0xFF84CC16),  // 연두 (관심)
                            0.4f to Color(0xFFEAB308),  // 노랑 (주의)
                            0.6f to Color(0xFFF97316),  // 주황 (위험)
                            1.0f to Color(0xFFEF4444),  // 빨강 (매우 위험)
                        ),
                        center = center,
                    ),
                    startAngle = 135f,
                    sweepAngle = sweepAngle,
                    // ...
                )
            }
        }

        // 게이지 중앙 — 점수 숫자와 등급 라벨
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = animatedScore.value.toInt().toString(),
                fontSize = (size.value * 0.22f).sp,
                fontWeight = FontWeight.Bold,
                color = gaugeColor,
            )
            Text(
                text = riskLevel.labelKo,
                fontSize = (size.value * 0.1f).sp,
                color = gaugeColor,
            )
        }
    }
}
```

### 각도 계산 — 왜 135도에서 시작하는가

Android Canvas의 각도 기준은 시계 3시 방향(오른쪽)이 0도이고 시계 방향으로 증가합니다. 일반적인 수학 좌표계와 다릅니다.

```
       90°
        |
180° ───┼─── 0°
        |
       270°
```

135도에서 시작하면 왼쪽 아래에서 시작해서 270도를 쓸면 오른쪽 아래에 도달합니다. 이것이 자동차 속도계처럼 왼쪽 아래에서 오른쪽 아래까지 이어지는 반원 호 모양이 되는 이유입니다.

### Animatable vs animateFloatAsState

두 애니메이션 API의 차이를 알아야 합니다.

| 기준 | `Animatable` | `animateFloatAsState` |
|------|-------------|----------------------|
| 제어 | 명시적 `animateTo()` 호출 | 타겟값 변경 시 자동 시작 |
| 현재값 | `.value`로 접근 | `by` 위임으로 접근 |
| 취소/대기 | 직접 제어 가능 | 자동 관리 |
| 적합한 경우 | 복잡한 시퀀스, 취소 필요 | 단순 상태 기반 애니메이션 |

RiskGauge는 `score`가 바뀔 때마다 `LaunchedEffect`에서 `animateTo()`를 호출해야 하므로 `Animatable`이 더 적합합니다. `animateFloatAsState`는 `by` 위임만으로 쓸 수 있어서 더 간단하지만 세밀한 제어가 어렵습니다.

---

## 15.5 Navigation Graph 구성

### NavHost로 화면 연결하기

SearCam의 화면 이동은 Jetpack Navigation Compose로 관리합니다. 책의 목차처럼, NavHost가 어떤 화면(목적지)이 있고 어떻게 이동할 수 있는지를 중앙에서 정의합니다.

```kotlin
// navigation/SearCamNavGraph.kt

@Composable
fun SearCamNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        // 홈 화면
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToQuickScan = {
                    navController.navigate(Screen.QuickScan.route)
                },
                onNavigateToFullScan = {
                    navController.navigate(Screen.FullScan.route)
                },
                onNavigateToReport = { reportId ->
                    navController.navigate(Screen.Report.createRoute(reportId))
                },
                // ...
            )
        }

        // 스캔 화면
        composable(Screen.QuickScan.route) {
            ScanScreen(
                scanMode = ScanMode.QUICK,
                onNavigateUp = { navController.navigateUp() },
                onNavigateToResult = { reportId ->
                    // 스캔 화면을 백스택에서 제거하고 결과로 이동
                    navController.navigate(Screen.Report.createRoute(reportId)) {
                        popUpTo(Screen.QuickScan.route) { inclusive = true }
                    }
                },
            )
        }

        // 리포트 상세 화면 — reportId를 경로 파라미터로 전달
        composable(
            route = Screen.Report.route,
            arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            ReportScreen(
                reportId = reportId,
                onNavigateUp = { navController.navigateUp() },
            )
        }
    }
}

// 화면 경로 정의
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object QuickScan : Screen("quick_scan")
    data object FullScan : Screen("full_scan")

    // 파라미터가 있는 화면
    data object Report : Screen("report/{reportId}") {
        fun createRoute(reportId: String) = "report/$reportId"
    }
}
```

`popUpTo`는 화면 이동 후 백스택을 정리하는 데 쓰입니다. 스캔 화면에서 결과 화면으로 이동할 때 `popUpTo(Screen.QuickScan.route) { inclusive = true }`를 쓰면, 결과 화면에서 뒤로 가면 스캔 화면이 아닌 홈으로 돌아갑니다. 사용자가 결과를 보고 뒤로 갔을 때 다시 스캔이 시작되는 혼란을 방지합니다.

---

## 15.6 hiltViewModel()로 DI 연결

### Compose와 Hilt의 만남

Hilt ViewModel과 Compose를 연결하는 방법은 한 줄이면 충분합니다.

```kotlin
// gradle 의존성 (libs.versions.toml에 정의)
// androidx.hilt:hilt-navigation-compose:1.2.0

@Composable
fun HomeScreen(
    // ...
    viewModel: HomeViewModel = hiltViewModel(),  // Hilt가 주입
) {
```

`hiltViewModel()`은 `viewModel()` 함수의 Hilt 버전입니다. Navigation의 백스택 항목이나 Activity/Fragment의 생명주기와 연동하여 올바른 범위의 ViewModel 인스턴스를 반환합니다. 동일한 화면(백스택 항목)에서 여러 번 호출해도 같은 인스턴스를 반환합니다.

### ViewModel 범위 선택

```kotlin
// 화면 수준 ViewModel (기본)
val viewModel: HomeViewModel = hiltViewModel()

// Activity 수준 ViewModel (여러 화면에서 공유)
val sharedViewModel: SharedViewModel = hiltViewModel(
    viewModelStoreOwner = LocalActivity.current
)

// Navigation 그래프 수준 ViewModel
val navBackStackEntry = rememberNavController().getBackStackEntry("graph_route")
val graphViewModel: GraphViewModel = hiltViewModel(navBackStackEntry)
```

SearCam에서는 각 화면이 독립적인 ViewModel을 가집니다. 화면 간 데이터 공유가 필요한 경우 Repository를 통해 공유하고, ViewModel 간 직접 통신은 하지 않습니다.

---

## 15.7 DisposableEffect로 카메라 생명주기 관리

### 카메라는 열면 반드시 닫아야 한다

카메라는 앱 내에서 독점적으로 사용하는 하드웨어 자원입니다. 다른 앱이 카메라를 쓰려면 SearCam이 먼저 해제해야 합니다. 전화가 와도, 앱이 백그라운드로 가도 카메라를 해제하지 않으면 시스템 전체가 영향을 받습니다.

`DisposableEffect`는 Compose에서 이런 "설정 후 정리가 반드시 필요한 자원"을 관리하는 도구입니다. 마치 `try-finally`처럼, `onDispose` 블록은 컴포저블이 화면에서 사라질 때 반드시 실행됩니다.

```kotlin
// ui/lens/LensFinderScreen.kt

@Composable
fun LensFinderScreen(
    onNavigateUp: () -> Unit,
    viewModel: LensFinderViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // DisposableEffect: 진입 시 카메라 바인딩, 이탈 시 해제
    DisposableEffect(lifecycleOwner) {
        // 효과 시작: 카메라 바인딩
        viewModel.bindCamera(lifecycleOwner)

        onDispose {
            // 정리: 컴포저블이 화면에서 제거될 때 반드시 실행
            viewModel.unbindCamera()
        }
    }

    // CameraX PreviewView를 Compose에 통합
    AndroidView(
        factory = { context ->
            PreviewView(context).also { previewView ->
                viewModel.setPreviewView(previewView)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
```

`DisposableEffect`의 key(`lifecycleOwner`)가 바뀌면 `onDispose`를 실행하고 효과를 다시 시작합니다. `lifecycleOwner`는 Activity 재생성 시 바뀌므로, 화면 회전 같은 상황에서 카메라를 올바르게 재바인딩합니다.

### CameraX와 Compose의 통합 패턴

`AndroidView`는 Compose 트리 안에 기존 View 시스템의 뷰를 포함시키는 브릿지입니다. CameraX의 `PreviewView`는 전통적인 Android View이므로 `AndroidView`를 통해 Compose UI에 통합합니다.

```kotlin
// ViewModel에서 카메라 바인딩 관리
@HiltViewModel
class LensFinderViewModel @Inject constructor(
    private val cameraProvider: ProcessCameraProvider,
) : ViewModel() {

    private var previewView: PreviewView? = null

    fun setPreviewView(view: PreviewView) {
        previewView = view
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner) {
        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        previewView?.let { view ->
            preview.setSurfaceProvider(view.surfaceProvider)
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
        }
    }

    fun unbindCamera() {
        cameraProvider.unbindAll()
    }
}
```

이 패턴의 장점은 카메라 생명주기가 CameraX와 Android 생명주기 모두와 동기화된다는 점입니다. `bindToLifecycle`에 `lifecycleOwner`를 전달하면 CameraX가 Activity/Fragment의 생명주기에 맞게 자동으로 카메라를 시작하고 멈춥니다.

---

## 15.8 스캔 결과 화면 — 상태별 UI 분기

### when 표현식으로 완전한 분기

Compose에서 sealed class를 사용하면 `when`의 완전성 검사(exhaustive check) 혜택을 받습니다. 새 상태를 추가하면 컴파일러가 처리하지 않은 분기를 경고합니다.

```kotlin
@Composable
fun ScanScreen(
    onNavigateToResult: (String) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 일회성 이벤트 처리
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScanUiEvent.NavigateToResult -> onNavigateToResult(event.reportId)
                is ScanUiEvent.ShowSnackbar -> { /* 스낵바 표시 */ }
            }
        }
    }

    // 상태별 UI 분기 — 컴파일러가 모든 케이스 처리 강제
    when (val state = uiState) {
        is ScanUiState.Idle -> {
            IdleContent(
                onStartQuickScan = viewModel::startQuickScan,
            )
        }
        is ScanUiState.Scanning -> {
            ScanningContent(
                progress = state.progress,
                elapsedSeconds = state.elapsedSeconds,
                currentStep = state.currentStep,
                foundDevices = state.foundDevices,
                onCancel = viewModel::cancelScan,
            )
        }
        is ScanUiState.Success -> {
            // 성공 시 이벤트로 이미 이동 중 — 로딩 표시
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ScanUiState.Error -> {
            ErrorContent(
                errorCode = state.code,
                message = state.message,
                onRetry = viewModel::startQuickScan,
            )
        }
    }
}
```

`val state = uiState`로 스마트 캐스트를 활용합니다. `state`는 각 브랜치 내에서 구체적인 타입(`ScanUiState.Scanning` 등)으로 스마트 캐스트되어 `state.progress`처럼 해당 서브클래스의 프로퍼티에 바로 접근할 수 있습니다.

---

## 15.9 Composable 함수 설계 원칙

### 상태 끌어올리기 (State Hoisting)

Compose에서 상태를 설계할 때 가장 중요한 원칙은 상태 끌어올리기입니다. 상태를 사용하는 가장 낮은 공통 조상(Composable)으로 상태를 이동시키는 패턴입니다.

```kotlin
// 잘못된 예: 상태를 하위 컴포저블 안에 숨김
@Composable
fun BadSearchBar() {
    var query by remember { mutableStateOf("") }
    TextField(value = query, onValueChange = { query = it })
}

// 올바른 예: 상태를 상위로 끌어올림
@Composable
fun GoodSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    TextField(value = query, onValueChange = onQueryChange)
}

// 상위에서 상태 관리
@Composable
fun SearchScreen() {
    var query by remember { mutableStateOf("") }
    GoodSearchBar(query = query, onQueryChange = { query = it })
}
```

`BadSearchBar`는 외부에서 query를 읽거나 초기화할 방법이 없습니다. `GoodSearchBar`는 상태가 외부에 노출되므로 테스트 시 임의의 query로 렌더링하거나 변경 이벤트를 검증할 수 있습니다. SearCam의 모든 컴포넌트는 상태 끌어올리기 원칙을 따릅니다.

### 컴포저블 분리 기준

함수가 50줄을 넘기 시작하면 분리를 고려합니다. SearCam의 `HomeScreen.kt`는 이 기준으로 설계되었습니다.

| 함수 | 역할 | 줄 수 |
|------|------|------|
| `HomeScreen` | 이벤트 처리, Scaffold 뼈대 | ~45줄 |
| `HomeContent` | 상태별 분기 | ~90줄 |
| `QuickScanButton` | 펄스 애니메이션 버튼 | ~65줄 |
| `LastReportCard` | 최근 리포트 카드 | ~65줄 |

`HomeContent`는 Preview에서 독립적으로 테스트할 수 있습니다. `viewModel` 의존성 없이 `uiState`와 콜백만 받으므로 `@Preview`에서 가짜 데이터로 모든 상태를 확인할 수 있습니다.

---

## 실습

> **실습 15-1**: `RiskGauge`의 `RepeatMode.Restart`를 `RepeatMode.Reverse`로 바꿔보세요. 펄스 애니메이션이 어떻게 달라지는지 확인하고, 어떤 방식이 레이더 효과에 더 적합한지 생각해보세요.

> **실습 15-2**: `ScanUiState`에 새 상태 `Paused(val reason: String)`를 추가해보세요. Kotlin 컴파일러가 처리하지 않은 `when` 브랜치를 어디서 경고하는지 확인하세요.

> **실습 15-3**: `HomeContent`를 `@Preview`로 미리보기해보세요. `HomeUiState.Ready`, `HomeUiState.Loading`, `HomeUiState.Error` 세 가지 상태를 각각 Preview로 만들어보세요.

---

## 핵심 정리

| 개념 | 핵심 |
|------|------|
| sealed class UiState | 모든 UI 상태를 타입 안전하게 표현, 컴파일러가 분기 누락 검사 |
| StateFlow | 현재 상태 보관, 새 구독자도 즉시 최신값 수신 |
| SharedFlow | 일회성 이벤트 (화면 이동, 스낵바), 놓친 이벤트 재전달 없음 |
| InfiniteTransition | 무한 반복 애니메이션, `Restart`/`Reverse` 모드 선택 |
| Animatable | 값 변화 시 부드러운 전환, 명시적 `animateTo()` 제어 |
| DisposableEffect | 자원 획득/해제 쌍 보장, `onDispose`는 반드시 실행 |
| hiltViewModel() | Compose-Hilt 연결, Navigation 범위에 맞는 인스턴스 반환 |
| 상태 끌어올리기 | 상태를 상위로, 이벤트는 하위로 — 테스트 가능성과 재사용성 향상 |

- Compose의 재구성(Recomposition)은 상태를 읽는 함수만 선택적으로 실행한다
- `by viewModel.uiState.collectAsStateWithLifecycle()`은 배터리를 아끼는 생명주기 인식 구독이다
- Canvas의 각도 기준은 3시 방향 0도, 시계 방향으로 증가함을 기억하라
- DisposableEffect의 `onDispose`는 예외가 발생해도 반드시 실행된다

---

## 15.9 코드 리뷰 개선 사항 — `LifecycleOwner` 전파 아키텍처

### 문제: 카메라 바인딩에 `LifecycleOwner`가 필요한데 어디서 제공해야 하나

CameraX는 `ProcessCameraProvider.bindToLifecycle(lifecycleOwner, ...)` 호출 시 `LifecycleOwner`를 요구합니다. 초기 구현에서는 Repository 레이어에서 `Application Context`로 임시 처리하거나, ViewModel에서 Context를 직접 참조하는 패턴을 사용했습니다. 이는 메모리 누수와 테스트 어려움이라는 두 가지 문제를 만들었습니다.

### 해결: `LifecycleOwner`를 Compose Screen에서 UseCase까지 파라미터로 전달

```
[LensFinderScreen / IrCameraScreen]   ← LocalLifecycleOwner.current 획득
        ↓ 파라미터 전달
[LensViewModel.startLensDetection(lifecycleOwner)]
        ↓ 파라미터 전달
[LensDetectionRepository.startDetection(lifecycleOwner)]
        ↓ 파라미터 전달
[LensDetector.startDetection(lifecycleOwner)]   ← CameraX 바인딩
```

Compose Screen에서 `LocalLifecycleOwner.current`로 현재 LifecycleOwner를 얻어 아래로 전달합니다.

```kotlin
// ui/lens/LensFinderScreen.kt — LocalLifecycleOwner.current로 획득
@Composable
fun LensFinderScreen(
    onNavigateUp: () -> Unit,
    viewModel: LensViewModel = hiltViewModel(),
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // DisposableEffect(lifecycleOwner): lifecycleOwner가 바뀌면 카메라를 재바인딩
    DisposableEffect(lifecycleOwner) {
        viewModel.startLensDetection(lifecycleOwner)
        onDispose {
            viewModel.stopLensDetection()
        }
    }
    // ...
}
```

```kotlin
// ui/lens/LensViewModel.kt — Screen에서 받아 Repository로 전달
@HiltViewModel
class LensViewModel @Inject constructor(
    private val lensDetectionRepository: LensDetectionRepository,
    private val irDetectionRepository: IrDetectionRepository,
) : ViewModel() {

    fun startLensDetection(lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            _lensUiState.value = LensUiState.Starting
            val startResult = lensDetectionRepository.startDetection(lifecycleOwner)
            if (startResult.isFailure) {
                _lensUiState.value = LensUiState.Error(
                    code = "E1001",
                    message = "카메라를 시작할 수 없습니다: ${startResult.exceptionOrNull()?.message}",
                )
                return@launch
            }
            lensDetectionRepository.observeRetroreflections()
                .catch { e ->
                    _lensUiState.value = LensUiState.Error(code = "E1002", message = e.message ?: "")
                }
                .collect { points ->
                    _lensUiState.value = LensUiState.Detecting(retroPoints = points)
                }
        }
    }

    // onCleared(): viewModelScope 취소 시점에 동기 해제 보장
    override fun onCleared() {
        super.onCleared()
        runBlocking {
            withTimeout(1_000L) {
                lensDetectionRepository.stopDetection()
                irDetectionRepository.stopDetection()
            }
        }
    }
}
```

```kotlin
// domain/repository/LensDetectionRepository.kt — 인터페이스에 LifecycleOwner 파라미터
interface LensDetectionRepository {
    suspend fun startDetection(lifecycleOwner: LifecycleOwner): Result<Unit>
    fun observeRetroreflections(): Flow<List<RetroreflectionPoint>>
    suspend fun stopDetection()
}
```

### 왜 `LifecycleOwner`를 Repository 인터페이스에 두는가

이상적으로는 도메인 레이어 인터페이스에 Android 의존성(`LifecycleOwner`)이 없어야 합니다. 하지만 `LifecycleOwner`는 사실 Android Framework 클래스가 아니라 `androidx.lifecycle` 인터페이스입니다. 아키텍처 결정의 실용적 트레이드오프입니다.

| 방법 | 장점 | 단점 |
|------|------|------|
| Repository에 `LifecycleOwner` 전달 | 구현 간단, CameraX 직접 연동 | 도메인 레이어에 Android 의존성 |
| ApplicationContext로 LifecycleOwner 우회 | 도메인 순수 유지 | 메모리 누수, 생명주기 연동 어려움 |
| ProcessCameraProvider를 Hilt 싱글턴으로 | DI 일관성 | 생명주기 관리 복잡성 증가 |

SearCam은 실용성을 택했습니다. `LifecycleOwner`는 `Lifecycle`을 노출하는 단순 인터페이스이며, 테스트에서 `TestLifecycleOwner`로 쉽게 대체할 수 있습니다.

---

## 다음 장 예고

화면이 완성되었으니 이제 데이터를 영구적으로 저장할 차례입니다. Ch16에서는 Room DB로 스캔 이력을 저장하고, SQLCipher로 암호화하는 방법을 구현합니다.

---
*참고 문서: docs/09-ui-ux-spec.md, docs/04-system-architecture.md, docs/14-security-design.md*
