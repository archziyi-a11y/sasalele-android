package com.example.sasalele

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.sasalele.data.CaptureSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File

class ScreenCaptureService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private var id=""
    private var finished=false
    private var encoding=false
    private var captureWidth=0
    private var captureHeight=0
    private val handler=Handler(Looper.getMainLooper())
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(projection!=null || id.isNotEmpty()) return START_NOT_STICKY
        id=intent?.getStringExtra("requestId").orEmpty()
        if(!CaptureSession.state.value.busy || CaptureSession.state.value.id!=id) { stopSelf(); return START_NOT_STICKY }
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("screen_capture","单次屏幕截图",NotificationManager.IMPORTANCE_LOW))
            val notification=NotificationCompat.Builder(this,"screen_capture").setSmallIcon(R.mipmap.ic_launcher).setContentTitle("正在获取一张截图").build()
            if(Build.VERSION.SDK_INT>=29) startForeground(2,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(2,notification)
            val data=if(Build.VERSION.SDK_INT>=33) intent?.getParcelableExtra("projectionData",Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra<Intent>("projectionData")
            require(data!=null)
            projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent!!.getIntExtra("resultCode",Activity.RESULT_CANCELED),data)
            projection?.registerCallback(object:MediaProjection.Callback() {
                override fun onStop() { if(!finished && !encoding) finishCapture("系统已停止截图，请重新授权或选择已有图片") }
                override fun onCapturedContentResize(width:Int,height:Int) {
                    if(captureWidth>0 && (width!=captureWidth || height!=captureHeight)) finishCapture("屏幕尺寸发生变化，请保持方向后重新截图或选图")
                }
            },handler)
            scope.launch {
                try {
                    withTimeout(15000) {
                        CaptureSession.state.first { it.id==id && it.targetReady }
                        delay(650) // Activity and system permission dialog must leave the compositor.
                        if(!CaptureSession.state.value.busy || CaptureSession.state.value.id!=id) { finishCapture("截图已取消"); return@withTimeout }
                        beginCapture()
                        while(!finished) delay(100)
                    }
                } catch(e:TimeoutCancellationException) { finishCapture("截图超时，已恢复助手；可选择已有图片") }
                catch(e:CancellationException) { throw e }
                catch(e:Exception) { finishCapture("截图失败，已恢复助手；可选择已有图片") }
            }
        } catch(e:Exception) { finishCapture("系统限制或截图启动失败，已恢复助手；可选择已有图片") }
        return START_NOT_STICKY
    }
    private fun beginCapture() {
        val wm=getSystemService(WindowManager::class.java)
        val bounds=if(Build.VERSION.SDK_INT>=30) wm.maximumWindowMetrics.bounds else android.graphics.Rect().apply {
            val metrics=android.util.DisplayMetrics(); @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics); right=metrics.widthPixels; bottom=metrics.heightPixels
        }
        captureWidth=bounds.width(); captureHeight=bounds.height()
        reader=ImageReader.newInstance(captureWidth,captureHeight,PixelFormat.RGBA_8888,2)
        val readyAfter=SystemClock.elapsedRealtime()
        reader!!.setOnImageAvailableListener({ source ->
            val image=source.acquireLatestImage() ?: return@setOnImageAvailableListener
            if(finished || encoding || SystemClock.elapsedRealtime()<readyAfter) { image.close(); return@setOnImageAvailableListener }
            encoding=true
            try {
                val plane=image.planes[0]
                val paddedWidth=captureWidth+(plane.rowStride-plane.pixelStride*captureWidth)/plane.pixelStride
                val padded=Bitmap.createBitmap(paddedWidth,captureHeight,Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(plane.buffer)
                val bitmap=Bitmap.createBitmap(padded,0,0,captureWidth,captureHeight)
                if(bitmap!==padded) padded.recycle()
                image.close()
                releaseProjection()
                scope.launch {
                    var file:File?=null
                    try {
                        file=File(cacheDir,"screen-capture/$id.png")
                        withContext(Dispatchers.IO) {
                            val dark= (1..10).all { x -> (1..10).all { y -> bitmap.getPixel(bitmap.width*x/11,bitmap.height*y/11) and 0x00ffffff == 0 } }
                            require(!dark) { "画面受保护或为空" }
                            File(cacheDir,"screen-capture").mkdirs()
                            file!!.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
                        }
                        if(!CaptureSession.finish(id,"截图完成，正在识别顶栏",Uri.fromFile(file))) file?.delete()
                        finished=true; stopSelf()
                    } catch(e:CancellationException) { file?.delete(); throw e }
                    catch(e:Exception) { file?.delete(); finishCapture("画面受保护或截图处理失败，请选择已有图片") }
                    finally { bitmap.recycle() }
                }
            } catch(e:Exception) { image.close(); finishCapture("截图解码失败，请选择已有图片") }
        },handler)
        display=projection!!.createVirtualDisplay("SASALELE-single-shot",captureWidth,captureHeight,resources.configuration.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
    }
    private fun releaseProjection() {
        reader?.setOnImageAvailableListener(null,null)
        display?.release(); display=null
        reader?.close(); reader=null
        val old=projection; projection=null; old?.stop()
    }
    private fun finishCapture(message:String) {
        if(finished) return
        finished=true
        releaseProjection()
        CaptureSession.finish(id,message)
        stopSelf()
    }
    override fun onDestroy() {
        if(!finished) finishCapture("截图服务已停止，已恢复助手；可选择已有图片")
        releaseProjection(); scope.cancel(); stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
