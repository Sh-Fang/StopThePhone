package com.stopthephone.domain

/**
 * 使用理由的有效性规则：只数字母、数字和汉字，不少于 10 个。
 */
object ReasonRule {

    const val MIN_LENGTH = 10

    fun effectiveLength(text: String): Int = text.codePoints().filter { Character.isLetterOrDigit(it) }.count().toInt()

    fun isValid(text: String): Boolean = effectiveLength(text) >= MIN_LENGTH
}
