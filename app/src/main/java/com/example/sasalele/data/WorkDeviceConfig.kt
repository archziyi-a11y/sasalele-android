package com.example.sasalele.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.example.sasalele.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class WorkDeviceConnection(val serviceUrl:String,val deviceId:String,val token:String,val expiresEpoch:Long)
object WorkDeviceConfig {
 val updates=kotlinx.coroutines.flow.MutableStateFlow(0)
 private const val ALIAS="sasalele-work-device-v1"
 fun validServiceUrl(value:String):Boolean=runCatching {
  val uri=URI(value);val host=uri.host ?: return false
  uri.scheme=="https" && host.contains('.') && !host.matches(Regex("[0-9.]+")) && !host.endsWith(".localhost") && !host.endsWith(".local") && uri.port in listOf(-1,443) && uri.userInfo==null && uri.query==null && uri.fragment==null && uri.path.orEmpty() in listOf("","/")
 }.getOrDefault(false)
 fun parse(text:String):WorkDeviceConnection {
  if(text.toByteArray().size>8192) throw BusinessFailure("发机配置文件过大",FailureKind.CONFIGURATION)
  val j=JSONObject(text);val value=WorkDeviceConnection(j.getString("service_url").trimEnd('/'),j.getString("device_id"),j.getString("device_token"),j.getLong("expires_epoch"))
  if(j.optInt("schema_version")!=1 || !validServiceUrl(value.serviceUrl) || !value.deviceId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) || !value.token.matches(Regex("sdv_${Regex.escape(value.deviceId)}_[A-Za-z0-9_-]{43}"))) throw BusinessFailure("发机配置格式错误，请由管理员重新提供",FailureKind.CONFIGURATION)
  if(BuildConfig.SALES_SERVICE_URL.isNotBlank() && value.serviceUrl!=BuildConfig.SALES_SERVICE_URL.trimEnd('/')) throw BusinessFailure("发机配置与本APP正式服务不一致",FailureKind.CONFIGURATION)
  if(value.expiresEpoch<=System.currentTimeMillis()/1000) throw BusinessFailure("工作机授权已过期，请联系管理员",FailureKind.AUTHORIZATION)
  return value
 }
 private fun file(context:Context)=AtomicFile(File(context.noBackupFilesDir,"work-device.enc"))
 @Synchronized private fun key():SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
  (store.getKey(ALIAS,null) as? SecretKey)?.let {return it}
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
 }
 @Synchronized fun save(context:Context,value:WorkDeviceConnection) {
  val j=JSONObject().put("schema_version",1).put("service_url",value.serviceUrl).put("device_id",value.deviceId).put("device_token",value.token).put("expires_epoch",value.expiresEpoch)
  parse(j.toString())
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
  val sealed=JSONObject().put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).put("payload",Base64.encodeToString(cipher.doFinal(j.toString().toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)).toString().toByteArray()
  val target=file(context);val out=target.startWrite()
  try {out.write(sealed);target.finishWrite(out);updates.value++}catch(e:Exception){target.failWrite(out);throw e}
 }
 @Synchronized fun load(context:Context):WorkDeviceConnection? {
  val target=file(context);if(!target.baseFile.exists())return null
  try {
   val j=JSONObject(String(target.readFully(),Charsets.UTF_8));val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(j.getString("iv"),Base64.NO_WRAP)))}
   return parse(String(cipher.doFinal(Base64.decode(j.getString("payload"),Base64.NO_WRAP)),Charsets.UTF_8))
  }catch(e:BusinessFailure){throw e}catch(e:Exception){throw BusinessFailure("工作机配置无法读取，请联系管理员重新配置",FailureKind.CONFIGURATION)}
 }
 @Synchronized fun clear(context:Context){file(context).delete();updates.value++}
}
