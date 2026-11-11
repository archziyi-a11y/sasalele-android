package com.example.sasalele.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class IdentitySample(val client: Client, val aliases: List<String>, val summary: String, val source: String = "来源未提供")

object IdentitySamples {
    val revision = MutableStateFlow(0)
    private const val LIMIT = 5 * 1024 * 1024
    fun parse(text: String): List<IdentitySample> {
        require(text.length <= LIMIT) { "画像文件超过5MB限制" }
        val root = JSONObject(text)
        require(root.optInt("schema_version", 1) == 1) { "不支持的schema_version，当前支持1" }
        val rows = root.optJSONArray("customers") ?: error("缺少customers数组")
        require(rows.length() <= 20000) { "一次最多导入20000条身份" }
        val seen = mutableSetOf<ClientKey>()
        return (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: error("第${index + 1}条必须为对象")
            fun field(key: String, required: Boolean = false): String {
                require(!row.has(key) || row.isNull(key) || row.get(key) is String) { "第${index + 1}条${key}必须为字符串" }
                val value = if (row.isNull(key)) "" else row.optString(key).trim()
                require(value.length <= 20000 && (!required || value.isNotBlank())) { "第${index + 1}条${key}为空或过长" }
                return value
            }
            val account = field("account_id", true)
            val id = field("friend_id", true)
            require(seen.add(ClientKey(account, id))) { "第${index + 1}条重复工作账号ID＋客户ID" }
            val name = field("display_name", true)
            val aliases = row.optJSONArray("aliases")
            require(!row.has("aliases") || aliases != null) { "第${index + 1}条aliases必须为字符串数组" }
            val names = (0 until (aliases?.length() ?: 0)).map { n ->
                require(aliases!!.get(n) is String) { "第${index + 1}条别名必须为字符串" }
                aliases.getString(n).trim()
            }
            val avatar = field("avatar_url").takeIf { it.isNotBlank() }
            require(avatar == null || Uri.parse(avatar).scheme in setOf("content", "file", "android.resource")) { "头像只支持已授权本地URI，不读取远程图片" }
            IdentitySample(Client(id, account, name, field("company").ifBlank { "公司未提供" },
                field("intent_level").ifBlank { "意向未提供" }, avatar), (listOf(name) + names).distinct(),
                field("profile_summary").ifBlank { "暂无画像摘要" }, field("profile_source").ifBlank { "来源未提供" })
        }
    }
    fun load(context: Context): List<IdentitySample> {
        val file = File(context.filesDir, "identities/authorized-profiles.json")
        if (file.exists()) return AtomicFile(file).openRead().bufferedReader().use { parse(it.readText()) }
        return try { context.assets.open("customer-identity-sample.json").bufferedReader().use { parse(it.readText()) } }
        catch (_: java.io.FileNotFoundException) { emptyList() }
    }
    fun match(samples: List<IdentitySample>, account: String?, lines: List<String>): List<IdentitySample> =
        samples.filter { (account.isNullOrBlank() || it.client.workAccountId == account) && HeaderIdentityRules.matches(lines, it.aliases) }
    fun byKey(context: Context, key: ClientKey): IdentitySample? = load(context).find { it.client.key == key }
    suspend fun importJson(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded() } ?: error("画像文件无法打开")
        val incoming = parse(bytes.toString(Charsets.UTF_8))
        require(incoming.isNotEmpty()) { "customers为空，没有可导入身份" }
        val existing = load(context).associateBy { it.client.key }.toMutableMap()
        val updates = incoming.count { it.client.key in existing }
        incoming.forEach { existing[it.client.key] = it }
        val rows = org.json.JSONArray()
        existing.values.forEach { sample -> rows.put(JSONObject().apply {
            put("account_id",sample.client.workAccountId); put("friend_id",sample.client.id)
            put("display_name",sample.client.name); put("aliases",org.json.JSONArray(sample.aliases))
            put("profile_summary",sample.summary); put("profile_source",sample.source)
            put("company",sample.client.company); put("intent_level",sample.client.intentLevel)
            sample.client.avatarUrl?.let { put("avatar_url",it) }
        }) }
        val file = File(context.filesDir,"identities/authorized-profiles.json").apply { parentFile?.mkdirs() }
        val atomic = AtomicFile(file); val stream = atomic.startWrite()
        try { stream.write(JSONObject().put("schema_version",1).put("customers",rows).toString().toByteArray()); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
        revision.value += 1
        "导入成功：新增${incoming.size-updates}条，更新${updates}条；本地共${existing.size}条身份。未接入全部业务画像。"
    }
    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = read(buffer); if (n < 0) break; require(output.size()+n <= LIMIT) { "画像文件超过5MB限制" }; output.write(buffer,0,n) }
        return output.toByteArray()
    }
}
