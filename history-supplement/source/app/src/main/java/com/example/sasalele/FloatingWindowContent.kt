package com.example.sasalele

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sasalele.data.Client
import com.example.sasalele.data.ClientRepository
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.example.sasalele.data.db.AppDatabase
import com.example.sasalele.data.db.MaterialEntity
import com.example.sasalele.data.repository.MaterialRepository
import com.example.sasalele.data.preferences.SettingsDataStore
import com.example.sasalele.data.ReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

import android.content.Intent
import androidx.compose.material.icons.filled.Image
import com.example.sasalele.data.ImagePickerBus
import com.example.sasalele.data.MockOcrEngineImpl
import com.example.sasalele.data.OcrResultState
import com.example.sasalele.data.OcrResult
import kotlinx.coroutines.flow.collectLatest
import com.example.sasalele.data.repository.GuidanceRepository
import com.example.sasalele.data.db.GuidanceMessageEntity
import androidx.compose.foundation.clickable

enum class ActivePanel {
    NONE, CLIENT_PROFILE, AI_GUIDANCE, SMART_REPLY, MATERIALS, SETTINGS, REMINDERS, OCR_RESULT
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingWindowContent(
    windowManager: WindowManager,
    params: WindowManager.LayoutParams,
    view: View,
    context: Context
) {
    var isExpanded by remember { mutableStateOf(true) }
    var isDockedLeft by remember { mutableStateOf(false) }
    var isWindowVisible by remember { mutableStateOf(true) }
    var activePanel by remember { mutableStateOf(ActivePanel.NONE) }
    
    LaunchedEffect(activePanel) {
        params.flags = if (activePanel == ActivePanel.NONE) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        windowManager.updateViewLayout(view, params)
    }
    val currentClient by ClientRepository.currentBoundClient.collectAsState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var ocrState by remember { mutableStateOf(OcrResultState.SUCCESS) }
    var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
    val realRecognizer = remember { com.example.sasalele.data.ChineseImageTextRecognizer(context) }
    var headerRecognition by remember { mutableStateOf<com.example.sasalele.data.HeaderRecognition?>(null) }
    var recognitionError by remember { mutableStateOf<String?>(null) }
    var recognitionUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isRecognizing by remember { mutableStateOf(false) }

    var recognitionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val capture by com.example.sasalele.data.CaptureSession.state.collectAsState()
    val startRecognition: (android.net.Uri, com.example.sasalele.data.HeaderCrop) -> Unit = { uri, region ->
        recognitionJob?.cancel()
        recognitionUri=uri; headerRecognition=null; recognitionError=null
        activePanel=ActivePanel.OCR_RESULT; isExpanded=true
        recognitionJob=scope.launch {
            isRecognizing=true
            try { headerRecognition=kotlinx.coroutines.withTimeout(30000) { realRecognizer.readHeader(uri,region) } }
            catch(e:kotlinx.coroutines.TimeoutCancellationException) { recognitionError="识别超时，请重试或选择已有图片" }
            catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(e:Exception) { recognitionError="图片无法打开或模型识别失败，请重新选图" }
            finally { isRecognizing=false }
        }
    }
    LaunchedEffect(Unit) { ImagePickerBus.imageUriFlow.collect { uri -> startRecognition(uri,com.example.sasalele.data.HeaderCrop()) } }
    LaunchedEffect(capture.id,capture.busy,capture.image) {
        if(!capture.busy && capture.id.isNotBlank()) {
            if(capture.image!=null) startRecognition(capture.image!!,com.example.sasalele.data.HeaderCrop())
            else if(capture.message.isNotBlank()) Toast.makeText(context,capture.message,Toast.LENGTH_LONG).show()
        }
    }
    DisposableEffect(Unit) { onDispose { recognitionJob?.cancel(); com.example.sasalele.data.CaptureSession.clearImage(context) } }

    LaunchedEffect(activePanel) {
        if(activePanel!=ActivePanel.OCR_RESULT && recognitionUri!=null) {
            recognitionJob?.cancel()
            com.example.sasalele.data.CaptureSession.clearImage(context)
            recognitionUri=null
        }
    }
    var showFlash by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        com.example.sasalele.data.FlashState.flashFlow.collectLatest {
            showFlash = true
            kotlinx.coroutines.delay(300)
            showFlash = false
        }
    }

