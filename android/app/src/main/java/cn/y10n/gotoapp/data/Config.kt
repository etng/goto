package cn.y10n.gotoapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class QuickLink(val label: String, val url: String)

data class SettingsConfig(val id: String, val enabled: Boolean)

/**
 * 应用配置：token 为局域网访问口令，其余为可管理项。
 * 序列化用 org.json（Android 内置，不引入额外依赖）。
 */
data class Config(
    val token: String,
    val webFavorites: List<String>,   // 网页处理应用收藏（有序）
    val pinnedApps: List<String>,     // 固定到主页的应用（有序）
    val quickLinks: List<QuickLink>,  // 网页快捷链接
    val settings: List<SettingsConfig>, // 系统设置快捷项开关
) {
    companion object {
        fun randomToken(): String =
            (SecureRandom().nextInt(900000) + 100000).toString()

        val defaultQuickLinks: List<QuickLink> = listOf(
            QuickLink("京东", "https://www.jd.com"),
            QuickLink("淘宝", "https://www.taobao.com"),
        )

        val defaultSettings: List<SettingsConfig> =
            SettingsCatalog.all.map { SettingsConfig(it.id, it.enabledByDefault) }

        fun default(): Config = Config(
            token = randomToken(),
            webFavorites = emptyList(),
            pinnedApps = emptyList(),
            quickLinks = defaultQuickLinks,
            settings = defaultSettings,
        )
    }
}

// ---------- JSON ----------

private fun List<String>.toJsonArray(): JSONArray = JSONArray(this)

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).map { getString(it) }

fun Config.toJson(): JSONObject = JSONObject().apply {
    put("token", token)
    put("webFavorites", webFavorites.toJsonArray())
    put("pinnedApps", pinnedApps.toJsonArray())
    put("quickLinks", JSONArray(quickLinks.map {
        JSONObject().put("label", it.label).put("url", it.url)
    }))
    put("settings", JSONArray(settings.map {
        JSONObject().put("id", it.id).put("enabled", it.enabled)
    }))
}

fun JSONObject.toConfig(): Config = Config(
    token = optString("token").ifEmpty { Config.randomToken() },
    webFavorites = optJSONArray("webFavorites")?.toStringList() ?: emptyList(),
    pinnedApps = optJSONArray("pinnedApps")?.toStringList() ?: emptyList(),
    quickLinks = optJSONArray("quickLinks")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            QuickLink(o.optString("label"), o.optString("url"))
        }
    } ?: Config.defaultQuickLinks,
    settings = optJSONArray("settings")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            SettingsConfig(o.optString("id"), o.optBoolean("enabled", true))
        }
    } ?: Config.defaultSettings,
)
