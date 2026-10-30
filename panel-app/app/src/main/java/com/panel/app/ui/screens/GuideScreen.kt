package com.panel.app.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.R
import com.panel.app.data.model.UnifiedTask
import com.panel.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * 引导完成标记的读写约定：与主 ViewModel 共用 panel_hub_prefs。
 */
object GuidePrefs {
    const val PREFS_NAME = "panel_hub_prefs"
    const val KEY_COMPLETED = "guide_completed"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COMPLETED, false)

    fun markCompleted(context: Context) {
        prefs(context).edit().putBoolean(KEY_COMPLETED, true).apply()
    }
}

/** 屏内讲解的一页：真实页面预览 + 标题 + 一句说明 */
private data class GuidePage(
    val title: String,
    val body: String,
    val art: @Composable () -> Unit
)

/**
 * 引导预览专用假任务：仅在引导页展示，不写入 ViewModel 也不落库，
 * 让首次启动（还没登录、列表为空）的预览看起来是正常使用中的界面。
 */
private val guideMockTasks: List<UnifiedTask> = run {
    val now = System.currentTimeMillis() / 1000
    listOf(
        UnifiedTask(
            id = "guide_mock_1", name = "京东签到", command = "node jd_bignews.js",
            schedule = "30 8 * * *", statusText = "", isRunning = true,
            lastRunningTime = 65, lastExecutionTime = now - 300
        ),
        UnifiedTask(
            id = "guide_mock_2", name = "东东农场", command = "python3 dd_farm.py",
            schedule = "0 9 * * *", statusText = "", isPinned = true,
            lastExecutionTime = now - 7200
        ),
        UnifiedTask(
            id = "guide_mock_3", name = "每日宝藏", command = "node daily_treasure.js",
            schedule = "*/30 * * * *", statusText = "",
            lastExecutionTime = now - 1800
        ),
        UnifiedTask(
            id = "guide_mock_4", name = "签到助手", command = "node checkin.js",
            schedule = "0 7 * * *", statusText = "", isDisabled = true,
            lastExecutionTime = now - 90000
        )
    )
}

/**
 * 引导：欢迎页 → 屏内连续翻 4 页（HorizontalPager），每页直接渲染真实页面的半屏预览。
 * 全程停留在本页，不依赖任何真实页面；右上角「跳过」随时结束引导。
 * [onFinish] 前已写入完成标记；replay 模式返回上一页，首启模式进入登录页。
 */
