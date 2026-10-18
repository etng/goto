package cn.y10n.gotoapp.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import cn.y10n.gotoapp.actions.Actions
import cn.y10n.gotoapp.data.AppRepo
import cn.y10n.gotoapp.data.Config
import cn.y10n.gotoapp.data.ConfigStore
import cn.y10n.gotoapp.data.QuickLink
import cn.y10n.gotoapp.data.SettingsCatalog
import cn.y10n.gotoapp.data.toJson
import cn.y10n.gotoapp.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * 局域网管理服务器：静态托管 Vue 管理页（assets/web）+ REST API。
 * 基于原生 IPv4 ServerSocket 实现的 [HttpServer]。
 * 生命周期随应用进程：进程存活即服务，进程结束即关闭。
 */
object LanServer {
    const val BASE_PORT = 8787
    private const val MAX_OFFSET = 20

    private var server: HttpServer? = null
    private var boundPort: Int = 0

    @Synchronized
    fun start(context: Context) {
        if (server != null) return
        val impl = Impl(context.applicationContext)
        for (offset in 0 until MAX_OFFSET) {
            val port = BASE_PORT + offset
            val http = runCatching { HttpServer(port) { impl.route(it) } }.getOrNull() ?: continue
            val started = runCatching { http.start() }.isSuccess
            if (!started) {
                http.close()
                continue
            }
            server = http
            boundPort = port
            return
        }
    }

    fun url(): String {
        if (server == null || boundPort == 0) return ""
        val ip = NetworkUtils.getIpAddress(cn.y10n.gotoapp.GotoApp.instance)
        return if (ip.isEmpty()) "" else "http://$ip:$boundPort"
    }

    /** 实际绑定的端口（被占用时会顺延），供 /api/info 与 UI 展示 */
    fun port(): Int = boundPort
}

private class Impl(private val ctx: Context) {

    private val pm = ctx.packageManager

