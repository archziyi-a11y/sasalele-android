package com.example.sasalele

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjectionConfig
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.example.sasalele.data.CaptureSession

/** Translucent handoff only; never shows the assistant home over the original app. */
class CapturePermissionActivity : ComponentActivity() {
    private var id = ""
    private var granted = false
    private var resultReturned = false
    @Deprecated("Android framework callback retained to cancel the exact child authorization Activity on timeout")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=1007) return
        resultReturned=true
        if (CaptureSession.state.value.id != id || !CaptureSession.state.value.busy) { finish(); return }
        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                startForegroundService(Intent(this,ScreenCaptureService::class.java).putExtra("requestId",id)
                    .putExtra("resultCode",resultCode).putExtra("projectionData",data))
                granted=true
            } catch(e:Exception) { CaptureSession.finish(id,"系统无法启动截图服务，请选择已有图片") }
        } else CaptureSession.finish(id,"已取消截图授权，可选择已有图片")
        finish()
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        id=intent.getStringExtra("requestId").orEmpty()
        if (id != CaptureSession.state.value.id || !CaptureSession.state.value.busy) { finish(); return }
        lifecycleScope.launch {
            CaptureSession.state.first { it.id!=id || !it.busy }
            if(!resultReturned && !granted) {
                @Suppress("DEPRECATION") finishActivity(1007)
                finish()
            }
        }
        if(savedInstanceState==null) {
            try {
                val manager=getSystemService(MediaProjectionManager::class.java)
                val request=if(android.os.Build.VERSION.SDK_INT>=34) manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else manager.createScreenCaptureIntent()
                @Suppress("DEPRECATION") startActivityForResult(request,1007)
            } catch(e:Exception) { CaptureSession.finish(id,"此设备无法请求截图授权，请选择已有图片"); finish() }
        }
    }
    override fun onStop() {
        super.onStop()
        if(granted && isFinishing) CaptureSession.targetReady(id)
    }
    override fun onDestroy() {
        super.onDestroy()
        if(isFinishing && !granted && !resultReturned) CaptureSession.finish(id,"截图页面已退出，可选择已有图片")
    }
}
