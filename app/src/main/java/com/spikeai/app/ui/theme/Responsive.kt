package com.spikeai.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Device size buckets derived from the window width. */
enum class DeviceWidth { Compact, Medium, Expanded }

data class WindowInfo(
    val width: DeviceWidth,
    val isTablet: Boolean,
) {
    /** Max readable content width — content is centered on tablets (mirrors iOS `iPadReadable`). */
    val contentMaxWidth: Dp get() = if (isTablet) 720.dp else Dp.Unspecified

    /** Whether to use 2-column layouts for cards (tablet/landscape). */
    val useTwoColumns: Boolean get() = width == DeviceWidth.Expanded
}

val LocalWindowInfo = staticCompositionLocalOf {
    WindowInfo(width = DeviceWidth.Compact, isTablet = false)
}

/** Compute [WindowInfo] from a width in dp. */
fun windowInfoForWidth(widthDp: Float): WindowInfo {
    val bucket = when {
        widthDp < 600f -> DeviceWidth.Compact
        widthDp < 840f -> DeviceWidth.Medium
        else -> DeviceWidth.Expanded
    }
    return WindowInfo(width = bucket, isTablet = bucket != DeviceWidth.Compact)
}

/** Shorthand accessor for the current window info. */
val currentWindow: WindowInfo
    @Composable get() = LocalWindowInfo.current
