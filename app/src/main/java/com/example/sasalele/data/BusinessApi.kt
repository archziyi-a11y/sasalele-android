package com.example.sasalele.data

import android.content.Context
import com.example.sasalele.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlin.coroutines.resumeWithException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

enum class FailureKind { CONFIGURATION, OFFLINE, DNS, TIMEOUT, TLS, UNREACHABLE, AUTHORIZATION, UPSTREAM, BUSY, DATA }
class BusinessFailure(message:String,val kind:FailureKind=FailureKind.DATA,val requestId:String="",val diagnosticCode:String=""):Exception(message)
fun businessFeedback(error:Exception):String = if(error is BusinessFailure) error.message.orEmpty()+(if(error.requestId.isNotBlank()) "（查询编号：${error.requestId}）" else "") else "业务数据格式或配置异常，请检查后重试"
data class BusinessConnection(val url:String,val accessCode:String)
object BusinessApi {
    fun lanDiagnosticsEnabled(context:Context)=BuildConfig.DEBUG && context.getSharedPreferences("sales-diagnostics",0).getBoolean("lan",false)
    fun enableLanDiagnostics(context:Context,enabled:Boolean) { check(BuildConfig.DEBUG);context.getSharedPreferences("sales-diagnostics",0).edit().putBoolean("lan",enabled).apply() }
    fun isConfigured(context:Context):Boolean=runCatching { if(lanDiagnosticsEnabled(context)) {val c=load(context);validUrl(c.url) && c.accessCode.isNotBlank()} else WorkDeviceConfig.load(context)!=null }.getOrDefault(false)

