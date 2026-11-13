package com.example.sasalele
import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
object AcquisitionUi {
 val hidden=MutableStateFlow(false)
 fun open(context:Context,type:Class<out Activity>) {
  if(hidden.value) return
  hidden.value=true
  try { context.startActivity(Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  catch(e:Exception) { hidden.value=false; android.widget.Toast.makeText(context,"无法打开选择器，请重试",0).show() }
 }
}
