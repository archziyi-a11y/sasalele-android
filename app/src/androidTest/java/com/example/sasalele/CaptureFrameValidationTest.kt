package com.example.sasalele
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sasalele.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
@RunWith(AndroidJUnit4::class)
class CaptureFrameValidationTest {
 @Test fun actualProjectionFrameHeaderOnly():Unit=runBlocking { withTimeout(30000) {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val file=File(context.cacheDir,"screen-capture").listFiles()?.singleOrNull() ?: error("必须先在模拟私聊捕获一张截图")
  val result=ChineseImageTextRecognizer(context).readHeader(Uri.fromFile(file))
  assertTrue("actual OCR: ${result.lines}; crop=${result.cropTop}-${result.cropBottom}; size=${result.width}x${result.height}",result.lines.any { it.contains("测试客户甲") }); assertFalse(result.lines.any { it.contains("正文") }); assertFalse(result.rejected)
 } }
 @Test fun unreadableImageFailsExplicitly():Unit=runBlocking { withTimeout(10000) { assertTrue(runCatching { ChineseImageTextRecognizer(ApplicationProvider.getApplicationContext()).readHeader(Uri.parse("file:///not-a-real-image")) }.isFailure) } }
}
