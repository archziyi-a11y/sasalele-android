package com.example.sasalele

import com.example.sasalele.data.HeaderIdentityRules
import org.junit.Assert.*
import org.junit.Test

class HeaderIdentityRulesTest {
    @Test fun groupIsExcluded() { assertTrue(HeaderIdentityRules.isExcluded(listOf("客户交流群(23)"))) }
    @Test fun listIsExcluded() { assertTrue(HeaderIdentityRules.isExcluded(listOf("Weixin (26)"))) }
    @Test fun publicIsExcluded() { assertTrue(HeaderIdentityRules.isExcluded(listOf("公众号"))) }
    @Test fun privateNameDoesNotExclude() { assertFalse(HeaderIdentityRules.isExcluded(listOf("陈老师"))) }
    @Test fun exactAliasWithSpacing() { assertTrue(HeaderIdentityRules.matches(listOf("陈 老师"), listOf("陈老师"))) }
    @Test fun partialNameDoesNotMatch() { assertFalse(HeaderIdentityRules.matches(listOf("陈老师甲"), listOf("陈老师"))) }
}
