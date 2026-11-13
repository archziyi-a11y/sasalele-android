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
    @Synchronized fun request(context: Context) {
        if (state.value.busy) { android.widget.Toast.makeText(context,"已有截图请求，请先完成或取消",0).show(); return }
        clearImage(context)
        val id = java.util.UUID.randomUUID().toString()
        state.value = CaptureStatus(id, busy=true, hidden=true, message="等待系统截图授权")
        try { context.startActivity(Intent(context,com.example.sasalele.CapturePermissionActivity::class.java)
            .putExtra("requestId",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { finish(id,"无法打开系统授权，请选择已有图片") }
    }
    @Synchronized fun targetReady(id: String) { if (state.value.id == id && state.value.busy) state.value=state.value.copy(targetReady=true) }
    @Synchronized fun finish(id: String, message: String, image: Uri? = null): Boolean {
        if (state.value.id != id || !state.value.busy) return false
        state.value=state.value.copy(busy=false,hidden=false,message=message,image=image)
        return true
    }
    fun clearImage(context: Context) {
        File(context.cacheDir,"recognition-images").listFiles()?.forEach { it.delete() }
        state.value.image?.let { if(it.scheme=="file") File(it.path.orEmpty()).takeIf { f -> f.canonicalPath.startsWith(File(context.cacheDir,"screen-capture").canonicalPath+File.separator) }?.delete() }
        state.value=state.value.copy(image=null,message=if(state.value.busy) state.value.message else "")
    }
}
