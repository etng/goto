package cn.y10n.gotoapp.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * 局域网 IPv4（WiFi 优先，回退所有接口）。
     * 直接枚举 NetworkInterface 比 WifiManager.connectionInfo.ipAddress 更可靠
     * ——后者在部分 MIUI/HyperOS 上即使 WiFi 通也返回 0。
     */
    fun getIpAddress(context: Context): String {
        val candidates = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        }.getOrDefault(emptyList())
        // 优先非回环、site-local（局域网段）的 IPv4
        return candidates.asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses?.toList().orEmpty().asSequence() }
            .filter { !it.isLoopbackAddress && it is java.net.Inet4Address && it.isSiteLocalAddress }
            .map { it.hostAddress.orEmpty() }
            .firstOrNull { it.isNotEmpty() }
            ?: candidates.asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses?.toList().orEmpty().asSequence() }
                .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
                .map { it.hostAddress.orEmpty() }
                .firstOrNull { it.isNotEmpty() }
            ?: ""
    }

    /** 是否已授权"使用情况访问"（读高频应用的前置条件） */
    fun usageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
