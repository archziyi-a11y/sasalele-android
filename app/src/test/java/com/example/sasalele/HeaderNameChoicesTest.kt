package com.example.sasalele

import com.example.sasalele.data.HeaderRecognition
import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderNameChoicesTest {
 @Test fun keepsWholeNamesPrioritizesTitleAndExcludesNumericControls() {
  val header=HeaderRecognition(listOf("  林妈妈（美术） ","23:10","林妈妈（美术）","微信","周老师-高二"),0,false,title="周老师-高二")
  assertEquals(listOf("周老师-高二","林妈妈（美术）"),headerNameChoices(header))
 }
 @Test fun stripsBackCountAndKeepsFullRemarkAndUsefulWords() {
  val header=HeaderRecognition(listOf("<2 林妈妈 美术 10/17 camelli"),0,false,title="")
  assertEquals(listOf("林妈妈 美术 10/17 camelli","林妈妈","美术","camelli"),headerNameChoices(header))
 }
 @Test fun extractsLatinTailWithoutDateOrStatus() {
  val header=HeaderRecognition(listOf("〈2 （市场）小红书10.17camelli","5G"),0,false,title="")
  assertEquals(listOf("（市场）小红书10.17camelli","camelli"),headerNameChoices(header))
 }
 @Test fun handlesMissingHeader() {assertEquals(emptyList<String>(),headerNameChoices(null))}
}
