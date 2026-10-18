package cn.y10n.gotoapp.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * 系统设置入口目录。
 *
 * 设计：内置入口不再靠人硬编码维护，而是在运行时通过反射枚举
 * [Settings] 中所有 `ACTION_*` 常量，再用 [PackageManager] 过滤掉本机
 * 无法解析的（自动适配不同 ROM / API 版本）。用户只需勾选、排序即可。
 *
 * 仅有少数"非标准"入口（如直接安装证书）不在 [Settings] 的 ACTION 体系内，
 * 放在 [SUGGESTED] 作为推荐，由用户按需添加；其余完全免维护。
 */
object SettingsCatalog {

    data class Entry(
        val id: String,
        val label: String,
        val action: String,
        val data: String = "",
        /** true=扫描/内置，false=用户自定义或推荐项 */
        val builtin: Boolean = true,
        val enabledByDefault: Boolean = false,
        /** 当前是否启用（写入配置时的真实状态） */
        val enabled: Boolean = false,
    )

    // 默认启用的若干项（与旧版体验一致）；其余在运行时扫描补充
    private val SEED: List<Entry> = listOf(
        e("android.settings.APPLICATION_DEVELOPMENT_SETTINGS", "开发者选项", true),
        e("android.settings.WIRELESS_DEBUGGING_SETTINGS", "无线调试", true),
        e("android.settings.USAGE_ACCESS_SETTINGS", "使用情况访问", true),
        e("android.settings.APPLICATION_SETTINGS", "应用管理", true),
        e("android.settings.ALL_APPS_NOTIFICATION_SETTINGS", "通知管理", true),
        e("android.settings.WIFI_SETTINGS", "WiFi", true),
        e("android.settings.BLUETOOTH_SETTINGS", "蓝牙", true),
        e("android.settings.LOCATION_SOURCE_SETTINGS", "位置信息", true),
        e("android.settings.INTERNAL_STORAGE_SETTINGS", "存储", true),
        e("android.settings.DISPLAY_SETTINGS", "显示", false),
        e("android.settings.SOUND_SETTINGS", "声音", false),
        e("android.settings.BATTERY_SAVER_SETTINGS", "电池", false),
        e("android.settings.LOCALE_SETTINGS", "语言", false),
        e("android.settings.SECURITY_SETTINGS", "安全", false),
        e("android.settings.DATA_USAGE_SETTINGS", "流量使用", false),
        e("android.settings.ACCESSIBILITY_SETTINGS", "无障碍", false),
        e("android.settings.HOME_SETTINGS", "桌面与手势", false),
        e("android.settings.APPLICATION_DETAILS_SETTINGS", "本应用信息", false, "package:{pkg}"),
    )

    private fun e(action: String, label: String, enabledByDefault: Boolean, data: String = ""): Entry =
        Entry(id = action, label = label, action = action, data = data,
            builtin = true, enabledByDefault = enabledByDefault)

    /** 非标准但常用的推荐入口（不在 Settings.ACTION_* 体系内） */
    val SUGGESTED: List<Entry> = listOf(
        Entry("android.credentials.INSTALL", "安装证书 / 凭据", "android.credentials.INSTALL", builtin = false),
    )

    /** 默认配置：种子项按 enabledByDefault 展开 */
    fun seed(): List<Entry> = SEED.map { it.copy(enabled = it.enabledByDefault) }

    /** 旧版短 id → 新 action 的映射，用于配置迁移 */
    private val LEGACY_ID_TO_ACTION: Map<String, String> = mapOf(
        "dev_options" to "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
        "wireless_debug" to "android.settings.WIRELESS_DEBUGGING_SETTINGS",
        "usage_access" to "android.settings.USAGE_ACCESS_SETTINGS",
        "app_list" to "android.settings.APPLICATION_SETTINGS",
        "notifications" to "android.settings.ALL_APPS_NOTIFICATION_SETTINGS",
        "wifi" to "android.settings.WIFI_SETTINGS",
        "bluetooth" to "android.settings.BLUETOOTH_SETTINGS",
        "location" to "android.settings.LOCATION_SOURCE_SETTINGS",
        "storage" to "android.settings.INTERNAL_STORAGE_SETTINGS",
        "display" to "android.settings.DISPLAY_SETTINGS",
        "sound" to "android.settings.SOUND_SETTINGS",
        "battery" to "android.settings.BATTERY_SAVER_SETTINGS",
        "language" to "android.settings.LOCALE_SETTINGS",
        "security" to "android.settings.SECURITY_SETTINGS",
        "data_usage" to "android.settings.DATA_USAGE_SETTINGS",
        "accessibility" to "android.settings.ACCESSIBILITY_SETTINGS",
        "home_settings" to "android.settings.HOME_SETTINGS",
        "app_details" to "android.settings.APPLICATION_DETAILS_SETTINGS",
    )

    fun legacyAction(oldId: String): String? = LEGACY_ID_TO_ACTION[oldId]

    // ---------- 运行时扫描 ----------

    @Volatile private var cache: List<Entry>? = null

