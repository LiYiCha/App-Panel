package com.panel.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/** 分段规则管理页：规则一等公民（新建/编辑/绑定变量/删除），拆分入口在变量卡「分段」 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvRulesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var refreshKey by remember { mutableIntStateOf(0) }
    // 所有读取统一走 SegRuleStore（内部完成旧 seg_* 格式的幂等迁移）
    val rules = remember(refreshKey) { SegRuleStore.loadRules(context) }
    val bindings = remember(refreshKey) { SegRuleStore.loadBindings(context) }
    val envNames = remember(uiState.envs) {
        uiState.envs.map { it.name }.distinct().filter { it.isNotBlank() }
    }

    var editingRule by remember { mutableStateOf<SegRule?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deletingRule by remember { mutableStateOf<SegRule?>(null) }
    var showHelp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分段规则", fontSize = 15.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 帮助入口：复用底部删除后的使用说明弹窗（空态下也可打开）
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "使用说明")
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新建规则")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (rules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "暂无规则，点右上角新建",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            bindingCount = bindings.values.count { it == rule.id },
                            onClick = { editingRule = rule }
                        )
                    }
                }
            }
        }
    }

    // 编辑规则弹窗
    editingRule?.let { rule ->
        RuleEditDialog(
            initialRule = rule,
            envNames = envNames,
            boundVars = bindings.filterValues { it == rule.id }.keys,
            onConfirm = { updated, added, removed ->
                SegRuleStore.upsertRule(context, updated)
                added.forEach { SegRuleStore.bindRule(context, it, updated.id) }
                removed.forEach { name ->
                    if (bindings[name] == updated.id) SegRuleStore.unbindRule(context, name)
                }
                editingRule = null
                refreshKey++
            },
            onRequestDelete = { deletingRule = rule },
            onDismiss = { editingRule = null }
        )
    }

    // 新建规则弹窗（带内置模板）
    if (showCreateDialog) {
        RuleEditDialog(
            initialRule = null,
            envNames = envNames,
            boundVars = emptySet(),
            onConfirm = { created, added, _ ->
                SegRuleStore.upsertRule(context, created)
                added.forEach { SegRuleStore.bindRule(context, it, created.id) }
                showCreateDialog = false
                refreshKey++
            },
            onRequestDelete = null,
            onDismiss = { showCreateDialog = false }
        )
    }

    // 删除规则确认：仅解绑，不影响变量值
    deletingRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deletingRule = null },
            title = { Text("删除规则", fontSize = 15.sp) },
            text = {
                Text(
                    "确定删除规则「${rule.name}」？仅解绑已绑定的变量，不影响变量值。",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        SegRuleStore.deleteRule(context, rule.id)
                        deletingRule = null
                        editingRule = null
                        refreshKey++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除", fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { deletingRule = null }) { Text("取消", fontSize = 12.sp) }
            }
        )
    }

    if (showHelp) {
        EnvRulesHelpDialog(onDismiss = { showHelp = false })
    }
}

/** 规则卡：规则名 + 摘要 chips（账号 X · 字段 Y）+ 「绑定 n」角标 */
@Composable
private fun RuleCard(
    rule: SegRule,
    bindingCount: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = rule.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SummaryChip("账号 ${acctSepLabel(rule.acct, rule.acctCustom)}")
                    SummaryChip("字段 ${fieldSepLabel(rule.field, rule.fieldCustom)}")
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "绑定 $bindingCount",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun SummaryChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * 规则编辑底部抽屉（新建与编辑共用）：
 * 名称 / 账号分隔 chip 行 / 字段分隔 chip 行 / 绑定变量多选；新建附内置模板。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditDialog(
    initialRule: SegRule?,
    envNames: List<String>,
    boundVars: Set<String>,
    onConfirm: (SegRule, addedVars: List<String>, removedVars: List<String>) -> Unit,
    onRequestDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val isCreate = initialRule == null
    // remember 稳定住初始值：新建模式下每次重组都会生成新 id，直接当 key 会重置输入
    val initial = remember(initialRule) {
        initialRule ?: SegRule(
            name = "",
            acct = ACCT_NONE,
            acctCustom = "",
            field = FIELD_AUTO,
            fieldCustom = ""
        )
    }
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var acct by remember(initial.id) { mutableStateOf(initial.acct) }
    var acctCustom by remember(initial.id) { mutableStateOf(initial.acctCustom) }
    var field by remember(initial.id) { mutableStateOf(initial.field) }
    var fieldCustom by remember(initial.id) { mutableStateOf(initial.fieldCustom) }
    val selectedVars = remember(initial.id) {
        mutableStateListOf<String>().apply { addAll(boundVars) }
    }

    val context = LocalContext.current
    // 符号库/模板库是跨规则共享的全局状态（存删不随本弹窗取消回滚），增删后 bump 刷新重读
    var libRefresh by remember { mutableIntStateOf(0) }
    val acctSymbols = remember(libRefresh) { SegRuleStore.loadCustomSymbols(context, false) }
    val fieldSymbols = remember(libRefresh) { SegRuleStore.loadCustomSymbols(context, true) }
    val myTemplates = remember(libRefresh) { SegRuleStore.loadCustomTemplates(context) }
    // 待确认删除的「我的模板」（✕ 后弹确认，避免误删）
    var pendingDeleteTpl by remember { mutableStateOf<SegRule?>(null) }

    // 「＋存入库」：trim 后非空、非内置预设/枚举保留值、非重复才入库（静默忽略非法与重复），成功清空输入框
    val storeAcctSymbol: () -> Unit = {
        val v = acctCustom.trim()
        if (v.isNotEmpty() && v !in PRESET_SYM_ACCT && v != ACCT_NONE && v != ACCT_CUSTOM && v !in acctSymbols) {
            SegRuleStore.addCustomSymbol(context, false, v)
            acctCustom = ""
            libRefresh++
        }
    }
    val storeFieldSymbol: () -> Unit = {
        val v = fieldCustom.trim()
        if (v.isNotEmpty() && v !in PRESET_SYM_FIELD && v != FIELD_CUSTOM && v !in fieldSymbols) {
            SegRuleStore.addCustomSymbol(context, true, v)
            fieldCustom = ""
            libRefresh++
        }
    }
    // 存为模板：存当前编辑快照（含改过的名称与分隔符）；同名模板复用既有 id 走 upsert 原地覆盖，保证不出现重名双条目
    val saveAsTemplate: () -> Unit = {
        val tplName = name.trim()
        val sameName = myTemplates.firstOrNull { it.name.trim().equals(tplName, ignoreCase = true) }
        val snapshot = SegRule(
            name = tplName,
            acct = acct,
            acctCustom = if (acct == ACCT_CUSTOM) acctCustom.trim() else "",
            field = field,
            fieldCustom = if (field == FIELD_CUSTOM) fieldCustom.trim() else ""
        )
        SegRuleStore.upsertCustomTemplate(
            context,
            if (sameName != null) snapshot.copy(id = sameName.id) else snapshot
        )
        libRefresh++
        Toast.makeText(context, "已存为模板", Toast.LENGTH_SHORT).show()
    }

    val acctValid = acct != ACCT_CUSTOM || acctCustom.isNotBlank()
    val fieldValid = field != FIELD_CUSTOM || fieldCustom.isNotBlank()
    val canSave = name.isNotBlank() && acctValid && fieldValid

    // 底部抽屉外壳：固定头部 + 中部滚动区 + 固定操作条；关闭走抽屉自身 hide 动画完成后再回调 onDismiss
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val closeSheet: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = closeSheet,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 固定头部：标题 + 关闭按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCreate) "新建分段规则" else "编辑分段规则",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = closeSheet) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
            HorizontalDivider()

            // 中部滚动区：内容多时在抽屉内滚动，封顶高度避免抽屉被无限撑高
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (isCreate) {
                    Text(
                        "快速模板（点选即填分隔，名称仍需输入）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SEG_TEMPLATES.forEach { (label, tpl) ->
                            val selected = acct == tpl.acct && field == tpl.field &&
                                    acctCustom == tpl.acctCustom && fieldCustom == tpl.fieldCustom
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    acct = tpl.acct
                                    acctCustom = tpl.acctCustom
                                    field = tpl.field
                                    fieldCustom = tpl.fieldCustom
                                },
                                label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                    // 我的模板：编辑页「存为模板」存下的快照，点选连名称一起填入，✕ 确认后删除
                    if (myTemplates.isNotEmpty()) {
                        Text(
                            "我的模板（点选即填，✕ 删除）",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            myTemplates.forEach { tpl ->
                                val selected = acct == tpl.acct && field == tpl.field &&
                                        acctCustom == tpl.acctCustom && fieldCustom == tpl.fieldCustom
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        name = tpl.name
                                        acct = tpl.acct
                                        acctCustom = tpl.acctCustom
                                        field = tpl.field
                                        fieldCustom = tpl.fieldCustom
                                    },
                                    label = { Text(tpl.name, fontSize = 10.sp) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "删除模板",
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clickable { pendingDeleteTpl = tpl }
                                        )
                                    },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }

                SepChipRow(
                    label = "账号",
                    options = listOf(
                        ACCT_NONE to "无", "\n" to "换行", "@" to "@", "&" to "&",
                        "," to ",", "|" to "|", ACCT_CUSTOM to "自定义"
                    ),
                    selected = acct,
                    onSelect = { acct = it },
                    customSymbols = acctSymbols,
                    customLabel = { acctSepLabel(it, "") },
                    onRemoveCustom = {
                        SegRuleStore.removeCustomSymbol(context, false, it)
                        libRefresh++
                    }
                )
                SepChipRow(
                    label = "字段",
                    options = listOf(
                        FIELD_AUTO to "自动", ";" to ";", "&" to "&",
                        "=" to "=", "#" to "#", FIELD_CUSTOM to "自定义"
                    ),
                    selected = field,
                    onSelect = { field = it },
                    customSymbols = fieldSymbols,
                    customLabel = { fieldSepLabel(it, "") },
                    onRemoveCustom = {
                        SegRuleStore.removeCustomSymbol(context, true, it)
                        libRefresh++
                    }
                )
                if (acct == ACCT_CUSTOM || field == FIELD_CUSTOM) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (acct == ACCT_CUSTOM) {
                            OutlinedTextField(
                                value = acctCustom,
                                onValueChange = { if (it.length <= 4) acctCustom = it },
                                label = { Text("账号分隔符", fontSize = 10.sp) },
                                supportingText = if (!acctValid) {
                                    { Text("自定义分隔符不能为空", fontSize = 9.sp, color = MaterialTheme.colorScheme.error) }
                                } else null,
                                isError = !acctValid,
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            // ＋存入库：存入账号层符号库（全局共享，取消编辑不回滚）
                            IconButton(onClick = storeAcctSymbol) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "存入符号库",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (field == FIELD_CUSTOM) {
                            OutlinedTextField(
                                value = fieldCustom,
                                onValueChange = { if (it.length <= 4) fieldCustom = it },
                                label = { Text("字段分隔符", fontSize = 10.sp) },
                                supportingText = if (!fieldValid) {
                                    { Text("自定义分隔符不能为空", fontSize = 9.sp, color = MaterialTheme.colorScheme.error) }
                                } else null,
                                isError = !fieldValid,
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            // ＋存入库：存入字段层符号库（全局共享，取消编辑不回滚）
                            IconButton(onClick = storeFieldSymbol) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "存入符号库",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    "绑定变量（可多选，一条规则可绑多个变量）",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (envNames.isEmpty()) {
                    Text(
                        "当前面板暂无环境变量",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        envNames.forEach { varName ->
                            val selected = varName in selectedVars
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) selectedVars.remove(varName)
                                    else selectedVars.add(varName)
                                },
                                label = { Text(varName, fontSize = 10.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }

                // 滚动区尾部：删除规则入口；存为模板已迁至底部固定操作条
                if (onRequestDelete != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onRequestDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("删除规则", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // 固定底部操作条：编辑态可把当前快照存为模板；取消/保存常驻
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isCreate) {
                    TextButton(enabled = canSave, onClick = saveAsTemplate) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("存为模板", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = closeSheet) { Text("取消", fontSize = 12.sp) }
                Button(
                    enabled = canSave,
                    onClick = {
                        onConfirm(
                            SegRule(
                                id = initial.id,
                                name = name.trim(),
                                acct = acct,
                                acctCustom = if (acct == ACCT_CUSTOM) acctCustom.trim() else "",
                                field = field,
                                fieldCustom = if (field == FIELD_CUSTOM) fieldCustom.trim() else ""
                            ),
                            (selectedVars - boundVars).toList(),
                            (boundVars - selectedVars).toList()
                        )
                    }
                ) { Text("保存", fontSize = 12.sp) }
            }
        }
    }

    // 删除我的模板确认（只清模板库，不影响已保存规则与绑定）
    pendingDeleteTpl?.let { tpl ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTpl = null },
            title = { Text("删除模板", fontSize = 15.sp) },
            text = {
                Text(
                    "确定删除我的模板「${tpl.name}」？不影响已保存的规则与绑定。",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        SegRuleStore.deleteCustomTemplate(context, tpl.id)
                        pendingDeleteTpl = null
                        libRefresh++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除", fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTpl = null }) { Text("取消", fontSize = 12.sp) }
            }
        )
    }
}

/** 分隔符 chip 行（账号 / 字段共用）：内置预设在前，我的符号在后（✕ 移出库，预设不可删） */
@Composable
private fun SepChipRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    customSymbols: List<String> = emptyList(),
    customLabel: (String) -> String = { it },
    onRemoveCustom: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        options.forEach { (value, text) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(text, fontSize = 10.sp) },
                modifier = Modifier.height(26.dp)
            )
        }
        // 我的符号：点 chip 主体选为分隔符，点右侧 ✕ 移出符号库
        customSymbols.forEach { sym ->
            FilterChip(
                selected = selected == sym,
                onClick = { onSelect(sym) },
                label = { Text(customLabel(sym), fontSize = 10.sp) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "移出符号库",
                        modifier = Modifier
                            .size(13.dp)
                            .clickable { onRemoveCustom(sym) }
                    )
                },
                modifier = Modifier.height(26.dp)
            )
        }
    }
}

