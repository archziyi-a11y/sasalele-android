package com.example.sasalele

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sasalele.ui.theme.SASALELETheme

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.sasalele.data.ImagePickerBus

class MainActivity : ComponentActivity() {

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            lifecycleScope.launch {
                ImagePickerBus.sendUri(uri)
            }
        }
    }

    private val pickMaterialMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // Read stream and save to internal storage
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val dir = java.io.File(filesDir, "materials")
                    if (!dir.exists()) dir.mkdirs()
                    val file = java.io.File(dir, "material_${System.currentTimeMillis()}.jpg")
                    contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val db = com.example.sasalele.data.db.AppDatabase.getDatabase(this@MainActivity)
                    db.materialDao().insertAll(
                        listOf(
                            com.example.sasalele.data.db.MaterialEntity(
                                title = "新导入的图片素材",
                                content = file.absolutePath,
                                type = "IMAGE"
                            )
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            SASALELETheme {
                MainScreen(
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onStartService = { startFloatingService() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == "com.example.sasalele.ACTION_PICK_IMAGE") {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else if (intent.action == "com.example.sasalele.ACTION_ADD_MATERIAL_IMAGE") {
            AcquisitionUi.open(this,PickMaterialImageActivity::class.java)
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startFloatingService() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, FloatingWindowService::class.java)
            startForegroundService(intent)
        } else {
            requestOverlayPermission()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onRequestOverlayPermission: () -> Unit,
    onStartService: () -> Unit
) {
    val context = LocalContext.current
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleResumeEffect(Unit) {
        overlayGranted = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("SASALELE 销售助手") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            Text("准备好，再回到客户会话", style = MaterialTheme.typography.headlineSmall)
            Text("在工作手机微信的一对一客户会话中使用。销售无需登录或连接电脑。")
            androidx.compose.material3.ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    Text(if (overlayGranted) "悬浮窗权限已开启" else "必须开启悬浮窗权限", style = MaterialTheme.typography.titleMedium)
                    Text("助手需要在微信上方显示工具条；未开启时无法显示。截图将在长按客户图标时单独请求系统授权。")
                    if (!overlayGranted) Button(onClick = onRequestOverlayPermission, modifier = Modifier.fillMaxWidth()) { Text("开启悬浮窗权限") }
                    Button(onClick = onStartService, enabled = overlayGranted, modifier = Modifier.fillMaxWidth()) { Text("开启助手") }
                }
            }
            Text("使用步骤", style = MaterialTheme.typography.titleLarge)
            Text("1  回到微信客户私聊，长按悬浮条的客户图标截图。\n\n2  修正识别姓名，核对工作号与客户 ID，人工确认客户。\n\n3  查看画像，使用帮我回或润色；审阅、编辑后复制，自行发送。")
            HorizontalDivider()
            BusinessConnectionPanel(context)
            Text("安装 APK、保存设备配置、服务检查通过，均不代表该微信工作号已绑定方舟。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