    /**
     * 反射枚举 Settings 中全部 ACTION_* 常量，过滤本机可解析的。
     * 结果按标签排序并缓存（仅依赖设备与 PM，跨配置稳定）。
     */
    fun discover(context: Context): List<Entry> {
        cache?.let { return it }
        val pm = context.packageManager
        val actions = LinkedHashSet<String>()
        for (clazz in arrayOf<Class<*>>(
            Settings::class.java,
            Settings.Global::class.java,
            Settings.Secure::class.java,
            Settings.System::class.java,
        )) {
            runCatching {
                clazz.fields.forEach { f ->
                    if (f.type == String::class.java && f.name.startsWith("ACTION_")) {
                        (f.get(null) as? String)?.let { actions.add(it) }
                    }
                }
            }
        }
        val list = actions.mapNotNull { action ->
            val intent = Intent(action)
            if (pm.queryIntentActivities(intent, 0).isEmpty()) return@mapNotNull null
            Entry(id = action, label = labelFor(action), action = action, builtin = true)
        }.sortedBy { it.label }
        cache = list
        return list
    }

    // ---------- 中文标签 ----------

    private val LABELS: Map<String, String> = mapOf(
        "android.settings.APPLICATION_DEVELOPMENT_SETTINGS" to "开发者选项",
        "android.settings.WIRELESS_DEBUGGING_SETTINGS" to "无线调试",
        "android.settings.USAGE_ACCESS_SETTINGS" to "使用情况访问",
        "android.settings.APPLICATION_SETTINGS" to "应用管理",
        "android.settings.MANAGE_APPLICATIONS_SETTINGS" to "管理应用",
        "android.settings.MANAGE_DEFAULT_APPS_SETTINGS" to "默认应用",
        "android.settings.ALL_APPS_NOTIFICATION_SETTINGS" to "通知管理",
        "android.settings.APP_NOTIFICATION_SETTINGS" to "应用通知",
        "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS" to "勿扰权限",
        "android.settings.ZEN_MODE_SETTINGS" to "勿扰模式",
        "android.settings.CHANNEL_NOTIFICATION_SETTINGS" to "通知渠道",
        "android.settings.WIFI_SETTINGS" to "WiFi",
        "android.settings.WIFI_IP_SETTINGS" to "WiFi IP 设置",
        "android.settings.BLUETOOTH_SETTINGS" to "蓝牙",
        "android.settings.LOCATION_SOURCE_SETTINGS" to "位置信息",
        "android.settings.LOCATION_SCANNING_SETTINGS" to "位置扫描",
        "android.settings.INTERNAL_STORAGE_SETTINGS" to "存储",
        "android.settings.MEMORY_CARD_SETTINGS" to "存储卡",
        "android.settings.DISPLAY_SETTINGS" to "显示",
        "android.settings.SOUND_SETTINGS" to "声音",
        "android.settings.BATTERY_SAVER_SETTINGS" to "电池节省",
        "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS" to "电池优化",
        "android.settings.LOCALE_SETTINGS" to "语言",
        "android.settings.SECURITY_SETTINGS" to "安全",
        "android.settings.ENCRYPTION_AND_CREDENTIAL_SETTINGS" to "加密与凭据",
        "android.settings.DATE_SETTINGS" to "日期和时间",
        "android.settings.DEVICE_INFO_SETTINGS" to "关于手机",
        "android.settings.USER_SETTINGS" to "用户",
        "android.settings.SYNC_SETTINGS" to "账号同步",
        "android.settings.ADD_ACCOUNT_SETTINGS" to "添加账号",
        "android.settings.PRIVACY_SETTINGS" to "隐私",
        "android.settings.ACCESSIBILITY_SETTINGS" to "无障碍",
        "android.settings.CAPTIONING_SETTINGS" to "字幕",
        "android.settings.INPUT_METHOD_SETTINGS" to "输入法",
        "android.settings.VOICE_INPUT_SETTINGS" to "语音输入",
        "android.settings.PRINT_SETTINGS" to "打印",
        "android.settings.NFC_SETTINGS" to "NFC",
        "android.settings.NFC_SHARING_SETTINGS" to "NFC 共享",
        "android.settings.VPN_SETTINGS" to "VPN",
        "android.settings.AIRPLANE_MODE_SETTINGS" to "飞行模式",
        "android.settings.TETHER_SETTINGS" to "网络共享 / 热点",
        "android.settings.DATA_USAGE_SETTINGS" to "流量使用",
        "android.settings.NETWORK_OPERATOR_SETTINGS" to "网络运营商",
        "android.settings.HOME_SETTINGS" to "桌面与手势",
        "android.settings.APPLICATION_DETAILS_SETTINGS" to "应用详情",
        "android.settings.NIGHT_DISPLAY_SETTINGS" to "护眼模式",
        "android.settings.BIOMETRIC_ENROLL" to "生物识别录入",
        "android.settings.FACE_ENROLL" to "人脸录入",
        "android.settings.DREAM_SETTINGS" to "屏保",
        "android.settings.QUICK_ACCESS_WALLET_SETTINGS" to "快捷钱包",
        "android.settings.HARD_KEYBOARD_SETTINGS" to "外接键盘",
        "android.settings.ACTION_MANAGE_WRITE_SETTINGS" to "修改系统设置权限",
        "android.settings.MANAGE_UNKNOWN_APP_SOURCES" to "未知来源应用",
        "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to "忽略电池优化(指定应用)",
    )

    private fun labelFor(action: String): String =
        LABELS[action] ?: action.substringAfterLast('.')
            .replace('_', ' ')
            .lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
}
