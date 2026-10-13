package at.clavierhaus.unisonmaster.ui

import at.clavierhaus.unisonmaster.i18n.Strings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every word a screen shows comes from the string table (i18n/strings.tsv), in every language the
 * table has -- German first since 13 Oct 2026 (Peter: the app is developed for the German-speaking
 * trade). This reads the source of `ui` (and the free app's `androidApp`) and fails on a word written straight into
 * a screen: a string literal with a word in it, as the first argument of the calls that put text
 * on a screen. Names that are the same in every language -- the brand, a licence's name -- are
 * the only words allowed there, listed in [SAME_IN_EVERY_LANGUAGE].
 *
 * It also asks the table for the keys the Quick start reads by name (help.qs.<section>.s<n>), which
 * the generated constants cannot check, in both languages.
 */
class WordsWallTest {
    private val calls = Regex("""\b(Text|FitText|Chip|ReadOnlyRow|SwitchRow|StepperRow|SettingsSection|SettingsNote|ChoiceRow|ActionRow|TextRow|HomeHubItem|drawText)\(\s*"([^"]*)"""")
    private val SAME_IN_EVERY_LANGUAGE = setOf("Clavier Tuner", "clavierhaus.at")

    private fun repoRoot(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null && !File(d, "settings.gradle.kts").exists()) d = d.parentFile
        return d ?: fail("not inside the repository")
    }

    @Test
    fun noScreenWritesAWordOfItsOwn() {
        val root = repoRoot()
        val found = ArrayList<String>()
        // The screens this repository's words are kept for: the ui library and, where this is the
        // free app's repository (ui/ at the root), its app. Where the library arrives as core/ui
        // (an edition that mounts it), the library is read and the edition's own screens are the
        // edition's to wall.
        val modules = if (File(root, "ui").isDirectory) listOf("ui" to File(root, "ui"), "androidApp" to File(root, "androidApp"))
            else listOf("ui" to File(root, "core/ui"))
        for ((module, dir) in modules) {
            File(dir, "src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                f.readLines().forEachIndexed { i, line ->
                    if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) return@forEachIndexed
                    for (m in calls.findAll(line)) {
                        // what stands inside a string template's braces is code, not a word on the screen
                        val text = m.groupValues[2].replace(Regex("""\$\{[^}]*\}"""), "")
                        if (Regex("[A-Za-zÄÖÜäöüß]{3,}").containsMatchIn(text) && text !in SAME_IN_EVERY_LANGUAGE)
                            found += "$module/${f.name}:${i + 1}: ${m.groupValues[1]}(\"${m.groupValues[2]}\")"
                    }
                }
            }
        }
        if (found.isNotEmpty()) fail("words written into a screen instead of the table (i18n/strings.tsv):\n  " + found.joinToString("\n  "))
    }

    @Test
    fun theQuickStartsLinesAreInTheTableInEveryLanguage() {
        val sections = mapOf("a4" to 4, "sampling" to 4, "octaves" to 5, "tuning" to 5)
        for (lang in Strings.languages) for ((section, n) in sections) for (i in 1..n) {
            val key = "help.qs.$section.s$i"
            val text = Strings.get(lang, key)
            assertTrue(text != key && text.isNotBlank(), "$key has no $lang text")
        }
    }
}
