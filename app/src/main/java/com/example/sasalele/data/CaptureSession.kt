package com.example.sasalele.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

data class CaptureStatus(val id: String = "", val busy: Boolean = false, val hidden: Boolean = false,
    val targetReady: Boolean = false, val message: String = "", val image: Uri? = null)
object CaptureSession {
    val state = MutableStateFlow(CaptureStatus())
    private var appContext:Context?=null
    @Synchronized fun initialize(context:Context) {
        if(appContext!=null) return
        appContext=context.applicationContext
        val last=context.getSharedPreferences("capture-diagnostics",Context.MODE_PRIVATE).getString("last","").orEmpty()
        if(state.value.id.isBlank() && last.isNotBlank()) state.value=CaptureStatus(message=last)
    }
    private fun record(message:String) {
        val context=appContext ?: return
        val prefs=context.getSharedPreferences("capture-diagnostics",Context.MODE_PRIVATE)
        val rows=runCatching { org.json.JSONArray(prefs.getString("history","[]")) }.getOrDefault(org.json.JSONArray())
        val next=org.json.JSONArray()
        for(i in maxOf(0,rows.length()-23) until rows.length()) next.put(rows.get(i))
        next.put(org.json.JSONObject().put("device_time_ms",System.currentTimeMillis())
            .put("uptime_ms",android.os.SystemClock.elapsedRealtime()).put("request_id",state.value.id)
            .put("stage",message.take(500)))
        prefs.edit().putString("last",message.take(500)).putString("history",next.toString()).apply()
        android.util.Log.i("SASALELECapture",message.take(500))
    }
    fun diagnosticText(context:Context):String {
        initialize(context)
        val prefs=context.getSharedPreferences("capture-diagnostics",Context.MODE_PRIVATE)
        return org.json.JSONObject().put("version_code",com.example.sasalele.BuildConfig.VERSION_CODE)
            .put("model",android.os.Build.MODEL).put("android",android.os.Build.VERSION.RELEASE)
            .put("clock_source","device wall clock and elapsedRealtime; no cross-device latency inference")
            .put("events",org.json.JSONArray(prefs.getString("history","[]"))).toString(2)
    }
    @Synchronized fun request(context: Context) {
        initialize(context)
        if (state.value.busy) { android.widget.Toast.makeText(context,"已有截图请求，请先完成或取消",0).show(); return }
        clearImage(context)
        val id = java.util.UUID.randomUUID().toString()
        state.value = CaptureStatus(id, busy=true, hidden=true, message="等待系统截图授权")
        record("等待系统截图授权")
        try { context.startActivity(Intent(context,com.example.sasalele.CapturePermissionActivity::class.java)
            .putExtra("requestId",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) }
        catch (e: Exception) { finish(id,"无法打开系统授权，请选择已有图片") }
    }
    @Synchronized fun progress(id: String, message: String) {
        if(state.value.id==id && state.value.busy) {
            state.value=state.value.copy(message=message)
            record(message)
        }
    }
    @Synchronized fun targetReady(id: String) { if (state.value.id == id && state.value.busy) state.value=state.value.copy(targetReady=true) }
    @Synchronized fun finish(id: String, message: String, image: Uri? = null): Boolean {
        if (state.value.id != id || !state.value.busy) return false
        state.value=state.value.copy(busy=false,hidden=false,message=message,image=image)
        record(message)
        return true
    }
    fun clearImage(context: Context) {
        File(context.cacheDir,"recognition-images").listFiles()?.forEach { it.delete() }
        state.value.image?.let { if(it.scheme=="file") File(it.path.orEmpty()).takeIf { f -> f.canonicalPath.startsWith(File(context.cacheDir,"screen-capture").canonicalPath+File.separator) }?.delete() }
        state.value=state.value.copy(image=null)
    }
}
