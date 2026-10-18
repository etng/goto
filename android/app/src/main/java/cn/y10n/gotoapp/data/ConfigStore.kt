package cn.y10n.gotoapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.y10n.gotoapp.GotoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 配置持久化：内存态（StateFlow）+ DataStore 落盘。
 * 服务端与 UI 同进程读写，单例足够。
 */
object ConfigStore {

    private const val KEY = "json"
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "goto_config")

    val state = MutableStateFlow(Config.default())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: DataStore<Preferences>

    fun init(context: Context) {
        store = context.applicationContext.dataStore
        scope.launch {
            runCatching {
                val json = store.data.first()[stringPreferencesKey(KEY)]
                if (json != null) {
                    state.value = org.json.JSONObject(json).toConfig()
                } else {
                    // 首次运行：立即落盘默认配置，保证访问口令等跨重启稳定
                    save(Config.default())
                }
            }
        }
    }

    suspend fun save(cfg: Config) {
        state.value = cfg
        runCatching {
            store.edit { it[stringPreferencesKey(KEY)] = cfg.toJson().toString() }
        }
    }
}
