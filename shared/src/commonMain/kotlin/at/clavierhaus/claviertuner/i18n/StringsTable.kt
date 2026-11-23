package at.clavierhaus.claviertuner.i18n

// GENERATED from i18n/strings.tsv by scripts/i18n.py — edit the table, not this file.

/** Every key of the table, as a constant: the compiler catches a key that is not there. */
object K {
    const val app_settings = "app.settings"
    const val app_done = "app.done"
    const val crash_title = "crash.title"
    const val crash_text = "crash.text"
    const val crash_copy = "crash.copy"
    const val crash_close = "crash.close"
    const val app_progress = "app.progress"
    const val app_tuning = "app.tuning"
    const val app_partials = "app.partials"
    const val app_onePartial = "app.onePartial"
    const val sampling_a4_mute = "sampling.a4.mute"
    const val sampling_a4_pitch = "sampling.a4.pitch"
    const val sampling_a4_tune = "sampling.a4.tune"
    const val sampling_a4_done = "sampling.a4.done"
    const val step_a4 = "step.a4"
    const val step_sampling = "step.sampling"
    const val step_tuning = "step.tuning"
    const val sampling_order_why = "sampling.order.why"
    const val sampling_line = "sampling.line"
    const val sampling_lineSet = "sampling.lineSet"
    const val sampling_strikeAny = "sampling.strikeAny"
    const val sampling_caught = "sampling.caught"
    const val sampling_kept = "sampling.kept"
    const val sampling_measuring = "sampling.measuring"
    const val sampling_measured = "sampling.measured"
    const val sampling_short = "sampling.short"
    const val app_accept = "app.accept"
    const val sampling_count = "sampling.count"
    const val state_matches = "state.matches"
    const val state_sharp = "state.sharp"
    const val state_flat = "state.flat"
    const val state_near = "state.near"
    const val state_complete = "state.complete"
    const val state_heardOther = "state.heardOther"
    const val state_off = "state.off"
    const val origin_reference = "origin.reference"
    const val origin_temperament = "origin.temperament"
    const val origin_curve = "origin.curve"
    const val origin_wide = "origin.wide"
    const val origin_narrow = "origin.narrow"
    const val origin_beatless = "origin.beatless"
    const val origin_check = "origin.check"
    const val origin_wideWord = "origin.wideWord"
    const val origin_narrowWord = "origin.narrowWord"
    const val origin_octave = "origin.octave"
    const val origin_untuned = "origin.untuned"
    const val settings_tab_tuning = "settings.tab.tuning"
    const val settings_tab_stretch = "settings.tab.stretch"
    const val settings_tab_piano = "settings.tab.piano"
    const val settings_tab_help = "settings.tab.help"
    const val settings_tab_app = "settings.tab.app"
    const val settings_page_temperament = "settings.page.temperament"
    const val settings_page_reading = "settings.page.reading"
    const val settings_page_bass = "settings.page.bass"
    const val settings_page_middle = "settings.page.middle"
    const val settings_page_treble = "settings.page.treble"
    const val settings_page_range = "settings.page.range"
    const val settings_page_language = "settings.page.language"
    const val settings_page_probe = "settings.page.probe"
    const val settings_page_quickstart = "settings.page.quickstart"
    const val settings_page_about = "settings.page.about"
    const val settings_page_display = "settings.page.display"
    const val settings_navigationBar = "settings.navigationBar"
    const val settings_statusBar = "settings.statusBar"
    const val settings_note_bars = "settings.note.bars"
    const val settings_language = "settings.language"
    const val settings_language_system = "settings.language.system"
    const val hub_continue_verb = "hub.continue.verb"
    const val hub_continue_rest = "hub.continue.rest"
    const val hub_continue_note = "hub.continue.note"
    const val hub_new_verb = "hub.new.verb"
    const val hub_new_rest = "hub.new.rest"
    const val hub_new_note = "hub.new.note"
    const val hub_new_rejected = "hub.new.rejected"
    const val hub_new_ask = "hub.new.ask"
    const val hub_new_askNote = "hub.new.askNote"
    const val hub_new_askYes = "hub.new.askYes"
    const val hub_dateFormat = "hub.dateFormat"
    const val hub_fundamentalA4 = "hub.fundamentalA4"
    const val hub_fullSpectrum = "hub.fullSpectrum"
    const val sampling_a4Hint = "sampling.a4Hint"
    const val sampling_a4Strobe = "sampling.a4Strobe"
    const val sampling_temperament = "sampling.temperament"
    const val sampling_temperamentStart = "sampling.temperamentStart"
    const val sampling_temperamentEnd = "sampling.temperamentEnd"
    const val sampling_caughtLabel = "sampling.caughtLabel"
    const val sampling_strikeKey = "sampling.strikeKey"
    const val sampling_sampledLabel = "sampling.sampledLabel"
    const val sampling_atLeast = "sampling.atLeast"
    const val sampling_next = "sampling.next"
    const val sampling_order_strings = "sampling.order.strings"
    const val sampling_order_count = "sampling.order.count"
    const val sampling_order_accept = "sampling.order.accept"
    const val app_cancel = "app.cancel"
    const val tuning_partialsUsed = "tuning.partialsUsed"
    const val tuning_partialOne = "tuning.partialOne"
    const val tuning_info = "tuning.info"
    const val tuning_infoTitle = "tuning.infoTitle"
    const val tuning_infoOrders = "tuning.infoOrders"
    const val tuning_suggestShort = "tuning.suggestShort"
    const val tuning_suggest = "tuning.suggest"
    const val tuning_suggestUse = "tuning.suggestUse"
    const val tuning_suggestNoneShort = "tuning.suggestNoneShort"
    const val tuning_suggestNone = "tuning.suggestNone"
    const val tuning_partialsNot = "tuning.partialsNot"
    const val tuning_follows = "tuning.follows"
    const val tuning_leavingKeeps = "tuning.leavingKeeps"
    const val tuning_temperamentDone = "tuning.temperamentDone"
    const val tuning_temperamentFirst = "tuning.temperamentFirst"
    const val tuning_target = "tuning.target"
    const val tuning_targetPartial = "tuning.targetPartial"
    const val tuning_fundamental = "tuning.fundamental"
    const val progress_caption = "progress.caption"
    const val display_tooSmall = "display.tooSmall"
    const val about_version = "about.version"
    const val about_microphone = "about.microphone"
    const val about_mic_unprocessed = "about.mic.unprocessed"
    const val about_mic_undeclared = "about.mic.undeclared"
    const val about_mic_processed = "about.mic.processed"
    const val about_mic_notYet = "about.mic.notYet"
    const val about_core = "about.core"
    const val about_typeface = "about.typeface"
    const val about_typefaceValue = "about.typefaceValue"
    const val about_icon = "about.icon"
    const val about_licence = "about.licence"
    const val about_vienna = "about.vienna"
    const val settings_temperament = "settings.temperament"
    const val settings_temperamentOctave = "settings.temperamentOctave"
    const val settings_order = "settings.order"
    const val settings_temperamentFirst = "settings.temperamentFirst"
    const val settings_screen = "settings.screen"
    const val settings_followKey = "settings.followKey"
    const val settings_cleanTogether = "settings.cleanTogether"
    const val tuning_strobeAll = "tuning.strobeAll"
    const val tuning_strobeAll_note = "tuning.strobeAll.note"
    const val tuning_pinPace = "tuning.pinPace"
    const val tuning_strobeLimit_note = "tuning.strobeLimit.note"
    const val tuning_strobeOne_note = "tuning.strobeOne.note"
    const val tuning_strobeLimit = "tuning.strobeLimit"
    const val tuning_strobeOne = "tuning.strobeOne"
    const val settings_strobeTogether = "settings.strobeTogether"
    const val settings_note_strobeTogether = "settings.note.strobeTogether"
    const val settings_strobeSmoothing = "settings.strobeSmoothing"
    const val settings_strobeSmoothingWhole = "settings.strobeSmoothingWhole"
    const val settings_note_strobeSmoothing = "settings.note.strobeSmoothing"
    const val settings_highestPartial = "settings.highestPartial"
    const val settings_reading = "settings.reading"
    const val settings_readingMemory = "settings.readingMemory"
    const val settings_matchWindow = "settings.matchWindow"
    const val settings_theLowestKey = "settings.theLowestKey"
    const val settings_octave = "settings.octave"
    const val settings_octaveType = "settings.octaveType"
    const val settings_octaveWidth = "settings.octaveWidth"
    const val settings_beatless = "settings.beatless"
    const val settings_cWide = "settings.cWide"
    const val settings_cNarrow = "settings.cNarrow"
    const val settings_sixPoints = "settings.sixPoints"
    const val settings_page_stretchDefault = "settings.page.stretchDefault"
    const val settings_page_sixPoints = "settings.page.sixPoints"
    const val settings_stretchDefault = "settings.stretchDefault"
    const val settings_stretchPoints = "settings.stretchPoints"
    const val settings_stretchThree = "settings.stretchThree"
    const val settings_stretchSix = "settings.stretchSix"
    const val settings_stretchBoth = "settings.stretchBoth"
    const val settings_stretchThreeNote = "settings.stretchThreeNote"
    const val settings_stretchSixNote = "settings.stretchSixNote"
    const val settings_note_sixPoints = "settings.note.sixPoints"
    const val settings_farEnd = "settings.farEnd"
    const val settings_atFar = "settings.atFar"
    const val settings_same = "settings.same"
    const val settings_piano = "settings.piano"
    const val settings_lowestKey = "settings.lowestKey"
    const val settings_lowestUnwound = "settings.lowestUnwound"
    const val settings_note_bass = "settings.note.bass"
    const val settings_note_middle = "settings.note.middle"
    const val settings_note_treble = "settings.note.treble"
    const val settings_note_temperament = "settings.note.temperament"
    const val settings_note_reading = "settings.note.reading"
    const val settings_note_range = "settings.note.range"
    const val settings_note_unwound = "settings.note.unwound"
    const val settings_note_about = "settings.note.about"
    const val help_index = "help.index"
    const val help_qs_a4_s2 = "help.qs.a4.s2"
    const val help_qs_a4_s3 = "help.qs.a4.s3"
    const val help_qs_a4_s5 = "help.qs.a4.s5"
    const val help_qs_sampling_s1 = "help.qs.sampling.s1"
    const val help_qs_sampling_s2 = "help.qs.sampling.s2"
    const val help_qs_sampling_s3 = "help.qs.sampling.s3"
    const val help_qs_octaves_s2 = "help.qs.octaves.s2"
    const val help_qs_octaves_s4 = "help.qs.octaves.s4"
    const val help_qs_tuning_s3 = "help.qs.tuning.s3"
    const val help_qs_tuning_s5 = "help.qs.tuning.s5"
    const val help_qs_top_s1 = "help.qs.top.s1"
    const val help_qs_top_s2 = "help.qs.top.s2"
    const val help_qs_top_s3 = "help.qs.top.s3"
    const val help_qs_top_s4 = "help.qs.top.s4"
    const val help_qs_top_s5 = "help.qs.top.s5"
    const val table_target = "table.target"
    const val table_measured = "table.measured"
    const val table_cents = "table.cents"
    const val octave_strike = "octave.strike"
    const val octave_tuned = "octave.tuned"
    const val settings_page_topOctave = "settings.page.topOctave"
    const val settings_octaveBars = "settings.octaveBars"
    const val settings_note_octaveBars = "settings.note.octaveBars"
    const val edge_temperament = "edge.temperament"
    const val edge_octaves = "edge.octaves"
    const val edge_strobe = "edge.strobe"
    const val edge_piano = "edge.piano"
    const val edge_noPiano = "edge.noPiano"
    const val a4_window = "a4.window"
    const val start_next = "start.next"
    const val temperament_equal = "temperament.equal"
    const val temperament_werckmeister3 = "temperament.werckmeister3"
    const val temperament_kirnberger3 = "temperament.kirnberger3"
    const val temperament_vallotti = "temperament.vallotti"
    const val temperament_young2 = "temperament.young2"
    const val temperament_kellner = "temperament.kellner"
    const val temperament_meantoneQuarter = "temperament.meantoneQuarter"
    const val octaves_suggest = "octaves.suggest"
    const val octaves_suggestShort = "octaves.suggestShort"
    const val help_temp_title = "help.temp.title"
    const val help_temp_h1 = "help.temp.h1"
    const val help_temp_h2 = "help.temp.h2"
    const val help_temp_equal = "help.temp.equal"
    const val help_temp_werckmeister3 = "help.temp.werckmeister3"
    const val help_temp_kirnberger3 = "help.temp.kirnberger3"
    const val help_temp_vallotti = "help.temp.vallotti"
    const val help_temp_young2 = "help.temp.young2"
    const val help_temp_kellner = "help.temp.kellner"
    const val help_temp_meantoneQuarter = "help.temp.meantoneQuarter"
    const val help_temp_choose = "help.temp.choose"
    const val settings_tab_advanced = "settings.tab.advanced"
    const val temperament_schubert = "temperament.schubert"
    const val help_temp_schubert = "help.temp.schubert"
    const val help_start_h = "help.start.h"
    const val help_start_s1 = "help.start.s1"
    const val help_start_s2 = "help.start.s2"
    const val help_start_s3 = "help.start.s3"
    const val help_start_s4 = "help.start.s4"
    const val help_start_s5 = "help.start.s5"
    const val help_a4_title = "help.a4.title"
    const val help_a4_h1 = "help.a4.h1"
    const val help_a4_h2 = "help.a4.h2"
    const val help_a4_h3 = "help.a4.h3"
    const val help_a4_s1 = "help.a4.s1"
    const val help_a4_s2 = "help.a4.s2"
    const val help_samp_title = "help.samp.title"
    const val help_samp_h1 = "help.samp.h1"
    const val help_samp_h2 = "help.samp.h2"
    const val help_samp_h3 = "help.samp.h3"
    const val help_samp_s1 = "help.samp.s1"
    const val help_samp_s3 = "help.samp.s3"
    const val help_oct_title = "help.oct.title"
    const val help_oct_h1 = "help.oct.h1"
    const val help_oct_h2 = "help.oct.h2"
    const val help_oct_h3 = "help.oct.h3"
    const val help_oct_s1 = "help.oct.s1"
    const val help_oct_s2 = "help.oct.s2"
    const val help_oct_s3 = "help.oct.s3"
    const val help_scr_title = "help.scr.title"
    const val help_scr_h1 = "help.scr.h1"
    const val help_scr_h2 = "help.scr.h2"
    const val help_scr_h3 = "help.scr.h3"
    const val help_scr_h4 = "help.scr.h4"
    const val help_scr_s2 = "help.scr.s2"
    const val help_scr_s3 = "help.scr.s3"
    const val help_scr_s4 = "help.scr.s4"
    const val help_scr_s5 = "help.scr.s5"
    const val help_set_title = "help.set.title"
    const val help_set_s1 = "help.set.s1"
    const val help_set_s2 = "help.set.s2"
    const val help_set_s3 = "help.set.s3"
    const val help_set_s4 = "help.set.s4"
    const val help_set_s5 = "help.set.s5"
    const val help_adv_title = "help.adv.title"
    const val help_adv_s1 = "help.adv.s1"
    const val help_adv_s2 = "help.adv.s2"
    const val help_adv_s3 = "help.adv.s3"
    const val help_adv_s4 = "help.adv.s4"
    const val help_samp_s2 = "help.samp.s2"
    const val help_scr_s1 = "help.scr.s1"
    const val help_adv_s5 = "help.adv.s5"
    const val stretch_schubertTitle = "stretch.schubertTitle"
    const val stretch_schubertFixed = "stretch.schubertFixed"
}

