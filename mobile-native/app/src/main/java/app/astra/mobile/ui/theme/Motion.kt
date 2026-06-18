package app.astra.mobile.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

// Easings do Astra (espelham --ease-* do apps/web/src/index.css).
// "negative space": deslocamentos grandes ficam soft, micro-UI fica crispy.
val EaseSpring = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)        // slow-end suave
val EaseOutStd = CubicBezierEasing(0f, 0f, 0.2f, 1f)
val EaseOutSoft = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
val EaseSnappy = CubicBezierEasing(0.5f, 1.6f, 0.45f, 1f)      // overshoot leve (micro UI)
