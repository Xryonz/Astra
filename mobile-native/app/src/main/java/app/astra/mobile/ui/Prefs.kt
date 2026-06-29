package app.astra.mobile.ui

import androidx.compose.runtime.compositionLocalOf
import app.astra.mobile.core.data.AppPrefs

val LocalAppPrefs = compositionLocalOf { AppPrefs() }
