package com.example.sasalele
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File
class PickRecognitionImageActivity:ComponentActivity() {
 private val picker=registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
  if(uri==null) { android.widget.Toast.makeText(this,"已取消选图",0).show(); finish() }
  else lifecycleScope.launch {
   var file:File?=null
   try {
    file=File(cacheDir,"recognition-images/${java.util.UUID.randomUUID()}.image").apply { parentFile?.mkdirs() }
    withTimeout(15000) { withContext(Dispatchers.IO) {
     contentResolver.openInputStream(uri)?.use { input -> file!!.outputStream().use { output ->
      val buffer=ByteArray(8192); var total=0
      while(true) { ensureActive(); val n=input.read(buffer); if(n<0) break; total+=n; require(total<=50*1024*1024) { "图片超过50MB" }; output.write(buffer,0,n) }
     } } ?: error("图片无法打开")
    } }
    com.example.sasalele.data.ImagePickerBus.sendUri(Uri.fromFile(file))
   } catch(e:CancellationException) { file?.delete(); throw e }
   catch(e:Exception) { file?.delete(); android.widget.Toast.makeText(this@PickRecognitionImageActivity,"选图失败：${e.message ?: "图片无法打开"}",1).show() }
   finally { finish() }
  }
 }
 override fun onCreate(state:Bundle?) { super.onCreate(state); if(state==null) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
 override fun onDestroy() { if(isFinishing) AcquisitionUi.hidden.value=false; super.onDestroy() }
}