@Composable
fun GuideScreen(
    replay: Boolean,
    localScriptEnabled: Boolean,
    viewModel: MainViewModel,
    onFinish: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var started by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState { 4 }

    fun finish() {
        GuidePrefs.markCompleted(context)
        onFinish()
    }

    // 引导内的「返回」语义：回上一页，欢迎页上视为跳过引导直接进入
    val backWithinGuide: () -> Unit = {
        val page = pagerState.currentPage
        if (page > 0) {
            scope.launch { pagerState.animateScrollToPage(page - 1) }
        } else {
            started = false
        }
    }

    // 返回键：讲解中回上一页/回欢迎页；欢迎页上视为跳过引导直接进入
    BackHandler(enabled = started) { backWithinGuide() }
    BackHandler(enabled = !started) {
        GuidePrefs.markCompleted(context)
        if (replay && onBack != null) onBack() else onFinish()
    }

    val pages = listOf(
        GuidePage(
            title = "选择面板类型",
            body = "支持青龙和白虎两种面板。填好面板地址、账号和密码就能登录，账号密码只保存在你的手机里。",
            art = {
                ScreenPreview {
                    LoginScreen(
                        viewModel = viewModel,
                        onBack = null,
                        onEnterLocalMode = null,
                        onLoginSuccess = {},
                        preview = true
                    )
                }
            }
        ),
        GuidePage(
            title = "底部导航栏",
            body = if (localScriptEnabled) {
                "底部有五个页面：任务、变量、配置、本地、设置，点一下就能切换。"
            } else {
                "底部有四个页面：任务、变量、配置、设置，点一下就能切换。"
            },
            art = {
                ScreenPreview {
                    MainFlowScreen(
                        viewModel = viewModel,
                        onOpenPanelManager = {},
                        onOpenTaskDetail = {},
                        onOpenLogScreen = {},
                        onOpenScriptEditorScreen = {},
                        onOpenDepsScreen = {},
                        localScriptEnabled = localScriptEnabled,
                        previewTasks = guideMockTasks
                    )
                }
            }
        ),
        GuidePage(
            title = "任务卡片操作",
            body = "任务卡片向左滑可以启动、禁用或删除；点击卡片进入详情，查看实时日志。",
            art = {
                ScreenPreview {
                    TasksScreen(
                        viewModel = viewModel,
                        onOpenTaskDetail = {},
                        onOpenLog = {},
                        previewTasks = guideMockTasks
                    )
                }
            }
        ),
        GuidePage(
            title = "开启应用权限",
            body = "开启通知权限，任务完成后会收到提醒；开启安装权限，才能在应用内更新版本。",
            art = {
                // 权限页自带 BackHandler，回传引导内返回语义避免吞掉系统返回键
                ScreenPreview {
                    AppPermissionsScreen(onBack = backWithinGuide)
                }
            }
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部栏：重看模式带返回箭头，右侧「跳过」随时结束
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (replay && onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            // 跳过：描边胶囊按钮，比正文更醒目，随时结束引导
            val skipShape = RoundedCornerShape(999.dp)
            Surface(
                shape = skipShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .clip(skipShape)
                    .clickable(onClick = { finish() })
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "跳过",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp)
                )
            }
        }

        if (!started) {
            // 欢迎页占满顶栏以下区域（weight 需在 ColumnScope 内应用）
            val welcomeModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            WelcomeContent(
                modifier = welcomeModifier,
                localScriptEnabled = localScriptEnabled,
                onStart = { started = true }
            )
        } else {
            // 屏内讲解：4 页横滑图文
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                PageContent(pages[page])
            }

            // 底部：页码点 + 主按钮
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    repeat(4) { i ->
                        val selected = i == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(if (selected) 8.dp else 6.dp)
                                .background(
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(999.dp)
                                )
                        )
                    }
                }
                Button(
                    onClick = {
                        val page = pagerState.currentPage
                        if (page < 3) {
                            scope.launch { pagerState.animateScrollToPage(page + 1) }
                        } else {
                            finish()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        if (pagerState.currentPage < 3) "下一步" else "完成",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

/** 欢迎页主体（[modifier] 须由调用方在 ColumnScope 内施加 weight） */
@Composable
private fun WelcomeContent(
    modifier: Modifier = Modifier,
    localScriptEnabled: Boolean,
    onStart: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        // Logo：与登录页同源 ic_main，装进主色容器
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_main),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "烛龙面板",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "一个 App 管好青龙与白虎面板\n跑任务、管变量、写本地脚本",
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 能力标签
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("任务列表", "环境变量", if (localScriptEnabled) "本地脚本" else "配置与脚本")
                .forEach { label ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onStart,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("开始", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "进入后会有 4 页图文带你快速上手",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(34.dp))
    }
}

/** 讲解单页：真实页面预览（占满剩余空间）+ 标题 + 一句说明 */
@Composable
private fun PageContent(page: GuidePage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 预览窗口吃掉全部剩余高度，标题说明只占底部一小截
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            page.art()
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = page.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = page.body,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 30.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 真实页面预览框：铺满可用空间的圆角裁剪窗口，
 * 高度装不下的部分自然裁掉（半页面效果）；
 * 上层透明覆盖吞掉全部触摸 —— 预览只看不可点，也不会触发页面内部跳转。
 */
@Composable
private fun ScreenPreview(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            )
        }
    }
}

