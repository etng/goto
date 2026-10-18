package cn.y10n.gotoapp

import cn.y10n.gotoapp.data.Config
import cn.y10n.gotoapp.data.QuickLink
import cn.y10n.gotoapp.data.SettingsConfig
import cn.y10n.gotoapp.data.toConfig
import cn.y10n.gotoapp.data.toJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigJsonTest {

    @Test
    fun roundTrip() {
        val cfg = Config(
            token = "123456",
            webFavorites = listOf("com.android.chrome", "com.miui.browser"),
            pinnedApps = listOf("com.tencent.mm"),
            quickLinks = listOf(QuickLink("京东", "https://www.jd.com")),
            settings = listOf(
                SettingsConfig("dev_options", true),
                SettingsConfig("wifi", false),
            ),
        )
        val back = cfg.toJson().toConfig()
        assertEquals(cfg, back)
    }

    @Test
    fun defaultHasSixDigitToken() {
        val cfg = Config.default()
        assertEquals(6, cfg.token.length)
        assertTrue(cfg.token.all { it.isDigit() })
    }

    @Test
    fun emptyJsonFallsBackToDefaults() {
        val back = JSONObject("{}").toConfig()
        assertEquals(6, back.token.length)
        assertEquals(emptyList<String>(), back.webFavorites)
        assertEquals(emptyList<String>(), back.pinnedApps)
        assertTrue(back.settings.isNotEmpty())
    }

    @Test
    fun tokenAlwaysGeneratedWhenMissing() {
        val cfg = Config(token = "", webFavorites = emptyList(), pinnedApps = emptyList(),
            quickLinks = emptyList(), settings = emptyList())
        val back = cfg.toJson().toConfig()
        assertEquals(6, back.token.length)
    }
}
