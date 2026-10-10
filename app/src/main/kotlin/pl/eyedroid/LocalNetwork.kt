package pl.eyedroid

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.LinkProperties
import android.net.NetworkRequest
import java.net.Inet4Address

/** Select an actual Wi-Fi LinkAddress, not cellular, VPN or an arbitrary interface. */
class LocalNetwork(context: Context, private val onChanged: () -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    data class Binding(val network: Network, val address: String)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onChanged()
        override fun onLost(network: Network) = onChanged()
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = onChanged()
    }
    private var registered = false
    fun binding(): Binding? = manager.allNetworks.firstNotNullOfOrNull { network ->
        val caps = manager.getNetworkCapabilities(network)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) null
        else manager.getLinkProperties(network)?.linkAddresses?.firstNotNullOfOrNull {
            val ip = it.address
            if (ip is Inet4Address && !ip.isLoopbackAddress && !ip.isLinkLocalAddress)
                ip.hostAddress?.let { address -> Binding(network, address) } else null
        }
    }
    fun start() {
        manager.registerNetworkCallback(NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
        registered = true
    }
    fun close() { if (registered) manager.unregisterNetworkCallback(callback); registered = false }
}
