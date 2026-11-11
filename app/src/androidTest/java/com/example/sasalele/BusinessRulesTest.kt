package com.example.sasalele
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sasalele.data.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class BusinessRulesTest {
 @Test fun realPhoneRejectsLocalhostAndPublic() { assertFalse(BusinessApi.validUrl("http://127.0.0.1:8876"));assertFalse(BusinessApi.validUrl("http://8.8.8.8:8876"));assertFalse(BusinessApi.validUrl("http://localhost:8876")) }
 @Test fun privateLanAndEmulatorUrl() {assertTrue(BusinessApi.validUrl("http://192.168.1.20:8876"));assertTrue(BusinessApi.validUrl("http://10.0.2.2:8876"))}
 @Test fun headerNameSkipsClockAndBackLabel() {assertEquals("合成客户",HeaderNameRules.candidate(listOf("8:33 |个","返回 合成客户","5G")))}
 @Test fun staleQueryEpochCannotApply() {val gate=RequestEpoch();val old=gate.next();val new=gate.next();assertFalse(gate.accepts(old));assertTrue(gate.accepts(new))}
 @org.junit.Test fun skipBackArrowAndUnreadCount() {
  org.junit.Assert.assertEquals("合成老师（合成名）",HeaderNameRules.candidate(listOf("〈7","合成老师（合成名）")))
  org.junit.Assert.assertEquals("合成客户",HeaderNameRules.candidate(listOf("< 12","返回","15:04","合成客户")))
  org.junit.Assert.assertEquals("",HeaderNameRules.candidate(listOf("〈7","5G","15:04")))
 }
 @org.junit.Test fun productionServiceOrigins() {
  org.junit.Assert.assertTrue(WorkDeviceConfig.validServiceUrl("https://sales.example.org"))
  org.junit.Assert.assertFalse(WorkDeviceConfig.validServiceUrl("http://sales.example.org"))
  org.junit.Assert.assertFalse(WorkDeviceConfig.validServiceUrl("https://192.168.70.43"))
  org.junit.Assert.assertFalse(WorkDeviceConfig.validServiceUrl("https://sales.example.org/?secret=bad"))
 }
}
