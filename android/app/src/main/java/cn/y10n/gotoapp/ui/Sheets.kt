package cn.y10n.gotoapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.y10n.gotoapp.data.AppRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val StarColor = Color(0xFFF59E0B)

/**
 * 多收藏选择 Sheet：只列出收藏的可用应用（不复刻系统"全部列出"）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSheet(
    pkgs: List<String>,
    favorites: Set<String>,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "选择打开方式（仅收藏应用）",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn {
            items(pkgs, key = { it }) { pkg ->
                val fav = pkg in favorites
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(pkg) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(pkg, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            AppRepo.label(context, pkg),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onToggleFavorite(pkg) }) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = if (fav) "取消收藏" else "收藏",
                            tint = if (fav) StarColor else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 网页处理应用管理 Sheet：全部可处理网页的应用，星标收藏 + 排序。
 * 收藏列表顺序即打开优先级。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandlersSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    var handlers by remember { mutableStateOf<List<AppRepo.WebHandler>>(emptyList()) }

    LaunchedEffect(Unit) {
        handlers = withContext(Dispatchers.IO) { AppRepo.webHandlers(context) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "网页处理应用",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            "打星收藏后，打开网页只会列出这些应用（可上下调整优先级）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Column(Modifier.fillMaxWidth().height(420.dp)) {
            if (handlers.isEmpty()) {
                Text("未找到可打开网页的应用", Modifier.padding(20.dp))
            } else {
                LazyColumn {
                    items(handlers, key = { it.pkg }) { h ->
                        val fav = h.pkg in config.webFavorites
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(h.pkg, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.label, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(h.pkg, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (fav) {
                                IconButton(onClick = {
                                    vm.updateConfig { cfg ->
                                        val l = cfg.webFavorites.toMutableList()
                                        val i = l.indexOf(h.pkg)
                                        if (i > 0) { l[i] = l[i - 1]; l[i - 1] = h.pkg }
                                        cfg.copy(webFavorites = l)
                                    }
                                }) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    vm.updateConfig { cfg ->
                                        val l = cfg.webFavorites.toMutableList()
                                        val i = l.indexOf(h.pkg)
                                        if (i in 0 until l.size - 1) { l[i] = l[i + 1]; l[i + 1] = h.pkg }
                                        cfg.copy(webFavorites = l)
                                    }
                                }) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = {
                                vm.updateConfig { cfg ->
                                    if (fav) cfg.copy(webFavorites = cfg.webFavorites - h.pkg)
                                    else cfg.copy(webFavorites = cfg.webFavorites + h.pkg)
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = if (fav) "取消收藏" else "收藏",
                                    tint = if (fav) StarColor else MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
