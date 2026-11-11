package com.example.sasalele
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.sasalele.data.*
import kotlinx.coroutines.*

/** Administrator imports a per-machine file during issue; no staff login or QR/PC pairing. */
class ProvisionWorkDeviceActivity:ComponentActivity() {
 private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if(uri==null){Toast.makeText(this,"已取消管理员发机配置",0).show();finish()}
  else lifecycleScope.launch {
   try {
    val value=withTimeout(15000){withContext(Dispatchers.IO){
     val bytes=contentResolver.openInputStream(uri)?.use {input ->
      val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(1024)
      while(true){ensureActive();val count=input.read(buffer);if(count<0)break;if(output.size()+count>8192)throw BusinessFailure("发机配置文件超过限制",FailureKind.CONFIGURATION);output.write(buffer,0,count)}
      output.toByteArray()
     } ?: throw BusinessFailure("发机配置文件无法打开",FailureKind.CONFIGURATION)
     WorkDeviceConfig.parse(String(bytes,Charsets.UTF_8))
    }}
    withContext(Dispatchers.IO){WorkDeviceConfig.save(this@ProvisionWorkDeviceActivity,value)}
    if(BuildConfig.DEBUG)BusinessApi.enableLanDiagnostics(this@ProvisionWorkDeviceActivity,false)
    try {BusinessApi.call(this@ProvisionWorkDeviceActivity,"health");Toast.makeText(this@ProvisionWorkDeviceActivity,"工作机配置成功，正式业务服务检查通过",1).show()}
    catch(e:CancellationException){throw e}catch(e:Exception){Toast.makeText(this@ProvisionWorkDeviceActivity,"工作机配置已保存；发机前必须完成服务检查："+businessFeedback(e),1).show()}
   }catch(e:CancellationException){throw e}catch(e:Exception){Toast.makeText(this@ProvisionWorkDeviceActivity,businessFeedback(e),1).show()}
   finally{finish()}
  }
 }
 override fun onCreate(state:Bundle?){super.onCreate(state);if(state==null)picker.launch(arrayOf("application/json","application/octet-stream"))}
 override fun onDestroy(){if(isFinishing)AcquisitionUi.hidden.value=false;super.onDestroy()}
}
