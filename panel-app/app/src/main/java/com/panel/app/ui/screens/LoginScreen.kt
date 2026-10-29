package com.panel.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.app.data.model.PanelType
import com.panel.app.ui.viewmodel.MainViewModel
import com.panel.app.util.PanelUrl

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import com.panel.app.data.model.PanelInstance

/** 从面板地址提取 host（2FA 持久化标记的 key） */
private fun hostOf(url: String): String = try {
    java.net.URI(url).host ?: ""
} catch (e: Exception) {
    ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    editPanel: PanelInstance? = null,
    onBack: (() -> Unit)? = null,
    onEnterLocalMode: (() -> Unit)? = null,
    onLoginSuccess: () -> Unit
) {
    // 从面板管理等上级页面进入时，返回键应回到上一页；
    // 作为根页面（没有上级回退栈）时不拦截，交给系统退出应用
    onBack?.let { back -> BackHandler { back() } }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val uiState by viewModel.uiState.collectAsState()

    var panelType by remember(editPanel) { mutableStateOf(editPanel?.type ?: PanelType.BAIHU) }
    var panelName by remember(editPanel) { mutableStateOf(editPanel?.name ?: "白虎面板") }
    var baseUrl by remember(editPanel) { mutableStateOf(editPanel?.baseUrl ?: "") }
    var username by remember(editPanel) { mutableStateOf(editPanel?.username ?: "") }
    var password by remember(editPanel) { mutableStateOf(editPanel?.password ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // 两步验证：内联在登录表单里（不再弹独立对话框）。
    // 青龙（420）/白虎（require_otp）默认直接显示验证码框（未开启 2FA 时留空即可）；
    // 已撞过 420 的面板同样带出，与账密共用同一个「登录」按钮完成两段接力。
    var twoFactorCode by remember { mutableStateOf("") }
    var showTwoFactorField by remember(baseUrl, panelType) {
        mutableStateOf(
            panelType == PanelType.QINGLONG_V15 || panelType == PanelType.BAIHU ||
                    viewModel.isTwoFactorKnown(hostOf(baseUrl))
        )
    }
    val twoFactorPending = uiState.otpPendingPanel != null

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isSwitchAccount = editPanel != null

    fun doLogin() {
        // 第二段：账号密码已被服务端确认，此时同一个按钮提交动态验证码
        if (twoFactorPending) {
            if (twoFactorCode.isBlank()) {
                errorMessage = "请输入验证器中的动态验证码"
                return
            }
            focusManager.clearFocus()
            viewModel.submitOtp(twoFactorCode) { success, msg ->
                if (success) {
                    Toast.makeText(context, if (isSwitchAccount) "账号切换成功" else "登录成功", Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                } else {
                    errorMessage = msg
                }
            }
            return
        }
        if (baseUrl.isBlank()) {
            errorMessage = "请输入面板服务地址"
            return
        }
        if (username.isBlank() || password.isBlank()) {
            errorMessage = "请输入登录账号与密码"
            return
        }
        // 地址在提交前先校验并规范化：漏写 http:// 会自动补全并回显给用户；
        // 确实非法（如 ftp://x、缺主机名）则就地提示，不让脏地址流到网络层
        val normalizedUrl = PanelUrl.normalize(baseUrl).getOrElse {
            errorMessage = it.message ?: "面板地址格式不正确，示例：http://192.168.1.100:5700"
            return
        }
        baseUrl = normalizedUrl
        errorMessage = null
        focusManager.clearFocus()

        viewModel.authenticateAndSavePanel(
            existingId = editPanel?.id,
            name = panelName.ifBlank { if (panelType == PanelType.BAIHU) "白虎面板" else "青龙面板" },
            type = panelType,
            baseUrl = normalizedUrl,
            username = username.trim(),
            password = password
        ) { success, msg ->
            if (success) {
                Toast.makeText(context, if (isSwitchAccount) "账号切换成功" else "登录成功", Toast.LENGTH_SHORT).show()
                onLoginSuccess()
            } else if (uiState.otpPendingPanel != null) {
                // 两步验证：展开内联验证码框，由同一个登录按钮完成第二段提交；
                // 同时持久化"该面板已开启 2FA"标记，下次登录直接带出验证码框
                showTwoFactorField = true
                errorMessage = null
                hostOf(baseUrl).takeIf { it.isNotEmpty() }?.let { viewModel.markTwoFactorKnown(it) }
                // 预填场景：验证码已填则自动续跑第二段，无需再点一次登录
                if (twoFactorCode.isNotBlank()) {
                    viewModel.submitOtp(twoFactorCode) { success, msg ->
                        if (success) {
                            Toast.makeText(context, if (isSwitchAccount) "账号切换成功" else "登录成功", Toast.LENGTH_SHORT).show()
                            onLoginSuccess()
                        } else {
                            errorMessage = msg
                        }
                    }
                }
            } else {
                errorMessage = msg
            }
        }
    }

    fun resetTwoFactor() {
        viewModel.cancelOtp()
        twoFactorCode = ""
        errorMessage = null
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(if (isSwitchAccount) "切换账号登录" else "添加面板登录", fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo 与标题
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(92.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(com.panel.app.R.drawable.ic_main),
                        contentDescription = "Logo",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "烛龙",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (isSwitchAccount) "修改信息并切换登录账号" else "青龙与白虎面板登录",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))

            // 表单卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 面板类型切换
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = panelType == PanelType.BAIHU,
                            onClick = {
                                panelType = PanelType.BAIHU
                                panelName = "白虎面板"
                                if (baseUrl.contains("5700")) {
                                    baseUrl = "http://118.190.150.79:18082"
                                }
                            },
                            label = { Text("白虎面板", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = panelType == PanelType.QINGLONG_V15,
                            onClick = {
                                panelType = PanelType.QINGLONG_V15
                                panelName = "青龙面板"
                                if (baseUrl.contains("18082")) {
                                    baseUrl = "http://192.168.1.100:5700"
                                }
                            },
                            label = { Text("青龙面板", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 服务地址
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; errorMessage = null },
                        label = { Text("面板地址 (URL)", fontSize = 12.sp) },
                        placeholder = { Text("http://192.168.1.100:5700") },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // 账号
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; errorMessage = null },
                        label = { Text("登录账号", fontSize = 12.sp) },
                        placeholder = { Text("admin") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // 密码
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("登录密码", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { doLogin() }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // 两步验证（内联）：白虎 OTP / 青龙 TOTP 通用。
                    // 青龙/白虎面板默认直接带出验证码框（未开启 2FA 留空即可）；
                    // 已撞过 420 的面板用提示条标识第二段状态。
                    if (showTwoFactorField) {
                        if (twoFactorPending) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "「${uiState.otpPendingPanel?.name}」已开启两步验证，请输入验证器中的 6 位动态验证码",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = twoFactorCode,
                            onValueChange = { if (it.length <= 8) twoFactorCode = it.filter { c -> c.isDigit() }; errorMessage = null },
                            label = { Text("动态验证码（未开启 2FA 可留空）", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            enabled = !uiState.isAuthenticating,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { doLogin() }),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        if (twoFactorPending) {
                            TextButton(
                                onClick = { resetTwoFactor() },
                                enabled = !uiState.isAuthenticating,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("返回修改账号密码", fontSize = 11.sp)
                            }
                        }
                    }

                    if (!errorMessage.isNullOrEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Text(
                                    text = errorMessage!!,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // 登录按键
                    Button(
                        onClick = { doLogin() },
                        enabled = !uiState.isAuthenticating,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        if (uiState.isAuthenticating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在登录...", fontSize = 14.sp)
                        } else {
                            Text(
                                if (twoFactorPending) "验证并登录"
                                else if (isSwitchAccount) "登录并保存" else "登 录",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    // 本地模式：跳过面板登录直接使用本地脚本能力（standard 变体不显示）
                    if (onEnterLocalMode != null) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onEnterLocalMode,
                            enabled = !uiState.isAuthenticating,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Text("本地模式（无需登录面板）", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
}
