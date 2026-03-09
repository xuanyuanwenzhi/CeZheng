package com.czsk.app.ui.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val PageBgColor = Color(0xFFF4EBD8)

private const val GITHUB_URL = "https://github.com/xuanyuanwenzhi/CeZheng"
private const val AFDIAN_URL = "https://afdian.com/a/cezheng"
private const val CONTACT_EMAIL = "skyward_sword@163.com"

@Composable
fun MoreScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun openLink(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar("打开链接失败")
            }
        }
    }

    fun copyEmail(email: String) {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("contact_email", email)
        clipboardManager.setPrimaryClip(clipData)

        scope.launch {
            snackbarHostState.showSnackbar("邮箱已复制")
        }
    }

    Scaffold(
        containerColor = PageBgColor,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PageBgColor)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBack) {
                    Text("返回")
                }
                Text(
                    text = "其他",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBgColor)
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "开源说明",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "本软件代码已开源，可免费使用。欢迎自行修改、完善和二次开发，但请保留原作者署名，禁止将本项目用于商业用途。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = GITHUB_URL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { openLink(GITHUB_URL) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("打开 GitHub")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "支持作者",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "如果这个软件对你有帮助，可以通过爱发电支持后续维护与更新。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = AFDIAN_URL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { openLink(AFDIAN_URL) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("前往支持")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "反馈与建议",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "欢迎通过下面的邮箱反馈问题、提出建议。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = CONTACT_EMAIL,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Button(
                        onClick = { copyEmail(CONTACT_EMAIL) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("复制邮箱")
                    }
                }
            }
        }
    }
}