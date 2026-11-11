package com.example.sasalele

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.TextFields
import com.example.sasalele.data.MaterialKind
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    var panelExpanded by remember { mutableStateOf(false) }
    var panelValue by remember { mutableStateOf(ActivePanel.NONE) }
    var panelRevision by remember { mutableIntStateOf(0) }
    // A panel change and expansion reset are one state transaction.
    var activePanel: ActivePanel by remember<ReadWriteProperty<Any?, ActivePanel>> {
        object : ReadWriteProperty<Any?, ActivePanel> {
            override fun getValue(thisRef: Any?, property: KProperty<*>) = panelValue
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: ActivePanel) {
                if (panelValue != value) { panelExpanded = false; panelRevision++ }
                panelValue = value
            }
        }
    }
    var returnPanel by remember { mutableStateOf(ActivePanel.NONE) }
    val generationStore = remember { GenerationDraftStore() }

    val positionPrefs = remember { context.getSharedPreferences("floating_position_v31", Context.MODE_PRIVATE) }
    var dragX by remember { mutableIntStateOf(positionPrefs.getInt("x",0)) }
    var dragY by remember { mutableIntStateOf(positionPrefs.getInt("y",0)) }
    var contentBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val detailOpened by com.example.sasalele.data.BusinessUi.detailOpened.collectAsState()
    val fullOverlay = activePanel != ActivePanel.NONE
    val configuration = LocalConfiguration.current
    val displayDensity = LocalDensity.current
    // Compose configuration changes for rotation, display size and font scale must
    // invalidate the actual WindowManager bounds, not just the inner layout.
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val orientation = configuration.orientation
    val fontScale = displayDensity.fontScale
    val density = displayDensity.density
    val layoutKey = listOf(activePanel,panelRevision,panelExpanded,screenWidthDp,screenHeightDp,orientation,density,fontScale)
    var readyLayoutKey by remember { mutableStateOf<List<Any>?>(null) }
    var readyPanel by remember { mutableStateOf(ActivePanel.NONE) }
    LaunchedEffect(activePanel,isExpanded,panelExpanded,dragX,dragY,screenWidthDp,screenHeightDp,orientation,density,fontScale) {
        val screenWidth=(screenWidthDp*density).roundToInt().coerceAtLeast(1)
        val screenHeight=(screenHeightDp*density).roundToInt().coerceAtLeast(1)
        val sideWidth=maxOf((screenWidth*.72f).roundToInt(), (320*density*fontScale.coerceIn(1f,1.3f)).roundToInt()).coerceAtMost(screenWidth)
        val side=activePanel in setOf(ActivePanel.CLIENT_PROFILE,ActivePanel.AI_GUIDANCE,ActivePanel.MATERIALS)
        val reply=activePanel==ActivePanel.SMART_REPLY
        params.width=when { activePanel==ActivePanel.NONE -> ((if(isExpanded) 72 else 56)*density).toInt(); panelExpanded || !side -> WindowManager.LayoutParams.MATCH_PARENT; else -> sideWidth }
        params.height=when { activePanel==ActivePanel.NONE -> ((if(isExpanded) 328 else 48)*density).roundToInt(); panelExpanded || (!side && !reply) || side -> WindowManager.LayoutParams.MATCH_PARENT; else -> (screenHeight*.58f).toInt() }
        params.gravity=if(reply && !panelExpanded) android.view.Gravity.BOTTOM or android.view.Gravity.END else android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
        val toolbarWidth=((if(isExpanded) 72 else 56)*density).roundToInt()
        val toolbarHeight=((if(isExpanded) 328 else 48)*density).roundToInt()
        val maxHorizontal=(screenWidth-toolbarWidth).coerceAtLeast(0)
        val maxVertical=((screenHeight-toolbarHeight).coerceAtLeast(0)/2)
        params.x=if(activePanel==ActivePanel.NONE) (-dragX).coerceIn(0,maxHorizontal) else 0
        params.y=if(activePanel==ActivePanel.NONE) dragY.coerceIn(-maxVertical,maxVertical) else 0
        params.flags=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or (if(activePanel==ActivePanel.NONE) WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE else 0)
        params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        windowManager.updateViewLayout(view,params)
        if (activePanel != ActivePanel.NONE) {
            val requestedWidth=params.width
            val requestedHeight=params.height
            // Do not display a new panel until the WindowManager's new bounds
            // have reached the view; the old narrow toolbar is not a panel layout.
            suspendCancellableCoroutine<Unit> { continuation ->
                val observer=view.viewTreeObserver
                val listener=object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        val widthReady=if(requestedWidth==WindowManager.LayoutParams.MATCH_PARENT)
                            view.width >= screenWidth*.90f
                        else kotlin.math.abs(view.width-requestedWidth) <= 2*density
                        val imeShown=ViewCompat.getRootWindowInsets(view)
                            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                        val heightReady=imeShown || if(requestedHeight==WindowManager.LayoutParams.MATCH_PARENT)
                            view.height >= screenHeight*.85f
                        else kotlin.math.abs(view.height-requestedHeight) <= 2*density
                        if(widthReady && heightReady && continuation.isActive) {
                            if(observer.isAlive) observer.removeOnPreDrawListener(this)
                            continuation.resume(Unit)
                        }
                        return true
                    }
                }
                observer.addOnPreDrawListener(listener)
                continuation.invokeOnCancellation { if(observer.isAlive) observer.removeOnPreDrawListener(listener) }
                view.invalidate()
            }
            readyPanel=activePanel
            readyLayoutKey=layoutKey
        } else {
            readyPanel=ActivePanel.NONE
            readyLayoutKey=null
        }
    }
    val keyboardController=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    val imeVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    BackHandler(enabled=activePanel!=ActivePanel.NONE) {
        if(imeVisible) { keyboardController?.hide(); focusManager.clearFocus() }
        else if(com.example.sasalele.data.BusinessUi.detailOpened.value) com.example.sasalele.data.BusinessUi.detailOpened.value=false
        else if(panelExpanded) panelExpanded=false
        else activePanel=if(activePanel==ActivePanel.SETTINGS && returnPanel!=ActivePanel.NONE) returnPanel.also { returnPanel=ActivePanel.NONE } else ActivePanel.NONE
    }
    val chosenWorkAccount by com.example.sasalele.data.WorkAccountSelection.current.collectAsState()
    LaunchedEffect(chosenWorkAccount?.id) {
        if(chosenWorkAccount!=null && activePanel==ActivePanel.SETTINGS && returnPanel!=ActivePanel.NONE) {activePanel=returnPanel;returnPanel=ActivePanel.NONE}
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
            if(capture.image!=null) startRecognition(capture.image!!,com.example.sasalele.data.HeaderCrop(excludeSystemStatusBar=true))
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

    val rootModifier = if(activePanel==ActivePanel.NONE) Modifier.wrapContentSize() else Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp)
    Box(rootModifier) {
        Row(
            modifier = Modifier.wrapContentSize().align(Alignment.CenterEnd).onGloballyPositioned { contentBounds=it.boundsInRoot() }.offset { androidx.compose.ui.unit.IntOffset(if(fullOverlay && activePanel==ActivePanel.NONE) dragX else 0,if(fullOverlay && activePanel==ActivePanel.NONE) dragY else 0) },
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val pillContent = @Composable {
                if (isExpanded) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Collapse Button
                    IconButton(
                        onClick = { 
                            isExpanded = false
                            activePanel = ActivePanel.NONE 
                        },
                        modifier = Modifier.size(36.dp)
                        .pointerInput(screenWidthDp, screenHeightDp, density, isExpanded) {
                            detectDragGestures(onDragEnd={positionPrefs.edit().putInt("x",dragX).putInt("y",dragY).apply()}) { change, dragAmount ->
                                change.consume()
                                val widthPx=(screenWidthDp*density).roundToInt()
                                val heightPx=(screenHeightDp*density).roundToInt()
                                val barWidth=((if(isExpanded) 72 else 56)*density).roundToInt()
                                val barHeight=((if(isExpanded) 328 else 48)*density).roundToInt()
                                val maxX=(widthPx-barWidth).coerceAtLeast(0)
                                val maxY=(heightPx-barHeight).coerceAtLeast(0)/2
                                dragX = (dragX.coerceIn(-maxX,0) + dragAmount.x.toInt()).coerceIn(-maxX,0)
                                dragY = (dragY.coerceIn(-maxY,maxY) + dragAmount.y.toInt()).coerceIn(-maxY,maxY)
                                isDockedLeft = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isDockedLeft) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "收起",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Client/Avatar (Entry 1)
                    FloatingIcon(
                        icon = Icons.Default.AccountCircle, // Ideally use avatar if available
                        description = "客户资料",
                        avatarUrl = currentClient?.avatarUrl,
                        label = currentClient?.name ?: "客户",
                        tint = if (currentClient != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            recognitionJob?.cancel()
                            com.example.sasalele.data.CaptureSession.request(context)
                        },
                        onClick = {
                            activePanel = if (activePanel == ActivePanel.CLIENT_PROFILE) ActivePanel.NONE else ActivePanel.CLIENT_PROFILE
                        }
                    )

                    // Smart Reply (Entry 3)
                    FloatingIcon(
                        icon = Icons.Default.ChatBubble,
                        description = "回复",
                        label = "回复",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            activePanel = if (activePanel == ActivePanel.SMART_REPLY) ActivePanel.NONE else ActivePanel.SMART_REPLY
                        }
                    )

                    // Reminders
                    val reminderStates by ReminderScheduler.pendingReminders.collectAsState()
                    val badgeAccount by com.example.sasalele.data.WorkAccountSelection.current.collectAsState()
                    val activeFollowups by com.example.sasalele.data.FollowupRepository.state.collectAsState()
                    val pillPendingCount=if(activeFollowups.accountId==badgeAccount?.id)activeFollowups.contacts.count{it.status=="needs_review"} else 0
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
                            icon = Icons.Default.NotificationsActive,
                            description = "智能跟进提醒",
                            label = "跟进",
                            onClick = { activePanel = if (activePanel == ActivePanel.REMINDERS) ActivePanel.NONE else ActivePanel.REMINDERS }
                        )
                    }

                    // Materials
                    FloatingIcon(
                        icon = Icons.Default.PhotoLibrary,
                        description = "销售物料",
                        label = "物料",
                        onClick = { activePanel = if (activePanel == ActivePanel.MATERIALS) ActivePanel.NONE else ActivePanel.MATERIALS }
                    )

                    // Settings
                    FloatingIcon(
                        icon = Icons.Default.Settings,
                        description = "更多设置",
                        label = "更多",
                        onClick = { activePanel = if (activePanel == ActivePanel.SETTINGS) ActivePanel.NONE else ActivePanel.SETTINGS }
                    )
                }
            } else {
                // Collapsed state
                val collapsedStates by ReminderScheduler.pendingReminders.collectAsState()
                val collapsedAccount by com.example.sasalele.data.WorkAccountSelection.current.collectAsState()
                val collapsedFollowups by com.example.sasalele.data.FollowupRepository.state.collectAsState()
                val pendingRemindersCount=if(collapsedFollowups.accountId==collapsedAccount?.id)collapsedFollowups.contacts.count{it.status=="needs_review"} else 0
                Box(
                    modifier = Modifier
                        .pointerInput(screenWidthDp, screenHeightDp, density, isExpanded) {
                            detectDragGestures(onDragEnd={positionPrefs.edit().putInt("x",dragX).putInt("y",dragY).apply()}) { change, dragAmount ->
                                change.consume()
                                val widthPx=(screenWidthDp*density).roundToInt()
                                val heightPx=(screenHeightDp*density).roundToInt()
                                val barWidth=((if(isExpanded) 72 else 56)*density).roundToInt()
                                val barHeight=((if(isExpanded) 328 else 48)*density).roundToInt()
                                val maxX=(widthPx-barWidth).coerceAtLeast(0)
                                val maxY=(heightPx-barHeight).coerceAtLeast(0)/2
                                dragX = (dragX.coerceIn(-maxX,0) + dragAmount.x.toInt()).coerceIn(-maxX,0)
                                dragY = (dragY.coerceIn(-maxY,maxY) + dragAmount.y.toInt()).coerceIn(-maxY,maxY)
                                isDockedLeft = false
                            }
                        }
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { isExpanded = true }) {
                        Icon(
                            imageVector = if (isDockedLeft) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            if (activePanel != ActivePanel.NONE && readyPanel == activePanel) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if(readyLayoutKey == layoutKey) 1f else 0f }
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(when(activePanel){ActivePanel.CLIENT_PROFILE->"客户资料";ActivePanel.AI_GUIDANCE->"销售指导";ActivePanel.SMART_REPLY->"帮我回 / 润色";ActivePanel.OCR_RESULT->"识别与确认";ActivePanel.SETTINGS->"助手设置";ActivePanel.MATERIALS->"销售物料";ActivePanel.REMINDERS->"跟进提醒";else->"销售助手"},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        IconButton(onClick={panelExpanded=!panelExpanded},modifier=Modifier.size(48.dp).semantics { contentDescription=if(panelExpanded) "收起面板" else "展开面板" }){Text(if(panelExpanded) "↙" else "↗",style=MaterialTheme.typography.titleLarge)}
                        IconButton(onClick={com.example.sasalele.data.BusinessUi.detailOpened.value=false;activePanel=if(activePanel==ActivePanel.SETTINGS && returnPanel!=ActivePanel.NONE) returnPanel.also { returnPanel=ActivePanel.NONE } else ActivePanel.NONE},modifier=Modifier.size(48.dp).semantics { contentDescription=if(activePanel==ActivePanel.SETTINGS && returnPanel!=ActivePanel.NONE) "返回" else "关闭面板" }){Text(if(activePanel==ActivePanel.SETTINGS && returnPanel!=ActivePanel.NONE) "‹" else "×",style=MaterialTheme.typography.headlineSmall)}
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    when (activePanel) {
                        ActivePanel.CLIENT_PROFILE -> WorkAccountScope(context,{returnPanel=activePanel;activePanel=ActivePanel.SETTINGS}) {BusinessProfilePanel(context, onSearch = { recognitionUri=null; headerRecognition=null; recognitionError=null; activePanel=ActivePanel.OCR_RESULT }, onGuidance={activePanel=ActivePanel.AI_GUIDANCE})}
                        ActivePanel.AI_GUIDANCE -> WorkAccountScope(context,{returnPanel=activePanel;activePanel=ActivePanel.SETTINGS}) {BusinessGenerationPanel(context,guidanceOnly=true,store=generationStore)}
                        ActivePanel.SMART_REPLY -> WorkAccountScope(context,{returnPanel=activePanel;activePanel=ActivePanel.SETTINGS}) {BusinessGenerationPanel(context,store=generationStore)}
                        ActivePanel.MATERIALS -> MaterialsPanel(context, onClose = { activePanel = ActivePanel.NONE })
                        ActivePanel.SETTINGS -> SettingsPanel(context, onClose = { activePanel = ActivePanel.NONE })
                        ActivePanel.REMINDERS -> RemindersPanel(onClose = { activePanel = ActivePanel.NONE },onSettings={returnPanel=activePanel;activePanel=ActivePanel.SETTINGS})
                        ActivePanel.OCR_RESULT -> WorkAccountScope(context,{returnPanel=activePanel;activePanel=ActivePanel.SETTINGS}) {BusinessSearchPanel(context, recognitionUri, headerRecognition, recognitionError,
                            onBound = { recognitionJob?.cancel(); com.example.sasalele.data.CaptureSession.clearImage(context); recognitionUri=null; activePanel=ActivePanel.CLIENT_PROFILE },
                            onRead = { region -> recognitionUri?.let { startRecognition(it,region) } })}
                        else -> {}
                    }
                    }
                }
            }
        }

        if (isDockedLeft) {
            if(activePanel==ActivePanel.NONE) pillContent()
            panelContent()
        } else {
            panelContent()
            if(activePanel==ActivePanel.NONE) pillContent()
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
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    avatarUrl: String? = null,
    label: String? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            if(avatarUrl!=null) AsyncImage(model=avatarUrl,contentDescription=description,modifier=Modifier.size(22.dp).clip(CircleShape))
            else Icon(imageVector=icon,contentDescription=description,tint=tint,modifier=Modifier.size(22.dp))
            if(label!=null) Text(label,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=10.sp,lineHeight=12.sp)
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


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialsPanel(context: Context, onClose: () -> Unit, repositoryOverride: MaterialRepository? = null) {
    if(repositoryOverride!=null) { LocalMaterialsPanel(context,onClose,repositoryOverride);return }
    var team by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        Row {
            FilterChip(selected=team,onClick={team=true},label={Text("团队物料")})
            Spacer(Modifier.width(8.dp))
            FilterChip(selected=!team,onClick={team=false},label={Text("本机物料")})
        }
        Box(Modifier.weight(1f)) {
            if(team) TeamMaterialsPanel(context,onClose,onImportImage={team=false})
            else LocalMaterialsPanel(context,onClose,onUpload={material,request->val team=com.example.sasalele.data.TeamMaterials.upload(context,material,request);com.example.sasalele.data.MaterialNaming.linkUploaded(context,material,team)})
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LocalMaterialsPanel(context: Context, onClose: () -> Unit, repositoryOverride: MaterialRepository? = null,
    onUpload:(suspend (MaterialEntity,String)->Unit)?=null) {
    val repository = remember(context,repositoryOverride) { repositoryOverride ?: MaterialRepository(AppDatabase.getDatabase(context).materialDao()) }
    val scope = rememberCoroutineScope()

    var pendingDelete by remember { mutableStateOf<MaterialEntity?>(null) }
    var imageBusy by remember { mutableStateOf(false) }
    var materialFeedback by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<MaterialEntity?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var renameBusy by remember { mutableStateOf(false) }
    var pendingUpload by remember { mutableStateOf<MaterialEntity?>(null) }
    var uploadTitle by remember { mutableStateOf("") }
    var uploadBusy by remember { mutableStateOf(false) }
    fun imageAction(material: MaterialEntity, copy: Boolean) {
        scope.launch {
            imageBusy=true; materialFeedback="正在准备图片…"
            try {
                val image=com.example.sasalele.data.MaterialImages.prepare(context,material.content)
                if(copy) {
                    com.example.sasalele.data.MaterialImages.copy(context,image)
                    materialFeedback="已复制图片；如微信未提供图片粘贴，请使用分享图片"
                } else {
                    val share=com.example.sasalele.data.MaterialImages.shareIntent(context,image)
                    onClose()
                    context.startActivity(Intent.createChooser(share,"分享图片").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    materialFeedback="已打开图片分享"
                }
            } catch(e:kotlinx.coroutines.CancellationException){throw e}
              catch(e:Exception){materialFeedback=if(e is IllegalArgumentException) e.message ?: "图片准备失败，请重试" else "图片读取或下载失败，请重试"}
            finally {imageBusy=false}
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf(MaterialKind.ALL) }
    var filterExpanded by remember { mutableStateOf(false) }
    var addingText by remember { mutableStateOf(false) }
    var draftTitle by remember { mutableStateOf("") }
    var draftContent by remember { mutableStateOf("") }
    var savingText by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val allMaterials by repository.allMaterials.collectAsState(initial = emptyList())
    val materials = allMaterials.filter { material ->
        material.title.contains(searchQuery.trim(),ignoreCase=true) &&
        (selectedKind==MaterialKind.ALL || MaterialKind.of(material)==selectedKind)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("本机", style = MaterialTheme.typography.labelMedium)
            Row {
                IconButton(enabled=!savingText,onClick={addingText=true;materialFeedback=""}) {
                    Icon(Icons.Default.TextFields,contentDescription="添加文本或链接")
                }
                IconButton(onClick = {
                    AcquisitionUi.open(context,PickMaterialImageActivity::class.java)
                }) {
                    Icon(Icons.Default.Image, contentDescription = "添加图片")
                }

            }
        }
        if(addingText) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("新增文本或链接",fontWeight=FontWeight.Bold)
                OutlinedTextField(value=draftTitle,onValueChange={draftTitle=it},label={Text("物料标题")},
                    enabled=!savingText,singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=draftContent,onValueChange={draftContent=it},label={Text("文本内容或完整链接")},
                    enabled=!savingText,minLines=3,maxLines=8,modifier=Modifier.fillMaxWidth())
                Text("完整的 http:// 或 https:// 网址自动归为链接，其余内容归为纯文本。",style=MaterialTheme.typography.bodySmall)
                Row {
                    Button(enabled=!savingText && draftTitle.isNotBlank() && draftContent.isNotBlank(),onClick={
                        val candidate=MaterialEntity(title=draftTitle.trim(),content=draftContent.trim(),type="TEXT")
                        val material=candidate.copy(type=MaterialKind.of(candidate).name)
                        savingText=true
                        scope.launch {
                            try {
                                repository.insert(material)
                                searchQuery="";selectedKind=MaterialKind.ALL
                                draftTitle="";draftContent="";addingText=false
                                focusManager.clearFocus();materialFeedback="已添加${MaterialKind.of(material).label}物料"
                            } catch(e:kotlinx.coroutines.CancellationException){throw e}
                              catch(e:Exception){materialFeedback="保存失败，请重试"}
                            finally{savingText=false}
                        }
                    }) { Text(if(savingText) "保存中…" else "保存物料") }
                    TextButton(enabled=!savingText,onClick={addingText=false;draftTitle="";draftContent="";focusManager.clearFocus()}){Text("取消新增")}
                }
                if(materialFeedback.isNotBlank()) Text(materialFeedback)
            }
            return@Column
        }
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text("搜索物料...") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick={filterExpanded=!filterExpanded}) {
                    Icon(Icons.Default.FilterAlt,contentDescription="筛选物料类型",
                        tint=if(selectedKind==MaterialKind.ALL) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                }
            }
        )
        if(filterExpanded) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            MaterialKind.entries.forEach { kind ->
                FilterChip(selected=selectedKind==kind,onClick={selectedKind=kind},label={Text(kind.label)})
            }
        }
        if(selectedKind!=MaterialKind.ALL) Text("当前筛选：${selectedKind.label}",style=MaterialTheme.typography.bodySmall)
        if(materials.isEmpty() && (searchQuery.isNotBlank() || selectedKind!=MaterialKind.ALL)) {
            Text("没有符合条件的物料")
            TextButton(onClick={searchQuery="";selectedKind=MaterialKind.ALL}) { Text("清除筛选和搜索") }
        }

        
        if(materialFeedback.isNotBlank()) Text(materialFeedback)
        if(onUpload!=null) Text("本机内容不会自动共享。确认后上传到团队库，所有工作机可用。",style=MaterialTheme.typography.bodySmall)
        renameTarget?.let { target->Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
            OutlinedTextField(renameTitle,{renameTitle=it.take(120)},label={Text("本机物料标题")},enabled=!renameBusy,modifier=Modifier.fillMaxWidth())
            Row {
                TextButton(enabled=!renameBusy && renameTitle.isNotBlank(),onClick={scope.launch {
                    renameBusy=true
                    try {materialFeedback=com.example.sasalele.data.MaterialNaming.renameLocal(context,target,renameTitle,repository);renameTarget=null}
                    catch(e:kotlinx.coroutines.CancellationException){throw e}
                    catch(e:Exception){materialFeedback="标题保存失败，请重试"}
                    finally{renameBusy=false}
                    scope.launch{runCatching{com.example.sasalele.data.MaterialNaming.flush(context,repository)}}
                }}){Text("保存标题")}
                TextButton(enabled=!renameBusy,onClick={renameTarget=null}){Text("取消改名")}
            }
        }}}
        pendingUpload?.let { target->Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
            Text("上传到团队库：${target.title}？")
            OutlinedTextField(value=uploadTitle,onValueChange={uploadTitle=it.take(120)},label={Text("团队物料标题")},singleLine=true,enabled=!uploadBusy,modifier=Modifier.fillMaxWidth())
            if(target.type=="IMAGE")Text("支持PNG、JPEG、WebP；单张不超过25MB。",style=MaterialTheme.typography.bodySmall)
            Row {
                TextButton(enabled=!uploadBusy && uploadTitle.isNotBlank(),onClick={scope.launch {
                    uploadBusy=true
                    try {
                        val prefs=context.getSharedPreferences("material-upload-requests",0)
                        val key="local-${target.id}-${uploadTitle.trim().hashCode()}-${target.content.hashCode()}"
                        val request=prefs.getString(key,null) ?: java.util.UUID.randomUUID().toString().also{prefs.edit().putString(key,it).commit()}
                        onUpload?.invoke(target.copy(title=uploadTitle.trim()),request);pendingUpload=null;materialFeedback="已上传到团队库，其他工作机刷新后可见"
                    }catch(e:kotlinx.coroutines.CancellationException){throw e}
                     catch(e:Exception){materialFeedback=com.example.sasalele.data.businessFeedback(e)}
                    finally{uploadBusy=false}
                }}) {Text(if(uploadBusy) "上传中…" else "确认上传")}
                TextButton(enabled=!uploadBusy,onClick={pendingUpload=null}){Text("取消上传")}
            }
        }}}
        pendingDelete?.let { target ->
            Card(Modifier.fillMaxWidth().padding(bottom=8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("删除物料：${target.title}？")
                    Row {
                        TextButton(onClick={scope.launch{repository.delete(target,java.io.File(context.filesDir,"materials"));pendingDelete=null;materialFeedback="已删除物料"}}){Text("确认删除")}
                        TextButton(onClick={pendingDelete=null}){Text("取消")}
                    }
                }
            }
        }
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
                            TextButton(enabled=!renameBusy,onClick={renameTitle=material.title;renameTarget=material},modifier=Modifier.weight(1f)) {
                                Text(material.title,Modifier.fillMaxWidth(),fontWeight=FontWeight.Bold)
                            }
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
                                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled=!imageBusy){scope.launch {
                                    imageBusy=true
                                    try {
                                        val image=com.example.sasalele.data.MaterialImages.prepare(context,material.content)
                                        AcquisitionUi.open(context,MaterialImagePreviewActivity::class.java){it.putExtra("image_uri",image.uri.toString()).putExtra("image_title",material.title)}
                                    }catch(e:kotlinx.coroutines.CancellationException){throw e}
                                    catch(_:Exception){materialFeedback="图片预览失败，请重试"}
                                    finally{imageBusy=false}
                                }},
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(material.content, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if(onUpload!=null && material.teamSourceId==null) TextButton(enabled=!uploadBusy,onClick={uploadTitle=material.title;pendingUpload=material}){Text("上传到团队库")}
                            TextButton(enabled=!imageBusy,onClick={pendingDelete=material}){Text("删除")}
                            if (material.type == "IMAGE") {
                                TextButton(enabled=!imageBusy,onClick={imageAction(material,true)}){Text("复制图片")}
                                TextButton(enabled=!imageBusy,onClick={imageAction(material,false)}){Text("分享图片")}
                            } else {
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("material_content", material.content)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(if(MaterialKind.of(material)==MaterialKind.LINK) "复制链接" else "复制")
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
    val settingsDataStore=remember { SettingsDataStore(context) }
    val scope=rememberCoroutineScope()
    val autoCopy by settingsDataStore.autoCopyFlow.collectAsState(initial=true)
    val enableReminders by settingsDataStore.enableRemindersFlow.collectAsState(initial=false)
    var diagnostics by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("日常使用",style=MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("生成后自动复制");Switch(checked=autoCopy,onCheckedChange={scope.launch {settingsDataStore.setAutoCopy(it)}})
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("开启跟进提醒");Switch(checked=enableReminders,onCheckedChange={scope.launch {settingsDataStore.setEnableReminders(it)}})
        }
        HorizontalDivider()
        WorkAccountBindingPanel(context)
        HorizontalDivider()
        TextButton(onClick={diagnostics=!diagnostics}) {Text(if(diagnostics) "收起管理与诊断" else "管理与诊断")}
        if(diagnostics) BusinessConnectionPanel(context)
        TextButton(onClick={context.stopService(Intent(context,FloatingWindowService::class.java));Toast.makeText(context,"助手已暂停",Toast.LENGTH_SHORT).show()}) {Text("暂停助手")}
    }
}

@Composable
fun RemindersPanel(onClose: () -> Unit,onSettings:()->Unit={}) {
    FollowupsPanel(onSettings)
}