    if (!isWindowVisible) {
        // Invisible state for OCR simulation
        Spacer(modifier = Modifier.size(0.dp))
        return
    }

    Box {
        Row(
            modifier = Modifier.wrapContentSize(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val pillContent = @Composable {
                if (isExpanded) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White)
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Collapse Button
                    IconButton(
                        onClick = { 
                            isExpanded = false
                            activePanel = ActivePanel.NONE 
                        },
                        modifier = Modifier.size(36.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                params.x += dragAmount.x.toInt()
                                params.y += dragAmount.y.toInt()
                                isDockedLeft = params.x < 0
                                windowManager.updateViewLayout(view, params)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isDockedLeft) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "收起",
                            tint = Color.DarkGray
                        )
                    }

                    // Client/Avatar (Entry 1)
                    FloatingIcon(
                        icon = Icons.Default.AccountCircle, // Ideally use avatar if available
                        description = "客户资料",
                        avatarUrl = currentClient?.avatarUrl,
                        label = currentClient?.name,
                        tint = if (currentClient != null) MaterialTheme.colorScheme.primary else Color.DarkGray,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            recognitionJob?.cancel()
                            com.example.sasalele.data.CaptureSession.request(context)
                        },
                        onClick = {
                            activePanel = if (activePanel == ActivePanel.CLIENT_PROFILE) ActivePanel.NONE else ActivePanel.CLIENT_PROFILE
                        }
                    )

                    // AI Guidance (Entry 2)
                    FloatingIcon(
                        icon = Icons.Default.Star,
                        description = "销售指导",
                        tint = if (currentClient != null) Color.DarkGray else Color.LightGray,
                        onClick = {
                            if (currentClient == null) {
                                Toast.makeText(context, "请先识别客户", Toast.LENGTH_SHORT).show()
                            } else {
                                activePanel = if (activePanel == ActivePanel.AI_GUIDANCE) ActivePanel.NONE else ActivePanel.AI_GUIDANCE
                            }
                        }
                    )

                    // Smart Reply (Entry 3)
                    FloatingIcon(
                        icon = Icons.Default.ChatBubble,
                        description = "帮你回",
                        tint = Color.DarkGray,
                        onClick = {
                            activePanel = if (activePanel == ActivePanel.SMART_REPLY) ActivePanel.NONE else ActivePanel.SMART_REPLY
                        }
                    )

                    // Reminders
                    val pillPendingCount by ReminderScheduler.pendingReminderCount.collectAsState()
                    BadgedBox(
                        badge = {
                            if (pillPendingCount > 0) {
                                Badge(containerColor = Color.Red, contentColor = Color.White) { 
                                    Text(pillPendingCount.toString()) 
                                }
                            }
                        }
                    ) {
                        FloatingIcon(
                            icon = androidx.compose.material.icons.Icons.Default.Favorite,
                            description = "待处理提醒",
                            onClick = { activePanel = if (activePanel == ActivePanel.REMINDERS) ActivePanel.NONE else ActivePanel.REMINDERS }
                        )
                    }

                    // Materials
                    FloatingIcon(
                        icon = Icons.Default.Build,
                        description = "常用素材",
                        onClick = { activePanel = if (activePanel == ActivePanel.MATERIALS) ActivePanel.NONE else ActivePanel.MATERIALS }
                    )

                    // Settings
                    FloatingIcon(
                        icon = Icons.Default.Settings,
                        description = "设置",
                        onClick = { activePanel = if (activePanel == ActivePanel.SETTINGS) ActivePanel.NONE else ActivePanel.SETTINGS }
                    )
                }
            } else {
                // Collapsed state
                val pendingRemindersCount by ReminderScheduler.pendingReminderCount.collectAsState()
                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                params.x += dragAmount.x.toInt()
                                params.y += dragAmount.y.toInt()
                                isDockedLeft = params.x < 0
                                windowManager.updateViewLayout(view, params)
                            }
                        }
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { isExpanded = true }) {
                        Icon(
                            imageVector = if (isDockedLeft) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "展开",
                            tint = Color.DarkGray
                        )
                    }
                    if (pendingRemindersCount > 0) {
                        Badge(
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                            containerColor = Color.Red,
                            contentColor = Color.White
                        ) {
                            Text(pendingRemindersCount.toString())
                        }
                    }
                }
            }
        }

        val panelContent = @Composable {
            if (activePanel != ActivePanel.NONE) {
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .height(400.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    when (activePanel) {
                        ActivePanel.CLIENT_PROFILE -> ClientProfilePanel(currentClient, ocrState, { ocrState = it },
                            onClose = { activePanel = ActivePanel.NONE }, onSearch = { recognitionUri=null; headerRecognition=null; recognitionError=null; activePanel=ActivePanel.OCR_RESULT })
                        ActivePanel.AI_GUIDANCE -> AIGuidancePanel(context, onClose = { activePanel = ActivePanel.NONE })
                        ActivePanel.SMART_REPLY -> SmartReplyPanel(context, onClose = { activePanel = ActivePanel.NONE })
                        ActivePanel.MATERIALS -> MaterialsPanel(context, onClose = { activePanel = ActivePanel.NONE })
                        ActivePanel.SETTINGS -> SettingsPanel(context, onClose = { activePanel = ActivePanel.NONE })
                        ActivePanel.REMINDERS -> RemindersPanel(onClose = { activePanel = ActivePanel.NONE })
                        ActivePanel.OCR_RESULT -> RealRecognitionPanel(context, recognitionUri, headerRecognition, recognitionError,
                            onClose = { recognitionJob?.cancel(); com.example.sasalele.data.CaptureSession.clearImage(context); recognitionUri=null; activePanel=ActivePanel.NONE },
                            onRead = { region -> recognitionUri?.let { startRecognition(it,region) } })
                        else -> {}
                    }
                }
            }
        }

        if (isDockedLeft) {
            pillContent()
            panelContent()
        } else {
            panelContent()
            pillContent()
        }
        
        if (showFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }
    }
}
}

