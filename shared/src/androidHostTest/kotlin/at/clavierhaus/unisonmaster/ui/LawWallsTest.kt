package at.clavierhaus.unisonmaster.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * The law walls of the screens, as a test that only tightens.
 *
 * Two laws of the screen can be checked in the source without a device: nothing
 * scrolls, and no text is ever cut (docs/UI.md). This test reads every Kotlin
 * file of the `ui` and `androidApp` modules and counts the constructs that
 * break them. Each file's count is held in [ALLOWED], the violations standing
 * on the day the wall was raised; a file may never exceed its number, and when
 * a file is mended the number is lowered here in the same commit — the test
 * fails in both directions, so the list only ever shrinks and the record stays
 * honest. A new file starts at zero.
 *
 * It runs on the JVM (`:shared:testAndroidHostTest`) and needs no Android SDK:
 * it finds the repository by walking up to `settings.gradle.kts`.
 */
class LawWallsTest {
    /** What breaks which law, as it appears in Compose source. */
    private val walls = listOf(
        "verticalScroll(" to "scrolls",
        "horizontalScroll(" to "scrolls",
        "LazyColumn(" to "scrolls",
        "LazyRow(" to "scrolls",
        "TextOverflow.Ellipsis" to "cuts text",
        Regex("""maxLines\s*=\s*1\b""") to "cuts text",
    )

    /** The violations standing on 2 October 2026, per file. Lower, never raise. */
    private val ALLOWED = mapOf(
        "ui/TuningScreenParts.kt" to 9,
        "ui/SettingsParts.kt" to 1,
        "ui/HubParts.kt" to 4,
        "androidApp/BasicHub.kt" to 7,
        "androidApp/RecordStrikesScreen.kt" to 2,
    )

    private fun repoRoot(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null && !File(d, "settings.gradle.kts").exists()) d = d.parentFile
        return d ?: fail("not inside the repository: no settings.gradle.kts above ${System.getProperty("user.dir")}")
    }

    private fun count(text: String): Int = walls.sumOf { (needle, _) ->
        when (needle) {
            is String -> text.windowed(needle.length).count { it == needle }
            is Regex -> needle.findAll(text).count()
            else -> 0
        }
    }

    @Test
    fun nothingScrollsAndNoTextIsCutBeyondWhatStoodWhenTheWallWasRaised() {
        val root = repoRoot()
        val found = HashMap<String, Int>()
        for (module in listOf("ui", "androidApp")) {
            File(root, "$module/src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                val n = count(f.readText())
                if (n > 0) found["$module/${f.name}"] = n
            }
        }
        val problems = ArrayList<String>()
        for ((file, n) in found) {
            val allowed = ALLOWED[file] ?: 0
            if (n > allowed) problems += "$file: $n (allowed $allowed) — a new scroll or cut; mend it, or say why in docs/UI.md and raise nothing"
        }
        for ((file, allowed) in ALLOWED) {
            val n = found[file] ?: 0
            if (n < allowed) problems += "$file: $n (allowed $allowed) — mended; lower the number in LawWallsTest.ALLOWED so the wall holds"
        }
        if (problems.isNotEmpty()) fail("the law walls:\n  " + problems.joinToString("\n  "))
    }
}
