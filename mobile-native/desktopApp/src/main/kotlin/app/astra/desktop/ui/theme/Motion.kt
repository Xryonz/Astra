package app.astra.desktop.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

// Curvas de motion migradas do mobile (Motion.kt) — mesmo vocabulario nos dois apps.
val EaseSpring = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
val EaseOutStd = CubicBezierEasing(0f, 0f, 0.2f, 1f)
val EaseOutSoft = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
val EaseSnappy = CubicBezierEasing(0.5f, 1.6f, 0.45f, 1f)
