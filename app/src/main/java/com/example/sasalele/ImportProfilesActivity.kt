package com.example.sasalele
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.sasalele.data.IdentitySamples
import kotlinx.coroutines.launch

object ProfileImportFeedback { val message=kotlinx.coroutines.flow.MutableStateFlow("") }
class ImportProfilesActivity:ComponentActivity() {
 private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if(uri==null) { ProfileImportFeedback.message.value="已取消画像导入"; finish() }
  else lifecycleScope.launch {
   try { ProfileImportFeedback.message.value=IdentitySamples.importJson(this@ImportProfilesActivity,uri) }
   catch(e:kotlinx.coroutines.CancellationException) { throw e }
   catch(e:Exception) { ProfileImportFeedback.message.value="导入失败：${e.message ?: "文件格式不正确"}；原数据保留" }
   finally { finish() }
  }
 }
 override fun onCreate(state:Bundle?) { super.onCreate(state); if(state==null) picker.launch(arrayOf("application/json","text/plain","application/octet-stream")) }
 override fun onDestroy() { if(isFinishing) AcquisitionUi.hidden.value=false; super.onDestroy() }
}
