package at.clavierhaus.unisonmaster.app

import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.ui.HelpTopic

/*
 * The Help tab's first page (Peter, 12 Oct 2026): the way through a tuning, from A4 to the
 * tuning screen, in four short sections -- A4, the sampling, the octave types, the tuning. It
 * holds words and no logic; each line says what the screens it names do as they are written
 * (SamplingPage, TunerSettingsScreen's Stretch tab, BasicHub), and is changed when they are.
 * It is the first topic of Help's index; an edition adds its own topics after it
 * (TunerSettingsScreen's extraHelpTopics).
 */

/** The Quick start, one column on the right of Help: A4, the sampling, the octave types, the tuning. */
internal fun quickStartTopic(): HelpTopic = HelpTopic(
    t(K.settings_page_quickstart),
    listOf(
        t(K.help_qs_a4_title) to steps("a4", 5), t(K.help_qs_sampling_title) to steps("sampling", 4),
        t(K.help_qs_octaves_title) to steps("octaves", 5), t(K.help_qs_tuning_title) to steps("tuning", 5),
    ),
)

/** The [n] lines of one section, from the table (help.qs.<section>.s1 … sn), in the language in force. */
private fun steps(section: String, n: Int): List<String> = (1..n).map { t("help.qs.$section.s$it") }
