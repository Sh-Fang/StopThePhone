package com.stopthephone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasonRuleTest {

    @Test
    fun countsChineseLettersAndDigits() {
        assertEquals(11, ReasonRule.effectiveLength("需要回复工作群里的消息"))
        assertEquals(4, ReasonRule.effectiveLength("abc1"))
    }

    @Test
    fun ignoresWhitespaceAndPunctuation() {
        assertEquals(0, ReasonRule.effectiveLength(" 　\n\t，。！？、…—「」.,!?()"))
        assertEquals(4, ReasonRule.effectiveLength("回 复，消 息。"))
    }

    @Test
    fun ignoresEmojiAndInvisibleCharacters() {
        assertEquals(2, ReasonRule.effectiveLength("好😀👍​‍️的"))
    }

    @Test
    fun boundaryIsTen() {
        assertFalse(ReasonRule.isValid("一二三四五六七八九"))
        assertTrue(ReasonRule.isValid("一二三四五六七八九十"))
        assertFalse(ReasonRule.isValid("一二三四五，六七八九。。。"))
        assertFalse(ReasonRule.isValid(""))
    }
}