/** 使用说明底部抽屉：核心概念 + 6 步 + 常见模板 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnvRulesHelpDialog(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val closeSheet: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = closeSheet,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 固定头部：标题 + 关闭按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "使用说明",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = closeSheet) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
            HorizontalDivider()

            // 中部正文滚动区：封顶高度避免抽屉被无限撑高
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "核心概念：规则=拆分方式模板，变量绑定规则后按其分隔拆分；改规则只影响拆法，不动变量值。",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text("① 顶栏「分段规则」进本页，点右上角「＋新建」起名、选分隔符", fontSize = 12.sp)
                Text("② 编辑弹窗里勾选绑定变量（一条规则可绑多个）", fontSize = 12.sp)
                Text("③ 变量列表点「分段」→ 按规则拆好 → 改值保存", fontSize = 12.sp)
                Text("④ 改拆法：来本页改分隔符，已绑定变量下次生效", fontSize = 12.sp)
                Text("⑤ 删规则只解绑，不清除变量值", fontSize = 12.sp)
                Text("⑥ 未绑定规则的变量按自动识别拆分", fontSize = 12.sp)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    "常见模板：Cookie=无/; ｜ 互助码=&/@ ｜ 多账号=换行/自动 ｜ 键值=无/=",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 固定底部操作条：确定关闭
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = closeSheet) { Text("知道了", fontSize = 12.sp) }
            }
        }
    }
}
