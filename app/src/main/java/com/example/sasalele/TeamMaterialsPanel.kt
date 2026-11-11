package com.example.sasalele

import android.content.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.example.sasalele.data.*
import com.example.sasalele.data.db.MaterialEntity
import com.example.sasalele.data.repository.MaterialRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamMaterialsPanel(context:Context,onClose:()->Unit,onImportImage:()->Unit,repositoryOverride:MaterialRepository?=null) {
 val scope=rememberCoroutineScope()
 val repository=remember(context,repositoryOverride){repositoryOverride ?: MaterialNaming.repository(context)}
 val local by repository.allMaterials.collectAsState(initial=emptyList())
 var rows by remember {mutableStateOf(TeamMaterials.cached(context))}
 var busy by remember {mutableStateOf(false)}
 var feedback by remember {mutableStateOf("")}
 var search by remember {mutableStateOf("")};var kind by remember{mutableStateOf(MaterialKind.ALL)}
 var filter by remember{mutableStateOf(false)};var adding by remember{mutableStateOf(false)}
 var title by remember{mutableStateOf("")};var content by remember{mutableStateOf("")}
 var saving by remember{mutableStateOf<TeamMaterial?>(null)}
 var initialSaveTitle by remember{mutableStateOf("")};var saveTitle by remember{mutableStateOf("")}
 var saveAlias by remember{mutableStateOf<MaterialAlias?>(null)}
 var previouslyUploaded by remember{mutableStateOf(false)}
 var legacy by remember{mutableStateOf<List<MaterialEntity>>(emptyList())}
 var selectedLegacy by remember{mutableStateOf<MaterialEntity?>(null)}
 var request by remember{mutableStateOf(java.util.UUID.randomUUID().toString())}
 val focus=androidx.compose.ui.platform.LocalFocusManager.current
 suspend fun refresh() {
  busy=true
  try {
   MaterialNaming.flush(context,repository)
   rows=TeamMaterials.sync(context)
   feedback=if(MaterialNaming.pending(context,repository)>0)"已同步团队物料；部分命名记录待同步，联网后再次刷新即可补传" else "已同步团队物料"
   if(MaterialNaming.rejected(context,repository)>0)feedback+="；部分旧命名记录未被接纳（素材下架或别名失效），本机内容仍保留"
  }catch(e:CancellationException){throw e}
  catch(e:Exception){feedback=businessFeedback(e)+if(rows.isNotEmpty()) "；保留上次列表，已下载图片仍可使用" else "；暂未取得团队列表"}
  finally{busy=false}
 }
 LaunchedEffect(Unit){refresh()}
 LaunchedEffect(saving?.id) {
  selectedLegacy=null;legacy=emptyList()
  val item=saving
  if(item!=null && repository.byTeamSource(MaterialNaming.source(context,item.id))==null)legacy=MaterialNaming.legacyCandidates(item,repository)
 }
 Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   Text("团队",style=MaterialTheme.typography.labelMedium)
   Row {
    IconButton(enabled=!busy,onClick={adding=true;saving=null}){Icon(Icons.Default.TextFields,"新增团队文本或链接")}
    IconButton(enabled=!busy,onClick={onImportImage();AcquisitionUi.open(context,PickMaterialImageActivity::class.java)}){Icon(Icons.Default.Image,"导入图片后上传团队库")}
   }
  }
  Text("公司共享 · 自定义标题保存在本机 · 公共删除请联系管理员",style=MaterialTheme.typography.bodySmall)
  val target=saving
  if(target!=null) {
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Text("保存到本机 / 自定义标题",style=MaterialTheme.typography.titleMedium)
    Text("团队名称：${target.title}")
    Text("修改后只更新本机同一条素材；你的命名可作为团队搜索别名，公共标题保持不变。")
    OutlinedTextField(saveTitle,{saveTitle=it.take(120)},label={Text("本机物料标题")},enabled=!busy,modifier=Modifier.fillMaxWidth())
    if(legacy.isNotEmpty()) {
     Text("发现相同内容的旧本机素材，可选择更新它；不选择则新建本机副本。")
     legacy.forEach{old->FilterChip(selected=selectedLegacy?.id==old.id,onClick={selectedLegacy=if(selectedLegacy?.id==old.id)null else old},label={Text("关联已有：${old.title}")},enabled=!busy)}
    }
    Row {
     Button(enabled=!busy && saveTitle.isNotBlank(),onClick={scope.launch {
      busy=true
      var savedToLocal=false
      try {
       val adopted=saveAlias?.takeIf{MaterialNaming.normalize(saveTitle)==MaterialNaming.normalize(initialSaveTitle)}
       val confirmation=MaterialNaming.saveLocal(context,target,saveTitle,adopted,if(adopted!=null)"adopt" else "explicit",repository,selectedLegacy,
        recordEvidence=!(previouslyUploaded && MaterialNaming.normalize(saveTitle)==MaterialNaming.normalize(initialSaveTitle)))
       savedToLocal=true
       kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate){feedback=confirmation;focus.clearFocus(force=true);saving=null}
       try{MaterialNaming.flush(context,repository);rows=TeamMaterials.sync(context);if(MaterialNaming.pending(context,repository)>0)feedback+="；命名记录待同步"}catch(e:CancellationException){throw e}catch(_:Exception){feedback+="；命名记录待同步"}
      }catch(e:CancellationException){throw e}catch(e:Exception){
       if(BuildConfig.DEBUG)android.util.Log.e("MaterialSave","Local committed=$savedToLocal; follow-up failed",e)
       feedback=if(savedToLocal)"已保存本机；命名记录待同步，请刷新团队库重试" else "保存到本机失败，请检查图片下载或稍后重试"
      }finally{busy=false}
     }}){Text(if(busy)"保存中…" else "保存到本机")}
     TextButton(enabled=!busy,onClick={focus.clearFocus(force=true);saving=null}){Text("取消保存")}
    }
    if(feedback.isNotBlank())Text(feedback)
   }
   return@Column
  }
  if(adding) {
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Text("新增团队文本或链接")
    OutlinedTextField(title,{title=it.take(120);request=java.util.UUID.randomUUID().toString()},label={Text("物料标题")},enabled=!busy,modifier=Modifier.fillMaxWidth())
    OutlinedTextField(content,{content=it.take(16000);request=java.util.UUID.randomUUID().toString()},label={Text("文本内容或完整链接")},enabled=!busy,minLines=3,maxLines=8,modifier=Modifier.fillMaxWidth())
    Text("发布后所有工作机可用；完整网址归为链接。")
    Row {
     Button(enabled=!busy && title.isNotBlank() && content.isNotBlank(),onClick={scope.launch {
      busy=true
      var published=false
      try{
       TeamMaterials.upload(context,MaterialEntity(title=title.trim(),content=content.trim(),type="TEXT"),request);published=true
       kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate){focus.clearFocus(force=true);adding=false;title="";content="";search="";kind=MaterialKind.ALL;request=java.util.UUID.randomUUID().toString()}
       refresh()
      }
      catch(e:CancellationException){throw e}catch(e:Exception){feedback=if(published)"物料已发布，请刷新团队库查看" else businessFeedback(e)}finally{busy=false}
     }}){Text("发布到团队库")}
     TextButton(enabled=!busy,onClick={focus.clearFocus(force=true);adding=false;title="";content="";request=java.util.UUID.randomUUID().toString()}){Text("取消新增")}
    }
    if(feedback.isNotBlank())Text(feedback)
   }
   return@Column
  }
  OutlinedTextField(search,{search=it},placeholder={Text("搜索团队标题或员工别名")},singleLine=true,modifier=Modifier.fillMaxWidth(),trailingIcon={IconButton(onClick={filter=!filter}){Icon(Icons.Default.FilterAlt,"筛选团队物料类型")}})
  if(filter)FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {MaterialKind.entries.forEach{k->FilterChip(selected=kind==k,onClick={kind=k},label={Text(k.label)})}}
  if(kind!=MaterialKind.ALL)Text("当前筛选：${kind.label}")
  Row {TextButton(enabled=!busy,onClick={scope.launch{refresh()}}){Text(if(busy)"同步中…" else "刷新团队库")}
   if(search.isNotEmpty() || kind!=MaterialKind.ALL)TextButton(onClick={search="";kind=MaterialKind.ALL}){Text("清除筛选和搜索")}}
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(feedback.isNotBlank())Text(feedback,style=MaterialTheme.typography.bodySmall)
  val query=MaterialNaming.normalize(search)
  val visible=rows.filter{(it.title.contains(query,true) || it.aliases.any{a->a.title.contains(query,true)}) && (kind==MaterialKind.ALL || it.type==kind.name)}
  if(visible.isEmpty() && !busy)Text(if(rows.isEmpty())"团队库暂无物料，可新增文本或到本机物料选择上传" else "没有符合条件的物料")
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   items(visible,key={it.id}){item->
    val existing=local.firstOrNull{it.teamSourceId==MaterialNaming.source(context,item.id)}
    TeamMaterialCard(context,item,query,existing!=null,onClose,repository){alias->
     saving=item;saveAlias=alias ?: existing?.teamAliasId?.let{id->item.aliases.firstOrNull{it.id==id}}
     saveTitle=alias?.title ?: existing?.title ?: item.title;initialSaveTitle=saveTitle
     previouslyUploaded=alias==null && existing?.titleOrigin=="upload"
    }
   }
  }
 }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamMaterialCard(context:Context,item:TeamMaterial,query:String,hasLocal:Boolean,onClose:()->Unit,repository:MaterialRepository,onSave:(MaterialAlias?)->Unit) {
 val candidates=item.aliases.filter{query.isBlank() || it.title.contains(query,true)}
 var chosen by remember(item.id,query){mutableStateOf(if(query.isNotBlank() && !item.title.contains(query,true))candidates.singleOrNull()?.id else null)}
 val alias=candidates.firstOrNull{it.id==chosen}
 val searchAlias=alias?.takeIf{query.isNotBlank() && it.title.contains(query,true)}
 val scope=rememberCoroutineScope();var favorite by remember(item.id){mutableStateOf(TeamMaterials.favorite(context,item.id))}
 var image by remember(item.id){mutableStateOf<File?>(null)};var busy by remember(item.id){mutableStateOf(false)};var feedback by remember(item.id){mutableStateOf("")}
 LaunchedEffect(item.id) {if(item.type=="IMAGE")try{image=TeamMaterials.imageFile(context,item)}catch(e:CancellationException){throw e}catch(e:Exception){feedback="图片未下载，联网后可重试"}}
 suspend fun noteUse(event:String) {
  try {MaterialNaming.usedAfterAliasSearch(context,item,searchAlias,event,repository)}
  catch(e:CancellationException){throw e}catch(e:Exception){feedback+="；使用记录未同步"}
 }
 Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Row {
   TextButton(enabled=!busy,onClick={onSave(null)},modifier=Modifier.weight(1f)) {
    Text(item.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
    Icon(Icons.Default.Edit,contentDescription=null,modifier=Modifier.size(16.dp))
   }
   IconButton(onClick={favorite=!favorite;TeamMaterials.favorite(context,item.id,favorite)}){Icon(if(favorite)Icons.Default.Favorite else Icons.Default.FavoriteBorder,"收藏团队物料")}}
  if(candidates.isNotEmpty()) {
   Text(if(query.isBlank())"员工别名（点击保存到本机）" else "匹配名称（点击保存到本机）",style=MaterialTheme.typography.bodySmall)
   FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {candidates.forEach{a->AssistChip(onClick={onSave(a)},label={Text(a.title)})}}
  }
  if(item.type=="IMAGE") {if(image!=null)AsyncImage(model=image,contentDescription="团队图片",modifier=Modifier.fillMaxWidth().height(120.dp).clickable(enabled=!busy){scope.launch {
   busy=true
   try {val prepared=MaterialImages.prepare(context,image!!.absolutePath);AcquisitionUi.open(context,MaterialImagePreviewActivity::class.java){it.putExtra("image_uri",prepared.uri.toString()).putExtra("image_title",item.title)}}
   catch(e:CancellationException){throw e}catch(_:Exception){feedback="图片预览失败，请重试"}finally{busy=false}
  }}) else Text("图片未下载")}
  else Text(item.content,maxLines=3,overflow=TextOverflow.Ellipsis)
  if(feedback.isNotBlank())Text(feedback,style=MaterialTheme.typography.bodySmall)
  FlowRow {
   if(!hasLocal)TextButton(enabled=!busy,onClick={onSave(alias)}){Text("保存到本机")}
   if(item.type=="IMAGE")for(copy in listOf(true,false))TextButton(enabled=!busy,onClick={scope.launch {
    busy=true
    try {val file=TeamMaterials.imageFile(context,item);image=file;val prepared=MaterialImages.prepare(context,file.absolutePath)
     if(copy){MaterialImages.copy(context,prepared);feedback="已复制图片";noteUse("copy")}
     else {context.startActivity(Intent.createChooser(MaterialImages.shareIntent(context,prepared),"分享图片").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));noteUse("share");onClose()}
    }catch(e:CancellationException){throw e}catch(e:Exception){feedback=businessFeedback(e)}finally{busy=false}
   }}){Text(if(copy)"复制图片" else "分享图片")}
   else TextButton(onClick={scope.launch{
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("团队物料",item.content));feedback="已复制";noteUse("copy")
   }}){Text(if(item.type=="LINK")"复制链接" else "复制")}
  }
 }}
}
