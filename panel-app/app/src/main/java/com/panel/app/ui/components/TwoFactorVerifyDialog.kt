package com.panel.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 两步验证码弹窗（主界面全局入口）。
 * 场景：面板 token 过期且开启了两步验证（青龙 420 / 白虎 require_otp），
 * 自愈链路无法静默续期时挂起 otpPendingPanel，由本弹窗收集动态验证码；
 * 验证成功后由 ViewModel 的 submitOtp 保存新凭据并自动重新刷新。
 * 错误提示通过 onError 回调交由调用方展示（如 Toast）。
 */
@Composable
fun TwoFactorVerifyDialog(
    panelName: String,
    isVerifying: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (code: String, onError: (String) -> Unit) -> Unit
) {
    var code by remember { mutableStateOf("") }
    val isValid = code.trim().length >= 6

    AlertDialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        title = { Text("两步验证", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "面板「$panelName」的登录凭据已过期且开启了两步验证，请输入验证器中的 6 位动态验证码继续。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 8) code = it.filter { c -> c.isDigit() } },
                    label = { Text("动态验证码", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    enabled = !isVerifying,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isValid && !isVerifying) onConfirm(code) { } }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(code) { } },
                enabled = isValid && !isVerifying
            ) {
                Text(if (isVerifying) "验证中..." else "验证并继续")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isVerifying) { Text("取消") }
        }
    )
}
