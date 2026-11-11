package com.example.sasalele.data

import android.content.*
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class MaterialImage(val uri:Uri,val mime:String)
object MaterialImages {
 private const val MAX_BYTES=25L*1024*1024
 private val http=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).build()
 suspend fun prepare(context:Context,source:String):MaterialImage=withContext(Dispatchers.IO) {
  val folder=File(context.cacheDir,"material-share").apply{mkdirs()}
  val temp=File(folder,UUID.randomUUID().toString()+".tmp")
  fun copy(input:java.io.InputStream){input.use{stream->temp.outputStream().use{output->
   val buffer=ByteArray(8192);var total=0L
   while(true){val count=stream.read(buffer);if(count<0)break;total+=count
    require(total<=MAX_BYTES){"图片超过25MB，暂不能分享"};output.write(buffer,0,count)}
  }}}
  try {
   when {
    source.startsWith("https://") || source.startsWith("http://")-> {
     http.newCall(Request.Builder().url(source).build()).execute().use{response->
      require(response.isSuccessful){"图片下载失败，请稍后重试"}
      val body=response.body ?: error("图片内容为空")
      require(body.contentLength()<=MAX_BYTES){"图片超过25MB，暂不能分享"}
      copy(body.byteStream())
     }
    }
    source.startsWith("content://")->copy(context.contentResolver.openInputStream(Uri.parse(source)) ?: error("图片无法读取，请重新导入"))
    else->{val file=File(source);require(file.isFile){"图片文件不存在，请重新导入"};copy(file.inputStream())}
   }
   val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
   BitmapFactory.decodeFile(temp.absolutePath,options)
   require(options.outWidth>0 && options.outHeight>0 && options.outMimeType?.startsWith("image/")==true){"该物料不是有效图片，请重新导入"}
   val mime=options.outMimeType!!
   val extension=when(mime){"image/png"->"png";"image/webp"->"webp";"image/gif"->"gif";"image/heif","image/heic"->"heic";else->"jpg"}
   val target=File(folder,temp.nameWithoutExtension+"."+extension)
   check(temp.renameTo(target)){"图片准备失败，请重试"}
   MaterialImage(FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",target),mime)
  } finally {temp.delete()}
 }
 fun shareIntent(context:Context,image:MaterialImage)=Intent(Intent.ACTION_SEND).apply {
  type=image.mime;putExtra(Intent.EXTRA_STREAM,image.uri)
  clipData=ClipData.newUri(context.contentResolver,"销售物料图片",image.uri)
  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
 }
 fun copy(context:Context,image:MaterialImage) {
  // WeChat can read the content URI if its editor supports image clipboard paste.
  runCatching{context.grantUriPermission("com.tencent.mm",image.uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
  (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
   .setPrimaryClip(ClipData.newUri(context.contentResolver,"销售物料图片",image.uri))
 }
}