    private val http=OkHttpClient.Builder().connectTimeout(4,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    fun validUrl(url:String):Boolean = runCatching {
        val parsed=URI(url); val parts=parsed.host?.split('.')?.map { it.toInt() } ?: return false
        parts.size==4 && parts.all { it in 0..255 } && parsed.scheme in listOf("http","https") && parsed.userInfo==null && parsed.query==null && parsed.fragment==null &&
            (parts[0]==10 || (parts[0]==172 && parts[1] in 16..31) || (parts[0]==192 && parts[1]==168)) && parsed.path.orEmpty().trim('/').isEmpty()
    }.getOrDefault(false)
    fun load(context:Context):BusinessConnection {
        val file=File(context.noBackupFilesDir,"lan-connection.json")
        if(!file.exists()) return BusinessConnection("","")
        return runCatching { val j=JSONObject(file.readText()); BusinessConnection(j.getString("url"),j.getString("accessCode")) }.getOrDefault(BusinessConnection("",""))
    }
    fun save(context:Context,connection:BusinessConnection) {
        check(BuildConfig.DEBUG) {"仅调试诊断允许局域网配置"}
        if(!validUrl(connection.url)) throw BusinessFailure("必须填写局域网IPv4地址，不能填写PC的localhost或公网地址")
        if(!connection.accessCode.matches(Regex("[A-Za-z0-9_-]{20,128}"))) throw BusinessFailure("必须填写PC的短期访问码code字段，不能填写方舟Cookie或飞书凭据")
        enableLanDiagnostics(context,true)
        File(context.noBackupFilesDir,"lan-connection.json").writeText(JSONObject().put("url",connection.url.trimEnd('/')).put("accessCode",connection.accessCode).toString())
    }
    internal fun connection(context:Context):BusinessConnection = if(lanDiagnosticsEnabled(context)) {
            load(context).also { if(!validUrl(it.url) || !it.accessCode.matches(Regex("[A-Za-z0-9_-]{20,128}"))) throw BusinessFailure("诊断连接尚未配置",FailureKind.CONFIGURATION) }
        } else {
            val device=WorkDeviceConfig.load(context) ?: throw BusinessFailure(if(BuildConfig.SALES_SERVICE_URL.isBlank()) "项目正式服务尚未配置，当前构建不能用于上线验收" else "此工作机尚未完成发机配置，请联系管理员",FailureKind.CONFIGURATION)
            BusinessConnection(device.serviceUrl,device.token)
        }
    suspend fun call(context:Context,path:String,body:JSONObject?=null):JSONObject=withContext(Dispatchers.IO) {
        val connection=connection(context)
        val selected=WorkAccountSelection.current.value
        val operation=path.substringBefore('?').substringBefore('/')
        if(operation !in listOf("health","accounts","materials")) {
            if(selected==null && !BuildConfig.DEBUG) throw BusinessFailure("请先在助手设置中选择工作号",FailureKind.CONFIGURATION)
            if(selected!=null) {
                if(operation=="search") body?.put("account_id",selected.id)
                val requested=body?.optString("account_id") ?: android.net.Uri.parse("https://local/"+path).getQueryParameter("account_id")
                if(requested!=selected.id) throw BusinessFailure("客户不属于当前工作号，请重新确认客户",FailureKind.DATA)
            }
        }
        val builder=Request.Builder().url(connection.url.trimEnd('/')+"/sales/"+path).header("Authorization","Bearer "+connection.accessCode)
        if(body!=null) builder.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        try {
            val request=builder.build();var retries=0
            while(true) {
                try {
                    val response=queryJson(request)
                    if(selected!=null && selected!=WorkAccountSelection.current.value && operation !in listOf("health","accounts","materials")) throw BusinessFailure("工作号已切换，请重新操作",FailureKind.DATA)
                    return@withContext response
                }
                catch(e:java.io.IOException) {
                    val transient=e !is javax.net.ssl.SSLException && e !is java.net.UnknownHostException && e !is java.io.InterruptedIOException
                    val safeRetry=path in setOf("health","search","detail","context","profile/ensure") || path.startsWith("jobs/") || (path=="draft" && !body?.optString("request_id").isNullOrBlank())
                    if(safeRetry && transient && retries++==0) delay(250) else throw e
                }
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        } catch(e:javax.net.ssl.SSLException) { throw BusinessFailure("服务安全连接失败，请联系管理员核对证书；不能绕过验证",FailureKind.TLS) }
        catch(e:java.net.UnknownHostException) { throw BusinessFailure("无法解析业务服务地址，请检查手机网络或稍后重试",FailureKind.DNS) }
        catch(e:java.net.SocketTimeoutException) { throw BusinessFailure("业务请求超时，请稍后重试",FailureKind.TIMEOUT) }
        catch(e:java.io.InterruptedIOException) {throw BusinessFailure("业务请求超时，请稍后重试",FailureKind.TIMEOUT)}
        catch(e:java.io.IOException) {
            val manager=context.getSystemService(android.net.ConnectivityManager::class.java)
            val offline=manager.activeNetwork==null
            throw BusinessFailure(if(offline) "手机当前没有可用网络，请连接WiFi或移动网络后重试" else "网络连接失败，暂时无法访问业务服务，请稍后重试",if(offline) FailureKind.OFFLINE else FailureKind.UNREACHABLE,diagnosticCode=e.javaClass.simpleName+":"+e.stackTrace.firstOrNull()?.let { it.className+"."+it.methodName }+":"+when {
                e.message.orEmpty().contains("unexpected end",true)->"unexpected_end"
                e.message.orEmpty().contains("status line",true)->"invalid_status_line"
                e.message.orEmpty().contains("bytes",true)->"byte_count_mismatch"
                else->"other_protocol_error"
            })
        }
        catch(e:org.json.JSONException) { throw BusinessFailure("业务服务返回格式异常，请重试",FailureKind.DATA) }
    }
    private suspend fun queryJson(request:Request):JSONObject {
            val call=http.newCall(request)
            val received=suspendCancellableCoroutine<Response> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object:Callback {
                    override fun onFailure(call:Call,error:java.io.IOException){if(continuation.isActive)continuation.resumeWithException(error)}
                    override fun onResponse(call:Call,response:Response){continuation.resume(response,onCancellation={_,value,_->value.close()})}
                })
            }
            return received.use { response ->
                val result=JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if(!response.isSuccessful) {
                    val code=result.optString("detail")
                    val requestId=response.header("X-Request-ID").orEmpty().take(64)
                    val kind=if(code=="service_busy_retry") FailureKind.BUSY else when(response.code){401,403->FailureKind.AUTHORIZATION;429->FailureKind.BUSY;502,503,504->FailureKind.UPSTREAM;else->FailureKind.DATA}
                    val message=when(code) {
                        "invalid_material" -> "请填写标题（最多120字）和内容（最多16000字）"
                        "material_public_rename_disabled" -> "团队标题暂不开放修改，请升级APP后保存到本机并自定义标题"
                        "material_alias_stale" -> "该别名已变化，请刷新团队库后重试"
                        "account_scope_unavailable" -> "请在设置中刷新工作号列表，再刷新团队物料；本机内容不受影响"
                        "material_alias_limit" -> "该素材候选标题已达上限，请联系管理员"
                        "material_image_too_large" -> "图片超过25MB，无法上传"
                        "material_image_invalid" -> "请使用有效的PNG、JPEG或WebP图片"
                        "material_library_full" -> "团队物料空间或数量已满，请联系管理员"
                        "material_request_conflict" -> "上传内容与上次请求不一致，请重新操作"
                        "model_not_configured" -> "业务服务尚未配置模型，请联系管理员"
                        "polish_text_required" -> "请先填写待润色原文"
                        "chat_insufficient" -> "该客户本次范围内没有足够文本聊天"
                        "generation_queue_full" -> "生成队列已满，请稍后重试"
                        "generation_job_expired","generation_job_not_found" -> "生成任务已失效，请重新提交"
                        else -> when(response.code) {
                        401 -> if(code=="device_access_expired") "工作机授权已过期，请联系管理员重新配置" else "工作机授权无效，请联系管理员；无需销售人员登录"
                        403 -> if(code=="device_access_revoked") "此工作机授权已撤销，请联系管理员" else "此工作号不在服务授权范围"
                        409 -> if(code=="followup_changed_refresh") "联系人有新消息，请刷新列表后再处理" else if(code=="human_confirmation_required") "请先人工确认业务身份" else "响应双标识冲突，未绑定"
                        410 -> "客户已删除或不再存在，请重新搜索"
                        429 -> "查询过于频繁，请稍后重试"
                        502,503,504 -> if(code=="service_busy_retry") "业务服务正忙，请稍后重试" else "业务服务或上游暂不可用，请稍后重试"
                        in 300..399 -> "服务地址发生跳转，已停止请求；请联系管理员核对部署"
                        else -> "业务服务返回异常，请重试"
                    }
                    }
                    throw BusinessFailure(message,kind,requestId,code)
                }
                result
            }
    }
    fun target(key:ClientKey,refresh:Boolean=false)=JSONObject().put("account_id",key.workAccountId).put("friend_id",key.clientId).put("refresh",refresh)
    fun client(identity:JSONObject):Client {
        require(identity.optBoolean("dual_key_verified")) { "服务未验证双标识" }
        return Client(identity.getString("friend_id"),identity.getString("account_id"),identity.optString("name","名称未提供"),"公司未提供","意向未提供",identity.optString("avatar").takeIf { it.isNotBlank() && it!="null" })
    }
}
object BusinessUi { val detailOpened=kotlinx.coroutines.flow.MutableStateFlow(false) }
object HeaderNameRules {
 fun candidate(lines:List<String>):String = lines.map { it.trim().removePrefix("返回").trim() }
  .firstOrNull { value -> value.isNotBlank() && !Regex(".*\\d{1,2}:\\d{2}.*").matches(value) &&
   !Regex("^[0-9\\s:|丨<>〈〉‹›《》←→❮❯()（）]+$").matches(value) && !value.startsWith("5G") && !value.startsWith("4G") && value !in setOf("返回","关闭","更多","未读") } ?: ""
}
class RequestEpoch { private var epoch=0; fun next():Int=++epoch; fun accepts(value:Int)=value==epoch }
