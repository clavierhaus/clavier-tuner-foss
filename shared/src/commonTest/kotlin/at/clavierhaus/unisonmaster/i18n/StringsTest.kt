package at.clavierhaus.unisonmaster.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringsTest {
    @Test
    fun everyKeyHasEveryLanguageAndTheSameArguments() {
        val n = StringsTable.languages.size
        for ((key, texts) in StringsTable.table) {
            assertEquals(n, texts.size, key)
            assertTrue(texts.all { it.isNotBlank() }, "$key has a hole")
            val args = texts.map { t -> Regex("\\{(\\d+)}").findAll(t).map { it.groupValues[1] }.toSet() }.toSet()
            assertEquals(1, args.size, "$key: the languages disagree on the arguments")
        }
    }

    @Test
    fun theLanguageInForceIsUsedAndFallsBackToEnglish() {
        Strings.use("de-AT")
        assertEquals("de", Strings.language.value)
        assertEquals("3 von 19", t(K.sampling_count, 3, 19))
        Strings.use("fr")
        assertEquals("en", Strings.language.value)
        assertEquals("3 of 19", t(K.sampling_count, 3, 19))
        assertEquals("no.such.key", t("no.such.key"))
        Strings.use(null)
    }

    /** A second catalogue, as Pro registers one: Swiss German only where it differs, no French at all. */
    private object Extra : Catalog {
        override val name = "test-extra"
        override val version = "test"
        override val languages = listOf("en", "de", "de-CH")
        override val table = mapOf(
            "extra.word" to arrayOf("word", "Wort", ""),
            "extra.street" to arrayOf("street", "Straße", "Strasse"),
        )
    }

    @Test
    fun aSecondCatalogueIsLookedUpWithTheRegionalChainAndSaysWhatItLacks() {
        Strings.register(Extra)
        Strings.use("de-CH")
        assertEquals("de-CH", Strings.language.value)
        assertEquals("Strasse", t("extra.street"))
        assertEquals("Wort", t("extra.word"))                 // de-CH empty → de
        assertEquals("3 von 19", t(K.sampling_count, 3, 19))  // FOSS has no de-CH column → de
        Strings.use("fr")
        assertEquals("en", Strings.language.value)
        assertEquals(listOf("clavier-tuner", "test-extra"), Strings.missing("fr").map { it.name })
        assertEquals(emptyList(), Strings.missing("de"))
        Strings.use(null)
        Strings.unregister(Extra.name)
    }
}
