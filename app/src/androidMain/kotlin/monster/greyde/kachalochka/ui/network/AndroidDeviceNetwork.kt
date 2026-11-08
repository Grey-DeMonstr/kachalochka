package monster.greyde.kachalochka.ui.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.LastFailure
import monster.greyde.kachalochka.sync.WorkManagerSyncTrigger
import java.net.InetAddress
import java.util.TimeZone

private val TRANSPORTS =
    listOf(
        NetworkCapabilities.TRANSPORT_WIFI to "wifi",
        NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
        NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet",
        NetworkCapabilities.TRANSPORT_VPN to "VPN",
    )

class AndroidDeviceNetwork(
    private val context: Context,
    private val sync: WorkManagerSyncTrigger,
    private val failures: LastFailure,
) : DeviceNetwork {
    override fun facts(): List<Pair<String, String>> {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val link = connectivity.getLinkProperties(network)
        val lastSync =
            sync.lastPass?.let { (at, clean) ->
                if (clean) "$at, clean" else "$at, failed"
            }
        val lastFailure = failures.last?.let { (at, failure) -> "$at, $failure" }
        return listOf(
            "Android" to
                "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                "${Build.MANUFACTURER} ${Build.MODEL}",
            "Time zone" to TimeZone.getDefault().id,
            "Network" to describe(connectivity.getNetworkCapabilities(network)),
            "DNS servers" to link?.dnsServers?.joinToString { it.hostAddress.orEmpty() }.orEmpty(),
            "Private DNS" to privateDns(link),
            "System proxy" to (link?.httpProxy?.toString() ?: "none"),
            "Last sync" to (lastSync ?: "none"),
            "Last failure" to (lastFailure ?: "none"),
        )
    }

    override suspend fun addressesOf(host: String): List<String> =
        withContext(Dispatchers.IO) {
            InetAddress.getAllByName(host).map { it.hostAddress.orEmpty() }
        }

    private fun describe(capabilities: NetworkCapabilities?): String {
        if (capabilities == null) return "none"
        val transports =
            TRANSPORTS.mapNotNull { (id, name) -> name.takeIf { capabilities.hasTransport(id) } }
        val validated =
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                "validated"
            } else {
                "not validated"
            }
        return (transports + validated).joinToString()
    }

    private fun privateDns(link: LinkProperties?): String {
        if (link?.isPrivateDnsActive != true) return "off"
        return "on, ${link.privateDnsServerName ?: "automatic"}"
    }
}
