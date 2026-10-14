package at.clavierhaus.unisonmaster.app

import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.ui.SettingsPage
import at.clavierhaus.unisonmaster.ui.helpPage

/*
 * The Help tab's first page (Peter, 12 Oct 2026): the way through a tuning, from A4 to the
 * tuning screen, in four short sections -- A4, the sampling, the octave types, the tuning. It
 * holds words and no logic; each line says what the screens it names do as they are written
 * (SamplingPage, TunerSettingsScreen's Stretch tab, BasicHub), and is changed when they are.
 * An edition adds its own help pages after this one (TunerSettingsScreen's extraHelpPages).
 */

/** The Quick start: left A4 and the sampling, right the octave types and the tuning. */
internal fun quickStartPage(): SettingsPage = helpPage(
    t(K.settings_page_quickstart),
    left = listOf(t(K.help_qs_a4_title) to steps("a4", 5), t(K.help_qs_sampling_title) to steps("sampling", 4)),
    right = listOf(t(K.help_qs_octaves_title) to steps("octaves", 5), t(K.help_qs_tuning_title) to steps("tuning", 5)),
)

/** The [n] lines of one section, from the table (help.qs.<section>.s1 … sn), in the language in force. */
private fun steps(section: String, n: Int): List<String> = (1..n).map { t("help.qs.$section.s$it") }
