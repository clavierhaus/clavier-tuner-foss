package at.clavierhaus.unisonmaster.app

import kotlin.math.pow
import at.clavierhaus.unisonmaster.ui.num
import at.clavierhaus.unisonmaster.i18n.noteName
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.Strings
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.tuning.TuningSession
import at.clavierhaus.unisonmaster.ui.DejaVuSerif
import at.clavierhaus.unisonmaster.ui.NotePicker
import at.clavierhaus.unisonmaster.ui.PartialChips
import at.clavierhaus.unisonmaster.ui.ProgressButton
import at.clavierhaus.unisonmaster.ui.ProgressKeyboard
import at.clavierhaus.unisonmaster.ui.ProbeButton
import at.clavierhaus.unisonmaster.ui.CORNER_RIGHT_WIDTH
import at.clavierhaus.unisonmaster.ui.Corners
import at.clavierhaus.unisonmaster.ui.StateWord
import at.clavierhaus.unisonmaster.ui.TuningPictureView
import at.clavierhaus.unisonmaster.picture.TuningPicture
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.measure.Partials
import at.clavierhaus.unisonmaster.ui.formatHz
import java.util.Locale
import kotlin.math.abs

/**
 * The one main screen. Before A4 is set it is the hub (define A4 on one
 * string). After Done it is the tuning screen of docs/SCREEN.md: one note,
 * the partial listened to and its target, where the target comes from
 * (docs/ENGINE.md), the partial as read, the beat as motion, a scale
 * magnified at the match window, one state word — and Done. "Partials" opens Full Spectrum (the bells, the readout
 * column and the partial row); "Progress" the keyboard. The corners are
 * every screen's (ui/Corners.kt): the gear top left, the name and home top
 * right; no back arrow (blueprint §18–§19). In the band between them, the
 * state word and — in Pro, switched on in the settings — the red recording
 * button ([recordButton], Pro's own) and the play button.
 */
/**
 * Whether the tuning screen colours a partial by the engine's false-beat claim. Off from 7 Oct
 * 2026, 15:56: the claim was a spread in cents and the ear's criterion is a rate in hertz, and the
 * two disagree in both directions on the recordings (SAMICK-2026-09-30-FINDINGS §20, §21). On again
 * since 8 Oct 2026: the claim is the rate, measured from the band power (BeatMeasure) with the
 * string alone while sampling and kept as the string's facts, and in tuning the colour is those
 * facts -- never a beat heard with the unison sounding, which could be the unison's.
 */
private const val FALSE_MARKS_ON_TUNING = true

/** The partials shown while sampling, in fixed seats: "maybe 5" (Peter, 7 Oct 2026, 10:18). */
private val SAMPLING_SEATS: List<Int> = (1..5).toList()

/**
 * The tuning screen's bands, in seats fixed for the note from the moment it is on screen, so the whole row
 * stands at once and never grows band by band (Peter, 30 Oct 2026: "make all partials you can catch visible at
 * the same time"). The seats are the partials offered under the strobe (1 to [offered], the chips' row) less the
 * ones the tuner took out, the lowest [count] of them with the band being tuned always among them, or all for 0.
 * A seat whose partial is not read stands empty. With one band (strobeOne) the seats are what the engine reads.
 */
internal fun strobeSeats(offered: Int, tuned: Int, hidden: Set<Int>, count: Int, oneBand: Boolean, shown: Set<Int>): List<Int> {
    if (oneBand) return (shown + tuned).sorted()
    val all = ((1..offered).toSet() - hidden + tuned).sorted()
    if (count <= 0 || all.size <= count) return all
    val lowest = all.take(count)
    return if (tuned in lowest) lowest else (lowest.dropLast(1) + tuned).sorted()
}

