package cn.y10n.gotoapp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.y10n.gotoapp.data.Config
import cn.y10n.gotoapp.data.ConfigStore
import cn.y10n.gotoapp.server.LanServer
import cn.y10n.gotoapp.util.NetworkUtils
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _config = ConfigStore.state
    val config: StateFlow<Config> = _config.asStateFlow()

    var serverUrl by mutableStateOf("")
        private set
    var usageGranted by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        serverUrl = LanServer.url()
        usageGranted = NetworkUtils.usageAccessGranted(getApplication())
    }

    fun updateConfig(transform: (Config) -> Config) {
        viewModelScope.launch { ConfigStore.save(transform(ConfigStore.state.value)) }
    }

    fun rotateToken() {
        viewModelScope.launch { ConfigStore.save(ConfigStore.state.value.copy(token = Config.randomToken())) }
    }
}
