package com.spikeai.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.spikeai.app.data.LocalPrefs

/** Remember a [LocalPrefs] bound to the application context. */
@Composable
fun rememberPrefs(): LocalPrefs {
    val context = LocalContext.current.applicationContext
    return remember { LocalPrefs(context) }
}
