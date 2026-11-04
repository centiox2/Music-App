package com.music.bitchord.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the phone can reach the internet right now, for the whole app: the offline card,
 * Home's offline layout and the log. Set from the system's default-network callbacks, so it
 * changes when the connection does and costs nothing in between.
 */
object Connectivity {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        set(manager.getNetworkCapabilities(manager.activeNetwork).hasInternet(), manager)
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = set(manager.getNetworkCapabilities(network).hasInternet(), manager)
                override fun onLost(network: Network) = set(manager.getNetworkCapabilities(manager.activeNetwork).hasInternet(), manager)
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = set(caps.hasInternet(), manager)
            })
        }
    }

    private fun set(value: Boolean, manager: ConnectivityManager) {
        if (last == value) return
        last = value
        _online.value = value
        val kind = manager.getNetworkCapabilities(manager.activeNetwork)?.let { caps ->
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "other"
            }
        } ?: "none"
        AppLog.state(if (value) "online ($kind)" else "offline")
    }

    /** The last state logged; null until the first reading. */
    @Volatile private var last: Boolean? = null

    private fun NetworkCapabilities?.hasInternet(): Boolean =
        this != null && hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
