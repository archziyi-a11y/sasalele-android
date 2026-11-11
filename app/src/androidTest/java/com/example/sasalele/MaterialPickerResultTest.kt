package com.example.sasalele

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sasalele.data.MaterialImages
import com.example.sasalele.data.MaterialNaming
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real app import path; only the system picker's external result is synthetic. */
class MaterialPickerResultTest {
 @Test fun selectedImageIsImportedAndOverlayRestored()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val repo=MaterialNaming.repository(context)
  val before=repo.allMaterials.first().map{it.id}.toSet()
  val file=File(context.cacheDir,"picker-result-${UUID.randomUUID()}.png")
  val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.GREEN)}
  file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  val prepared=MaterialImages.prepare(context,file.absolutePath)
  val filter=IntentFilter().apply {
   addAction("android.provider.action.PICK_IMAGES")
   addAction("com.google.android.gms.provider.action.PICK_IMAGES")
   addAction("androidx.activity.result.contract.action.PICK_IMAGES")
   addAction(Intent.ACTION_OPEN_DOCUMENT);addAction(Intent.ACTION_GET_CONTENT)
   addDataType("image/*")
  }
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val monitor=instrumentation.addMonitor(filter,Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(prepared.uri)),true)
  try {
   AcquisitionUi.hidden.value=false
   instrumentation.startActivitySync(Intent(context,PickMaterialImageActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   val deadline=android.os.SystemClock.elapsedRealtime()+10000
   var imported=repo.allMaterials.first().filter{it.id !in before}
   while((imported.isEmpty() || AcquisitionUi.hidden.value) && android.os.SystemClock.elapsedRealtime()<deadline) {
    kotlinx.coroutines.delay(50);imported=repo.allMaterials.first().filter{it.id !in before}
   }
   assertTrue("synthetic photo picker result must be consumed",monitor.hits>0)
   assertEquals(1,imported.size)
   val row=imported.single()
   assertEquals("IMAGE",row.type);assertEquals("新导入的图片素材",row.title)
   assertArrayEquals(file.readBytes(),File(row.content).readBytes())
   assertFalse(AcquisitionUi.hidden.value)
  }finally {
   instrumentation.removeMonitor(monitor)
   repo.allMaterials.first().filter{it.id !in before}.forEach{repo.delete(it,File(context.filesDir,"materials"))}
   context.contentResolver.delete(prepared.uri,null,null);file.delete()
  }
 }
}
