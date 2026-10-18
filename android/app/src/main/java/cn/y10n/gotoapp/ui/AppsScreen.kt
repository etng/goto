package cn.y10n.gotoapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.y10n.gotoapp.actions.Actions
import cn.y10n.gotoapp.data.AppRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var all by remember { mutableStateOf<List<AppRepo.AppInfo>>(emptyList()) }
    var frequent by remember { mutableStateOf<List<AppRepo.LaunchStats>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            all = AppRepo.launcherApps(context)
            frequent = AppRepo.usageStatsCached(context)
        }
        loading = false
    }

    val now = System.currentTimeMillis()
    val newInstalled = remember(all) {
        all.filter { now - it.firstInstall < 7 * 86_400_000L }
    }
    val filtered = remember(all, query) {
        if (query.isBlank()) all
        else all.filter { it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加应用") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索应用名称或包名") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("常用") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("新安装") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("全部") })
            }
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                tab == 0 && frequent.isEmpty() -> EmptyHint(
                    if (vm.usageGranted) "暂无使用记录，先去用用想加的应用吧"
                    else "未授权「使用情况访问」，无法读取高频应用"
                )
                tab == 1 && newInstalled.isEmpty() -> EmptyHint("近 7 天没有新安装的应用")
                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    val items = when (tab) {
                        0 -> frequent.map { it.pkg to it.count }
                        1 -> newInstalled.map { it.pkg to 0 }
                        else -> filtered.map { it.pkg to 0 }
                    }
                    items(items, key = { it.first }) { (pkg, count) ->
                        AppRow(
                            pkg = pkg,
                            count = count,
                            pinned = pkg in config.pinnedApps,
                            onToggle = {
                                vm.updateConfig { cfg ->
                                    if (pkg in cfg.pinnedApps) cfg.copy(pinnedApps = cfg.pinnedApps - pkg)
                                    else cfg.copy(pinnedApps = cfg.pinnedApps + pkg)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppRow(pkg: String, count: Int, pinned: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(pkg, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                AppRepo.label(context, pkg),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (count > 0) "$pkg · 近7天 $count 次" else pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onToggle) {
            Text(if (pinned) "已固定" else "固定")
        }
    }
}
