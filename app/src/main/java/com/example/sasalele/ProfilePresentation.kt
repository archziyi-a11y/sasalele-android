package com.example.sasalele

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal fun JSONObject.visibleText(key:String)=optString(key).takeIf { it.isNotBlank() && it!="null" } ?: "未提供"
private val profileLabels=mapOf("learner_identity" to "学员身份","learning_stage" to "学习阶段","subject_direction" to "学习方向","learning_goal" to "学习目标","foundation_level" to "当前基础","class_preference" to "班型偏好","enrollment_status" to "报名状态","selected_class" to "已选班型","intent_level" to "意向判断","emotion_risk" to "情绪风险")

@Composable
internal fun ProfilePresentation(profile:JSONObject) {
 Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
 Text("客户概况",style=MaterialTheme.typography.titleMedium)
 Text(profile.visibleText("summary").let {if(it=="未提供") "暂无画像，可根据近期聊天生成。" else it})
 } }
 if(profile.optBoolean("stale")) Text("近期情况可能有变化，可更新画像。",style=MaterialTheme.typography.bodySmall)
 val facts=profile.optJSONObject("facts")
 if(facts!=null && profile.optBoolean("structured_fields_available")) {
  Text("关键背景",style=MaterialTheme.typography.titleMedium)
  profileLabels.keys.take(8).forEach { field ->
   val item=facts.optJSONObject(field)
   if(item!=null && item.visibleText("value") !in listOf("未提供","未知","不明确")) Text("${profileLabels[field]}：${item.visibleText("value")}")
  }
  val inferences=profile.optJSONArray("inferences")
  if(inferences!=null && inferences.length()>0) {
   Text("跟进判断",style=MaterialTheme.typography.titleMedium)
   for(i in 0 until inferences.length()) {
    val item=inferences.optJSONObject(i) ?: continue
    Text("${profileLabels[item.optString("field")] ?: "判断"}：${item.visibleText("value")}\n依据：${item.visibleText("reason")}")
   }
  }
 }
 val coverage=profile.optJSONObject("coverage")
 if(coverage!=null && coverage.optInt("included_messages")<10) Text("聊天记录较少，画像供参考。",style=MaterialTheme.typography.bodySmall)
 var details by remember(profile){mutableStateOf(false)}
 TextButton(onClick={details=!details}){Text(if(details) "收起画像详情" else "画像来源与时间")}
 if(details) {
  val unknowns=profile.optJSONArray("unknowns")
  if(unknowns!=null && unknowns.length()>0) Text("待补充信息："+(0 until unknowns.length()).joinToString("、") {profileLabels[unknowns.optString(it)] ?: "其他信息"})
  if(coverage!=null) Text("记录范围（服务端时间，含原时区）：${coverage.visibleText("window_start")} 至 ${coverage.visibleText("window_end")}\n纳入 ${coverage.optInt("included_messages")} 条，省略 ${coverage.optInt("omitted_messages")} 条。",style=MaterialTheme.typography.bodySmall)
  Text("来源："+when(profile.optString("source")){"fangzhou_chat_generated"->"方舟聊天整理";"feishu"->"历史资料";else->"业务服务提供的历史资料"})
  Text("生成时间（服务端）：${profile.visibleText("summary_generated_at")}\n写入时间（服务端）：${profile.visibleText("job_written_at")}")
 }
}
