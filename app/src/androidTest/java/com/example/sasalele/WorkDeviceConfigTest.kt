package com.example.sasalele
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sasalele.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
@RunWith(AndroidJUnit4::class)
class WorkDeviceConfigTest {
 private val context=ApplicationProvider.getApplicationContext<Context>()
 private val id="00000000-0000-0000-0000-000000000001"
 private fun fixture()=JSONObject().put("schema_version",1).put("service_url","https://sales.example.org").put("device_id",id).put("device_token","sdv_${id}_"+"A".repeat(43)).put("expires_epoch",System.currentTimeMillis()/1000+86400)
 @Test fun keyStoreEncryptedRoundTripAndClear() {
  val folder=File(context.cacheDir,"work-provision-test-${System.nanoTime()}").apply {mkdirs()}
  val isolated=object:ContextWrapper(context){override fun getNoBackupFilesDir()=folder}
  try {val value=WorkDeviceConfig.parse(fixture().toString());val revision=WorkDeviceConfig.updates.value;WorkDeviceConfig.save(isolated,value);assertTrue(WorkDeviceConfig.updates.value>revision);assertEquals(value,WorkDeviceConfig.load(isolated));assertFalse(File(folder,"work-device.enc").readText().contains(value.token));WorkDeviceConfig.clear(isolated);assertNull(WorkDeviceConfig.load(isolated))}finally{folder.deleteRecursively()}
 }
 @Test fun expiredAndMalformedProvisioningRejected() {
  assertTrue(runCatching{WorkDeviceConfig.parse(fixture().put("expires_epoch",1).toString())}.exceptionOrNull() is BusinessFailure)
  assertTrue(runCatching{WorkDeviceConfig.parse(fixture().put("device_token","shared-permanent-key").toString())}.exceptionOrNull() is BusinessFailure)
  assertTrue(runCatching{WorkDeviceConfig.parse(fixture().put("service_url","http://sales.example.org").toString())}.exceptionOrNull() is BusinessFailure)
 }
 @Test fun corruptedStorageHasExplicitFeedback() {
  val folder=File(context.cacheDir,"work-corrupt-test-${System.nanoTime()}").apply{mkdirs()};val isolated=object:ContextWrapper(context){override fun getNoBackupFilesDir()=folder}
  try {File(folder,"work-device.enc").writeText("corrupt");val error=runCatching{WorkDeviceConfig.load(isolated)}.exceptionOrNull();assertTrue(error is BusinessFailure);assertTrue(error!!.message.orEmpty().contains("管理员"))}finally{folder.deleteRecursively()}
 }
}
