package com.example.sasalele
import com.example.sasalele.data.ClientKey
import org.junit.Assert.*
import org.junit.Test
class KnowledgeTrialTest {
 @Test fun editsKeepTaskButSwitchingStartsNewTask() {
  val s=GenerationDraftStore();val k=ClientKey("a","f")
  s.activate(k,"帮我回");val first=s.get(k,"帮我回").taskId
  s.get(k,"帮我回").latest="new input";s.activate(k,"帮我回")
  assertEquals(first,s.get(k,"帮我回").taskId)
  s.activate(k,"润色");s.activate(k,"帮我回")
  assertNotEquals(first,s.get(k,"帮我回").taskId)
 }
 @Test fun editDistanceMeasuresEditsNotSpan() {
  assertEquals(0.0,editRatio("abcdef","abcdef"),0.001)
  assertEquals(2.0/6,editRatio("abcdef","xbcdey"),0.001)
  assertEquals(1.0,editRatio("abc",""),0.001)
 }
}
