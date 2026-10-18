package cn.y10n.gotoapp.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import cn.y10n.gotoapp.data.AppRepo
import cn.y10n.gotoapp.data.SettingsCatalog
import java.net.URLEncoder

/**
 * 核心跳转动作 + 网页打开决策逻辑（纯函数，可单测）。
 */

enum class OpenMode { AUTO, CHOOSER, DEFAULT }

data class Pick(val mode: OpenMode, val candidates: List<String>)

object WebPick {
    /**
     * 收藏优先决策：
     *  - 恰 1 个收藏可打开 -> AUTO 直接打开
     *  - 多个收藏可打开   -> CHOOSER 弹出"只列收藏"的选择器
     *  - 无收藏可打开     -> DEFAULT 交给系统默认处理
     */
    fun decide(handlers: List<String>, favorites: List<String>): Pick {
        // 按收藏顺序过滤（收藏顺序即打开优先级），而非应用列表顺序
        val fav = favorites.filter { it in handlers }
        return when {
            fav.size == 1 -> Pick(OpenMode.AUTO, fav)
            fav.size > 1 -> Pick(OpenMode.CHOOSER, fav)
            else -> Pick(OpenMode.DEFAULT, handlers)
        }
    }
}

object Actions {

    fun normalizeUrl(raw: String): String {
        val s = raw.trim()
        return when {
            s.isEmpty() -> s
            s.startsWith("http://") || s.startsWith("https://") -> s
            else -> "https://$s"
        }
    }

    /** 启动应用，成功返回 true */
    fun launchApp(context: Context, pkg: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launch)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 打开设置页。按配置中的 [SettingsCatalog.Entry] 解析 Intent（action + 可选 data）。
     * @param entries 当前配置里的设置项列表（用于按 id 查找与无线调试回退）
     */
    fun openSettings(context: Context, id: String, entries: List<SettingsCatalog.Entry>): Boolean {
        val entry = entries.firstOrNull { it.id == id } ?: return false
        val intent = buildSettingsIntent(context, entry).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            if (entry.action == "android.settings.WIRELESS_DEBUGGING_SETTINGS") {
                entries.firstOrNull { it.action == "android.settings.APPLICATION_DEVELOPMENT_SETTINGS" }
                    ?.let { dev ->
                        return runCatching {
                            context.startActivity(
                                buildSettingsIntent(context, dev).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            true
                        }.getOrDefault(false)
                    }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /** 由配置项构造 Intent；data 中的 {pkg} 占位符替换为当前应用包名 */
    private fun buildSettingsIntent(context: Context, e: SettingsCatalog.Entry): Intent =
        Intent(e.action).apply {
            val data = e.data.replace("{pkg}", context.packageName)
            if (data.isNotBlank()) this.data = Uri.parse(data)
        }

    /**
     * 按收藏规则打开网页。
     * @return 决策结果；CHOOSER 时调用方需展示选择器（candidates 即收藏候选）
     */
    fun openUrl(context: Context, url: String, favorites: List<String>): Pick {
        val handlers = AppRepo.resolveHandlers(context, url).map { it.pkg }
        val pick = WebPick.decide(handlers, favorites)
        when (pick.mode) {
            OpenMode.AUTO -> {
                Actions.launchPackageActivity(context, pick.candidates.first(), url)
            }
            OpenMode.DEFAULT -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
            OpenMode.CHOOSER -> Unit // 由调用方展示 UI
        }
        return pick
    }

    /** 服务端无 UI 场景：多收藏时取第一个 */
    fun openUrlServer(context: Context, url: String, favorites: List<String>): Pair<String, String> {
        val handlers = AppRepo.resolveHandlers(context, url).map { it.pkg }
        val pick = WebPick.decide(handlers, favorites)
        return when (pick.mode) {
            OpenMode.AUTO -> {
                launchPackageActivity(context, pick.candidates.first(), url)
                "auto" to pick.candidates.first()
            }
            OpenMode.CHOOSER -> {
                launchPackageActivity(context, pick.candidates.first(), url)
                "chooser_first" to pick.candidates.first()
            }
            OpenMode.DEFAULT -> {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                "default" to ""
            }
        }
    }

    fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    private fun launchPackageActivity(context: Context, pkg: String, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Actions.launchApp(context, pkg) }
    }

    fun encodeQuery(v: String): String = URLEncoder.encode(v, "UTF-8")
}
