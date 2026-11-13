package com.example.sasalele

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
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
            pickMaterialMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("私聊", "群聊")

    var openedChat by remember { mutableStateOf<String?>(null) }
    val privateChats = listOf("测试客户甲", "测试客户乙", "测试客户丙")
    val groupChats = listOf("销售团队测试群", "活动测试群")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(openedChat ?: "模拟聊天列表") },
                navigationIcon = { if(openedChat!=null) androidx.compose.material3.TextButton(onClick={openedChat=null}) { Text("返回") } }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if(openedChat!=null) {
                Text("合成测试页面 · 未连接真实聊天",Modifier.padding(16.dp))
                Text("截图目标：${openedChat}",Modifier.padding(16.dp))
                Text("这是本地模拟消息，不应被顶栏OCR提取",Modifier.padding(16.dp))
                var draft by remember { mutableStateOf("") }
                androidx.compose.material3.OutlinedTextField(value=draft,onValueChange={draft=it},label={Text("模拟输入，不会发送")},modifier=Modifier.fillMaxWidth().padding(16.dp))
                return@Column
            }
            // Permissions & Service Control
            Row(modifier = Modifier.padding(16.dp)) {
                Button(onClick = onRequestOverlayPermission) {
                    Text("授权悬浮窗")
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onStartService) {
                    Text("开启助手")
                }
            }

            // Tabs for chats
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            // Chat list
            val currentChats = if (selectedTabIndex == 0) privateChats else groupChats
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(currentChats) { chatName ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openedChat=if(selectedTabIndex==0) chatName else "$chatName(23)" }
                            .padding(16.dp)
                    ) {
                        Text(text = chatName, style = MaterialTheme.typography.titleMedium)
                        Text(text = "模拟消息摘要…", style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}