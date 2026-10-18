package cn.y10n.gotoapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class QuickLink(val label: String, val url: String)

/**
 * 系统设置快捷项：改为完整可编辑的数据（不再只是开关）。
 * 见 [SettingsCatalog.Entry]。
 */
typealias SettingsEntry = SettingsCatalog.Entry

/**
 * 应用配置：token 为局域网访问口令，其余为可管理项。
 * 序列化用 org.json（Android 内置，不引入额外依赖）。
 */
data class Config(
    val token: String,
    val webFavorites: List<String>,   // 网页处理应用收藏（有序）
    val pinnedApps: List<String>,     // 固定到主页的应用（有序）
    val quickLinks: List<QuickLink>,  // 网页快捷链接
    val settings: List<SettingsEntry>, // 系统设置快捷项（有序、可勾选/自定义）
) {
    companion object {
        fun randomToken(): String =
            (SecureRandom().nextInt(900000) + 100000).toString()

        val defaultQuickLinks: List<QuickLink> = listOf(
            QuickLink("京东", "https://www.jd.com"),
            QuickLink("淘宝", "https://www.taobao.com"),
        )

        val defaultSettings: List<SettingsEntry> = SettingsCatalog.seed()

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
        JSONObject().put("id", it.id).put("label", it.label).put("action", it.action)
            .put("data", it.data).put("builtin", it.builtin).put("enabled", it.enabled)
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
            parseSettingsEntry(o)
        }
    } ?: Config.defaultSettings,
)

/**
 * 解析单个设置项，兼容两种格式：
 *  - 新格式：含 action 字段 → 直接使用。
 *  - 旧格式：仅 id + enabled（无 action）→ 经 [SettingsCatalog.legacyAction] 映射到 action，
 *    迁移时保留启用状态与顺序。无法识别的旧 id 直接丢弃。
 */
private fun parseSettingsEntry(o: JSONObject): SettingsEntry? {
    val action = o.optString("action").ifBlank {
        SettingsCatalog.legacyAction(o.optString("id")) ?: return null
    }
    val id = o.optString("id").ifBlank { action }
    val label = o.optString("label").ifBlank { action.substringAfterLast('.') }
    return SettingsEntry(
        id = id,
        label = label,
        action = action,
        data = o.optString("data"),
        builtin = o.optBoolean("builtin", true),
        enabledByDefault = o.optBoolean("enabledByDefault", false),
        enabled = o.optBoolean("enabled", true),
    )
}