    // 应用图标 PNG 缓存（LRU，防 Web 端反复解码）
    private val iconCache = object : LinkedHashMap<String, ByteArray>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) =
            size > 48
    }

    fun route(req: Request): HttpResponse {
        return try {
            val path = req.path
            if (path.startsWith("/api/")) api(req, path.removePrefix("/api/"))
            else static(req, path)
        } catch (e: Exception) {
            HttpResponse.text(500, "application/json; charset=utf-8", """{"error":"${e.message ?: "internal"}"}""")
        }
    }

    // ---------- 静态资源 ----------

    private fun static(req: Request, path: String): HttpResponse {
        val asset = when {
            path == "/" || path.isBlank() -> "web/index.html"
            else -> "web/" + path.trimStart('/')
        }
        val bytes = try {
            ctx.assets.open(asset).use { it.readBytes() }
        } catch (e: Exception) {
            return HttpResponse.text(404, "application/json; charset=utf-8", """{"error":"not found"}""")
        }
        // CORS 头必需：vite 给 module script 打了 crossorigin，无该头脚本被拦截不执行
        return HttpResponse(200, mime(asset), bytes,
            mapOf("Cache-Control" to "no-cache", "Access-Control-Allow-Origin" to "*"))
    }

    private fun mime(name: String): String = when {
        name.endsWith(".html") -> "text/html; charset=utf-8"
        name.endsWith(".js") -> "application/javascript; charset=utf-8"
        name.endsWith(".css") -> "text/css; charset=utf-8"
        name.endsWith(".svg") -> "image/svg+xml"
        name.endsWith(".png") -> "image/png"
        name.endsWith(".ico") -> "image/x-icon"
        name.endsWith(".woff2") -> "font/woff2"
        name.endsWith(".woff") -> "font/woff"
        name.endsWith(".json") -> "application/json; charset=utf-8"
        else -> "application/octet-stream"
    }

    // ---------- API ----------

    private fun api(req: Request, rest: String): HttpResponse {
        if (!authorized(req)) {
            return json(401, JSONObject().put("error", "unauthorized"))
        }
        return when {
            rest == "info" && req.method == "GET" -> info()
            rest == "config" && req.method == "GET" -> configGet()
            rest == "config" && req.method == "PUT" -> configPut(body(req))
            rest == "token" && req.method == "POST" -> rotateToken()
            rest == "apps" && req.method == "GET" -> apps(req)
            rest == "handlers" && req.method == "GET" -> handlers()
            rest.startsWith("icon/") && req.method == "GET" -> icon(rest.removePrefix("icon/"))
            rest == "open/app" && req.method == "POST" -> openApp(body(req))
            rest == "open/url" && req.method == "POST" -> openUrl(body(req))
            rest == "open/settings" && req.method == "POST" -> openSettings(body(req))
            rest == "settings-catalog" && req.method == "GET" -> settingsCatalog()
            else -> json(404, JSONObject().put("error", "no such api"))
        }
    }

    /** 口令校验：header X-Access-Token 或 ?token=（图片请求无法带 header） */
    private fun authorized(req: Request): Boolean {
        val cfg = ConfigStore.state.value
        if (cfg.token.isEmpty()) return true
        val header = req.headers["x-access-token"] ?: req.query["token"] ?: ""
        return header == cfg.token
    }

    private fun body(req: Request): JSONObject {
        val raw = String(req.body, Charsets.UTF_8)
        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    private fun info(): HttpResponse {
        val ip = NetworkUtils.getIpAddress(ctx)
        return json(200, JSONObject()
            .put("appVersion", "0.1.0")
            .put("model", android.os.Build.MODEL)
            .put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("ip", ip)
            .put("port", LanServer.port())
            .put("usageAccess", NetworkUtils.usageAccessGranted(ctx))
            .put("hasToken", ConfigStore.state.value.token.isNotEmpty()))
    }

    private fun configGet(): HttpResponse =
        json(200, ConfigStore.state.value.toJson())

    private fun configPut(b: JSONObject): HttpResponse {
        if (b.length() == 0) {
            // body 为空说明客户端没发对，显式 400 而非"假成功"
            return json(400, JSONObject().put("error", "empty body"))
        }
        val current = ConfigStore.state.value
        val next = Config(
            token = b.optString("token", current.token).ifEmpty { current.token },
            webFavorites = b.optJSONArray("webFavorites")?.toStrList() ?: current.webFavorites,
            pinnedApps = b.optJSONArray("pinnedApps")?.toStrList() ?: current.pinnedApps,
            quickLinks = b.optJSONArray("quickLinks")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { o ->
                        QuickLink(o.optString("label"), o.optString("url"))
                    }
                }
            } ?: current.quickLinks,
            settings = b.optJSONArray("settings")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val action = o.optString("action").ifBlank {
                        SettingsCatalog.legacyAction(o.optString("id")) ?: return@mapNotNull null
                    }
                    SettingsCatalog.Entry(
                        id = o.optString("id").ifBlank { action },
                        label = o.optString("label").ifBlank { action.substringAfterLast('.') },
                        action = action,
                        data = o.optString("data"),
                        builtin = o.optBoolean("builtin", true),
                        enabledByDefault = o.optBoolean("enabledByDefault", false),
                        enabled = o.optBoolean("enabled", true),
                    )
                }
            } ?: current.settings,
        )
        LanServerIo.save(next)
        return json(200, JSONObject().put("ok", true))
    }

    private fun rotateToken(): HttpResponse {
        val next = ConfigStore.state.value.copy(token = Config.randomToken())
        LanServerIo.save(next)
        return json(200, JSONObject().put("token", next.token))
    }

    private fun apps(req: Request): HttpResponse {
        val q = req.query["q"]?.trim()?.lowercase() ?: ""
        val all = AppRepo.launcherApps(ctx)
        val frequent = AppRepo.usageStatsCached(ctx)
        val now = System.currentTimeMillis()
        val freqPkgs = frequent.map { it.pkg }
        val newInstalled = all.filter { now - it.firstInstall < 7 * 86_400_000L }
        val filtered = if (q.isEmpty()) all else all.filter {
            it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q)
        }
        return json(200, JSONObject()
            .put("all", JSONArray(filtered.map {
                JSONObject()
                    .put("pkg", it.pkg)
                    .put("label", it.label)
                    .put("isSystem", it.isSystem)
                    .put("firstInstall", it.firstInstall)
            }))
            .put("frequent", JSONArray(freqPkgs.map { pkg ->
                JSONObject()
                    .put("pkg", pkg)
                    .put("label", AppRepo.label(ctx, pkg))
                    .put("count", frequent.firstOrNull { it.pkg == pkg }?.count ?: 0)
            }))
            .put("new", JSONArray(newInstalled.map {
                JSONObject()
                    .put("pkg", it.pkg)
                    .put("label", it.label)
                    .put("firstInstall", it.firstInstall)
            })))
    }

    private fun handlers(): HttpResponse {
        val cfg = ConfigStore.state.value
        return json(200, JSONObject()
            .put("favorites", JSONArray(cfg.webFavorites))
            .put("handlers", JSONArray(AppRepo.webHandlers(ctx).map {
                JSONObject()
                    .put("pkg", it.pkg)
                    .put("label", it.label)
                    .put("https", it.https)
                    .put("http", it.http)
            })))
    }

    private fun icon(pkg: String): HttpResponse {
        val cached = iconCache[pkg]
        val bytes = cached ?: runCatching {
            val drawable = pm.getApplicationIcon(pkg)
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
            val bmp = if (drawable is BitmapDrawable) drawable.bitmap
                ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            else Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                val c = Canvas(it)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(c)
            }
            java.io.ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        }.getOrNull()
        if (bytes == null) return json(404, JSONObject().put("error", "no icon"))
        iconCache[pkg] = bytes
        return HttpResponse(200, "image/png", bytes, mapOf("Cache-Control" to "max-age=86400"))
    }

    private fun openApp(b: JSONObject): HttpResponse {
        val pkg = b.optString("pkg")
        if (pkg.isEmpty()) return json(400, JSONObject().put("error", "pkg required"))
        val ok = Actions.launchApp(ctx, pkg)
        return json(if (ok) 200 else 404, JSONObject().put("ok", ok).put("pkg", pkg))
    }

    private fun openUrl(b: JSONObject): HttpResponse {
        val raw = b.optString("url")
        if (raw.isBlank()) return json(400, JSONObject().put("error", "url required"))
        val url = Actions.normalizeUrl(raw)
        val (mode, pkg) = Actions.openUrlServer(ctx, url, ConfigStore.state.value.webFavorites)
        return json(200, JSONObject().put("ok", true).put("mode", mode).put("pkg", pkg))
    }

    private fun openSettings(b: JSONObject): HttpResponse {
        val id = b.optString("id")
        if (id.isEmpty()) return json(400, JSONObject().put("error", "id required"))
        val ok = Actions.openSettings(ctx, id, ConfigStore.state.value.settings)
        return json(200, JSONObject().put("ok", ok))
    }

    /** 返回本机可解析的全部设置入口（供 Web 端勾选添加），已加入的标记 added */
    private fun settingsCatalog(): HttpResponse {
        val presets = SettingsCatalog.discover(ctx) + SettingsCatalog.SUGGESTED
        val added = ConfigStore.state.value.settings.map { it.id }.toSet()
        return json(200, JSONObject()
            .put("presets", JSONArray(presets.map { e ->
                JSONObject()
                    .put("id", e.id).put("label", e.label).put("action", e.action)
                    .put("data", e.data).put("builtin", e.builtin)
            }))
            .put("added", JSONArray(added.toList())))
    }

    // ---------- 工具 ----------

    private fun JSONArray.toStrList(): List<String> = (0 until length()).map { getString(it) }

    private fun json(status: Int, obj: JSONObject): HttpResponse =
        HttpResponse.text(status, "application/json; charset=utf-8", obj.toString())
}

/** 与 ConfigStore.save 的桥接（服务器线程 → IO 协程） */
private object LanServerIo {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun save(cfg: Config) {
        scope.launch { ConfigStore.save(cfg) }
    }
}