@Composable
fun BasicHub(
    controller: TuningController,
    /**
     * Pro's red recording button, drawn by the edition that owns a recorder;
     * null in the free edition, which has none. The band the button sits in is
     * drawn here, once, for both editions; what goes in it, and the recorder
     * behind it, are Pro's (Peter, 5 Oct 2026: the recorder leaves foss).
     */
    recordButton: (@Composable () -> Unit)? = null,
    onSettings: () -> Unit,
    /** Home: the hub, from the corner every screen shares. */
    onHome: () -> Unit,
    feeding: String? = null,
    onPickRecording: (() -> Unit)? = null,
    onUseMicrophone: () -> Unit = {},
    /** Switches TunerSettings.autoNote: off when the tuner names a key by hand, on when he taps the name again. */
    onAutoNote: ((Boolean) -> Unit)? = null,
    /** Moves the temperament octave's low note (C3…A3) from the sampling page; null when the app does not offer it. */
    onTemperamentLow: ((Int) -> Unit)? = null,
    /**
     * Opens Settings at the stretch of region 0..3 (TunerSettings.regionOf). Nothing on the screen calls it since the
     * "partial N" line it hung on left (26 Oct 2026); kept so both editions' calls stand until a place for it is chosen.
     */
    onStretch: ((Int) -> Unit)? = null,
    /** The tuning screen's left panel, drawn by an edition (Pro: the unison toggle and its 2 x 2 grid); null leaves it blank. */
    leftPanel: (@Composable () -> Unit)? = null,
    /** The tuning screen's strobe bands an edition colours: green, and dimmed (TuningPictureView). */
    greenBands: Set<Int> = emptySet(),
    dimBands: Set<Int> = emptySet(),
    /** Changes the settings in force: the window behind the info line (TuningInfo.kt); null, no window. */
    onChangeSettings: (((TunerSettings) -> TunerSettings) -> Unit)? = null,
) {
    val partials by controller.livePartials.collectAsState()
    val audible by controller.liveAudible.collectAsState()
    var showProgress by remember { mutableStateOf(false) }
    // the one picture: built frame by frame from the engine's readings of the strike now sounding
    val pictureBuilder = remember { TuningPicture.Builder() }
    val a4 by controller.referenceA4Hz.collectAsState()
    val tuning by controller.tuning.collectAsState()
    val shownTuning by controller.shownPartials.collectAsState()
    val hiddenTuning by controller.hiddenPartials.collectAsState()
    val suggested by controller.suggested.collectAsState()
    val active by controller.activePartial.collectAsState()
    val targets by controller.targets.collectAsState()
    val cfg by controller.settings.collectAsState()
    val liveTarget by controller.targetHz.collectAsState()
    val reading by controller.reading.collectAsState()
    val language by Strings.language.collectAsState()          // read so the screen recomposes on a change
    val hubHz by controller.liveHz.collectAsState()          // before the session: A4's first partial, by the hub's reader

    val t = tuning
    Box(Modifier.fillMaxSize().background(Color(Brand.BLACK)).safeDrawingPadding()) {
    // the tuning's settings between the gear and the name, a tap opening the ones that matter while tuning
    var infoOpen by remember { mutableStateOf(false) }
    // the window opens by itself when the tuner ends the sampling with Done (Peter, 29 and 31 Oct 2026: after A4
    // and the samples, the piano's size and the octave types it recommends); never on a tuning restored past it
    val samplingDone by controller.samplingDone.collectAsState()
    val seenDone = remember { mutableStateOf(samplingDone) }
    LaunchedEffect(samplingDone) {
        if (samplingDone != seenDone.value) { seenDone.value = samplingDone; if (onChangeSettings != null) infoOpen = true }
    }
    Corners(onSettings = onSettings, onHome = onHome, info = tuningInfoLine(cfg, a4),
        onInfo = if (onChangeSettings != null) ({ infoOpen = true }) else null)
    if (infoOpen && onChangeSettings != null) TuningInfoDialog(cfg, onChangeSettings) { infoOpen = false }
    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        // the start of a tuning — A4, the temperament octave, the sampling — is its own page (Peter, 4 Oct 2026)
        // The one picture, built here once for every state of the screen -- one place puts the number
        // on it (Amendment 3). Before the session: A4's first partial as the hub's reader hears it,
        // against the pitch chosen, and only that partial, because without a measured B no other
        // partial has a lawful target (Rule 0). From the session on, sampling or tuning: the engine's
        // reading of the note on screen -- its partials as read, their targets placed from the
        // measured B, its false claims from the three-strike tally -- the frame the tuning screen has
        // always had. Until 6 Oct 2026 the sampling page built a frame of its own beside this one, a
        // synthetic partial 1 with the false set hardcoded empty, and so could never show a false
        // beat, which is what the sampling pass is for (Peter's word for "b", 22:42).
        fun engineFrame(t: TuningController.TuningView): TuningPicture.Frame {
            val read = reading; val l = t.listening
            return TuningPicture.Frame(
                note = noteName(t.midi), listened = read?.k ?: l.k, live = read?.live == true,
                listenedHz = read?.hz, listenedTargetHz = read?.targetHz ?: l.targetHz,
                partials = partials.associate { it.k to TuningPicture.Frame.Partial(it.hz, it.level, it.widthCents, it.overNoiseDb) },
                targets = targets.associate { it.k to it.hz },
                falsePartials = read?.falsePartials?.toSet() ?: emptySet(), dropped = emptySet(),
                matchCents = cfg.matchCents, nowMs = System.currentTimeMillis(),
                // while sampling the stripes are partials 1-5 in fixed seats (Peter, 7 Oct 2026)
                seats = if (t.sampling) SAMPLING_SEATS else strobeSeats(controller.highestPartial(liveTarget), read?.k ?: l.k,
                    hiddenTuning, cfg.strobeCount, cfg.strobeOne, shownTuning),
            )
        }
        // Since 22 Oct 2026 the A4 page shows the string's partials 2 and up as strobes too, each against the
        // place the strike put it -- a measured place, which is what the note above asked for -- so a false
        // beat shows before a string is taken as the reference (Peter, at the Steinway B). Partial 1 stands
        // against the pitch chosen, as before.
        fun hubFrame(): TuningPicture.Frame {
            val upper = partials.filter { it.k >= 2 }
            return TuningPicture.Frame(
                note = "A4", listened = 1, live = hubHz != null, listenedHz = hubHz, listenedTargetHz = a4,
                partials = (hubHz?.let { mapOf(1 to TuningPicture.Frame.Partial(it, 1.0, null, null)) } ?: emptyMap()) +
                    upper.associate { it.k to TuningPicture.Frame.Partial(it.hz, it.level, it.widthCents, it.overNoiseDb) },
                targets = mapOf(1 to a4) + upper.associate { it.k to it.hz * 2.0.pow(-it.cents / 1200.0) },
                falsePartials = emptySet(), dropped = emptySet(),
                matchCents = cfg.matchCents, nowMs = System.currentTimeMillis(),
                // one column per partial button under the strobe, in the same order and width (Peter,
                // 23 Oct 2026: the strobe columns correspond to the buttons below); a partial taken out
                // keeps its empty column, so the two rows never shift against each other
                seats = (1..controller.highestPartial(a4).coerceIn(1, Partials.MAX)).toList(),
            )
        }
        val picture = pictureBuilder.build(if (t == null) hubFrame() else engineFrame(t))
        if (t == null || t.sampling) {
            SamplingPage(controller, t, picture, onTemperamentLow, feeding, onPickRecording, onUseMicrophone)
            return@Box
        }

        // ---- the tuning screen ----
        val name = noteName(t.midi)
        // What is tuned by: the note's listened partial (or one the tuner
        // tapped) against its target, read by phase (docs/ENGINE.md)
        val read = reading
        val l = t.listening
        val shownHz = read?.hz
        val shownTarget = read?.targetHz ?: l.targetHz
        val live = read?.live == true
        val origin = when (l.source) {
            TuningSession.Source.REFERENCE -> t(K.origin_reference)
            TuningSession.Source.TEMPERAMENT -> t(K.origin_temperament, formatHz(a4))
            TuningSession.Source.OCTAVE -> t(K.origin_octave, l.type!!.label, noteName(l.refMidi!!)) +
                (if (l.widthCents > 0.0) t(K.origin_wide, String.format(Locale.ROOT, "%.1f", l.widthCents)) else t(K.origin_beatless))
            TuningSession.Source.PARTNER_UNTUNED -> t(K.origin_untuned, noteName(l.refMidi!!))
            TuningSession.Source.CURVE -> t(K.origin_curve, l.type!!.label, noteName(l.refMidi!!)) +
                (if (l.widthCents > 0.0) t(K.origin_wide, String.format(Locale.ROOT, "%.1f", l.widthCents)) else t(K.origin_beatless)) +
                (l.checkCents?.let { t(K.origin_check, noteName(l.refMidi!!), num(abs(it), 1), if (it >= 0) t(K.origin_wideWord) else t(K.origin_narrowWord)) } ?: "")
        }
        // Every number on this screen is derived from the A4 reference, and the
        // screen showed the target, the measurement, the cents, the octave and its
        // width -- and never the one figure the rest hangs from. Four rounds at the
        // piano on 1 Oct 2026 went into deducing it from where the bells landed. It
        // is said here, always, whatever it is and wherever it came from.
        val originLine = "A4 ${formatHz(a4)} \u00b7 $origin"
        // the picture is `picture`, built above for every state of the screen so the header's state
        // word and the picture say the same thing; the picture decides what is shown, the view how (docs/UI.md)
        // The word above the picture says only whether the engine hears the string: nothing about
        // where it stands. "33 cents sharp", "That's F5", "flat" -- every word about pitch is gone
        // (Peter, 7 Oct 2026, 15:33: irritating, serving no purpose): the strobe says which way and
        // how far, the bells say where the partials agree, and a word beside them was a third
        // place saying the same thing. "Complete" stays, said once when the range is done.
        val state: Pair<String, Color> = when {
            t.sampling -> "" to Color(Brand.WHITE_MUTED)
            t.complete && !live -> t(K.state_complete) to Color(Brand.GO_GREEN)
            // "listening" left on 23 Oct 2026 (Peter: it serves no purpose): an empty strobe already says
            // nothing is heard, and the word stood over the info line in the band
            else -> "" to Color(Brand.WHITE_MUTED)
        }
        val follows = cfg.autoNote && (!cfg.temperamentFirst || t.temperamentComplete) && !t.sampling
        // which partials the reading is made of, when the clean partials are read together; a false one is named
        // the same claim in words waits with the colour (FALSE_MARKS_ON_TUNING)
        val partialsLine = read?.takeIf { it.combined || (FALSE_MARKS_ON_TUNING && it.falsePartials.isNotEmpty()) }?.let { r ->
            (if (r.combined) t(K.tuning_partialsUsed, r.usedPartials.joinToString(" ")) else t(K.tuning_partialOne, r.k)) +
                (if (FALSE_MARKS_ON_TUNING && r.falsePartials.isNotEmpty()) t(K.tuning_partialsNot, r.falsePartials.joinToString(" ")) else "")
        }
        val status = if (t.sampling) "" else (if (follows) t(K.tuning_follows) else "") + when {
            !cfg.temperamentFirst -> t(K.tuning_leavingKeeps)
            t.temperamentComplete -> t(K.tuning_temperamentDone)
            else -> t(K.tuning_temperamentFirst)
        } + (partialsLine?.let { " · $it" } ?: "")

        // the band between the corners: the state word, and Pro's two buttons
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = CORNER_RIGHT_WIDTH)) {
            if (!t.sampling) StateWord(state.first, state.second)
            if (recordButton != null && !t.sampling) {
                Spacer(Modifier.width(18.dp))
                recordButton()
            }
            if (onPickRecording != null && !t.sampling) {
                Spacer(Modifier.width(4.dp))
                ProbeButton(feeding = feeding, onPick = onPickRecording, onStop = onUseMicrophone)
            }
        }

        // the note name is the picture's one control: following the key struck is the default; a tap
        // opens the picker and the key chosen there holds until the name is tapped again
        var pickingNote by remember { mutableStateOf(false) }
        when {
            !showProgress && !t.sampling -> {
                TuningPictureView(
                    picture,
                    matchCents = cfg.matchCents,
                    modifier = Modifier.fillMaxSize().padding(top = 52.dp, bottom = 84.dp),
                    follows = follows,
                    left = leftPanel,
                    greenBands = greenBands, dimBands = dimBands,
                    together = cfg.strobeTogether,
                    // No red-violet on the tuning screen until the engine measures a rate. The claim
                    // behind it is a spread over 0.5 cents, and the ear's criterion is a rate in hertz
                    // (0.5-8 Hz, second plane within 10 dB): at partial 2 of G#4, 0.5 c is 0.24 Hz,
                    // under the audible floor, so the line catches the inaudible polarisation doublets
                    // and coloured a clean string on three strikes (Peter at the piano, 7 Oct 2026,
                    // 15:53-15:56: "the sudden change to red-blue made the whole thing moot"); on the
                    // Samick's A4 it missed an audible 0.67 Hz (SAMICK findings §20). The detection
                    // and the tally run on and the claim stays in the picture; only the colour waits
                    // for a verdict that is a measurement.
                    marks = FALSE_MARKS_ON_TUNING,
                    onNoteTap = { if (cfg.autoNote) pickingNote = true else onAutoNote?.invoke(true) },
                )
                if (pickingNote) NotePicker(
                    lowMidi = t.stepLowMidi,
                    highMidi = t.stepHighMidi,
                    current = t.midi,
                    onPick = { midi -> controller.selectNote(midi); onAutoNote?.invoke(false); pickingNote = false },
                    onDismiss = { pickingNote = false },
                )
            }
            showProgress -> {
                ProgressKeyboard(
                    done = t.tuned,
                    current = t.midi,
                    deviations = t.deviations,
                    curve = t.curve,
                    lowestMidi = t.lowestMidi,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 40.dp)
                        .width(640.dp)
                        .height(216.dp),
                )
            }
        }

        // the bottom row: Progress · the partials · Done
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
        ) {
            ProgressButton(showingProgress = showProgress, onClick = { showProgress = !showProgress })
            Spacer(Modifier.width(10.dp))
            if (!showProgress) {
                // the partials to tap: one row that always fits; a false one is violet, and a
                // tap on it takes it out of the bells
                PartialChips(
                    a4Hz = a4,
                    baseHz = liveTarget,
                    count = controller.highestPartial(liveTarget),
                    shown = shownTuning,
                    falseMarked = if (FALSE_MARKS_ON_TUNING) picture.falseMarked else emptySet(),
                    tappable = (targets.map { it.k }.toSet() + audible) - t.listening.k,
                    onTap = { k -> controller.tapPartial(k) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(status, color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
            }
            // No Done while tuning: the strobe still is the verdict and the engine records the note
            // from it (Peter, 7 Oct 2026, 16:20). Done stays on the sampling page, where it ends a pass.
        }
    }
    }
}
