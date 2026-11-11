package com.example.sasalele.data

import com.example.sasalele.data.db.MaterialEntity
import java.net.URI

enum class MaterialKind(val label:String) {
 ALL("全部"), IMAGE("图片"), LINK("链接"), TEXT("纯文本");
 companion object {
  fun of(material:MaterialEntity):MaterialKind {
   if(material.type=="IMAGE")return IMAGE
   val value=material.content.trim()
   val link=runCatching {
    val uri=URI(value)
    uri.scheme?.lowercase() in setOf("http","https") && !uri.host.isNullOrBlank() && value.none{it.isWhitespace()}
   }.getOrDefault(false)
   return if(link) LINK else TEXT
  }
 }
}
