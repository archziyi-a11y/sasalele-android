package com.example.sasalele

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner

class FloatingWindowService : Service() {
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var serviceLifecycleOwner: ServiceLifecycleOwner

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        serviceLifecycleOwner = ServiceLifecycleOwner()
        serviceLifecycleOwner.onCreate()
        serviceLifecycleOwner.onStart()
        serviceLifecycleOwner.onResume()

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "floating_window_channel")
            .setContentTitle("销售助手已开启")
            .setContentText("悬浮工具条正在运行")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(1, notification)

        com.example.sasalele.data.UnbindScheduler.start(this, serviceScope)
        com.example.sasalele.data.ReminderScheduler.start(this, serviceScope)

        showFloatingWindow()
        java.io.File(cacheDir,"screen-capture").listFiles()?.forEach { it.delete() }
        java.io.File(cacheDir,"recognition-images").listFiles()?.forEach { it.delete() }
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(com.example.sasalele.data.CaptureSession.state,AcquisitionUi.hidden) { state, picker -> state to picker }.collectLatest { (state,picker) ->
                composeView.visibility = if(state.hidden || picker) android.view.View.INVISIBLE else android.view.View.VISIBLE
                if(state.busy) {
                    val complete=kotlinx.coroutines.withTimeoutOrNull(60000) { com.example.sasalele.data.CaptureSession.state.first { !it.busy || it.id!=state.id } }
                    if(complete==null) { stopService(Intent(this@FloatingWindowService,ScreenCaptureService::class.java)); com.example.sasalele.data.CaptureSession.finish(state.id,"授权等待超时，已恢复助手；可选择已有图片") }
                }
            }
        }
    }

    private fun showFloatingWindow() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        layoutParams.x = 0
        layoutParams.y = 0

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(serviceLifecycleOwner)
            setViewTreeViewModelStoreOwner(serviceLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(serviceLifecycleOwner)
            setContent {
                FloatingWindowContent(
                    windowManager = windowManager,
                    params = layoutParams,
                    view = this,
                    context = this@FloatingWindowService
                )
            }
        }
        windowManager.addView(composeView, layoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        AcquisitionUi.hidden.value=false
        stopService(Intent(this,ScreenCaptureService::class.java))
        val capture=com.example.sasalele.data.CaptureSession.state.value
        com.example.sasalele.data.CaptureSession.finish(capture.id,"助手已停止，截图已取消")
        com.example.sasalele.data.CaptureSession.clearImage(this)
        com.example.sasalele.data.ReminderScheduler.stop()
        com.example.sasalele.data.UnbindScheduler.stop()
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        if (::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
        serviceLifecycleOwner.onPause()
        serviceLifecycleOwner.onStop()
        serviceLifecycleOwner.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "floating_window_channel",
                "销售助手悬浮服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
