package com.example.sasalele

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sasalele.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun WorkAccountBindingPanel(context:Context) {
 WorkAccountSelection.initialize(context)
 val current by WorkAccountSelection.current.collectAsState()
 val scope=rememberCoroutineScope()
 var rows by remember {mutableStateOf<JSONArray?>(null)}
 var feedback by remember {mutableStateOf("")}
 var busy by remember {mutableStateOf(false)}
 var chosen by remember {mutableStateOf<SelectedWorkAccount?>(null)}
 fun fetch(force:Boolean) {scope.launch {
  busy=true
  val cached=WorkAccountDirectoryCache.load(context)
  if(!force && cached!=null) {
   rows=cached.rows;chosen=null
   if(cached.fresh) {feedback="已显示缓存的工作号";busy=false;return@launch}
  }
  feedback=if(rows==null) "正在获取工作号…" else "正在刷新工作号…"
  try {
   val result=BusinessApi.call(context,if(force) "accounts?refresh=true" else "accounts").getJSONArray("accounts")
   WorkAccountDirectoryCache.save(context,result)
   rows=result;chosen=null
   feedback=if(result.length()==0) "暂无可选工作号，请联系管理员" else "请选择工作号，再点击下方确认按钮"
  } catch(e:CancellationException){throw e}
    catch(e:Exception){feedback=businessFeedback(e)+(if(rows!=null) "；仍可选择已缓存的工作号" else "")}
    finally {busy=false}
 }}
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("当前工作号",style=MaterialTheme.typography.titleMedium)
  Text(current?.name?.ifBlank {"工作号尾号 "+current?.id?.takeLast(6)} ?: "尚未选择工作号")
  Text("选择后，客户查找、画像、销售指导和提醒共用此工作号。")
  Button(enabled=!busy,onClick={fetch(false)}){Text(if(current==null) "选择工作号" else "切换工作号")}
  Text(feedback)
  chosen?.let {account->
   Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Text("使用此工作号？",style=MaterialTheme.typography.titleMedium)
     Text(account.name+"\n切换后需重新确认客户，原工作号的数据不会被删除。")
     Row {
      TextButton(enabled=!busy,onClick={scope.launch {
       busy=true
       try {WorkAccountSelection.select(context,account);rows=null;chosen=null;feedback="已选择工作号"}
       catch(e:Exception){if(e is CancellationException) throw e;feedback="保存未完成，请重试"}finally{busy=false}
      }}){Text("使用此工作号")}
      TextButton(enabled=!busy,onClick={chosen=null}){Text("取消")}
     }
    }
   }
  }
  if(chosen==null) rows?.let {list->
   Column(Modifier.fillMaxWidth().heightIn(max=240.dp).verticalScroll(rememberScrollState())) {
   for(i in 0 until list.length()) {
    val row=list.getJSONObject(i)
    val account=SelectedWorkAccount(row.getString("account_id"),row.optString("display_name").ifBlank {"工作号尾号 "+row.getString("account_id").takeLast(6)})
    TextButton(enabled=!busy,onClick={chosen=account}){Text(account.name+" · 尾号 "+account.id.takeLast(6))}
   }
   }
   TextButton(enabled=!busy,onClick={fetch(true)}){Text("刷新工作号列表")}
   TextButton(onClick={rows=null}){Text("收起工作号列表")}
  }
 }

}

@Composable
fun WorkAccountScope(context:Context,onSettings:()->Unit,content:@Composable ()->Unit) {
 WorkAccountSelection.initialize(context)
 val current by WorkAccountSelection.current.collectAsState()
 if(current==null) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text("请先选择这台手机使用的工作号")
  Button(onClick=onSettings){Text("前往助手设置")}
 } else key(current!!.id) {content()}
}