@Composable
fun FloatingIcon(
    icon: ImageVector,
    description: String,
    tint: Color = Color.DarkGray,
    avatarUrl: String? = null,
    label: String? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFFF0F0F0))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            if(avatarUrl!=null) AsyncImage(model=avatarUrl,contentDescription=description,modifier=Modifier.size(28.dp).clip(CircleShape))
            else Icon(imageVector=icon,contentDescription=description,tint=tint)
            if(label!=null) Text(label,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=9.sp)
        }
    }
}


@Composable
fun ClientProfilePanel(client: Client?, ocrState: OcrResultState, onOcrStateChange: (OcrResultState) -> Unit, onClose: () -> Unit, onSearch: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("客户档案", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onClose) { Text("关闭") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (client != null) {
            client.avatarUrl?.let { AsyncImage(model=it,contentDescription="已绑定客户头像",modifier=Modifier.size(48.dp)) }
            Text("姓名: ${client.name}")
            Text("工作号: ${client.workAccountId}")
            val profileContext=androidx.compose.ui.platform.LocalContext.current
            val profile = runCatching { com.example.sasalele.data.IdentitySamples.byKey(profileContext,client.key) }.getOrNull()
            Text(profile?.summary ?: "暂无画像摘要")
            Text("来源: ${profile?.source ?: "来源未提供"}")
            Spacer(modifier = Modifier.height(8.dp))
            Text("公司: ${client.company}")
            Spacer(modifier = Modifier.height(8.dp))
            Text("意向: ${client.intentLevel}")
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { 
                    ClientRepository.unbindClient()
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("解除绑定")
            }
        } else {
            Text("未绑定客户")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("长按客户图标截当前屏幕；拖动使用收起箭头")
        TextButton(onClick=onSearch) { Text("全量画像搜索／选择已有图片／导入JSON") }
        /*
        OcrResultState.entries.forEach { state ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state == ocrState,
                    onClick = { onOcrStateChange(state) }
                )
                Text(state.name)
            }
        } */
    }
}

@Composable
fun OcrResultPanel(ocrResult: OcrResult?, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("识别结果", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onClose) { Text("关闭") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        when (ocrResult) {
            is OcrResult.Success -> {
                Text("成功匹配到客户：${ocrResult.client.name}")
                Button(onClick = {
                    ClientRepository.bindClient(ocrResult.client)
                    onClose()
                }) {
                    Text("绑定客户")
                }
            }
            is OcrResult.MultiCandidate -> {
                Text("匹配到多个客户，请选择：")
                LazyColumn {
                    items(ocrResult.candidates) { candidate ->
                        Button(onClick = {
                            ClientRepository.bindClient(candidate)
                            onClose()
                        }) {
                            Text(candidate.name)
                        }
                    }
                }
            }
            OcrResult.NoMatch -> Text("未匹配到客户")
            OcrResult.NoClue -> Text("未提取到有效信息")
            OcrResult.NonPrivateChat -> Text("非私聊页面，无法识别")
            null -> Text("未知结果")
        }
    }
}


@Composable
fun AIGuidancePanel(context: Context, onClose: () -> Unit) {
    val db = remember { AppDatabase.getDatabase(context) }
    val repository = remember { GuidanceRepository(db.guidanceMessageDao()) }
    val currentClient by ClientRepository.currentBoundClient.collectAsState()
    
    // UI state: true = showing Leaderboard, false = showing Chat
    var showLeaderboard by remember { mutableStateOf(currentClient == null) }
    
    LaunchedEffect(currentClient) {
        if (currentClient != null) {
            showLeaderboard = false
        } else {
            showLeaderboard = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI 销售指导", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onClose) { Text("关闭") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        if (showLeaderboard) {
            Text("当前客户排名: 前15%", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("待处理客户跟进建议：")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(3) { index ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { 
                                ClientRepository.viewProfile(Client("c_$index", "wa_1", "待跟进客户$index", "公司$index", "中意向"))
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("待跟进客户$index", fontWeight = FontWeight.Bold)
                            Text("上周联系过，建议今天发个问候。")
                        }
                    }
                }
            }
        } else {
            val scope = rememberCoroutineScope()
            val clientId = currentClient?.id ?: ""
            val workAccountId = currentClient?.workAccountId ?: ""
            
            val messages by repository.getMessagesForClient(clientId, workAccountId)
                .collectAsState(initial = emptyList())
                
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showLeaderboard = true }) {
                    Text("返回榜单")
                }
                Text(currentClient?.name ?: "", fontWeight = FontWeight.SemiBold)
            }
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    ChatBubbleMock(text = msg.content, isUser = msg.isFromUser)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        val newCrmMsg = GuidanceMessageEntity(
                            clientId = clientId,
                            workAccountId = workAccountId,
                            content = "CRM 记录: 客户刚刚在官网浏览了报价单 (时间: ${System.currentTimeMillis()})",
                            isFromUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                        repository.insertMessage(newCrmMsg)
                        
                        delay(1000)
                        
                        val aiAdvice = GuidanceMessageEntity(
                            clientId = clientId,
                            workAccountId = workAccountId,
                            content = "AI建议: 客户对价格比较敏感，建议强调我们的服务和ROI。",
                            isFromUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                        repository.insertMessage(aiAdvice)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("看最新消息给我建议")
            }
        }
    }
}


