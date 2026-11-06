package dev.nekuz.appwall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelsTest {

    private val fallback = "com.example.app"

    @Test
    fun keepsOrdinaryLabels() {
        assertEquals("Chrome", Labels.clean("Chrome", fallback))
        assertEquals("Café Ñandú", Labels.clean("Café Ñandú", fallback))
        assertEquals("微信", Labels.clean("微信", fallback))
    }

    @Test
    fun keepsCharactersOutsideTheBasicPlane() {
        assertEquals("Fun 😀", Labels.clean("Fun 😀", fallback))
    }

    @Test
    fun stripsBidiAndFormatCharacters() {
        assertEquals("evilgpj.exe", Labels.clean("‮evil⁦gpj⁩‏.exe​", fallback))
    }

    @Test
    fun stripsControlAndLineBreakCharacters() {
        assertEquals("Twolines", Labels.clean("Two\n\u0000  lines\t", fallback))
    }

    @Test
    fun stripsUnpairedSurrogates() {
        assertEquals("ab", Labels.clean("a\uD83Db", fallback))
    }

    @Test
    fun fallsBackToPackageNameWhenNothingVisibleRemains() {
        assertEquals(fallback, Labels.clean(null, fallback))
        assertEquals(fallback, Labels.clean("", fallback))
        assertEquals(fallback, Labels.clean("     ", fallback))
        assertEquals(fallback, Labels.clean("​‮\u0007", fallback))
        assertEquals(fallback, Labels.clean("ㅤ⠀ᅟ", fallback))
    }

    @Test
    fun capsLength() {
        assertEquals(Labels.MAX_LENGTH, Labels.clean("a".repeat(100_000), fallback).length)
    }

    @Test
    fun capDoesNotSplitSurrogatePairs() {
        val cleaned = Labels.clean("😀".repeat(500), fallback)
        assertEquals(Labels.MAX_LENGTH, cleaned.codePointCount(0, cleaned.length))
        assertTrue(cleaned.endsWith("😀"))
    }

    @Test
    fun leadingSpacesDoNotUseTheCap() {
        val cleaned = Labels.clean(" ".repeat(500) + "Name", fallback)
        assertEquals("Name", cleaned)
    }

    @Test
    fun trimsTrailingSpaces() {
        assertEquals("Name", Labels.clean("  Name  ", fallback))
    }
}
