package com.example.sasalele

import com.example.sasalele.data.ClientKey
import org.junit.Assert.*
import org.junit.Test

class GenerationDraftStoreTest {
 @Test fun separatesWorkAccountCustomerAndTaskAndRestoresEdits() {
  val store=GenerationDraftStore()
  val a=ClientKey("account-a","client")
  val b=ClientKey("account-b","client")
  store.get(a,"帮我回").apply { latest="补充"; edited="人工修改"; revision++ }
  store.get(a,"润色").original="原文"
  assertEquals("人工修改",store.get(a,"帮我回").edited)
  assertEquals("补充",store.get(a,"帮我回").latest)
  assertEquals("",store.get(a,"润色").edited)
  assertEquals("",store.get(b,"帮我回").latest)
  assertEquals(1,store.get(a,"帮我回").revision)
 }
}