@Composable
fun ChatBubbleMock(text: String, isUser: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer else Color.White,
                    RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Text(text, fontSize = 14.sp)
        }
    }
}


enum class SmartReplyState {
    IDLE, LOADING, SUCCESS, ERROR
}

@Composable
fun SmartReplyPanel(context: Context, onClose: () -> Unit) {
    val currentClient by ClientRepository.currentBoundClient.collectAsState()
    var manualContext by remember { mutableStateOf("") }
    
    var generateState by remember { mutableStateOf(SmartReplyState.IDLE) }
    var generatedReplies by remember { mutableStateOf<List<String>>(emptyList()) }
    var requestId by remember { mutableStateOf(0) }
    
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { SettingsDataStore(context) }
    val autoCopy by settingsDataStore.autoCopyFlow.collectAsState(initial = true)

    fun startGeneration() {
        if (currentClient == null && manualContext.isBlank()) {
            Toast.makeText(context, "请先绑定客户或输入上下文", Toast.LENGTH_SHORT).show()
            return
        }
        
        generateState = SmartReplyState.LOADING
        requestId++
        val currentRequestId = requestId
        val boundClientId = currentClient?.id
        val requestedContext = manualContext
        
        scope.launch {
            delay(1500) // Mock API call
            
            // Versioning check
            if (currentRequestId != requestId || boundClientId != currentClient?.id || requestedContext != manualContext) {
                return@launch
            }
            
            generateState = SmartReplyState.SUCCESS
            generatedReplies = listOf(
                "您好，感谢您对我们产品的关注！针对您提到的需求，我们的方案是...",
                "关于价格方面，我们可以为您提供一个特别的优惠政策...",
                "不知道您下周三下午是否方便？我可以过去给您做个详细演示。"
            )
            
            if (autoCopy && generatedReplies.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("smart_reply", generatedReplies.first())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已自动复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("智能回复", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onClose) { Text("关闭") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        if (currentClient == null) {
            OutlinedTextField(
                value = manualContext,
                onValueChange = { manualContext = it },
                label = { Text("输入上下文") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        when (generateState) {
            SmartReplyState.IDLE -> {
                Button(onClick = { startGeneration() }, modifier = Modifier.fillMaxWidth()) {
                    Text("生成回复")
                }
            }
            SmartReplyState.LOADING -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { generateState = SmartReplyState.IDLE }, modifier = Modifier.fillMaxWidth()) {
                    Text("取消")
                }
            }
            SmartReplyState.SUCCESS -> {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(generatedReplies) { reply ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(reply, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.align(Alignment.End)) {
                                        TextButton(
                                            onClick = { /* Edit logic can go here */ },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("编辑")
                                        }
                                        TextButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("smart_reply", reply)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("复制")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = { startGeneration() }) {
                            Text("重新生成")
                        }
                        OutlinedButton(onClick = { generateState = SmartReplyState.IDLE }) {
                            Text("返回")
                        }
                    }
                }
            }
            SmartReplyState.ERROR -> {
                Text("生成失败，请重试", color = Color.Red)
                Button(onClick = { startGeneration() }, modifier = Modifier.fillMaxWidth()) {
                    Text("重试")
                }
            }
        }
    }
}


@Composable
fun MaterialsPanel(context: Context, onClose: () -> Unit) {
    val db = remember { AppDatabase.getDatabase(context) }
    val repository = remember { MaterialRepository(db.materialDao()) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        val materials = repository.allMaterials.first()
        if (materials.isEmpty()) {
            repository.insertAll(listOf(
                MaterialEntity(title = "公司介绍", content = "我们是一家提供智能SaaS服务的科技公司...", type = "TEXT"),
                MaterialEntity(title = "产品报价单", content = "标准版：199/月\n高级版：399/月", type = "TEXT", isFavorite = true),
                MaterialEntity(title = "宣传海报", content = "https://fastly.picsum.photos/id/237/200/300.jpg?hmac=TmmQSbShHz9CdQm0NkEjx1Dyh_Y984R9LpNrpvH2D_U", type = "IMAGE")
            ))
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val materials by if (searchQuery.isBlank()) {
        repository.allMaterials.collectAsState(initial = emptyList())
    } else {
        repository.searchMaterials(searchQuery).collectAsState(initial = emptyList())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("销售物料", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row {
                IconButton(onClick = {
                    val intent = Intent(context, MainActivity::class.java).setAction("com.example.sasalele.ACTION_ADD_MATERIAL_IMAGE").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Image, contentDescription = "添加图片")
                }
                TextButton(onClick = onClose) { Text("关闭") }
            }
        }
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text("搜索物料...") },
            singleLine = true
        )
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(materials) { material ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(material.title, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { 
                                scope.launch {
                                    repository.update(material.copy(isFavorite = !material.isFavorite))
                                }
                            }) {
                                Icon(
                                    imageVector = if (material.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "收藏",
                                    tint = if (material.isFavorite) Color.Red else Color.Gray
                                )
                            }
                        }
                        
                        if (material.type == "IMAGE") {
                            AsyncImage(
                                model = if (material.content.startsWith("/")) java.io.File(material.content) else material.content,
                                contentDescription = "素材图片",
                                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(material.content, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (material.type == "IMAGE") {
                                TextButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND)
                                        if (material.content.startsWith("/")) {
                                            val file = java.io.File(material.content)
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            shareIntent.type = "image/*"
                                            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        } else {
                                            shareIntent.type = "text/plain"
                                            shareIntent.putExtra(Intent.EXTRA_TEXT, material.content)
                                        }
                                        
                                        val chooser = Intent.createChooser(shareIntent, "分享图片")
                                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(chooser)
                                    }
                                ) {
                                    Text("分享")
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("material_content", material.content)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("复制")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SettingsPanel(context: Context, onClose: () -> Unit) {
    val settingsDataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    
    val autoCopy by settingsDataStore.autoCopyFlow.collectAsState(initial = true)
    val enableReminders by settingsDataStore.enableRemindersFlow.collectAsState(initial = false)
    val floatingWindowY by settingsDataStore.floatingWindowYFlow.collectAsState(initial = 0)
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("设置", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onClose) { Text("关闭") }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("生成后自动复制")
                    Switch(
                        checked = autoCopy,
                        onCheckedChange = { scope.launch { settingsDataStore.setAutoCopy(it) } }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("开启跟进提醒")
                    Switch(
                        checked = enableReminders,
                        onCheckedChange = { scope.launch { settingsDataStore.setEnableReminders(it) } }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("悬浮窗默认 Y 轴位置: $floatingWindowY")
                    Slider(
                        value = floatingWindowY.toFloat(),
                        onValueChange = { scope.launch { settingsDataStore.setFloatingWindowY(it.toInt()) } },
                        valueRange = 0f..1000f
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("其他设置", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(onClick = { /* Placeholder */ }, modifier = Modifier.fillMaxWidth()) {
                    Text("暂停助手 (Pause Assistant)")
                }
                TextButton(onClick = { /* Placeholder */ }, modifier = Modifier.fillMaxWidth()) {
                    Text("权限指南 (Permission Guide)")
                }
                TextButton(onClick = { /* Placeholder */ }, modifier = Modifier.fillMaxWidth()) {
                    Text("诊断 (Diagnostics)")
                }
            }
        }
    }
}

@Composable
fun RemindersPanel(onClose: () -> Unit) {
    val pendingRemindersMap by com.example.sasalele.data.ReminderScheduler.pendingReminders.collectAsState()
    val pendingReminders = pendingRemindersMap.values.filter { it.status == "ACTIVE" && !it.isMuted }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("智能跟进提醒", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onClose) { Text("关闭") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (pendingReminders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无待处理提醒", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pendingReminders) { reminderState ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "客户: ${reminderState.clientId}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("待处理消息: ${reminderState.messages.size}条", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(onClick = { 
                                    com.example.sasalele.data.ReminderScheduler.handleAction(
                                        reminderState.workAccountId, reminderState.clientId, 
                                        com.example.sasalele.data.ReminderAction.REPLIED
                                    ) 
                                }) { Text("已回复") }
                                
                                TextButton(onClick = { 
                                    com.example.sasalele.data.ReminderScheduler.handleAction(
                                        reminderState.workAccountId, reminderState.clientId, 
                                        com.example.sasalele.data.ReminderAction.LATER
                                    ) 
                                }) { Text("稍后") }
                                
                                TextButton(onClick = { 
                                    com.example.sasalele.data.ReminderScheduler.handleAction(
                                        reminderState.workAccountId, reminderState.clientId, 
                                        com.example.sasalele.data.ReminderAction.IGNORE
                                    ) 
                                }) { Text("忽略") }
                            }
                        }
                    }
                }
            }
        }
    }
}