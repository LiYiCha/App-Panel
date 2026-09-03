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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    editPanel: PanelInstance? = null,
    onBack: (() -> Unit)? = null,
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

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isSwitchAccount = editPanel != null

    fun doLogin() {
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
                // 两步验证：由下面的对话框接管，不再当成普通错误提示
                errorMessage = null
            } else {
                errorMessage = msg
            }
        }
    }

    // 两步验证输入框：开启 OTP 的账号必须再验一次动态码才能拿到登录凭据
    if (uiState.otpPendingPanel != null) {
        OtpVerifyDialog(
            panelName = uiState.otpPendingPanel!!.name,
            isVerifying = uiState.isAuthenticating,
            onDismiss = { viewModel.cancelOtp() },
            onConfirm = { code ->
                viewModel.submitOtp(code) { success, msg ->
                    if (success) {
                        Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                        onLoginSuccess()
                    } else {
                        errorMessage = msg
                    }
                }
            }
        )
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
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(com.panel.app.R.drawable.ic_main),
                        contentDescription = "Logo",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Panel Hub",
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
                            Text(if (isSwitchAccount) "登录并保存" else "登 录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * 两步验证输入框。
 * 白虎开启 OTP 后，首次登录只返回临时凭证而不下发 Cookie，
 * 必须再用 authenticator 里的 6 位动态码调一次 /auth/login/otp 才算真正登录。
 */
@Composable
private fun OtpVerifyDialog(
    panelName: String,
    isVerifying: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    val isValid = code.trim().length >= 6

    AlertDialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        title = { Text("两步验证", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "面板 [$panelName] 已开启两步验证，请输入验证器 App 中的 6 位动态验证码。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 8) code = it.filter { c -> c.isDigit() } },
                    label = { Text("动态验证码", fontSize = 12.sp) },
                    singleLine = true,
                    enabled = !isVerifying,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (isValid && !isVerifying) onConfirm(code) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(code) },
                enabled = isValid && !isVerifying
            ) {
                Text(if (isVerifying) "验证中..." else "验证并登录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isVerifying) {
                Text("取消")
            }
        }
    )
}
