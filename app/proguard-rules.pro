# SearCam ProGuard 규칙
# 릴리즈 빌드에서 코드 난독화 + 최적화 적용

# ── Kotlin ──
-keepattributes Signature
-keepattributes *Annotation*

# ── Hilt (DI) ──
-keepclassmembers class * {
    @dagger.hilt.* <fields>;
    @dagger.hilt.* <methods>;
}

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}

# ── Timber (로깅 제거) ──
# 릴리즈 빌드에서 Timber 로그 라인 제거 (선택 사항)
# -assumenosideeffects class timber.log.Timber {
#     public static *** d(...);
#     public static *** v(...);
# }

# ── Domain Model (직렬화 대상) ──
# Room Entity와 도메인 모델은 리플렉션으로 접근 — 난독화 제외
-keep class com.searcam.domain.model.** { *; }
-keep class com.searcam.data.local.entity.** { *; }

# ── CameraX ──
-keep class androidx.camera.** { *; }

# ── MPAndroidChart ──
-keep class com.github.mikephil.** { *; }
