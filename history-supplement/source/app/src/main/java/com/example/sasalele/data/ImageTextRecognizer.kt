package com.example.sasalele.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class HeaderCrop(val top: Float = 0f, val bottom: Float? = null)
data class HeaderRecognition(val lines: List<String>, val elapsedMs: Long, val rejected: Boolean,
    val width: Int = 0, val height: Int = 0, val cropTop: Int = 0, val cropBottom: Int = 0)

interface ImageTextRecognizer {
    suspend fun readHeader(uri: Uri, region: HeaderCrop = HeaderCrop()): HeaderRecognition
}

/** OCR only the top region: private bodies are neither recognized nor returned. */
class ChineseImageTextRecognizer(private val context: Context) : ImageTextRecognizer {
    override suspend fun readHeader(uri: Uri, region: HeaderCrop): HeaderRecognition = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val ratio = minOf(1.0, 2560.0 / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1), (info.size.height * ratio).toInt().coerceAtLeast(1))
            }
        } else {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            val options = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 2560).coerceAtLeast(1)
            }
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
                ?: error("图片无法打开")
        }
        val top = (bitmap.height * region.top.coerceIn(0f,0.5f)).toInt()
        val bottom = (region.bottom?.let { bitmap.height * it.coerceIn(0.01f,0.6f) }
            ?: minOf(bitmap.width * 0.27f, bitmap.height * 0.30f)).toInt().coerceIn(top+1,bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bottom-top)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                recognizer.process(InputImage.fromBitmap(crop, 0))
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            }
            val lines = result.textBlocks.flatMap { it.lines }.map { it.text.trim() }.filter { it.isNotBlank() }
            HeaderRecognition(lines, SystemClock.elapsedRealtime() - start, HeaderIdentityRules.isExcluded(lines),bitmap.width,bitmap.height,top,bottom)
        } finally {
            recognizer.close()
            if (crop !== bitmap) crop.recycle()
            bitmap.recycle()
        }
    }
}

object HeaderIdentityRules {
    fun normalize(value: String) = value.replace(Regex("[\\s·•]"), "").lowercase()
    fun isExcluded(lines: List<String>): Boolean = lines.any {
        Regex("[（(]\\s*\\d+\\s*[）)]").containsMatchIn(it) ||
            normalize(it) in setOf("公众号", "服务号", "订阅号", "通讯录", "聊天列表", "模拟聊天列表", "officialaccounts", "serviceaccounts") ||
            Regex("^(微信|Weixin|WeChat)\\s*([（(]\\d+[）)])?$", RegexOption.IGNORE_CASE).matches(it.trim())
    }
    fun matches(lines: List<String>, aliases: List<String>) = lines.any { line ->
        aliases.any { it.isNotBlank() && normalize(line) == normalize(it) }
    }
}
