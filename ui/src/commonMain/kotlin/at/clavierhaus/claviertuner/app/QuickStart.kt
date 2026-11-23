package at.clavierhaus.claviertuner.app

import at.clavierhaus.claviertuner.i18n.K
import at.clavierhaus.claviertuner.i18n.t
import at.clavierhaus.claviertuner.tuning.NamedTemperament
import at.clavierhaus.claviertuner.ui.HelpTopic

/*
 * Help's topics (Peter, 12 Oct 2026: the way through a tuning; 22 Nov 2026, for Plus: "The Quick start should be what it
 * says: Get to tuning ASAP. All the other stuff can have separate entries and cover stuff more in depth"; 23 Nov 2026, of
 * the free app's two topics: "some of the one from Plus would belong there"). Quick start is five lines from A4 to the first
 * note; each topic after it covers one part of the work and what its settings do, in Plus's words where they are true of
 * this app's screens (StartScreen, TuningScreen, TunerSettingsScreen) and its own where they are not: no piano index, no red
 * dots, no pitch raise. A line that explains a setting starts with the setting's own name, so the name in Help is the name on
 * the page. It holds words and no logic, and is changed when the screens it names are (Settings and Help change together,
 * 22 Nov 2026). An edition adds its own topics after these (TunerSettingsScreen's extraHelpTopics).
 */

private fun line(vararg keys: String): String = keys.joinToString(" ") { t(it) }
private fun named(label: String, vararg keys: String): String = t(label) + ": " + keys.joinToString(" ") { t(it) }

/**
 * Every topic of Help, in the order of the work. [rangeFixed]: the range is A0-C8 and cannot be changed (the free app);
 * false where an edition sets the lowest key itself (Pro, down to C0).
 */
internal fun helpTopics(rangeFixed: Boolean): List<HelpTopic> = listOf(
    HelpTopic(t(K.settings_page_quickstart), listOf(
        t("help.start.h") to (1..5).map { t("help.start.s$it") },
    )),
    HelpTopic(t("help.a4.title"), listOf(
        t("help.a4.h1") to listOf(line("help.qs.a4.s2"), line("help.qs.a4.s3"), line("help.a4.s1")),
        t("help.a4.h2") to listOf(line("help.a4.s2")),
        t("help.a4.h3") to listOf(line("help.qs.a4.s5")),
    )),
    HelpTopic(t("help.samp.title"), listOf(
        t("help.samp.h1") to listOf(line("help.samp.s1")),
        t("help.samp.h2") to listOf(line("help.qs.sampling.s1"), line("help.qs.sampling.s2"), line("help.qs.sampling.s3"), line("help.samp.s2")),
        t("help.samp.h3") to listOf(line("help.samp.s3")),
    )),
    HelpTopic(t("help.oct.title"), listOf(
        t("help.oct.h1") to listOf(line("help.qs.octaves.s2"), line("help.qs.octaves.s4")),
        t("help.oct.h2") to listOf(line("settings.stretchThreeNote"), line("settings.stretchSixNote"), line("help.oct.s1")),
        t("help.oct.h3") to listOf(line("help.oct.s2"), line("help.oct.s3")),
    )),
    temperamentsTopic(),
    HelpTopic(t("help.scr.title"), listOf(
        t("help.scr.h1") to listOf(line("help.scr.s1")),
        t("help.scr.h2") to listOf(line("help.scr.s5"), line("help.scr.s2"), line("help.qs.tuning.s5")),
        t("help.scr.h3") to listOf(line("help.scr.s4"), line("help.qs.tuning.s3")),
        t("help.scr.h4") to listOf(line("help.scr.s3")),
    )),
    HelpTopic(t("settings.page.topOctave"), listOf(
        t("settings.page.topOctave") to (1..5).map { t("help.qs.top.s$it") },
    )),
    HelpTopic(t("help.set.title"), listOf(
        t(K.settings_tab_tuning) to listOf(
            named("settings.temperamentFirst", "help.set.s1"),
            named("settings.stretchDefault", "help.set.s2"),
            named("settings.page.topOctave", "help.set.s3"),
            named("settings.matchWindow", "help.adv.s5", "settings.note.reading"),
        ),
        t(K.settings_tab_stretch) to listOf(line("help.set.s4")),
        t(K.settings_tab_piano) to listOf(
            if (rangeFixed) named("settings.lowestKey", "settings.note.range") else named("settings.lowestKey", "help.set.s5"),
        ),
        t(K.settings_tab_app) to listOf(named("settings.page.display", "settings.note.bars")),
    )),
    HelpTopic(t("help.adv.title"), listOf(
        t("help.adv.title") to listOf(
            named("settings.followKey", "help.adv.s1"),
            named("settings.cleanTogether", "help.adv.s2"),
            named("settings.highestPartial", "help.adv.s3"),
            named("settings.strobeTogether", "settings.note.strobeTogether"),
            named("settings.strobeSmoothing", "settings.note.strobeSmoothing"),
            named("settings.readingMemory", "help.adv.s4"),
        ),
    )),
)

/**
 * The temperaments, one line each (Plus, 23 Nov 2026; in the free app since the same day): how one is chosen, and each one's
 * author, year and narrowed fifths, the way NamedTemperament defines it.
 */
private fun temperamentsTopic(): HelpTopic = HelpTopic(
    t(K.help_temp_title),
    listOf(
        t(K.help_temp_h1) to listOf(t(K.help_temp_choose)),
        t(K.help_temp_h2) to NamedTemperament.entries.map { named -> temperamentName(named) + ": " + t("help.temp." + temperamentKey(named).removePrefix("temperament.")) } +
            listOf(t(K.temperament_schubert) + ": " + t(K.help_temp_schubert)),
    ),
)
