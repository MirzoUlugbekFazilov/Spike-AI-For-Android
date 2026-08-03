package com.spikeai.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live network-reachability signal for the whole app.
 *
 * Exposes a hot [online] StateFlow (and a synchronous [isOnline]) that reflects
 * whether the device currently has *validated* internet — not merely a connected
 * interface. The sync engine listens to this to flush pending changes the moment
 * connectivity returns, and the sign-in screen reads it to require internet at login.
 *
 * Initialized once from [com.spikeai.app.SpikeApp.onCreate].
 */
object Connectivity {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    /** Current connectivity, read synchronously (safe to call from any thread). */
    fun isOnline(): Boolean = _online.value

    fun init(context: Context) {
        val cm = context.applicationContext
            .getSystemService(ConnectivityManager::class.java) ?: return

        // Seed with the present state so the very first read is accurate.
        _online.value = hasValidatedInternet(cm)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _online.value = hasValidatedInternet(cm)
            }

            override fun onLost(network: Network) {
                _online.value = hasValidatedInternet(cm)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    private fun hasValidatedInternet(cm: ConnectivityManager): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
