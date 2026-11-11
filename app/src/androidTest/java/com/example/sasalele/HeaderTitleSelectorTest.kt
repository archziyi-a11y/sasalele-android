package com.example.sasalele

import android.graphics.*
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.sasalele.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HeaderTitleSelectorTest {
    @Test fun excludesNavigationAndStatusAcrossSizes() {
        for(width in listOf(720,1080,2400)) {
            val boxes=listOf(HeaderTextBox("〈4",10,100,80,135),
                HeaderTextBox("合成妈妈清美班27届山东",width*26/100,100,width*74/100,135),
                HeaderTextBox("14:28",20,10,100,40))
            assertEquals("合成妈妈清美班27届山东",HeaderTitleSelector.select(boxes,width,50))
        }
    }
    @Test fun ambiguousOrNavigationOnlyNeedsCorrection() {
        val title=HeaderTextBox("合成客户",200,100,500,140)
        assertEquals("",HeaderTitleSelector.select(listOf(title,title.copy(top=150,bottom=190)),720,50))
        assertEquals("",HeaderTitleSelector.select(listOf(HeaderTextBox("〈4",10,100,70,140)),720,50))
        assertTrue(HeaderIdentityRules.isExcluded(listOf("客户群(23)")))
        assertTrue(HeaderIdentityRules.isExcluded(listOf("微信(13)")))
    }
    @Test fun realChineseOcrChoosesTitleInsteadOfUnreadCounter()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap=Bitmap.createBitmap(720,1604,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK;textSize=28f}
        canvas.drawText("14:28",20f,40f,paint)
        canvas.drawText("〈4",24f,132f,paint)
        val title="合成妈妈清美班27届山东"
        canvas.drawText(title,(720-paint.measureText(title))/2,132f,paint)
        canvas.drawText("正文不应读取",80f,500f,paint)
        val file=File(context.cacheDir,"header-selector-test.png")
        try {
            file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            val result=ChineseImageTextRecognizer(context).readHeader(Uri.fromFile(file))
            assertEquals(title,result.title)
            assertFalse(result.lines.any {it.contains("正文")})
        } finally {bitmap.recycle();file.delete()}
    }
}
