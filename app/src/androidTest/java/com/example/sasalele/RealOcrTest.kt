package com.example.sasalele

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sasalele.data.ChineseImageTextRecognizer
import com.example.sasalele.data.HeaderIdentityRules
import com.example.sasalele.data.IdentitySamples
import com.example.sasalele.data.IdentitySample
import com.example.sasalele.data.Client
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RealOcrTest {
    private suspend fun recognize(title: String, width: Int=1080, height: Int=2400, titleY: Float=230f, region: com.example.sasalele.data.HeaderCrop=com.example.sasalele.data.HeaderCrop()): com.example.sasalele.data.HeaderRecognition {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f }
            drawText(title, 180f, titleY, paint)
            drawText("这段聊天正文不应读取", 100f, 850f, paint)
        }
        val file = File(context.cacheDir, "ocr-test-${System.nanoTime()}.png")
        try {
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return withTimeout(30000) { ChineseImageTextRecognizer(context).readHeader(Uri.fromFile(file),region) }
        } finally { image.recycle(); file.delete() }
    }

    @Test fun realChineseHeaderWithoutBody(): Unit = runBlocking {
        val result = recognize("陈老师")
        assertTrue(result.lines.joinToString().contains("陈老师"))
        assertFalse(result.lines.joinToString().contains("正文"))
        assertFalse(result.rejected)
    }
    @Test fun realGroupHeaderRejected(): Unit = runBlocking {
        assertTrue(recognize("客户交流群(23)").rejected)
    }
    @Test fun landscapeHeaderWithoutBody():Unit=runBlocking { val result=recognize("陈老师",2400,1080,180f); assertTrue(result.lines.joinToString().contains("陈老师")); assertFalse(result.lines.joinToString().contains("正文")) }
    @Test fun adjustableHeaderRegion():Unit=runBlocking { val result=recognize("陈老师",titleY=500f,region=com.example.sasalele.data.HeaderCrop(0.1f,0.25f)); assertTrue(result.lines.joinToString().contains("陈老师")); assertFalse(result.lines.joinToString().contains("正文")) }
    @Test fun accountScopedMatchingAndAmbiguity() {
        val samples = listOf(
            IdentitySample(Client("1", "a", "陈老师", "", ""), listOf("陈老师"), ""),
            IdentitySample(Client("2", "a", "陈老师", "", ""), listOf("陈老师"), ""),
            IdentitySample(Client("3", "b", "陈老师", "", ""), listOf("陈老师"), "")
        )
        assertEquals(2, IdentitySamples.match(samples, "a", listOf("陈老师")).size)
        assertEquals(1, IdentitySamples.match(samples, "b", listOf("陈老师")).size)
        assertTrue(IdentitySamples.match(samples, "a", listOf("未知客户")).isEmpty())
    }
}
