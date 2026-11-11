package com.example.sasalele
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sasalele.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class CaptureLifecycleTest {
 @Test fun invalidGrantFailureRestoresState():Unit=runBlocking { withTimeout(10000) {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val before=CaptureSession.state.value
  try {
   context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); delay(500)
   CaptureSession.state.value=CaptureStatus("failure-test",busy=true,hidden=true)
   context.startForegroundService(Intent(context,ScreenCaptureService::class.java).putExtra("requestId","failure-test").putExtra("resultCode",-1).putExtra("projectionData",Intent()))
   while(CaptureSession.state.value.busy) delay(50)
   assertFalse(CaptureSession.state.value.hidden); assertTrue(CaptureSession.state.value.message.contains("失败")); assertNull(CaptureSession.state.value.image)
  } finally { context.stopService(Intent(context,ScreenCaptureService::class.java)); CaptureSession.state.value=before }
 } }
}
