package com.example.sasalele

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.sasalele.data.db.AppDatabase
import com.example.sasalele.data.db.MaterialEntity
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

class PickMaterialImageActivity:ComponentActivity() {
 private val picker=registerForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->
  if(uri==null)finish()
  else lifecycleScope.launch {
   var file:File?=null
   var installed=false
   try {
    withContext(Dispatchers.IO) {
     val dir=File(filesDir,"materials").apply{mkdirs()}
     file=File(dir,"material-${UUID.randomUUID()}.image")
     contentResolver.openInputStream(uri)?.use{input->file!!.outputStream().use{output->input.copyTo(output)}} ?: error("图片无法读取")
     ensureActive()
     withContext(NonCancellable) {
      AppDatabase.getDatabase(this@PickMaterialImageActivity).materialDao().insert(MaterialEntity(title="新导入的图片素材",content=file!!.absolutePath,type="IMAGE"))
      installed=true
     }
    }
    Toast.makeText(this@PickMaterialImageActivity,"已导入本机物料，点击标题可改名",Toast.LENGTH_SHORT).show()
   }catch(e:CancellationException){if(!installed)file?.delete();throw e}
   catch(_:Exception){if(!installed)file?.delete();Toast.makeText(this@PickMaterialImageActivity,if(installed)"图片已保存到本机" else "图片导入失败，请重试",Toast.LENGTH_LONG).show()}
   finally{finish()}
  }
 }
 override fun onCreate(state:Bundle?) {
  super.onCreate(state);AcquisitionUi.hidden.value=true
  if(state==null)try{picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}catch(_:Exception){finish()}
 }
 override fun onDestroy(){if(isFinishing)AcquisitionUi.hidden.value=false;super.onDestroy()}
}
