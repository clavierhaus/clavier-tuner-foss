package at.clavierhaus.unisonmaster.i18n

// GENERATED from i18n/strings.tsv by scripts/i18n.py — edit the table, not this file.

/** Every key of the table, as a constant: the compiler catches a key that is not there. */
object K {
    const val app_settings = "app.settings"
    const val app_done = "app.done"
    const val app_progress = "app.progress"
    const val app_tuning = "app.tuning"
    const val app_partials = "app.partials"
    const val app_onePartial = "app.onePartial"
    const val sampling_title = "sampling.title"
    const val sampling_line = "sampling.line"
    const val sampling_lineSet = "sampling.lineSet"
    const val sampling_strikeAny = "sampling.strikeAny"
    const val sampling_caught = "sampling.caught"
    const val sampling_kept = "sampling.kept"
    const val app_accept = "app.accept"
    const val sampling_count = "sampling.count"
    const val state_listening = "state.listening"
    const val state_matches = "state.matches"
    const val state_sharp = "state.sharp"
    const val state_flat = "state.flat"
    const val state_complete = "state.complete"
    const val state_heardOther = "state.heardOther"
    const val state_off = "state.off"
    const val origin_reference = "origin.reference"
    const val origin_temperament = "origin.temperament"
    const val origin_curve = "origin.curve"
    const val origin_wide = "origin.wide"
    const val origin_beatless = "origin.beatless"
    const val origin_check = "origin.check"
    const val origin_wideWord = "origin.wideWord"
    const val origin_narrowWord = "origin.narrowWord"
    const val origin_octave = "origin.octave"
    const val origin_untuned = "origin.untuned"
    const val settings_tab_temperament = "settings.tab.temperament"
    const val settings_tab_about = "settings.tab.about"
    const val settings_tab_license = "settings.tab.license"
    const val settings_tab_care = "settings.tab.care"
    const val settings_tab_research = "settings.tab.research"
    const val settings_language = "settings.language"
    const val settings_language_system = "settings.language.system"
}

internal object StringsTable {
    val languages: List<String> = listOf("en", "de")
    val table: Map<String, Array<String>> = mapOf(
        "app.settings" to arrayOf("Settings", "Einstellungen"),
        "app.done" to arrayOf("Done", "Fertig"),
        "app.progress" to arrayOf("Progress", "Verlauf"),
        "app.tuning" to arrayOf("Tuning", "Stimmen"),
        "app.partials" to arrayOf("Partials", "Teiltöne"),
        "app.onePartial" to arrayOf("One partial", "Ein Teilton"),
        "sampling.title" to arrayOf("Sampling your piano", "Ihr Klavier wird vermessen"),
        "sampling.line" to arrayOf("Single strings, the keys you choose, {0} of them: Accept keeps a string; a key struck again replaces its sample.", "Einzelne Saiten, Tasten nach Wahl, {0} davon: Übernehmen behält eine Saite; eine erneut angeschlagene Taste ersetzt ihre Probe."),
        "sampling.lineSet" to arrayOf("Single strings, the {0} keys below: Accept keeps a string; a key struck again replaces its sample.", "Einzelne Saiten, die {0} Tasten unten: Übernehmen behält eine Saite; eine erneut angeschlagene Taste ersetzt ihre Probe."),
        "sampling.strikeAny" to arrayOf("strike a key: one string, one wedge", "eine Taste anschlagen: eine Saite, ein Keil"),
        "sampling.caught" to arrayOf("caught — Accept keeps it", "erkannt — Übernehmen behält sie"),
        "sampling.kept" to arrayOf("kept", "behalten"),
        "app.accept" to arrayOf("Accept", "Übernehmen"),
        "sampling.count" to arrayOf("{0} of {1}", "{0} von {1}"),
        "state.listening" to arrayOf("listening", "hört zu"),
        "state.matches" to arrayOf("matches", "stimmt"),
        "state.sharp" to arrayOf("sharp", "zu hoch"),
        "state.flat" to arrayOf("flat", "zu tief"),
        "state.complete" to arrayOf("complete", "fertig"),
        "state.heardOther" to arrayOf("that's {0}", "das ist {0}"),
        "state.off" to arrayOf("{0} c {1}", "{0} c {1}"),
        "origin.reference" to arrayOf("the reference, set on the hub", "die Referenz, auf dem Hub gesetzt"),
        "origin.temperament" to arrayOf("equal temperament on A4 {0}", "gleichstufig auf A4 {0}"),
        "origin.curve" to arrayOf("{0} octave to {1} on the curve", "{0}-Oktave zu {1} nach der Kurve"),
        "origin.wide" to arrayOf(", {0} c wide", ", {0} c weit"),
        "origin.beatless" to arrayOf(", beatless", ", schwebungsfrei"),
        "origin.check" to arrayOf(" · {0} stands {1} c {2}", " · {0} steht {1} c {2}"),
        "origin.wideWord" to arrayOf("wide", "weit"),
        "origin.narrowWord" to arrayOf("narrow", "eng"),
        "origin.octave" to arrayOf("{0} octave to {1}", "{0}-Oktave zu {1}"),
        "origin.untuned" to arrayOf("equal temperament — {0} not tuned yet", "gleichstufig — {0} noch nicht gestimmt"),
        "settings.tab.temperament" to arrayOf("Temperament", "Temperatur"),
        "settings.tab.about" to arrayOf("About", "Über"),
        "settings.tab.license" to arrayOf("License", "Lizenz"),
        "settings.tab.care" to arrayOf("Customer care", "Kundenpflege"),
        "settings.tab.research" to arrayOf("Research", "Forschung"),
        "settings.language" to arrayOf("Language", "Sprache"),
        "settings.language.system" to arrayOf("System", "System"),
    )
}
