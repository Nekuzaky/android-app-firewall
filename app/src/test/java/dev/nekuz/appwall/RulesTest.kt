package dev.nekuz.appwall

import org.junit.Assert.assertEquals
import org.junit.Test

class RulesTest {

    @Test
    fun countsOnlyInstalledPackages() {
        val installed = setOf("a", "c")
        assertEquals(2, Rules.countInstalled(setOf("a", "b", "c")) { it in installed })
    }

    @Test
    fun countsNothingWhenThereAreNoRules() {
        assertEquals(0, Rules.countInstalled(emptySet()) { true })
    }
}
