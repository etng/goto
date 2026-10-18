package cn.y10n.gotoapp.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build

/**
 * 内置的系统设置快捷项目录（静态），配置里只存开关。
 */
object SettingsCatalog {

    data class Entry(
        val id: String,
        val label: String,
        val intent: (Context) -> Intent,
        val requiresApi: Int = 1,
        val enabledByDefault: Boolean = false,
    )

    val all: List<Entry> = listOf(
        Entry("dev_options", "开发者选项",
            { Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }, enabledByDefault = true),
        Entry("wireless_debug", "无线调试",
            { Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS") }, requiresApi = 30, enabledByDefault = true),
        Entry("usage_access", "使用情况访问",
            { Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS) }, enabledByDefault = true),
        Entry("app_list", "应用管理",
            { Intent(Settings.ACTION_APPLICATION_SETTINGS) }, enabledByDefault = true),
        Entry("notifications", "通知管理",
            { Intent("android.settings.ALL_APPS_NOTIFICATION_SETTINGS") }, requiresApi = 26, enabledByDefault = true),
        Entry("wifi", "WiFi",
            { Intent(Settings.ACTION_WIFI_SETTINGS) }, enabledByDefault = true),
        Entry("bluetooth", "蓝牙",
            { Intent(Settings.ACTION_BLUETOOTH_SETTINGS) }, enabledByDefault = true),
        Entry("location", "位置信息",
            { Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) }, enabledByDefault = true),
        Entry("storage", "存储",
            { Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) }, enabledByDefault = true),
        Entry("display", "显示",
            { Intent(Settings.ACTION_DISPLAY_SETTINGS) }),
        Entry("sound", "声音",
            { Intent(Settings.ACTION_SOUND_SETTINGS) }),
        Entry("battery", "电池",
            { Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS) }),
        Entry("language", "语言",
            { Intent(Settings.ACTION_LOCALE_SETTINGS) }),
        Entry("security", "安全",
            { Intent(Settings.ACTION_SECURITY_SETTINGS) }),
        Entry("data_usage", "流量使用",
            { Intent(Settings.ACTION_DATA_USAGE_SETTINGS) }),
        Entry("accessibility", "无障碍",
            { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }),
        Entry("home_settings", "桌面与手势",
            { Intent(Settings.ACTION_HOME_SETTINGS) }, requiresApi = 30),
        Entry("app_details", "本应用信息",
            { Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.packageName}")) }),
    )

    /** 按当前系统版本过滤后的目录 */
    fun available(): List<Entry> =
        all.filter { it.requiresApi <= Build.VERSION.SDK_INT }

    fun byId(id: String): Entry? = all.firstOrNull { it.id == id }
}
