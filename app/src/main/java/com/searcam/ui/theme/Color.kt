package com.searcam.ui.theme

import androidx.compose.ui.graphics.Color

// ──────────────────────────────────────────────
// 위험도 색상 시스템
//
// 신호등 + 사이렌 콘셉트:
// 안전(초록) → 주의(노랑) → 위험(주황) → 위기(빨강)
// ──────────────────────────────────────────────

/** SAFE (0~29): 안전 — 짙은 초록 */
val RiskSafe = Color(0xFF2ECC71)
val RiskSafeContainer = Color(0xFFD5F5E3)
val RiskSafeOnContainer = Color(0xFF1A7A44)

/** CAUTION (30~59): 주의 — 노랑 */
val RiskCaution = Color(0xFFF39C12)
val RiskCautionContainer = Color(0xFFFEF9E7)
val RiskCautionOnContainer = Color(0xFF9A6109)

/** DANGER (60~79): 위험 — 주황 */
val RiskDanger = Color(0xFFE67E22)
val RiskDangerContainer = Color(0xFFFDEBD0)
val RiskDangerOnContainer = Color(0xFF884E15)

/** CRITICAL (80~100): 위기 — 빨강 */
val RiskCritical = Color(0xFFE74C3C)
val RiskCriticalContainer = Color(0xFFFADED9)
val RiskCriticalOnContainer = Color(0xFF8C2D22)

// ──────────────────────────────────────────────
// 앱 기본 색상 팔레트 (다크 테마 중심)
// ──────────────────────────────────────────────

/** 기본 Primary — 보안 앱의 신뢰감을 위한 딥 블루 */
val Primary = Color(0xFF1565C0)
val PrimaryVariant = Color(0xFF003C8F)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFD6E4FF)
val OnPrimaryContainer = Color(0xFF001B4F)

/** Secondary — 그레이 블루 (보조 액션) */
val Secondary = Color(0xFF546E7A)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFCFE8FF)
val OnSecondaryContainer = Color(0xFF1C3545)

/** Background */
val BackgroundLight = Color(0xFFF8F9FA)
val BackgroundDark = Color(0xFF0D1117)

/** Surface */
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF161B22)
val SurfaceVariantLight = Color(0xFFECEFF1)
val SurfaceVariantDark = Color(0xFF21262D)

/** On-Surface */
val OnSurfaceLight = Color(0xFF212121)
val OnSurfaceDark = Color(0xFFE6EDF3)
val OnSurfaceVariantLight = Color(0xFF546E7A)
val OnSurfaceVariantDark = Color(0xFF8B949E)

/** Outline */
val OutlineLight = Color(0xFFB0BEC5)
val OutlineDark = Color(0xFF30363D)

/** Error */
val ErrorColor = Color(0xFFCF6679)
val OnErrorColor = Color(0xFF370009)
val ErrorContainerColor = Color(0xFF4B1019)
val OnErrorContainerColor = Color(0xFFFFB3BA)

// ──────────────────────────────────────────────
// 스캔 UI 전용 색상
// ──────────────────────────────────────────────

/** 스캔 진행 중 — 파란 펄스 애니메이션 */
val ScanningPulse = Color(0xFF2196F3)

/** 레이더 그리드 라인 */
val RadarGrid = Color(0xFF1E3A5F)

/** 탐지 성공 포인트 */
val DetectionPoint = Color(0xFFFF6B6B)

/** EMF 그래프 선 색상 */
val EmfGraphLine = Color(0xFF00E5FF)
val EmfGraphFill = Color(0x1A00E5FF)
