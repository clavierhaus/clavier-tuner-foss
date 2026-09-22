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
}