internal object StringsTable : Catalog {
    override val name = "clavier-tuner"
    override val version = "2026-09-28"
    override val languages: List<String> = listOf("en", "de", "fr")
    override val table: Map<String, Array<String>> = mapOf(
        "app.settings" to arrayOf("Settings", "Einstellungen", "Réglages"),
        "app.done" to arrayOf("Done", "Fertig", "Terminé"),
        "crash.title" to arrayOf("The app stopped last time", "Die App wurde beim letzten Mal beendet", "L'app s'est arrêtée la dernière fois"),
        "crash.text" to arrayOf("This is what went wrong. Please copy it and send it to us: it shows where to look.", "Das ist dabei schiefgegangen. Bitte kopieren Sie es und schicken Sie es uns: Es zeigt, wo wir suchen müssen.", "Voici ce qui s'est mal passé. Copiez-le et envoyez-le-nous : il montre où chercher."),
        "crash.copy" to arrayOf("Copy", "Kopieren", "Copier"),
        "crash.close" to arrayOf("Close", "Schließen", "Fermer"),
        "app.progress" to arrayOf("Progress", "Fortschritt", "Progression"),
        "app.tuning" to arrayOf("Tuning", "Stimmen", "Accord"),
        "app.partials" to arrayOf("Partials", "Teiltöne", "Partiels"),
        "app.onePartial" to arrayOf("One partial", "Ein Teilton", "Un partiel"),
        "sampling.a4.mute" to arrayOf("Mute A4 to one string.", "A4 auf eine Saite abdämpfen.", "Isolez une corde de A4 avec des coins."),
        "sampling.a4.pitch" to arrayOf("Choose the pitch with − and +, or keep the one shown.", "Die Tonhöhe mit − und + wählen oder die angezeigte behalten.", "Choisissez le diapason avec − et +, ou gardez celui affiché."),
        "sampling.a4.tune" to arrayOf("Strike the string and tune it until the strobe stands still.", "Die Saite anschlagen und stimmen, bis der Strobe stillsteht.", "Frappez la corde et accordez-la jusqu'à ce que le stroboscope s'arrête."),
        "sampling.a4.done" to arrayOf("Then Done. The sampling comes next.", "Dann Fertig. Danach wird das Klavier vermessen.", "Puis Terminé. La mesure des cordes suit."),
        "step.a4" to arrayOf("A4", "A4", "A4"),
        "step.sampling" to arrayOf("Sampling", "Vermessen", "Mesure"),
        "step.tuning" to arrayOf("Tuning", "Stimmen", "Accord"),
        "sampling.order.why" to arrayOf("The app learns the piano from these strings.", "Aus diesen Saiten lernt die App das Klavier kennen.", "L'app apprend le piano à partir de ces cordes."),
        "sampling.line" to arrayOf("Single strings, the keys you choose, {0} of them: Accept keeps a string; a key struck again replaces its sample.", "Einzelne Saiten, Tasten nach Ihrer Wahl, {0} davon: Übernehmen behält einen Messpunkt; eine erneut angeschlagene Taste ersetzt ihren Messpunkt.", "Cordes seules, les touches de votre choix, {0} : Accepter garde une corde ; une touche frappée à nouveau remplace sa mesure."),
        "sampling.lineSet" to arrayOf("Single strings, the {0} keys below: Accept keeps a string; a key struck again replaces its sample.", "Einzelne Saiten, die {0} Tasten unten: Übernehmen behält einen Messpunkt; eine erneut angeschlagene Taste ersetzt ihren Messpunkt.", "Cordes seules, les {0} touches ci-dessous : Accepter garde une corde ; une touche frappée à nouveau remplace sa mesure."),
        "sampling.strikeAny" to arrayOf("strike a key: one string, one wedge", "Taste anschlagen: eine Saite, ein Keil", "frappez une touche : une corde, un coin"),
        "sampling.caught" to arrayOf("caught — Accept keeps it", "erkannt — Übernehmen behält sie", "saisie — Accepter la garde"),
        "sampling.kept" to arrayOf("kept", "behalten", "gardée"),
        "sampling.measuring" to arrayOf("measuring …", "misst …", "mesure …"),
        "sampling.measured" to arrayOf("measured", "gemessen", "mesurée"),
        "sampling.short" to arrayOf("too short: strike again", "zu kurz: noch einmal anschlagen", "trop court : frappez à nouveau"),
        "app.accept" to arrayOf("Accept", "Übernehmen", "Accepter"),
        "sampling.count" to arrayOf("{0} of {1}", "{0} von {1}", "{0} sur {1}"),
        "state.matches" to arrayOf("matches", "stimmt", "juste"),
        "state.sharp" to arrayOf("sharp", "zu hoch", "trop haut"),
        "state.flat" to arrayOf("flat", "zu tief", "trop bas"),
        "state.near" to arrayOf("near", "nah", "proche"),
        "state.complete" to arrayOf("complete", "fertig", "terminé"),
        "state.heardOther" to arrayOf("that's {0}", "das ist {0}", "c'est {0}"),
        "state.off" to arrayOf("{0} c {1}", "{0} c {1}", "{0} c {1}"),
        "origin.reference" to arrayOf("the reference, set when sampling", "die Referenz, beim Vermessen gesetzt", "la référence, fixée à la mesure"),
        "origin.temperament" to arrayOf("equal temperament on A4 {0}", "gleichstufig auf A4 {0}", "tempérament égal sur A4 {0}"),
        "origin.curve" to arrayOf("{0} octave to {1} on the curve", "{0}-Oktave zu {1} nach der Kurve", "octave {0} vers {1} sur la courbe"),
        "origin.wide" to arrayOf(", {0} c wide", ", {0} c weit", ", {0} c large"),
        "origin.narrow" to arrayOf(", {0} c narrow", ", {0} c eng", ", {0} c étroite"),
        "origin.beatless" to arrayOf(", beatless", ", schwebungsfrei", ", sans battement"),
        "origin.check" to arrayOf(" · {0} stands {1} c {2}", " · {0} steht {1} c {2}", " · {0} est à {1} c {2}"),
        "origin.wideWord" to arrayOf("wide", "weit", "large"),
        "origin.narrowWord" to arrayOf("narrow", "eng", "étroite"),
        "origin.octave" to arrayOf("{0} octave to {1}", "{0}-Oktave zu {1}", "octave {0} vers {1}"),
        "origin.untuned" to arrayOf("equal temperament — {0} not tuned yet", "gleichstufig — {0} noch nicht gestimmt", "tempérament égal — {0} pas encore accordée"),
        "settings.tab.tuning" to arrayOf("Tuning", "Stimmen", "Accord"),
        "settings.tab.stretch" to arrayOf("Stretch", "Spreizung", "Étirement"),
        "settings.tab.piano" to arrayOf("Piano", "Klavier", "Piano"),
        "settings.tab.help" to arrayOf("Help", "Hilfe", "Aide"),
        "settings.tab.app" to arrayOf("App", "App", "App"),
        "settings.page.temperament" to arrayOf("Temperament", "Temperatur", "Tempérament"),
        "settings.page.reading" to arrayOf("Strobe", "Stroboskop", "Stroboscope"),
        "settings.page.bass" to arrayOf("Bass", "Bass", "Basses"),
        "settings.page.middle" to arrayOf("Midrange", "Mittellage", "Médium"),
        "settings.page.treble" to arrayOf("Treble", "Diskant", "Aigus"),
        "settings.page.range" to arrayOf("Range", "Tonumfang", "Étendue"),
        "settings.page.language" to arrayOf("Language", "Sprache", "Langue"),
        "settings.page.probe" to arrayOf("Probe", "Sonde", "Sonde"),
        "settings.page.quickstart" to arrayOf("Quick start", "Schnellstart", "Démarrage rapide"),
        "settings.page.about" to arrayOf("About", "Über", "À propos"),
        "settings.page.display" to arrayOf("Phone bars", "Systemleisten", "Barres du téléphone"),
        "settings.navigationBar" to arrayOf("Navigation bar", "Navigationsleiste", "Barre de navigation"),
        "settings.statusBar" to arrayOf("Status bar", "Statusleiste", "Barre d'état"),
        "settings.note.bars" to arrayOf("The phone’s status and navigation bars, on every screen. Off, the screens have their room; a swipe from the edge shows the bars for a moment.", "Status- und Navigationsleiste des Telefons, auf jeder Seite. Aus, haben die Seiten ihren Platz; ein Wisch vom Rand zeigt die Leisten kurz.", "Les barres d'état et de navigation du téléphone, sur chaque écran. Désactivées, les écrans ont toute la place ; un glissement depuis le bord les montre un instant."),
        "settings.language" to arrayOf("Language", "Sprache", "Langue"),
        "settings.language.system" to arrayOf("System", "System", "Système"),
        "hub.continue.verb" to arrayOf("Continue", "Stimmung", "Continuer"),
        "hub.continue.rest" to arrayOf(" Tuning", " fortsetzen", " l'accord"),
        "hub.continue.note" to arrayOf("from {0}, A4 {1}", "vom {0}, A4 {1}", "du {0}, A4 {1}"),
        "hub.new.verb" to arrayOf("New", "Neue", "Nouvel"),
        "hub.new.rest" to arrayOf(" Tuning", " Stimmung", " accord"),
        "hub.new.note" to arrayOf("starting from A4", "beginnt mit A4", "en partant de A4"),
        "hub.new.rejected" to arrayOf("the saved tuning could not be verified", "die gespeicherte Stimmung ließ sich nicht prüfen", "l'accord enregistré n'a pas pu être vérifié"),
        "hub.new.ask" to arrayOf("Start a new tuning?", "Neue Stimmung beginnen?", "Commencer un nouvel accord ?"),
        "hub.new.askNote" to arrayOf("The tuning from {0}, A4 {1}, can then no longer be continued.", "Die Stimmung vom {0}, A4 {1}, lässt sich dann nicht mehr fortsetzen.", "L'accord du {0}, A4 {1}, ne pourra alors plus être continué."),
        "hub.new.askYes" to arrayOf("Start new", "Neu beginnen", "Commencer"),
        "hub.dateFormat" to arrayOf("d MMM", "d. MMM", "d MMM"),
        "hub.fundamentalA4" to arrayOf("Fundamental A4", "Grundton A4", "Fondamentale A4"),
        "hub.fullSpectrum" to arrayOf("Full Spectrum", "Alle Teiltöne", "Spectre complet"),
        "sampling.a4Hint" to arrayOf("Tap − or + to adjust. You can also slide left and right here.", "Tippen Sie − oder + zum Anpassen. Sie können hier auch nach links und rechts wischen.", "Touchez − ou + pour ajuster. Vous pouvez aussi glisser à gauche et à droite ici."),
        "sampling.a4Strobe" to arrayOf("Tune until the stripes stand still.", "Stimmen, bis die Streifen stillstehen.", "Accordez jusqu'à ce que les bandes s'arrêtent."),
        "sampling.temperament" to arrayOf("Temperament", "Temperatur", "Tempérament"),
        "sampling.temperamentStart" to arrayOf("Start", "Anfang", "Début"),
        "sampling.temperamentEnd" to arrayOf("End", "Ende", "Fin"),
        "sampling.caughtLabel" to arrayOf("Caught", "Erkannt", "Saisie"),
        "sampling.strikeKey" to arrayOf("strike a key", "Taste anschlagen", "frappez une touche"),
        "sampling.sampledLabel" to arrayOf("Sampled", "Messpunkte", "Mesurées"),
        "sampling.atLeast" to arrayOf("{0} · at least {1}", "{0} · mindestens {1}", "{0} · au moins {1}"),
        "sampling.next" to arrayOf("Next: {0}", "Als Nächstes: {0}", "Ensuite : {0}"),
        "sampling.order.strings" to arrayOf("Strike single strings, the others muted. Any keys up to G6, any order.", "Einzelne Saiten anschlagen, die anderen abdämpfen. Tasten bis G6, Reihenfolge beliebig.", "Frappez des cordes seules, les autres étouffées. N'importe quelles touches jusqu'à G6, dans n'importe quel ordre."),
        "sampling.order.count" to arrayOf("{0} notes are the minimum, C1 and one key from G#5 to G6 among them; 10–15 spread over the range give the best accuracy.", "{0} Töne sind das Minimum, darunter C1 und eine Taste von G#5 bis G6; 10–15 über den Tonumfang verteilt ergeben die beste Genauigkeit.", "{0} notes au minimum, dont C1 et une touche de G#5 à G6 ; 10 à 15 réparties sur l'étendue donnent la meilleure précision."),
        "sampling.order.accept" to arrayOf("Let each string ring until measured, then Accept.", "Klingen lassen bis „gemessen“, dann Übernehmen.", "Laissez chaque corde sonner jusqu'à ce qu'elle soit mesurée, puis Accepter."),
        "app.cancel" to arrayOf("Cancel", "Abbrechen", "Annuler"),
        "tuning.partialsUsed" to arrayOf("partials {0}", "Teiltöne {0}", "partiels {0}"),
        "tuning.partialOne" to arrayOf("partial {0}", "Teilton {0}", "partiel {0}"),
        "tuning.info" to arrayOf("A4 = {0} · equal temperament {1} · octaves {2}", "A4 = {0} · gleichstufig {1} · Oktaven {2}", "A4 = {0} · tempérament égal {1} · octaves {2}"),
        "tuning.infoTitle" to arrayOf("This tuning", "Diese Stimmung", "Cet accord"),
        "tuning.infoOrders" to arrayOf("These settings hold for this tuning. Change what you want, then Done.", "Das gilt für diese Stimmung. Nach Wunsch ändern, dann Fertig.", "Ces réglages valent pour cet accord. Changez ce que vous voulez, puis Terminé."),
        "tuning.suggestShort" to arrayOf("Suggested for the bass: {0}", "Vorschlag für den Bass: {0}", "Suggéré pour les basses : {0}"),
        "tuning.suggest" to arrayOf("Measured on your sampled strings: with {0} in the bass, no beat between two of them an octave apart is faster than {1} Hz ({2}). Every partial within {3} dB of its own string's loudest counts. This is a suggestion, not a setting: the octave type stays your choice, and an ear that prefers another is right to choose it. The six points a new tuning starts from are a suggestion as well: they come from elsewhere and usually work well. On larger pianos experience favours 10:5 in the bass.", "Gemessen an Ihren Saiten: Mit {0} im Bass schwebt zwischen zwei gemessenen Saiten im Oktavabstand nichts schneller als {1} Hz ({2}). Es zählt jeder Teilton bis {3} dB unter dem lautesten seiner Saite. Das ist ein Vorschlag, keine Einstellung: Der Oktavtyp bleibt Ihre Wahl, und ein Ohr, das einen anderen vorzieht, wählt zu Recht diesen. Auch die sechs Punkte, mit denen eine neue Stimmung beginnt, sind ein Vorschlag: Sie stammen aus anderer Quelle und bewähren sich meist. Auf größeren Klavieren spricht die Erfahrung für 10:5 im Bass.", "Mesuré sur vos cordes : avec {0} dans les basses, aucun battement entre deux d'entre elles à l'octave n'est plus rapide que {1} Hz ({2}). Chaque partiel à moins de {3} dB du plus fort de sa corde compte. C'est une suggestion, pas un réglage : le type d'octave reste votre choix, et une oreille qui en préfère un autre a raison de le choisir. Les six points d'où part un nouvel accord sont aussi une suggestion : ils viennent d'ailleurs et conviennent généralement bien. Sur les grands pianos, l'expérience préfère 10:5 dans les basses."),
        "tuning.suggestUse" to arrayOf("Use {0}", "{0} verwenden", "Prendre {0}"),
        "tuning.suggestNoneShort" to arrayOf("No suggestion for the bass yet", "Noch kein Vorschlag für den Bass", "Pas encore de suggestion pour les basses"),
        "tuning.suggestNone" to arrayOf("Sample two bass keys an octave apart, A1 and A2 say, and the app suggests the bass's octave type from them. Until then the six points stand, a suggestion that usually works well; on larger pianos experience favours 10:5 in the bass.", "Messen Sie zwei Basstasten im Oktavabstand, etwa A1 und A2, dann schlägt die App daraus den Oktavtyp für den Bass vor. Bis dahin gelten die sechs Punkte, ein Vorschlag, der sich meist bewährt; auf größeren Klavieren spricht die Erfahrung für 10:5 im Bass.", "Mesurez deux touches graves à l'octave, A1 et A2 par exemple, et l'app suggère le type d'octave des basses. D'ici là, les six points restent, une suggestion qui convient généralement bien ; sur les grands pianos, l'expérience préfère 10:5 dans les basses."),
        "tuning.partialsNot" to arrayOf(" · not {0}", " · ohne {0}", " · sans {0}"),
        "tuning.follows" to arrayOf("follows the key · ", "folgt der Taste · ", "suit la touche · "),
        "tuning.leavingKeeps" to arrayOf("leaving a note keeps it", "Weitergehen speichert den Ton", "quitter une note la garde"),
        "tuning.temperamentDone" to arrayOf("A3–A4 done · leaving a note keeps it", "A3–A4 fertig · Weitergehen speichert den Ton", "A3–A4 fait · quitter une note la garde"),
        "tuning.temperamentFirst" to arrayOf("A3–A4 first · Done keeps a note", "zuerst A3–A4 · Fertig speichert einen Ton", "A3–A4 d'abord · Terminé garde une note"),
        "tuning.target" to arrayOf("target {0}", "Ziel {0}", "cible {0}"),
        "tuning.targetPartial" to arrayOf(", partial {0}", ", Teilton {0}", ", partiel {0}"),
        "tuning.fundamental" to arrayOf("fundamental", "Grundton", "fondamentale"),
        "progress.caption" to arrayOf("measured — the piano as tuned, in cents from equal temperament", "gemessen — das Klavier wie gestimmt, in Cent gegen gleichstufig", "mesuré — le piano tel qu'accordé, en cents par rapport au tempérament égal"),
        "display.tooSmall" to arrayOf("This display is too small for Clavier Tuner ({0} dp; needs at least {1}).", "Dieser Bildschirm ist zu klein für Clavier Tuner ({0} dp; nötig sind mindestens {1}).", "Cet écran est trop petit pour Clavier Tuner ({0} dp ; il faut au moins {1})."),
        "about.version" to arrayOf("Version", "Version", "Version"),
        "about.microphone" to arrayOf("Microphone", "Mikrofon", "Microphone"),
        "about.mic.unprocessed" to arrayOf("unprocessed", "unbearbeitet", "non traité"),
        "about.mic.undeclared" to arrayOf("unprocessed, not guaranteed by this phone", "unbearbeitet, vom Telefon nicht zugesichert", "non traité, sans garantie de ce téléphone"),
        "about.mic.processed" to arrayOf("processed by the phone: readings can suffer", "vom Telefon bearbeitet: Messungen können leiden", "traité par le téléphone : les mesures peuvent en souffrir"),
        "about.mic.notYet" to arrayOf("opens with the tuning screen", "öffnet sich beim Stimmen", "s'ouvre avec l'écran d'accord"),
        "about.core" to arrayOf("Core", "Kern", "Noyau"),
        "about.typeface" to arrayOf("Typeface", "Schrift", "Police"),
        "about.typefaceValue" to arrayOf("DejaVu Serif · Bitstream Vera licence", "DejaVu Serif · Bitstream-Vera-Lizenz", "DejaVu Serif · licence Bitstream Vera"),
        "about.icon" to arrayOf("Settings icon", "Einstellungssymbol", "Icône des réglages"),
        "about.licence" to arrayOf("Licence", "Lizenz", "Licence"),
        "about.vienna" to arrayOf("Vienna", "Wien", "Vienne"),
        "settings.temperament" to arrayOf("Temperament", "Temperatur", "Tempérament"),
        "settings.temperamentOctave" to arrayOf("Temperament octave", "Temperaturoktave", "Partition"),
        "settings.order" to arrayOf("Tuning order", "Stimmreihenfolge", "Ordre d'accord"),
        "settings.temperamentFirst" to arrayOf("Temperament first", "Temperatur zuerst", "La partition d'abord"),
        "settings.screen" to arrayOf("Note switching", "Tonwechsel", "Changement de note"),
        "settings.followKey" to arrayOf("Auto note switching", "Automatischer Tonwechsel", "Changement de note automatique"),
        "settings.cleanTogether" to arrayOf("Average the clean partials", "Saubere Teiltöne mitteln", "Moyenne des partiels propres"),
        "tuning.strobeAll" to arrayOf("All partials", "Alle Teiltöne", "Tous les partiels"),
        "tuning.strobeAll.note" to arrayOf("A band for every partial.", "Ein Band für jeden Teilton.", "Une bande pour chaque partiel."),
        "tuning.pinPace" to arrayOf("Strobe at the pin's pace", "Strobe im Tempo des Stimmwirbels", "Stroboscope au rythme de la cheville"),
        "tuning.strobeLimit.note" to arrayOf("lowest partials", "tiefste Teiltöne", "partiels graves"),
        "tuning.strobeOne.note" to arrayOf("One band, moved by the clean partials together.", "Ein Band, bewegt von allen sauberen Teiltönen.", "Une bande pour tous les partiels propres."),
        "tuning.strobeLimit" to arrayOf("Limit to", "Nur", "Limiter à"),
        "tuning.strobeOne" to arrayOf("One strobe", "Ein Strobe", "Un stroboscope"),
        "settings.strobeTogether" to arrayOf("Strobes appear together", "Strobes erscheinen gemeinsam", "Les stroboscopes apparaissent ensemble"),
        "settings.note.strobeTogether" to arrayOf("On: the row comes up at once, when every strobe has been read, at most 0.8 s after the first. Off: each strobe comes up as soon as its partial is read. Either way a strobe fades in over 0.3 s.", "Ein: die Reihe erscheint auf einmal, sobald jeder Strobe gelesen ist, höchstens 0,8 s nach dem ersten. Aus: jeder Strobe erscheint, sobald sein Teilton gelesen ist. In beiden Fällen blendet ein Strobe über 0,3 s ein.", "Activé : la rangée apparaît d'un coup, quand chaque stroboscope a été lu, au plus 0,8 s après le premier. Désactivé : chaque stroboscope apparaît dès que son partiel est lu. Dans les deux cas un stroboscope apparaît en fondu sur 0,3 s."),
        "settings.strobeSmoothing" to arrayOf("Strobe smoothing", "Strobe-Glättung", "Lissage du stroboscope"),
        "settings.strobeSmoothingWhole" to arrayOf("whole", "ganz", "entier"),
        "settings.note.strobeSmoothing" to arrayOf("Every strobe but the one being tuned stands where the note as played puts it: at the tuned strobe's target times the ratio between the two partials as read. Smoothing is how far back that ratio is averaged. Whole: averaged over the whole strike, every wobble and false beat shows. Shorter: the strobes follow the tuned one more closely.", "Jeder Strobe außer dem gestimmten steht dort, wo der Anschlag ihn hinstellt: am Ziel des gestimmten Strobes mal dem Verhältnis der beiden Teiltöne, wie gelesen. Die Glättung ist, wie weit dieses Verhältnis zurück gemittelt wird. Ganz: über den ganzen Anschlag gemittelt, jede Schwebung der Saite in sich zeigt sich. Kürzer: die Strobes folgen dem gestimmten enger.", "Chaque stroboscope sauf celui qu'on accorde se place là où la note jouée le met : à la cible du stroboscope accordé fois le rapport entre les deux partiels tels que lus. Le lissage est la durée sur laquelle ce rapport est moyenné. Entier : moyenné sur toute la frappe, chaque oscillation et chaque faux battement se voit. Plus court : les stroboscopes suivent de plus près celui qu'on accorde."),
        "settings.highestPartial" to arrayOf("Partials up to", "Teiltöne bis", "Partiels jusqu'à"),
        "settings.reading" to arrayOf("Strobe", "Stroboskop", "Stroboscope"),
        "settings.readingMemory" to arrayOf("Averaging time", "Mittelungszeit", "Durée de moyenne"),
        "settings.matchWindow" to arrayOf("Tolerance", "Toleranz", "Tolérance"),
        "settings.theLowestKey" to arrayOf("the lowest key", "der tiefsten Taste", "la touche la plus grave"),
        "settings.octave" to arrayOf("The octave", "Oktave", "L'octave"),
        "settings.octaveType" to arrayOf("Octave type", "Oktavtyp", "Type d'octave"),
        "settings.octaveWidth" to arrayOf("Extra stretch", "Zusätzliche Spreizung", "Étirement ajouté"),
        "settings.beatless" to arrayOf("none (pure)", "keine (rein)", "aucun (pure)"),
        "settings.cWide" to arrayOf("+{0} c", "+{0} c", "+{0} c"),
        "settings.cNarrow" to arrayOf("−{0} c", "−{0} c", "−{0} c"),
        "settings.sixPoints" to arrayOf("Six-point stretch", "Spreizung mit sechs Punkten", "Étirement en six points"),
        "settings.page.stretchDefault" to arrayOf("Default", "Voreinstellung", "Par défaut"),
        "settings.page.sixPoints" to arrayOf("Six points", "Sechs Punkte", "Six points"),
        "settings.stretchDefault" to arrayOf("Stretch for new tunings", "Spreizung neuer Stimmungen", "Étirement des nouveaux accords"),
        "settings.stretchPoints" to arrayOf("Set at", "Festgelegt an", "Fixé en"),
        "settings.stretchThree" to arrayOf("Three points", "Drei Punkten", "Trois points"),
        "settings.stretchSix" to arrayOf("Six points", "Sechs Punkten", "Six points"),
        "settings.stretchBoth" to arrayOf("Either way the curve is worked out from what you measure. A tuning under way keeps its own: tap its line at the top of the tuning screen.", "In beiden Fällen wird die Kurve aus dem Gemessenen berechnet. Eine laufende Stimmung behält ihre eigene: Tippen Sie auf ihre Zeile oben im Stimmbildschirm.", "Dans les deux cas, la courbe est calculée à partir de ce que vous mesurez. Un accord en cours garde le sien : touchez sa ligne en haut de l'écran d'accord."),
        "settings.stretchThreeNote" to arrayOf("Three points: an octave type at the lowest key, in the temperament octave and at C8. Every octave between moves key by key from one to the next.", "Drei Punkte: je ein Oktavtyp an der tiefsten Taste, in der Temperaturoktave und bei C8. Jede Oktave dazwischen geht Taste für Taste vom einen zum nächsten über.", "Trois points : un type d'octave à la touche la plus grave, dans la partition et à C8. Chaque octave entre les deux passe touche par touche de l'un à l'autre."),
        "settings.stretchSixNote" to arrayOf("Six points: three more places, A2, F5 and A6, each with its own octave type and extra stretch. Upward the octaves go from 4:2 to 2:1 at F5 and 4:1 at A6. A little extra stretch throughout, the most at the lowest key, none at C8.", "Sechs Punkte: drei weitere Stellen, A2, F5 und A6, jede mit eigenem Oktavtyp und eigener zusätzlicher Spreizung. Nach oben gehen die Oktaven von 4:2 zu 2:1 bei F5 und 4:1 bei A6. Überall etwas zusätzliche Spreizung, am meisten an der tiefsten Taste, bei C8 keine.", "Six points : trois places de plus, A2, F5 et A6, chacune avec son type d'octave et son étirement supplémentaire. En montant, les octaves passent de 4:2 à 2:1 à F5 et 4:1 à A6. Un peu d'étirement supplémentaire partout, le plus à la touche la plus grave, aucun à C8."),
        "settings.note.sixPoints" to arrayOf("Three more places besides the ends: A2 on 6:3, F5 on 2:1, A6 on 4:1. Every octave moves key by key from one place to the next. On, it sets the octave types and extra stretch; off, they return to their defaults.", "Drei weitere Stellen neben den Enden: A2 auf 6:3, F5 auf 2:1, A6 auf 4:1. Jede Oktave geht Taste für Taste von einer Stelle zur nächsten über. Ein setzt es die Oktavtypen und die zusätzliche Spreizung, aus gehen sie auf ihre Vorgaben zurück.", "Trois places de plus que les extrémités : A2 en 6:3, F5 en 2:1, A6 en 4:1. Chaque octave passe touche par touche d'une place à la suivante. Activé, il fixe les types d'octave et l'étirement supplémentaire ; désactivé, ils reviennent à leurs valeurs par défaut."),
        "settings.farEnd" to arrayOf("Toward the end of the range", "Zum Ende des Bereichs", "Vers l'extrémité de l'étendue"),
        "settings.atFar" to arrayOf("At {0}", "Bei {0}", "À {0}"),
        "settings.same" to arrayOf("unchanged", "unverändert", "inchangé"),
        "settings.piano" to arrayOf("The piano", "Das Klavier", "Le piano"),
        "settings.lowestKey" to arrayOf("Lowest key", "Tiefste Taste", "Touche la plus grave"),
        "settings.lowestUnwound" to arrayOf("Lowest unwound string", "Tiefste Blanksaite", "Première corde non filée"),
        "settings.note.bass" to arrayOf("Its octave type is beatless at the lowest key. From the temperament octave down, every octave moves key by key from the middle’s type to this one, and the extra stretch with it, in cents: the lower note flatter, the upper sharper. These are the defaults for new tunings: a tuning under way keeps its own, changed by tapping its line at the top of the tuning screen.", "Sein Oktavtyp ist an der tiefsten Taste schwebungsfrei. Von der Temperaturoktave abwärts geht jede Oktave Taste für Taste vom Typ der Mittellage zu diesem über, und die zusätzliche Spreizung mit ihr, in Cent: der untere Ton tiefer, der obere höher. Das sind die Vorgaben für neue Stimmungen: eine laufende Stimmung behält ihre eigenen, die ein Tippen auf ihre Zeile oben beim Stimmen ändert.", "Son type d'octave est sans battement à la touche la plus grave. Depuis la partition vers le bas, chaque octave passe touche par touche du type du médium à celui-ci, et l'étirement supplémentaire avec lui, en cents : la note basse plus basse, la haute plus haute. Ce sont les valeurs par défaut des nouveaux accords : un accord en cours garde les siennes, changées en touchant sa ligne en haut de l'écran d'accord."),
        "settings.note.middle" to arrayOf("Beatless next to the temperament octave and within it: the octave below A4 is one of this type, divided equally. The extra stretch stands on that octave too, and below zero makes it narrower than beatless. The bass and the treble start from here. These are the defaults for new tunings: a tuning under way keeps its own, changed by tapping its line at the top of the tuning screen.", "Schwebungsfrei neben der Temperaturoktave und in ihr: die Oktave unter A4 ist eine dieses Typs, gleich geteilt. Die zusätzliche Spreizung gilt auch für diese Oktave, und unter null macht sie sie enger als schwebungsfrei. Bass und Diskant gehen von hier aus. Das sind die Vorgaben für neue Stimmungen: eine laufende Stimmung behält ihre eigenen, die ein Tippen auf ihre Zeile oben beim Stimmen ändert.", "Sans battement près de la partition et à l'intérieur : l'octave sous A4 est de ce type, divisée également. L'étirement supplémentaire vaut aussi pour cette octave, et sous zéro la rend plus étroite que sans battement. Les basses et les aigus partent d'ici. Ce sont les valeurs par défaut des nouveaux accords : un accord en cours garde les siennes, changées en touchant sa ligne en haut de l'écran d'accord."),
        "settings.note.treble" to arrayOf("Its octave type is beatless at C8. Above A4, every octave moves key by key from the middle’s type to this one. The extra stretch at C8 is where its slide ends. These are the defaults for new tunings: a tuning under way keeps its own, changed by tapping its line at the top of the tuning screen.", "Sein Oktavtyp ist bei C8 schwebungsfrei. Über A4 geht jede Oktave Taste für Taste vom Typ der Mittellage zu diesem über. Die zusätzliche Spreizung bei C8 ist das Ende ihres Gleitens. Das sind die Vorgaben für neue Stimmungen: eine laufende Stimmung behält ihre eigenen, die ein Tippen auf ihre Zeile oben beim Stimmen ändert.", "Son type d'octave est sans battement à C8. Au-dessus de A4, chaque octave passe touche par touche du type du médium à celui-ci. L'étirement supplémentaire à C8 est là où son glissement s'arrête. Ce sont les valeurs par défaut des nouveaux accords : un accord en cours garde les siennes, changées en touchant sa ligne en haut de l'écran d'accord."),
        "settings.note.temperament" to arrayOf("Tuned first, downward from A4, to equal temperament on it; every other note is tuned by octaves from here.", "Wird zuerst gestimmt, von A4 abwärts, gleichstufig darauf; jeder andere Ton wird von hier aus in Oktaven gestimmt.", "Accordé d'abord, de A4 vers le bas, en tempérament égal sur A4 ; toutes les autres notes s'accordent par octaves à partir d'ici."),
        "settings.note.reading" to arrayOf("The tolerance is yours, not the measurement’s: the strobe reads a string to a tenth of a cent. Inside the tolerance and still, the note is kept.", "Die Toleranz ist Ihre, nicht die der Messung: das Stroboskop liest eine Saite auf ein Zehntel Cent. Steht es innerhalb der Toleranz still, ist der Ton gespeichert.", "La tolérance est la vôtre, pas celle de la mesure : le stroboscope lit une corde au dixième de cent. Dans la tolérance et immobile, la note est gardée."),
        "settings.note.range" to arrayOf("The range is the 88 keys, A0 to C8.", "Der Tonumfang sind die 88 Tasten, A0 bis C8.", "L'étendue est de 88 touches, de A0 à C8."),
        "settings.note.unwound" to arrayOf("The first plain string. The tuning curve does not depend on it: until the measured strings have drawn the inharmonicity curve, a note not yet measured borrows from the nearest measured string of its own kind of wire.", "Die erste Blanksaite. Die Stimmkurve hängt nicht von ihr ab: bis die gemessenen Saiten die Inharmonizitätskurve gezogen haben, borgt ein noch nicht gemessener Ton von der nächsten gemessenen Saite gleicher Art.", "La première corde non filée. La courbe d'accord n'en dépend pas : tant que les cordes mesurées n'ont pas tracé la courbe d'inharmonicité, une note pas encore mesurée emprunte à la corde mesurée la plus proche du même genre de fil."),
        "settings.note.about" to arrayOf("Free to use, study, change and share, with attribution and no warranty — the full text is LICENSE in the repository.", "Frei zu nutzen, zu studieren, zu ändern und weiterzugeben, mit Namensnennung und ohne Gewähr — der volle Text steht als LICENSE im Repository.", "Libre d'utiliser, d'étudier, de modifier et de partager, avec attribution et sans garantie — le texte complet est LICENSE dans le dépôt."),
        "help.index" to arrayOf("Topics", "Themen", "Sujets"),
        "help.qs.a4.s2" to arrayOf("Mute A4 to one string. Choose its pitch with − and +, a tenth of a hertz at a time (hold to run on), or slide a finger left or right across them. To keep the pitch shown, tap Done.", "A4 auf eine Saite abdämpfen. Die Tonhöhe mit − und + wählen, ein Zehntel Hertz je Schritt (gedrückt halten läuft weiter), oder mit dem Finger darüber nach links oder rechts wischen. Um die angezeigte Tonhöhe zu behalten, Fertig tippen.", "Isolez une corde de A4 avec des coins. Choisissez son diapason avec − et +, un dixième de hertz à la fois (maintenir pour défiler), ou glissez un doigt à gauche ou à droite dessus. Pour garder le diapason affiché, touchez Terminé."),
        "help.qs.a4.s3" to arrayOf("Tune the string until the strobe stands still, then Done.", "Die Saite stimmen, bis das Stroboskop steht, dann Fertig.", "Accordez la corde jusqu'à ce que le stroboscope s'arrête, puis Terminé."),
        "help.qs.a4.s5" to arrayOf("Pitch raises are out of scope: a piano far from pitch needs one first.", "Vorstimmen ist nicht vorgesehen: ein Klavier weit weg von der Stimmhöhe braucht zuerst eines.", "Les mises au diapason ne sont pas prévues : un piano loin du diapason en a besoin d'abord."),
        "help.qs.sampling.s1" to arrayOf("Strike single strings, the others muted. The app catches the key by itself: let the string ring until it says measured, then Accept. Cancel drops what the strike caught, its sample too.", "Einzelne Saiten anschlagen, die anderen abgedämpft. Die App erkennt die Taste von selbst: die Saite klingen lassen, bis „gemessen“ dasteht, dann Übernehmen. Abbrechen verwirft, was der Anschlag erkannt hat, auch seinen Messpunkt.", "Frappez des cordes seules, les autres étouffées. L'app reconnaît la touche d'elle-même : laissez la corde sonner jusqu'à ce qu'elle dise mesurée, puis Accepter. Annuler écarte ce que la frappe a saisi, sa mesure aussi."),
        "help.qs.sampling.s2" to arrayOf("Spread the samples over the whole keyboard, in any order.", "Verteilen Sie die Messpunkte über die ganze Tastatur, in beliebiger Reihenfolge.", "Répartissez les mesures sur tout le clavier, dans n'importe quel ordre."),
        "help.qs.sampling.s3" to arrayOf("Eight samples are the minimum, C1 and one key from G#5 to G6 among them; Done waits for both. They matter more than the count: without a key that low the curve guesses the bass from the middle and can miss it by hundreds of cents, and eight keys with C1 and one from G#5 to G6 do better than fifteen without. Any other key of the lowest octave counts for C1. Ten to fifteen give the better result.", "Acht Messpunkte sind das Minimum, darunter C1 und eine Taste von G#5 bis G6; Fertig wartet auf beide. Sie zählen mehr als die Anzahl: ohne eine so tiefe Taste rät die Kurve den Bass aus der Mitte und kann ihn um Hunderte Cent verfehlen, und acht Tasten mit C1 und einer von G#5 bis G6 sind besser als fünfzehn ohne. Jede andere Taste der untersten Oktave zählt statt C1. Zehn bis fünfzehn geben das bessere Ergebnis.", "Huit mesures sont le minimum, dont C1 et une touche de G#5 à G6 ; Terminé attend les deux. Elles comptent plus que le nombre : sans touche aussi grave, la courbe devine les basses depuis le médium et peut se tromper de centaines de cents, et huit touches avec C1 et une de G#5 à G6 font mieux que quinze sans. Toute autre touche de l'octave la plus grave compte pour C1. Dix à quinze donnent le meilleur résultat."),
        "help.qs.octaves.s2" to arrayOf("An octave type names the partials made beatless: 4:2 puts the 4th partial of the lower note on the 2nd of the upper. The higher the numbers, the wider the octave. 4:1 is the double octave.", "Ein Oktavtyp nennt die Teiltöne, die schwebungsfrei werden: 4:2 legt den 4. Teilton des unteren Tons auf den 2. des oberen. Je höher die Zahlen, desto weiter die Oktave. 4:1 ist die Doppeloktave.", "Un type d'octave nomme les partiels rendus sans battement : 4:2 met le 4e partiel de la note basse sur le 2e de la haute. Plus les nombres sont grands, plus l'octave est large. 4:1 est la double octave."),
        "help.qs.octaves.s4" to arrayOf("The extra stretch, in cents, widens the octave beyond its type: the lower note flatter, the upper sharper.", "Die zusätzliche Spreizung, in Cent, macht die Oktave weiter als ihr Typ: der untere Ton tiefer, der obere höher.", "L'étirement supplémentaire, en cents, élargit l'octave au-delà de son type : la note basse plus basse, la haute plus haute."),
        "help.qs.tuning.s3" to arrayOf("Under the strobe, the string's partials: tap one to take it out of the reading, tap it again to bring it back.", "Unter dem Stroboskop die Teiltöne der Saite: ein Tippen nimmt einen aus der Messung, ein zweites holt ihn zurück.", "Sous le stroboscope, les partiels de la corde : touchez-en un pour le retirer de la lecture, touchez-le à nouveau pour le remettre."),
        "help.qs.tuning.s5" to arrayOf("Unisons are tuned by ear.", "Chöre werden nach Gehör gestimmt.", "Les unissons s'accordent à l'oreille."),
        "help.qs.top.s1" to arrayOf("From C#6 up the strobe gives way to two bars. Strings there ring for about half a second: a display that follows every moment shows the strike's attack and its fading tail, and flutters, where the ear hears one pitch.", "Ab C#6 treten zwei Balken an die Stelle des Stroboskops. Die Saiten klingen dort nur etwa eine halbe Sekunde: eine Anzeige, die jedem Augenblick folgt, zeigt den Einschwingvorgang und das Ausklingen und flackert, wo das Ohr eine Tonhöhe hört.", "À partir de C#6, le stroboscope laisse place à deux barres. Les cordes y sonnent environ une demi-seconde : un affichage qui suit chaque instant montre l'attaque et la queue qui s'éteint, et papillote, là où l'oreille entend une seule hauteur."),
        "help.qs.top.s2" to arrayOf("So each strike gives one value: the string's pitch over its loud part, past the attack. It stays on the bar after the sound, dimmed, until the next strike. Under the bar: the cents against the target and, in brackets, the change from the strike before.", "Darum gibt jeder Anschlag einen Wert: die Tonhöhe der Saite über ihren lauten Teil, nach dem Einschwingen. Er bleibt nach dem Klang gedimmt am Balken stehen, bis zum nächsten Anschlag. Unter dem Balken: die Cent gegen das Ziel und in Klammern die Änderung zum Anschlag davor.", "Chaque frappe donne donc une valeur : la hauteur de la corde sur sa partie forte, après l'attaque. Elle reste sur la barre après le son, atténuée, jusqu'à la frappe suivante. Sous la barre : les cents par rapport à la cible et, entre parenthèses, le changement depuis la frappe précédente."),
        "help.qs.top.s3" to arrayOf("Above the line is sharp, below is flat. Strike, turn the pin, strike again: the bar moves by what the pin did. A strike cut short by the next one is drawn as an outline only, too brief to trust.", "Über der Linie ist zu hoch, darunter zu tief. Anschlagen, Wirbel drehen, wieder anschlagen: der Balken bewegt sich um das, was der Wirbel bewirkt hat. Ein Anschlag, den der nächste abschneidet, steht nur als Umriss da, zu kurz, um ihm zu trauen.", "Au-dessus de la ligne c'est trop haut, en dessous trop bas. Frappez, tournez la cheville, frappez à nouveau : la barre bouge de ce qu'a fait la cheville. Une frappe coupée par la suivante n'est tracée qu'en contour, trop brève pour s'y fier."),
        "help.qs.top.s4" to arrayOf("The left bar is the key an octave below. Once it is tuned, the bar shows it as tuned, white and fixed, and the key's target is set from it by the treble's octave type. Not yet tuned: strike it alone first, then the key.", "Der linke Balken ist die Taste eine Oktave tiefer. Ist sie gestimmt, zeigt der Balken sie wie gestimmt, weiß und fest, und das Ziel der Taste wird daraus mit dem Oktavtyp des Diskants gesetzt. Noch nicht gestimmt: zuerst sie allein anschlagen, dann die Taste.", "La barre de gauche est la touche une octave plus bas. Une fois accordée, la barre la montre telle qu'accordée, blanche et fixe, et la cible de la touche en découle par le type d'octave des aigus. Pas encore accordée : frappez-la seule d'abord, puis la touche."),
        "help.qs.top.s5" to arrayOf("One string each, the others muted. Settings › Tuning › Above C6 brings back the strobe, for every tuning.", "Je eine Saite, die anderen abgedämpft. Einstellungen › Stimmen › Über C6 bringt das Stroboskop zurück, für jede Stimmung.", "Une corde chacune, les autres étouffées. Réglages › Accord › Au-dessus de C6 ramène le stroboscope, pour chaque accord."),
        "table.target" to arrayOf("target", "Ziel", "cible"),
        "table.measured" to arrayOf("measured", "gemessen", "mesuré"),
        "table.cents" to arrayOf("cents", "Cent", "cents"),
        "octave.strike" to arrayOf("Strike {0} alone, then {1} alone: one string each", "Erst {0} allein anschlagen, dann {1} allein: je eine Saite", "Frappez {0} seule, puis {1} seule : une corde chacune"),
        "octave.tuned" to arrayOf("{0} as tuned. Strike {1}, one string", "{0} wie gestimmt. {1} anschlagen, eine Saite", "{0} telle qu'accordée. Frappez {1}, une corde"),
        "settings.page.topOctave" to arrayOf("Above C6", "Über C6", "Au-dessus de C6"),
        "settings.octaveBars" to arrayOf("Two bars, the octave below as reference", "Zwei Balken, die Oktave darunter als Bezug", "Deux barres, l'octave inférieure comme référence"),
        "settings.note.octaveBars" to arrayOf("An experiment. From C#6 up the strobe gives way to two bars: left, the key an octave below, as tuned once it is; right, the key itself, one value per strike, held until the next. The key's target follows the key below by the octave type set for the treble. Help › Quick start › Above C6 says more. Off: the strobe, as below C6.", "Ein Experiment. Ab C#6 treten zwei Balken an die Stelle des Stroboskops: links die Taste eine Oktave tiefer, wie gestimmt, sobald sie es ist; rechts die Taste selbst, ein Wert je Anschlag, gehalten bis zum nächsten. Das Ziel der Taste folgt der tieferen Taste mit dem Oktavtyp des Diskants. Mehr unter Hilfe › Schnellstart › Über C6. Aus: das Stroboskop wie unter C6.", "Une expérience. À partir de C#6, le stroboscope laisse place à deux barres : à gauche, la touche une octave plus bas, telle qu'accordée une fois qu'elle l'est ; à droite, la touche elle-même, une valeur par frappe, gardée jusqu'à la suivante. La cible de la touche suit la touche inférieure par le type d'octave des aigus. Aide › Démarrage rapide › Au-dessus de C6 en dit plus. Désactivé : le stroboscope, comme sous C6."),
        "edge.temperament" to arrayOf("Temperament", "Temperatur", "Tempérament"),
        "edge.octaves" to arrayOf("Octaves", "Oktaven", "Octaves"),
        "edge.strobe" to arrayOf("Strobe", "Strobe", "Strobe"),
        "edge.piano" to arrayOf("Piano", "Klavier", "Piano"),
        "edge.noPiano" to arrayOf("none", "keines", "aucun"),
        "a4.window" to arrayOf("Tune A4 until the strobe stands still, or set the pitch it should stand at. A new pitch moves every target, the keys already tuned with them.", "A4 stimmen, bis der Strobe steht, oder die Tonhöhe setzen, auf der es stehen soll. Eine neue Tonhöhe verschiebt jedes Ziel, auch das der schon gestimmten Tasten.", "Accordez A4 jusqu’à ce que le stroboscope s’arrête, ou réglez le diapason auquel il doit se tenir. Un nouveau diapason déplace toutes les cibles, celles des touches déjà accordées aussi."),
        "start.next" to arrayOf("Next", "Als Nächstes", "Ensuite"),
        "temperament.equal" to arrayOf("Equal", "Gleichstufig", "Égal"),
        "temperament.werckmeister3" to arrayOf("Werckmeister III", "Werckmeister III", "Werckmeister III"),
        "temperament.kirnberger3" to arrayOf("Kirnberger III", "Kirnberger III", "Kirnberger III"),
        "temperament.vallotti" to arrayOf("Vallotti", "Vallotti", "Vallotti"),
        "temperament.young2" to arrayOf("Young II", "Young II", "Young II"),
        "temperament.kellner" to arrayOf("Kellner", "Kellner", "Kellner"),
        "temperament.meantoneQuarter" to arrayOf("Meantone ¼ comma", "Mitteltönig ¼ Komma", "Mésotonique ¼ de comma"),
        "octaves.suggest" to arrayOf("Measured on your sampled strings: with {0} in the bass, no beat between two of them an octave apart is faster than {1} Hz ({2}). Every partial within {3} dB of its own string's loudest counts. The sampling's Done set it; if your ear prefers another, choose it here. The six points a new tuning starts from come from elsewhere and usually work well. On larger pianos experience favours 10:5 in the bass.", "Gemessen an Ihren Saiten: Mit {0} im Bass schwebt zwischen zwei gemessenen Saiten im Oktavabstand nichts schneller als {1} Hz ({2}). Es zählt jeder Teilton bis {3} dB unter dem lautesten seiner Saite. Fertig im Vermessen hat ihn gesetzt; zieht Ihr Ohr einen anderen vor, wählen Sie ihn hier. Die sechs Punkte, mit denen eine neue Stimmung beginnt, stammen aus anderer Quelle und bewähren sich meist. Auf größeren Klavieren spricht die Erfahrung für 10:5 im Bass.", "Mesuré sur vos cordes : avec {0} dans les basses, aucun battement entre deux d'entre elles à l'octave n'est plus rapide que {1} Hz ({2}). Chaque partiel à moins de {3} dB du plus fort de sa corde compte. Terminé dans la mesure l'a réglé ; si votre oreille en préfère un autre, choisissez-le ici. Les six points d'où part un nouvel accord viennent d'ailleurs et conviennent généralement bien. Sur les grands pianos, l'expérience préfère 10:5 dans les basses."),
        "octaves.suggestShort" to arrayOf("Bass from your sampling: {0}", "Bass aus Ihrem Vermessen: {0}", "Basses d’après votre mesure : {0}"),
        "help.temp.title" to arrayOf("Temperaments", "Temperaturen", "Tempéraments"),
        "help.temp.h1" to arrayOf("Choosing one", "Die Wahl", "Le choix"),
        "help.temp.h2" to arrayOf("The seven", "Die sieben", "Les sept"),
        "help.temp.equal" to arrayOf("All twelve fifths narrowed alike, each by a twelfth of the Pythagorean comma, about 2 cents; every key sounds the same.", "Alle zwölf Quinten gleich verengt, jede um ein Zwölftel des pythagoreischen Kommas, etwa 2 Cent; jede Tonart klingt gleich.", "Les douze quintes rétrécies également, chacune d'un douzième du comma pythagoricien, environ 2 cents ; toutes les tonalités sonnent de même."),
        "help.temp.werckmeister3" to arrayOf("Andreas Werckmeister, 1691. C–G, G–D, D–A and B–F♯ narrowed by a quarter of the Pythagorean comma, the other eight fifths pure. The keys near C have the calmest thirds, the remote keys the liveliest.", "Andreas Werckmeister, 1691. C–G, G–D, D–A und H–Fis um ein Viertel des pythagoreischen Kommas verengt, die anderen acht Quinten rein. Die Tonarten nahe C haben die ruhigsten Terzen, die entfernten die lebhaftesten.", "Andreas Werckmeister, 1691. C–G, G–D, D–A et B–F♯ rétrécies d'un quart du comma pythagoricien, les huit autres quintes pures. Les tonalités proches de C ont les tierces les plus calmes, les éloignées les plus vives."),
        "help.temp.kirnberger3" to arrayOf("Johann Philipp Kirnberger, 1779. C–G, G–D, D–A and A–E narrowed by a quarter of the syntonic comma, so that C–E is a pure third; F♯–C♯ narrowed by the schisma; the rest pure.", "Johann Philipp Kirnberger, 1779. C–G, G–D, D–A und A–E um ein Viertel des syntonischen Kommas verengt, so dass C–E eine reine Terz ist; Fis–Cis um das Schisma verengt; die übrigen rein.", "Johann Philipp Kirnberger, 1779. C–G, G–D, D–A et A–E rétrécies d'un quart du comma syntonique, si bien que C–E est une tierce pure ; F♯–C♯ rétrécie du schisma ; les autres pures."),
        "help.temp.vallotti" to arrayOf("Francesco Antonio Vallotti, 18th century. The six fifths from F–C to E–B narrowed by a sixth of the Pythagorean comma, the other six pure: a gentle well-temperament.", "Francesco Antonio Vallotti, 18. Jahrhundert. Die sechs Quinten von F–C bis E–H um ein Sechstel des pythagoreischen Kommas verengt, die anderen sechs rein: eine sanfte wohltemperierte Stimmung.", "Francesco Antonio Vallotti, XVIIIe siècle. Les six quintes de F–C à E–B rétrécies d'un sixième du comma pythagoricien, les six autres pures : un tempérament inégal doux."),
        "help.temp.young2" to arrayOf("Thomas Young, 1800. Vallotti's pattern one fifth on: the six fifths from C–G to B–F♯ narrowed by a sixth of the Pythagorean comma, the other six pure.", "Thomas Young, 1800. Vallottis Muster um eine Quinte weiter: die sechs Quinten von C–G bis H–Fis um ein Sechstel des pythagoreischen Kommas verengt, die anderen sechs rein.", "Thomas Young, 1800. Le schéma de Vallotti décalé d'une quinte : les six quintes de C–G à B–F♯ rétrécies d'un sixième du comma pythagoricien, les six autres pures."),
        "help.temp.kellner" to arrayOf("Herbert Anton Kellner, 1970s. C–G, G–D, D–A, A–E and B–F♯ narrowed by a fifth of the Pythagorean comma, the rest pure: his reading of a temperament for Bach.", "Herbert Anton Kellner, 1970er Jahre. C–G, G–D, D–A, A–E und H–Fis um ein Fünftel des pythagoreischen Kommas verengt, die übrigen rein: seine Deutung einer Temperatur für Bach.", "Herbert Anton Kellner, années 1970. C–G, G–D, D–A, A–E et B–F♯ rétrécies d'un cinquième du comma pythagoricien, les autres pures : sa lecture d'un tempérament pour Bach."),
        "help.temp.meantoneQuarter" to arrayOf("Every fifth narrowed by a quarter of the syntonic comma, so that every major third in use is pure. The circle does not close: G♯–E♭ is the wolf, about 737.6 cents. Keys with few sharps or flats only.", "Jede Quinte um ein Viertel des syntonischen Kommas verengt, so dass jede gebrauchte große Terz rein ist. Der Quintenzirkel schließt sich nicht: Gis–Es ist die Wolfsquinte, etwa 737,6 Cent. Nur Tonarten mit wenigen Vorzeichen.", "Chaque quinte rétrécie d'un quart du comma syntonique, si bien que toute tierce majeure utilisée est pure. Le cycle ne se ferme pas : G♯–E♭ est la quinte du loup, environ 737,6 cents. Seulement les tonalités à peu d'altérations."),
        "help.temp.choose" to arrayOf("Temperament: equal unless you tap Temperament and choose another from its list: Werckmeister III, Kirnberger III, Vallotti, Young II, Kellner, meantone ¼ comma or Schubert. Each note then stands at its place in that temperament, every octave of it alike, over the same stretch; A4 does not move.", "Temperatur: gleichstufig, wenn Sie nicht auf Temperatur tippen und aus der Liste eine andere wählen: Werckmeister III, Kirnberger III, Vallotti, Young II, Kellner, mitteltönig ¼ Komma oder Schubert. Jeder Ton steht dann an seinem Platz in dieser Temperatur, jede seiner Oktaven gleich, über derselben Spreizung; A4 bleibt.", "Tempérament : égal, sauf si vous touchez Tempérament et en choisissez un autre dans la liste : Werckmeister III, Kirnberger III, Vallotti, Young II, Kellner, mésotonique ¼ de comma ou Schubert. Chaque note se place alors dans ce tempérament, toutes ses octaves de même, sur le même étirement ; A4 ne bouge pas."),
        "settings.tab.advanced" to arrayOf("Advanced", "Erweitert", "Avancé"),
        "temperament.schubert" to arrayOf("Schubert", "Schubert", "Schubert"),
        "help.temp.schubert" to arrayOf("Ron Koval's concert tuning, as he published it on Piano World (22 April 2014) from the tuning file of a pianist's own piano: equal temperament with his octaves. The temperament octave about 70 % 4:2 and 30 % 2:1, a little narrower for sweeter thirds; upward pure 4:2 by A5, about 60 % 4:1 at A6, 4:1 at C8; downward pure 6:3 by A2, 80 % 4:1 at A1, about 75 % 12:6 at the lowest key. It sets the octaves too: another temperament takes them out and the stretch returns to the six points, and so does choosing three or six points. With thanks to Ron Koval.", "Ron Kovals Konzertstimmung, wie er sie auf Piano World veröffentlicht hat (22. April 2014), aus der Stimmdatei des Flügels einer Pianistin: gleichstufig mit seinen Oktaven. Die Temperaturoktave etwa 70 % 4:2 und 30 % 2:1, etwas enger für süßere Terzen; aufwärts reines 4:2 bis A5, etwa 60 % 4:1 bei A6, 4:1 bei C8; abwärts reines 6:3 bis A2, 80 % 4:1 bei A1, etwa 75 % 12:6 an der tiefsten Taste. Sie setzt auch die Oktaven: Eine andere Temperatur nimmt sie heraus, und die Spreizung kehrt zu den sechs Punkten zurück, ebenso mit der Wahl von drei oder sechs Punkten. Mit Dank an Ron Koval.", "L'accord de concert de Ron Koval, tel qu'il l'a publié sur Piano World (22 avril 2014) d'après le fichier d'accord du piano d'une pianiste : le tempérament égal avec ses octaves. La partition environ 70 % 4:2 et 30 % 2:1, un peu plus serrée pour des tierces plus douces ; vers l'aigu 4:2 pur jusqu'à A5, environ 60 % 4:1 à A6, 4:1 à C8 ; vers le grave 6:3 pur jusqu'à A2, 80 % 4:1 à A1, environ 75 % 12:6 à la touche la plus grave. Il règle aussi les octaves : un autre tempérament les retire et l'étirement revient aux six points, de même que le choix de trois ou six points. Avec nos remerciements à Ron Koval."),
        "help.start.h" to arrayOf("From A4 to the first note", "Von A4 zum ersten Ton", "De A4 à la première note"),
        "help.start.s1" to arrayOf("New Tuning on the home screen.", "Auf dem Startbildschirm: Neue Stimmung.", "Sur l’écran d’accueil : Nouvel accord."),
        "help.start.s2" to arrayOf("A4: mute it to one string, set the pitch with − and +, tune the string until the strobe stands still, then Done.", "A4: auf eine Saite abdämpfen, die Tonhöhe mit − und + wählen, die Saite stimmen, bis der Strobe steht, dann Fertig.", "A4 : n’en laissez sonner qu’une corde, choisissez le diapason avec − et +, accordez la corde jusqu’à ce que le stroboscope s’arrête, puis Terminé."),
        "help.start.s3" to arrayOf("Sampling: strike single strings, the others muted, at least eight spread over the keyboard, a key of the lowest octave and one from G#5 to G6 among them. Accept each when it says measured, then Done.", "Vermessen: einzelne Saiten anschlagen, die anderen abgedämpft, mindestens acht über die ganze Tastatur verteilt, darunter eine Taste der tiefsten Oktave und eine von G#5 bis G6. Jede übernehmen, sobald sie als gemessen gilt, dann Fertig.", "Mesure : frappez des cordes seules, les autres étouffées, au moins huit réparties sur tout le clavier, dont une touche de l’octave la plus grave et une de G#5 à G6. Acceptez chacune quand elle est mesurée, puis Terminé."),
        "help.start.s4" to arrayOf("Octaves: Done in the sampling sets the bass to the octave type your sampled strings suggest, and the tuning starts. Octaves on the edge changes it.", "Oktaven: Fertig im Vermessen setzt den Bass auf den Oktavtyp, den Ihre vermessenen Saiten vorschlagen, und das Stimmen beginnt. Oktaven am Rand ändert ihn.", "Octaves : Terminé dans la mesure règle les basses sur le type d’octave que suggèrent vos cordes mesurées, et l’accord commence. Octaves sur le bord le change."),
        "help.start.s5" to arrayOf("Tuning: strike a key, turn the pin until the strobe stands still and the key turns green, strike the next. Everything else is in the topics below.", "Stimmen: eine Taste anschlagen, den Wirbel drehen, bis der Strobe steht und die Taste grün wird, die nächste anschlagen. Alles Weitere steht in den Themen darunter.", "Accord : frappez une touche, tournez la cheville jusqu’à ce que le stroboscope s’arrête et que la touche passe au vert, frappez la suivante. Tout le reste se trouve dans les sujets ci-dessous."),
        "help.a4.title" to arrayOf("A4 and the pitch", "A4 und die Tonhöhe", "A4 et le diapason"),
        "help.a4.h1" to arrayOf("Setting A4", "A4 einstellen", "Régler A4"),
        "help.a4.h2" to arrayOf("The temperament octave", "Die Temperaturoktave", "La partition"),
        "help.a4.h3" to arrayOf("Pitch raise", "Hochstimmen", "Mise au diapason"),
        "help.a4.s1" to arrayOf("Every target of the tuning is built from A4. Tap A4 on the edge of the tuning screen at any time: the screen goes to A4 to retune it, and − and + set the pitch it should stand at. A new pitch moves every target, the keys already tuned with them. ✕ goes back to the key you were on.", "Jedes Ziel der Stimmung baut auf A4 auf. A4 am Rand des Stimmbildschirms jederzeit antippen: Der Bildschirm geht auf A4, um es nachzustimmen, und − und + setzen die Tonhöhe, auf der es stehen soll. Eine neue Tonhöhe verschiebt jedes Ziel, auch das der schon gestimmten Tasten. ✕ führt zur Taste zurück, auf der Sie waren.", "Toutes les cibles de l’accord partent de A4. Touchez A4 au bord de l’écran d’accord à tout moment : l’écran passe à A4 pour le réaccorder, et − et + règlent le diapason auquel il doit se tenir. Un nouveau diapason déplace toutes les cibles, celles des touches déjà accordées aussi. ✕ ramène à la touche où vous étiez."),
        "help.a4.s2" to arrayOf("A3–A4 unless you set it otherwise; its low note moves under Octaves on the tuning screen's edge. Its notes are tuned to equal temperament on A4; every other note is tuned by octaves from it.", "A3–A4, wenn nicht anders eingestellt; ihr tiefer Ton wird unter Oktaven am Rand des Stimmbildschirms verschoben. Ihre Töne werden gleichstufig auf A4 gestimmt, jeder andere Ton in Oktaven von ihr aus.", "A3–A4, sauf réglage contraire ; sa note grave se déplace sous Octaves, sur le bord de l’écran d’accord. Ses notes sont accordées en tempérament égal sur A4 ; toutes les autres par octaves à partir d’elle."),
        "help.samp.title" to arrayOf("Sampling the piano", "Das Klavier vermessen", "La mesure du piano"),
        "help.samp.h1" to arrayOf("What it measures", "Was gemessen wird", "Ce qui est mesuré"),
        "help.samp.h2" to arrayOf("How", "Wie", "Comment"),
        "help.samp.h3" to arrayOf("Going back", "Zurückgehen", "Revenir en arrière"),
        "help.samp.s1" to arrayOf("The piano's inharmonicity: how far each string's partials stand above whole multiples of its fundamental. From the strings you sample the app draws one smooth curve over the whole range, and every target of the tuning is computed from it.", "Die Inharmonizität des Klaviers: wie weit die Teiltöne jeder Saite über den ganzzahligen Vielfachen ihres Grundtons stehen. Aus den vermessenen Saiten zieht die App eine glatte Kurve über den ganzen Tonumfang, und jedes Ziel der Stimmung wird daraus berechnet.", "L’inharmonicité du piano : de combien les partiels de chaque corde s’écartent au-dessus des multiples entiers de sa fondamentale. Des cordes mesurées, l’app tire une seule courbe lisse sur toute l’étendue, et toutes les cibles de l’accord en sont calculées."),
        "help.samp.s3" to arrayOf("The strip under the edge shows the three steps: A4, Sampling, Tuning. Tap an earlier one to go back; nothing measured is lost. Once a note is tuned, A4 and the sampling are closed; the octave types stay open under Octaves on the edge.", "Die Leiste unter dem Rand zeigt die drei Schritte: A4, Vermessen, Stimmen. Einen früheren antippen, um zurückzugehen; nichts Gemessenes geht verloren. Sobald ein Ton gestimmt ist, sind A4 und das Vermessen geschlossen; die Oktavtypen bleiben unter Oktaven am Rand offen.", "La bande sous le bord montre les trois étapes : A4, Mesure, Accord. Touchez une étape antérieure pour y revenir ; rien de mesuré n’est perdu. Dès qu’une note est accordée, A4 et la mesure sont fermés ; les types d’octave restent ouverts sous Octaves, sur le bord."),
        "help.oct.title" to arrayOf("Octave types and stretch", "Oktavtypen und Spreizung", "Types d’octave et étirement"),
        "help.oct.h1" to arrayOf("The octave type", "Der Oktavtyp", "Le type d’octave"),
        "help.oct.h2" to arrayOf("Three or six points", "Drei oder sechs Punkte", "Trois ou six points"),
        "help.oct.h3" to arrayOf("The suggestion", "Der Vorschlag", "La suggestion"),
        "help.oct.s1" to arrayOf("Settings › Tuning › Stretch for new tunings chooses between them; Settings › Stretch sets each point's octave type and extra stretch. These are the defaults: a tuning under way keeps its own, changed under Octaves on the tuning screen's edge.", "Einstellungen › Stimmen › Spreizung neuer Stimmungen wählt zwischen ihnen; Einstellungen › Spreizung setzt Oktavtyp und zusätzliche Spreizung jedes Punkts. Das sind die Vorgaben: Eine laufende Stimmung behält ihre eigenen, geändert unter Oktaven am Rand des Stimmbildschirms.", "Réglages › Accord › Étirement des nouveaux accords choisit entre eux ; Réglages › Étirement règle le type d’octave et l’étirement supplémentaire de chaque point. Ce sont les valeurs par défaut : un accord en cours garde les siennes, changées sous Octaves, sur le bord de l’écran d’accord."),
        "help.oct.s2" to arrayOf("When the sampling is done, the bass takes the octave type your sampled strings suggest: the type whose fastest octave beat between them is the slowest. Its figures are behind the (i) under Octaves on the edge; choose another there if you want it differently.", "Ist das Vermessen fertig, bekommt der Bass den Oktavtyp, den Ihre vermessenen Saiten vorschlagen: den Typ, dessen schnellste Oktavschwebung zwischen ihnen am langsamsten ist. Seine Zahlen stehen hinter dem (i) unter Oktaven am Rand; dort einen anderen wählen, wenn Sie es anders wollen.", "La mesure terminée, les basses prennent le type d’octave que suggèrent vos cordes mesurées : celui dont le battement d’octave le plus rapide entre elles est le plus lent. Ses chiffres sont derrière le (i) sous Octaves, sur le bord ; choisissez-en un autre là si vous le voulez autrement."),
        "help.oct.s3" to arrayOf("Change the octave types before the first note if you want others: every target is computed from them.", "Die Oktavtypen vor dem ersten Ton ändern, wenn Sie andere wollen: Jedes Ziel wird aus ihnen berechnet.", "Changez les types d’octave avant la première note si vous en voulez d’autres : toutes les cibles en sont calculées."),
        "help.scr.title" to arrayOf("The tuning screen", "Der Stimmbildschirm", "L’écran d’accord"),
        "help.scr.h1" to arrayOf("Along the top", "Am oberen Rand", "En haut"),
        "help.scr.h2" to arrayOf("The note", "Der Ton", "La note"),
        "help.scr.h3" to arrayOf("The strobe", "Der Strobe", "Le stroboscope"),
        "help.scr.h4" to arrayOf("The keyboard", "Die Tastatur", "Le clavier"),
        "help.scr.s2" to arrayOf("Tune until the strobe stands still. Within your tolerance and still, the note is kept and its key turns green.", "Stimmen, bis der Strobe steht. Innerhalb Ihrer Toleranz und ruhig, gilt der Ton als gestimmt, und seine Taste wird grün.", "Accordez jusqu’à ce que le stroboscope s’arrête. Dans votre tolérance et immobile, la note est retenue et sa touche passe au vert."),
        "help.scr.s3" to arrayOf("At the foot, the whole keyboard: the keys tuned in green, the key on screen marked, and above them the curve of how far each tuned key stands from its target. Continue Tuning on the home screen goes back to where you stopped.", "Unten die ganze Tastatur: die gestimmten Tasten grün, die Taste am Bildschirm markiert, darüber die Kurve, wie weit jede gestimmte Taste von ihrem Ziel steht. Stimmung fortsetzen auf dem Startbildschirm führt dorthin zurück, wo Sie aufgehört haben.", "En bas, tout le clavier : les touches accordées en vert, la touche à l’écran marquée, et au-dessus la courbe de l’écart de chaque touche accordée à sa cible. Continuer l’accord, sur l’écran d’accueil, ramène là où vous vous êtes arrêté."),
        "help.scr.s4" to arrayOf("Strobe on the edge, three ways of showing it. One strobe: one band for the note, moved by its clean partials together. Limit to N: the lowest N partials, a band each. All partials: a band for every partial. A tap changes it at once.", "Strobe am Rand, drei Darstellungen. Ein Strobe: ein Band für die Note, bewegt von ihren sauberen Teiltönen gemeinsam. Nur N: die tiefsten N Teiltöne, je ein Band. Alle Teiltöne: ein Band für jeden Teilton. Ein Tipp wechselt sofort.", "Strobe au bord, trois façons de l'afficher. Un stroboscope : une bande pour la note, mue par ses partiels propres ensemble. Limiter à N : les N partiels les plus bas, une bande chacun. Tous les partiels : une bande pour chaque partiel. Un toucher change aussitôt."),
        "help.scr.s5" to arrayOf("Strike a key and the screen is on it. Tap the note's name: a keyboard opens. Tap a key and the screen stays on it, Auto note switching off; turn Auto note switching on there and the screen goes to each note you strike again.", "Eine Taste anschlagen, und der Bildschirm ist auf ihr. Den Namen des Tons antippen: Eine Tastatur öffnet sich. Eine Taste antippen, und der Bildschirm bleibt auf ihr, Automatischer Tonwechsel aus; dort Automatischer Tonwechsel einschalten, und der Bildschirm geht wieder auf jeden angeschlagenen Ton.", "Frappez une touche et l’écran est sur elle. Touchez le nom de la note : un clavier s’ouvre. Touchez une touche et l’écran y reste, Changement de note automatique désactivé ; activez-y Changement de note automatique et l’écran passe de nouveau à chaque note frappée."),
        "help.set.title" to arrayOf("Settings: Tuning, Stretch, Piano, App", "Einstellungen: Stimmen, Spreizung, Klavier, App", "Réglages : Accord, Étirement, Piano, App"),
        "help.set.s1" to arrayOf("on, the temperament octave is tuned first, each of its notes waiting for Done; off, leaving any note keeps it. Off unless you turn it on.", "ein, wird die Temperaturoktave zuerst gestimmt, jeder ihrer Töne wartet auf Fertig; aus, gilt jeder verlassene Ton als gestimmt. Aus, solange Sie es nicht einschalten.", "activé, la partition est accordée d’abord, chacune de ses notes attendant Terminé ; désactivé, toute note quittée est retenue. Désactivé tant que vous ne l’activez pas."),
        "help.set.s2" to arrayOf("three or six points, the defaults for new tunings (Octave types and stretch).", "drei oder sechs Punkte, die Vorgabe für neue Stimmungen (Oktavtypen und Spreizung).", "trois ou six points, la valeur par défaut des nouveaux accords (Types d’octave et étirement)."),
        "help.set.s3" to arrayOf("on, the two bars from C#6 up; off, the strobe there too.", "ein, ab C#6 die zwei Balken; aus, auch dort der Strobe.", "activé, les deux barres à partir de C#6 ; désactivé, le stroboscope là aussi."),
        "help.set.s4" to arrayOf("Each region, or each of the six places: its octave type and extra stretch, the defaults for new tunings.", "Jede Lage oder jeder der sechs Punkte: sein Oktavtyp und seine zusätzliche Spreizung, die Vorgaben für neue Stimmungen.", "Chaque registre, ou chacun des six points : son type d’octave et son étirement supplémentaire, les valeurs par défaut des nouveaux accords."),
        "help.set.s5" to arrayOf("the lowest key, down to C0 for a piano that reaches below A0.", "die tiefste Taste, bis C0 für ein Klavier, das unter A0 reicht.", "la touche la plus grave, jusqu’à C0 pour un piano qui descend sous A0."),
        "help.adv.title" to arrayOf("Settings: Advanced", "Einstellungen: Erweitert", "Réglages : Avancé"),
        "help.adv.s1" to arrayOf("the screen follows the key struck. When a strike is another note and the note on screen is not heard, the screen moves to it and keeps the note it leaves. On by default.", "Der Bildschirm folgt der angeschlagenen Taste. Ist ein Anschlag ein anderer Ton und der Ton am Bildschirm nicht zu hören, wechselt der Bildschirm zu ihm und behält den verlassenen Ton. Standardmäßig ein.", "l’écran suit la touche frappée. Quand une frappe est une autre note et que la note à l’écran ne s’entend pas, l’écran passe à elle et retient la note quittée. Activé par défaut."),
        "help.adv.s2" to arrayOf("the reading is the string's clean partials read together; a partial that beats by itself is left out and named. Off: the partial being tuned alone, as an electronic tuning device reads. A partial tapped under the strobe is read alone either way. On by default.", "Die Anzeige liest die sauberen Teiltöne der Saite gemeinsam; ein Teilton, der von selbst schwebt, wird ausgelassen und benannt. Aus: nur der gestimmte Teilton, wie ein elektronisches Stimmgerät liest. Ein unter dem Strobe angetippter Teilton wird in jedem Fall allein gelesen. Standardmäßig ein.", "la lecture prend ensemble les partiels propres de la corde ; un partiel qui bat de lui-même est écarté et nommé. Désactivé : le seul partiel accordé, comme le lit un accordeur électronique. Un partiel touché sous le stroboscope est lu seul dans tous les cas. Activé par défaut."),
        "help.adv.s3" to arrayOf("partials above this note are not offered; nothing up there helps a tuning. D#7 by default.", "Teiltöne über diesem Ton werden nicht angeboten; dort oben hilft nichts mehr der Stimmung. Standard D#7.", "les partiels au-dessus de cette note ne sont pas proposés ; rien là-haut n’aide l’accord. D#7 par défaut."),
        "help.adv.s4" to arrayOf("how far back the reading looks. 0.15 s, the default, shows every wobble as it happens; longer steadies it in a noisy room or on a string that wanders, at the price of a beat shown slower than it is.", "wie weit die Anzeige zurückblickt. 0,15 s, der Standard, zeigt jedes Schwanken, wie es geschieht; länger beruhigt sie in einem lauten Raum oder bei einer wandernden Saite, um den Preis einer Schwebung, die langsamer erscheint, als sie ist.", "jusqu’où la lecture regarde en arrière. 0,15 s, la valeur par défaut, montre chaque oscillation à l’instant ; plus long la calme dans une pièce bruyante ou sur une corde qui erre, au prix d’un battement affiché plus lent qu’il n’est."),
        "help.samp.s2" to arrayOf("Done ends the sampling: the bass takes the octave type your sampled strings suggest, and the tuning starts. What was measured stays the basis of every target for the whole tuning.", "Fertig beendet das Vermessen: Der Bass nimmt den Oktavtyp, den Ihre vermessenen Saiten vorschlagen, und das Stimmen beginnt. Was gemessen wurde, bleibt für die ganze Stimmung die Grundlage jedes Ziels.", "Terminé met fin à la mesure : les basses prennent le type d’octave que suggèrent vos cordes mesurées, et l’accord commence. Ce qui a été mesuré reste la base de toutes les cibles pendant tout l’accord."),
        "help.scr.s1" to arrayOf("A4, Octaves and Strobe. A4 opens A4's window, Octaves the octave types, Strobe the strobe's three choices. Under them, the three steps.", "A4, Oktaven und Strobe. A4 öffnet das Fenster für A4, Oktaven die Oktavtypen, Strobe die drei Arten des Strobes. Darunter die drei Schritte.", "A4, Octaves et Strobe. A4 ouvre la fenêtre de A4, Octaves les types d’octave, Strobe les trois choix du stroboscope. En dessous, les trois étapes."),
        "help.adv.s5" to arrayOf("how close a note must stand to turn green, in cents; 1.5 c by default, about twice the scatter between strikes of one string.", "wie nah ein Ton stehen muss, um grün zu werden, in Cent; standardmäßig 1,5 c, etwa das Doppelte der Streuung zwischen Anschlägen einer Saite.", "à quelle distance une note doit se tenir pour passer au vert, en cents ; 1,5 c par défaut, environ deux fois la dispersion entre frappes d’une même corde."),
        "stretch.schubertTitle" to arrayOf("Schubert, after Ron Koval", "Schubert, nach Ron Koval", "Schubert, d'après Ron Koval"),
        "stretch.schubertFixed" to arrayOf("Set under Temperament. To change a place, choose three or six points.", "Unter Temperatur gesetzt. Um einen Punkt zu ändern, drei oder sechs Punkte wählen.", "Réglé sous Tempérament. Pour changer un point, choisissez trois ou six points."),
    )
}
