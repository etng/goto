package cn.y10n.gotoapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.y10n.gotoapp.actions.Actions
import cn.y10n.gotoapp.actions.OpenMode
import cn.y10n.gotoapp.data.AppRepo
import cn.y10n.gotoapp.data.Reorder
import cn.y10n.gotoapp.data.SettingsCatalog
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: MainViewModel, onPickApps: () -> Unit) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()

    var urlInput by remember { mutableStateOf("") }
    var chooserPkgs by remember { mutableStateOf<List<String>>(emptyList()) }
    var showChooser by remember { mutableStateOf(false) }
    var showHandlers by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<String?>(null) }
    var editingPinnedApps by remember { mutableStateOf(false) }

    fun openWeb(raw: String) {
        val url = Actions.normalizeUrl(raw)
        if (url.isEmpty()) return
        val pick = Actions.openUrl(context, url, config.webFavorites)
        when (pick.mode) {
            OpenMode.AUTO -> Actions.toast(context, "使用 ${AppRepo.label(context, pick.candidates.first())} 打开")
            OpenMode.CHOOSER -> {
                chooserPkgs = pick.candidates
                showChooser = true
            }
            OpenMode.DEFAULT -> Actions.toast(context, "已交给系统默认打开（可在下方收藏常用应用）")
        }
    }

    val enabledSettings = remember(config) {
        val enabledIds = config.settings.filter { it.enabled }.map { it.id }.toSet()
        SettingsCatalog.available().filter { it.id in enabledIds }
    }
    val quickLinks = remember(config) { config.quickLinks }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Goto") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onPickApps) {
                Icon(Icons.Filled.Add, contentDescription = "添加应用")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ① 固定应用（默认网格；编辑模式下拖放排序 / 移除）
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("固定应用")
                    if (editingPinnedApps) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "拖动排序 · 点击移除",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (config.pinnedApps.isNotEmpty() || editingPinnedApps) {
                        TextButton(onClick = { editingPinnedApps = !editingPinnedApps }) {
                            Text(if (editingPinnedApps) "完成" else "调整顺序")
                        }
                    }
                }
            }
            if (config.pinnedApps.isEmpty()) {
                item {
                    Text(
                        "点击右下角 + ，从常用 / 新安装 / 全部应用中选择",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    DraggablePinnedGrid(
                        pkgs = config.pinnedApps,
                        editing = editingPinnedApps,
                        onMove = { from, to ->
                            vm.updateConfig { it.copy(pinnedApps = Reorder.move(it.pinnedApps, from, to)) }
                        },
                        onRemove = { pkg -> pendingRemove = pkg },
                        onLaunch = { pkg -> Actions.launchApp(context, pkg) },
                    )
                }
            }

            // ② 系统设置快捷（自动换行，一目了然）
            item { SectionTitle("系统设置快捷") }
            item {
                if (enabledSettings.isEmpty()) {
                    Text("无启用的设置项，可在局域网管理页配置", style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        enabledSettings.forEach { entry ->
                            SectionChip(entry.label, onClick = { Actions.openSettings(context, entry.id) })
                        }
                    }
                }
            }

            // ③ 授权提示
            if (!vm.usageGranted) item { UsageBanner(vm) }

            // ④ 网页跳转
            item { SectionTitle("网页跳转") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入网址，如 jd.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { openWeb(urlInput) }) { Text("打开") }
                }
            }
            if (quickLinks.isNotEmpty()) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        quickLinks.forEach { link ->
                            SectionChip("${link.label} ↗", onClick = { openWeb(link.url) })
                        }
                    }
                }
            }

            item {
                TextButton(onClick = { showHandlers = true }) {
                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("管理网页打开应用")
                }
            }

            // ⑤ 局域网配置（连接信息置于页面底部）
            item { ServerCard(vm) }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showChooser && chooserPkgs.isNotEmpty()) {
        WebSheet(
            pkgs = chooserPkgs,
            favorites = config.webFavorites.toSet(),
            onOpen = { pkg ->
                showChooser = false
                Actions.launchApp(context, pkg)
            },
            onToggleFavorite = { pkg ->
                vm.updateConfig { cfg ->
                    val favs = cfg.webFavorites
                    if (pkg in favs) cfg.copy(webFavorites = favs - pkg) else cfg.copy(webFavorites = favs + pkg)
                }
            },
            onDismiss = { showChooser = false },
        )
    }
    if (showHandlers) {
        HandlersSheet(
            vm = vm,
            onDismiss = { showHandlers = false },
        )
    }
    pendingRemove?.let { pkg ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("移除固定应用？") },
            text = { Text(AppRepo.label(context, pkg)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateConfig { it.copy(pinnedApps = it.pinnedApps - pkg) }
                    pendingRemove = null
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("取消") }
            },
        )
    }
}

/**
 * 固定应用网格：普通模式点击启动；编辑模式拖动排序并可移除。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DraggablePinnedGrid(
    pkgs: List<String>,
    editing: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (String) -> Unit,
    onLaunch: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val minCellWidth = 72.dp
    val cellHeight = 88.dp
    val cellHeightPx = with(density) { cellHeight.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxWidth().animateContentSize()) {
        val minCellWidthPx = with(density) { minCellWidth.roundToPx() }
        val columnCount = (constraints.maxWidth / minCellWidthPx).coerceAtLeast(1)
        val cellWidthPx = constraints.maxWidth / columnCount
        val cellWidth = with(density) { cellWidthPx.toDp() }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = columnCount,
        ) {
            pkgs.forEachIndexed { index, pkg ->
                val isDragging = dragging == pkg
                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .height(cellHeight)
                        .offset {
                            IntOffset(
                                x = if (isDragging) dragOffsetX.roundToInt() else 0,
                                y = if (isDragging) dragOffsetY.roundToInt() else 0,
                            )
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        .pointerInput(pkg, editing, cellWidthPx, cellHeightPx) {
                            if (editing) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = pkg
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffsetX += amount.x
                                        dragOffsetY += amount.y
                                    },
                                    onDragEnd = {
                                        val columnShift = (dragOffsetX / cellWidthPx).roundToInt()
                                        val rowShift = (dragOffsetY / cellHeightPx).roundToInt()
                                        val target = (index + rowShift * columnCount + columnShift)
                                            .coerceIn(0, pkgs.lastIndex)
                                        if (target != index) onMove(index, target)
                                        dragging = null
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    },
                                )
                            }
                        }
                        .clickable {
                            if (editing) onRemove(pkg) else onLaunch(pkg)
                        }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        AppIcon(pkg, 48.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            AppRepo.label(context, pkg),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (editing) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "拖动排序",
                            modifier = Modifier.align(Alignment.TopStart).size(18.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(vm: MainViewModel) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("局域网配置", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(6.dp))
            if (vm.serverUrl.isEmpty()) {
                Text("局域网服务不可用，请检查 WiFi 连接或端口占用", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    vm.serverUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "口令：${config.token}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { copyToClipboard(context, vm.serverUrl) }) { Text("复制地址") }
                    TextButton(onClick = { copyToClipboard(context, config.token) }) { Text("复制口令") }
                    TextButton(onClick = { vm.rotateToken() }) { Text("重置口令") }
                }
            }
        }
    }
}

@Composable
private fun UsageBanner(vm: MainViewModel) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("需要「使用情况访问」权限", style = MaterialTheme.typography.bodyMedium)
                Text("用于读取最近使用的高频应用", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = { Actions.openSettings(context, "usage_access") }) { Text("去开启") }
        }
    }
}
