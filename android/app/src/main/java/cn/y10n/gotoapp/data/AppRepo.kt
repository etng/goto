package cn.y10n.gotoapp.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri

/**
 * 应用信息聚合：可启动应用列表、UsageStats 高频统计、网页处理应用解析。
 */
object AppRepo {

    data class AppInfo(
        val pkg: String,
        val label: String,
        val firstInstall: Long,
        val isSystem: Boolean,
    )

    data class LaunchStats(
        val pkg: String,
        val count: Int,
        val lastUsed: Long,
    )

    data class WebHandler(
        val pkg: String,
        val label: String,
        val https: Boolean,
        val http: Boolean,
    )

    // ---------- 可启动应用 ----------

    fun launcherApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        // firstInstallTime 位于 PackageInfo（可见包集合正好覆盖：桌面应用 + 网页处理应用）
        val installTimes = pm.getInstalledPackages(0).associate { it.packageName to it.firstInstallTime }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val app = ri.activityInfo.applicationInfo
                runCatching {
                    AppInfo(
                        pkg = app.packageName,
                        label = pm.getApplicationLabel(app).toString(),
                        firstInstall = installTimes[app.packageName] ?: 0L,
                        isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }.getOrNull()
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    // ---------- 高频使用（UsageStats，需授权"使用情况访问"） ----------

    @Volatile private var cacheTime = 0L
    @Volatile private var cacheStats: List<LaunchStats> = emptyList()

    fun usageStats(context: Context, days: Int = 7, limit: Int = 30): List<LaunchStats> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val end = System.currentTimeMillis()
        val begin = end - days * 86_400_000L
        val counts = HashMap<String, Int>()
        val last = HashMap<String, Long>()
        val events = usm.queryEvents(begin, end)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                counts[e.packageName] = (counts[e.packageName] ?: 0) + 1
                last[e.packageName] = e.timeStamp
            }
        }
        val launcher = launcherApps(context).map { it.pkg }.toSet()
        return counts
            .filterKeys { it in launcher && it != context.packageName }
            .map { (pkg, c) -> LaunchStats(pkg, c, last[pkg] ?: 0) }
            .sortedWith(compareByDescending<LaunchStats> { it.count }.thenByDescending { it.lastUsed })
            .take(limit)
    }

    /** 带 30s 缓存的高频统计，避免服务端频繁调用时重复扫 UsageEvents */
    fun usageStatsCached(context: Context): List<LaunchStats> {
        val now = System.currentTimeMillis()
        if (now - cacheTime > 30_000) {
            cacheStats = usageStats(context)
            cacheTime = now
        }
        return cacheStats
    }

    // ---------- 网页处理应用 ----------

    /**
     * 探测域名集合：覆盖主流中文互联网站点 + 通用域名。
     * 只用 example.com 查询只能命中"通配所有 host"的浏览器；
     * 京东/淘宝/微信等注册了特定 host 的 App 必须按各自域名查询才能枚举出来。
     */
    private val PROBE_HOSTS = listOf(
        "example.com", "jd.com", "taobao.com", "tmall.com", "weixin.qq.com",
        "bilibili.com", "douyin.com", "zhihu.com", "weibo.com", "qq.com",
        "baidu.com", "meituan.com", "ele.me", "microsoft.com", "google.com",
    )

    private fun queryViewHandlers(context: Context, url: String): Set<String> {
        val pm = context.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(url)), 0)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .toSet()
    }

    @Volatile private var handlersCacheTime = 0L
    @Volatile private var handlersCache: List<WebHandler> = emptyList()

    fun webHandlers(context: Context): List<WebHandler> {
        val now = System.currentTimeMillis()
        if (now - handlersCacheTime < 30_000 && handlersCache.isNotEmpty()) return handlersCache
        val pm = context.packageManager
        // 30 次 queryIntentActivities（15 host × 2 scheme），约百毫秒级，可接受
        val https = PROBE_HOSTS.flatMap { queryViewHandlers(context, "https://$it") }.toSet()
        val http = PROBE_HOSTS.flatMap { queryViewHandlers(context, "http://$it") }.toSet()
        val result = (https + http).mapNotNull { pkg ->
            runCatching {
                WebHandler(
                    pkg = pkg,
                    label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(),
                    https = pkg in https,
                    http = pkg in http,
                )
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
        handlersCache = result
        handlersCacheTime = now
        return result
    }

    /** 针对具体 URL 解析能打开它的应用（含 App Links 精确匹配） */
    fun resolveHandlers(context: Context, url: String): List<WebHandler> {
        val pm = context.packageManager
        return queryViewHandlers(context, url).mapNotNull { pkg ->
            runCatching {
                WebHandler(
                    pkg = pkg,
                    label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(),
                    https = true,
                    http = true,
                )
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(pkg, 0)
        ).toString()
    }.getOrDefault(pkg)
}
