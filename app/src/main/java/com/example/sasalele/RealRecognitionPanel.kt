package com.example.sasalele

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.sasalele.data.*

@Composable
fun RealRecognitionPanel(context:Context,uri:Uri?,header:HeaderRecognition?,error:String?,onClose:()->Unit,
    onRead:(HeaderCrop)->Unit = {}) {
    val revision by IdentitySamples.revision.collectAsState()
    val loaded=remember(revision) { runCatching { IdentitySamples.load(context) } }
    val samples=loaded.getOrDefault(emptyList())
    val accounts=samples.map { it.client.workAccountId }.distinct()
    var account by remember(uri) { mutableStateOf<String?>(null) }
    var confirmedPrivate by remember(uri) { mutableStateOf(false) }
    var name by remember(uri) { mutableStateOf("") }
    var cropTop by remember(uri,header) { mutableFloatStateOf(if(header==null || header.height==0) 0f else header.cropTop.toFloat()/header.height) }
    var cropBottom by remember(uri,header) { mutableFloatStateOf(if(header==null || header.height==0) 0.12f else header.cropBottom.toFloat()/header.height) }
    val importMessage by ProfileImportFeedback.message.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row { Text("画像搜索",Modifier.weight(1f)); TextButton(onClick=onClose) { Text("关闭") } }
        Text("本地全量搜索逻辑已启用；当前${samples.size}条身份，不代表全部业务画像已接入")
        if(loaded.isFailure) Text("画像读取失败，请检查或导入有效JSON：${loaded.exceptionOrNull()?.message}")
        Row {
            TextButton(onClick={ AcquisitionUi.open(context,PickRecognitionImageActivity::class.java) }) { Text("选择已有图片") }
            TextButton(onClick={ AcquisitionUi.open(context,ImportProfilesActivity::class.java) }) { Text("导入画像JSON") }
        }
        Text("仅导入你已授权使用的画像；文件只保存于应用私有目录")
        if(importMessage.isNotBlank()) Text(importMessage)
        if(uri!=null) AsyncImage(model=uri,contentDescription="待识别图片预览",modifier=Modifier.fillMaxWidth().height(100.dp))
        if(error!=null) Text(error)
        if(uri!=null) {
            Text("顶栏裁剪：起点${(cropTop*100).toInt()}%－终点${(cropBottom*100).toInt()}%。请只覆盖状态栏和聊天标题，不含正文")
            Slider(value=cropTop,onValueChange={ cropTop=it.coerceAtMost(cropBottom-0.01f) },valueRange=0f..0.4f)
            Slider(value=cropBottom,onValueChange={ cropBottom=it.coerceAtLeast(cropTop+0.01f) },valueRange=0.02f..0.6f)
            TextButton(onClick={ confirmedPrivate=false; onRead(HeaderCrop(cropTop,cropBottom)) }) { Text("按修正区域重新识别") }
        }
        if(uri!=null && header==null && error==null) Text("正在识别顶栏…")
        if(header!=null) {
            Text("读到的顶栏（${header.elapsedMs}毫秒）：${header.lines.joinToString(" / ").ifBlank { "未读到文字" }}")
            Text("解码尺寸${header.width}×${header.height}；裁剪纵坐标${header.cropTop}－${header.cropBottom}。方向、状态栏与顶栏需人工核对")
            if(header.rejected) { Text("检测到群聊、公众号或列表线索，已排除；不匹配画像、不读取正文"); return@Column }
            Text("页面类型：不确定。OCR不能可靠证明这是私聊，请核对预览")
        }
        if(uri!=null) Row { Checkbox(confirmedPrivate,{confirmedPrivate=it}); Text("我确认这是客户一对一私聊") }
        OutlinedTextField(value=name,onValueChange={name=it},label={Text(if(uri==null) "输入姓名／别名搜索" else "修正顶栏姓名／备注")},modifier=Modifier.fillMaxWidth())
        Text("工作号筛选（可选）：")
        TextButton(onClick={account=null}) { Text((if(account==null) "✓ " else "")+"全部已授权身份") }
        accounts.forEach { id -> TextButton(onClick={account=id}) { Text((if(account==id) "✓ " else "")+"工作号：$id") } }
        if(uri!=null && (!confirmedPrivate || header==null)) { Text("核对私聊后才能搜索候选"); return@Column }
        val lines=if(name.isNotBlank()) listOf(name) else header?.lines.orEmpty()
        if(lines.isEmpty()) { Text("输入姓名或先识别图片；不会自动绑定"); return@Column }
        val candidates=IdentitySamples.match(samples,account,lines)
        Text("候选${candidates.size}条；同名身份分别展示，必须人工确认")
        if(candidates.isEmpty()) Text("无匹配。可修正姓名、取消筛选或导入已授权画像")
        candidates.forEach { sample ->
            HorizontalDivider()
            if(sample.client.avatarUrl!=null) AsyncImage(model=sample.client.avatarUrl,contentDescription="客户头像",modifier=Modifier.size(48.dp)) else Text("头像未提供")
            Text(sample.client.name)
            Text("工作号：${sample.client.workAccountId} · 客户ID：${sample.client.id}")
            Text(sample.summary); Text("来源：${sample.source}")
            Button(onClick={
                val latest=runCatching { IdentitySamples.byKey(context,sample.client.key) }.getOrNull()
                if(latest==null) Toast.makeText(context,"对应双标识资料已不存在，请重新搜索",0).show()
                else { ClientRepository.bindClient(latest.client); Toast.makeText(context,"已绑定 ${latest.client.name}（工作号 ${latest.client.workAccountId}）",1).show(); onClose() }
            }) { Text("确认绑定此客户") }
        }
    }
}
