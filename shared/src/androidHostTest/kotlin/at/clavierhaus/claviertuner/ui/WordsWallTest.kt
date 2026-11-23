package at.clavierhaus.claviertuner.ui

import at.clavierhaus.claviertuner.i18n.Strings
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
 * It also asks the table for the keys Help reads by name, which the generated constants cannot check, in every
 * language.
 */
class WordsWallTest {
    private val calls = Regex("""\b(Text|FitText|Chip|ReadOnlyRow|SwitchRow|StepperRow|SettingsSection|SettingsNote|ChoiceRow|ActionRow|TextRow|InfoButton|HomeHubItem|drawText)\(\s*"([^"]*)"""")
    /** What an entry's (i) opens (14 Oct 2026): an explanation is words on a screen like any other. */
    private val infos = Regex("""\b(info)\s*=\s*"([^"]*)"""")
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
                    for (m in calls.findAll(line) + infos.findAll(line)) {
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

    /**
     * Help's lines read by name (QuickStart.kt: t("help..."), line("..."), named("...")), which the generated constants
     * cannot check, in every language; and the two families it counts out (help.start.s1..5, help.qs.top.s1..5). Since 23 Nov
     * 2026 the source is read for its keys rather than a written list, so a key Help names and the table lacks fails here.
     */
    @Test
    fun helpsLinesAreInTheTableInEveryLanguage() {
        val root = repoRoot()
        val src = listOf("ui", "core/ui").map { File(root, "$it/src/commonMain/kotlin/at/clavierhaus/claviertuner/app/QuickStart.kt") }
            .firstOrNull { it.isFile } ?: fail("QuickStart.kt not found")
        val named = Regex("\"([a-z]+(?:\\.[A-Za-z0-9]+)+)\"").findAll(src.readText()).map { it.groupValues[1] }
            .filter { !it.endsWith(".") }.toSet()
        val counted = (1..5).map { "help.start.s$it" } + (1..5).map { "help.qs.top.s$it" }
        val keys = named + counted
        assertTrue(keys.size > 40, "only ${keys.size} keys found in ${src.path}")
        for (lang in Strings.languages) for (key in keys) {
            val text = Strings.get(lang, key)
            assertTrue(text != key && text.isNotBlank(), "$key has no $lang text")
        }
    }
}
