package com.example.sasalele

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.sasalele.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FollowupsPanel(onSettings:()->Unit) {
 val context=LocalContext.current
 val account by WorkAccountSelection.current.collectAsState()
 val state by FollowupRepository.state.collectAsState()
 val scope=rememberCoroutineScope()
 var listMode by remember(account?.id){mutableStateOf(0)}
 var acting by remember(account?.id){mutableStateOf(false)}
 var syncDetails by remember(account?.id){mutableStateOf(false)}
 val rows=if(state.accountId==account?.id)state.contacts.filter{it.status!="ignored"} else emptyList()
 val ignored=if(state.accountId==account?.id)state.ignoredContacts else emptyList()
 Column(Modifier.fillMaxSize()) {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   Text(account?.name ?: "尚未选择工作号",modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
   TextButton(onClick=onSettings){Text(if(account==null) "选择工作号" else "切换")}
  }
  if(account==null)return@Column
  Card(Modifier.fillMaxWidth().padding(vertical=8.dp)) {Column(Modifier.padding(12.dp)) {
   Text("${rows.count{it.status=="needs_review"}} 位待查看",style=MaterialTheme.typography.headlineSmall)
   Text("近15天活跃 ${rows.size} 位",style=MaterialTheme.typography.bodySmall)
  }}
  if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
  if(state.feedback.isNotBlank() && state.feedback!="已同步") Text(state.feedback,style=MaterialTheme.typography.bodySmall)
  FlowRow {
   listOf("待查看","全部活跃","忽略列表（${ignored.size}）").forEachIndexed { index,label ->
    FilterChip(selected=listMode==index,onClick={listMode=index},label={Text(label)},modifier=Modifier.padding(end=6.dp))
   }
   TextButton(enabled=!state.busy,onClick={scope.launch{FollowupRepository.refresh(context,true)}}){Text(if(state.busy) "同步中" else "刷新")}
  }
  TextButton(onClick={syncDetails=!syncDetails}){Text(if(syncDetails) "收起同步详情" else "同步详情")}
  if(syncDetails && state.observedAt>0)Text("上次同步："+java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA).apply{timeZone=java.util.TimeZone.getTimeZone("Asia/Shanghai")}.format(java.util.Date(state.observedAt))+"（北京时间）",style=MaterialTheme.typography.bodySmall)
  Text("点击联系人卡片可复制昵称／备注",style=MaterialTheme.typography.bodySmall)
  val visible=when(listMode){2->ignored;1->rows;else->rows.filter{it.status=="needs_review"}}
  if(visible.isEmpty())Text(if(listMode==2) "暂无已忽略联系人" else if(state.busy) "正在建立列表…" else if(state.observedAt==0L) "尚未取得列表，请刷新或稍后重试" else if(listMode==1) "近15天暂无活跃联系人" else "暂时没有待查看联系人")
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   items(visible,key={it.friendId}){row->Card(Modifier.fillMaxWidth().clickable(onClickLabel="复制昵称或备注") {
    val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("联系人昵称或备注",row.name))
    Toast.makeText(context,"已复制昵称／备注",Toast.LENGTH_SHORT).show()
   }) {
    Column(Modifier.padding(12.dp)) {
     Text(row.name,style=MaterialTheme.typography.titleSmall)
     Text(when(row.status){"needs_review"->"客户最后发来消息 · 待查看";"replied"->"销售已回复";"done"->"已处理";"later"->"稍后查看（1小时）";"ignored"->"已忽略此联系人，新消息不再提醒";else->"消息状态待核对"})
     Text(java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA).apply{timeZone=java.util.TimeZone.getTimeZone("Asia/Shanghai")}.format(java.util.Date(row.time)))
     if(listMode==2)TextButton(enabled=!acting,onClick={scope.launch{acting=true;try{FollowupRepository.act(context,row,"restore")}finally{acting=false}}}){Text("移回提醒列表")}
     else {
      var more by remember(row.friendId){mutableStateOf(false)}
      Row {
      (if(row.status=="needs_review") listOf("已处理" to "done","稍后" to "later") else emptyList()).forEach{(label,action)->
       TextButton(enabled=!acting,onClick={scope.launch{acting=true;try{FollowupRepository.act(context,row,action)}finally{acting=false}}}){Text(label)}
      }
      TextButton(onClick={more=!more}){Text(if(more) "收起" else "更多")}
      }
      if(more) TextButton(enabled=!acting,onClick={scope.launch{acting=true;try{FollowupRepository.act(context,row,"ignored")}finally{acting=false}}}){Text("忽略此联系人")}
     }
    }
   }}
  }
 }
}
