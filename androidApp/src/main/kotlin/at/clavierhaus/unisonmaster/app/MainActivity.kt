package at.clavierhaus.unisonmaster.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import at.clavierhaus.unisonmaster.settings.SettingsModel
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.PartialMonitor
import at.clavierhaus.unisonmaster.model.StringSlot
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.createAudioSource
import at.clavierhaus.unisonmaster.tuning.Notes
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val audioSource by lazy { createAudioSource() }
    private val controller by lazy { TuningController(audioSource) { System.currentTimeMillis() } }
    private val settingsModel by lazy {
        SettingsModel(PrefsStore(getSharedPreferences("tuner-settings", MODE_PRIVATE)))
    }
    private var settingsOpen = false
    private val monitor by lazy { PartialMonitor(audioSource, controller) }

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) controller.startLive()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
        hideSystemBars()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(Brand.ORANGE),
                    onPrimary = Color(Brand.BLACK),
                    secondary = Color(Brand.WHITE),
                    onSecondary = Color(Brand.BLACK),
                    background = Color(Brand.BLACK),
                    onBackground = Color(Brand.WHITE),
                    surface = Color(Brand.NEAR_BLACK),
                    onSurface = Color(Brand.WHITE),
                )
            ) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val settings by settingsModel.settings.collectAsState()
                LaunchedEffect(settings) { controller.applySettings(settings) }
                LaunchedEffect(showSettings) {
                    // the microphone rests while settings are open: nothing is measured unseen
                    settingsOpen = showSettings
                    if (showSettings) controller.stopLive() else if (hasMic()) controller.startLive()
                }
                if (showSettings) {
                    BackHandler { showSettings = false }
                    TunerSettingsScreen(
                        controller = controller,
                        model = settingsModel,
                        version = packageManager.getPackageInfo(packageName, 0).versionName ?: "dev",
                        onBack = { showSettings = false },
                    )
                } else {
                    BasicHub(controller = controller, onSettings = { showSettings = true })
                }
            }
        }
    }

    private fun hasMic(): Boolean = androidx.core.content.ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        if (hasMic() && !settingsOpen) controller.startLive()
    }

    override fun onPause() {
        super.onPause()
        controller.stopLive()
        controller.stopMeasuring()
        monitor.stop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

// ---------------------------------------------------------------------------
// Screen 1: Hub — calibration foundation
// ---------------------------------------------------------------------------

@Composable
fun HubScreen(controller: TuningController, onStart: () -> Unit, onScope: () -> Unit = {}) {
    val referenceHz by controller.referenceA4Hz.collectAsState()
    val temperament by controller.temperament.collectAsState()
    val measuring by controller.measuring.collectAsState()
    val measuredHz by controller.lastMeasuredHz.collectAsState()
    val dispersionHz by controller.dispersionHz.collectAsState()
    val estimateCount by controller.estimateCount.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        HallBackground()

        Row(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                AppTitle()
                Spacer(Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ReferencePitchSection(
                            referenceHz = referenceHz,
                            measuring = measuring,
                            measuredHz = measuredHz,
                            dispersionHz = dispersionHz,
                            estimateCount = estimateCount,
                            onSet = controller::setReference,
                            onMeasure = {
                                if (measuring) controller.stopMeasuring()
                                else controller.measureReferenceFromInstrument()
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            temperament.displayName,
                            color = Color(Brand.WHITE_MUTED),
                            fontSize = 12.sp,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(Brand.WHITE))) { append("Start mastering ") }
                            withStyle(SpanStyle(color = Color(Brand.ORANGE))) { append("unisons") }
                            withStyle(SpanStyle(color = Color(Brand.WHITE))) { append(" now") }
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onScope, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(Brand.WHITE))) { append("Measure ") }
                            withStyle(SpanStyle(color = Color(Brand.ORANGE))) { append("frequency") }
                        },
                        fontSize = 16.sp,
                    )
                }
            }

            Spacer(Modifier.weight(0.55f))
        }
    }
}

// ---------------------------------------------------------------------------
// Screen 3: Analysis — full-width deviation display, bells for partials 1..8
// ---------------------------------------------------------------------------

@Composable
fun AnalysisScreen(
    monitor: PartialMonitor,
    controller: TuningController,
    onBack: () -> Unit,
) {
    val levels by monitor.levels.collectAsState()
    val listening by monitor.listening.collectAsState()
    val midi by monitor.selectedMidi.collectAsState()
    val locked by monitor.locked.collectAsState()
    val enabledPartials by monitor.enabledPartials.collectAsState()
    val referenceHz by controller.referenceA4Hz.collectAsState()
    val temperament by controller.temperament.collectAsState()
    val model by monitor.stringModel.collectAsState()
    val refModel by monitor.referenceModel.collectAsState()
    val modelFresh by monitor.modelFresh.collectAsState()
    val fitResidual by monitor.fitResidualCents.collectAsState()
    val refDelta by monitor.refDeltaCents.collectAsState()
    val refBeat by monitor.refBeatHz.collectAsState()
    val armedDelta by monitor.armedDeltaCents.collectAsState()
    val armedBeat by monitor.armedBeatHz.collectAsState()
    val tunable by monitor.tunablePartials.collectAsState()
    val recommended by monitor.recommendedPartial.collectAsState()
    val sustain by monitor.sustainSeconds.collectAsState()
    val captured by monitor.captured.collectAsState()
    val armedSlot by monitor.armedSlot.collectAsState()
    val armBlows by monitor.armBlows.collectAsState()
    val suspectSlots by monitor.suspectSlots.collectAsState()
    val referenceSlot by monitor.referenceSlot.collectAsState()
    val liveSlot by monitor.liveSlot.collectAsState()
    val currentModels by monitor.currentModels.collectAsState()
    val inputLevelDb by monitor.inputLevelDb.collectAsState()
    val rawDetectHz by monitor.rawDetectHz.collectAsState()
    val usable by monitor.usablePartials.collectAsState()
    val unisonBeats by monitor.unisonBeatsHz.collectAsState()
    val candidates by monitor.candidatePartials.collectAsState()
    val missingOn by monitor.missingOn.collectAsState()

    // Auto-start on entry; stop when leaving the screen.
    LaunchedEffect(Unit) {
        controller.stopMeasuring()
        monitor.start()
    }
    DisposableEffect(Unit) {
        onDispose { monitor.stop() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HallBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBack) {
                    Text(
                        "‹ Hub",
                        color = Color(Brand.ORANGE),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                StepButton("‹") {
                    monitor.setLocked(true)
                    monitor.step(-1)
                }
                Text(
                    Notes.name(midi),
                    color = Color(Brand.ORANGE),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                StepButton("›") {
                    monitor.setLocked(true)
                    monitor.step(1)
                }
                if (locked) {
                    // Stepping the note locks it, which silently disables
                    // detection for the rest of the session — a mode change
                    // the operator did not ask for and could not see. Say so
                    // in words, and offer the way back in the same control.
                    Button(
                        onClick = monitor::toggleLock,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "Locked · tap for Auto",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = monitor::toggleLock,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "Auto",
                            fontSize = 11.sp,
                            color = Color(Brand.ORANGE),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))

                // Reference capture: the step that makes unison comparison
                // mean anything, and the one an operator forgets. Given its
                // own prominent control in alert red, centred.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (slot in StringSlot.entries) {
                        StringSlotButton(
                            slot = slot,
                            isSet = captured.containsKey(slot),
                            isArmed = armedSlot == slot,
                            isSuspect = slot in suspectSlots,
                            isReference = referenceSlot == slot,
                            blows = armBlows,
                            blowsRequired = monitor.requiredBlows,
                            onClick = {
                                when {
                                    // A captured string that is not the
                                    // reference becomes it; tapping the
                                    // reference itself releases that string.
                                    referenceSlot == slot -> monitor.clearString(slot)
                                    captured.containsKey(slot) -> monitor.setReferenceSlot(slot)
                                    else -> monitor.armString(slot)
                                }
                            },
                        )
                    }
                    if (captured.isNotEmpty() || armedSlot != null) {
                        TextButton(onClick = monitor::resetSampling) {
                            Text(
                                "Reset",
                                fontSize = 11.sp,
                                color = Color(Brand.WHITE_MUTED),
                                maxLines = 1,
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Button(onClick = { if (listening) monitor.stop() else monitor.start() }) {
                    Text(if (listening) "Stop" else "Listen", maxLines = 1)
                }
            }

            // Full-width deviation display
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.60f),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PartialBellCanvas(
                        monitor = monitor,
                        armedSlot = armedSlot,
                        captured = captured,
                        referenceSlot = referenceSlot,
                        liveSlot = liveSlot,
                        currentModels = currentModels,
                        enabled = enabledPartials,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    // Fundamental frequency under the center line
                    if (captured.size == StringSlot.entries.size && enabledPartials.isEmpty()) {
                        Text(
                            "Arm a partial to align on",
                            color = Color(Brand.WHITE_MUTED),
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    val etTarget = temperament.frequencyOf(midi, referenceHz)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp),
                    ) {
                        // The SOUNDING first partial, f1*sqrt(1+B) — not the
                        // bare model parameter f1. This is exactly the
                        // quantity the hub's A4 measurement produces, so the
                        // two readings are directly comparable: measure A4 in
                        // the hub, analyse A4 here, and the numbers agree.
                        val soundingHz = levels.firstOrNull()?.measuredHz
                            ?: model?.partialHz(1)
                        val held = !modelFresh
                        Text(
                            soundingHz?.let { "%.2f Hz".format(it) } ?: "—",
                            color = if (held) Color(Brand.WHITE_MUTED) else Color(Brand.WHITE),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        // In focus mode the headline is the beat AT THE
                        // WORKING PARTIAL, not at the fundamental: that is
                        // what is being nulled, and it is k times larger.
                        // With a partial armed the headline is that partial's
                        // figures. Falling back to the fundamental here put a
                        // number ten times too small under a display of
                        // partial 10.
                        val focusK = enabledPartials.singleOrNull()
                        if (focusK != null && armedDelta != null && armedBeat != null) {
                            val f0Nom = temperament.frequencyOf(midi, referenceHz)
                            val name = Notes.name(Notes.nearestMidi(focusK * f0Nom, referenceHz))
                            Text(
                                "$name · %+.1f ¢ · %.2f Hz beat".format(armedDelta, armedBeat),
                                color = if (armedBeat!! < 0.05) Color(Brand.WHITE) else Color(Brand.ORANGE),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        } else if (focusK == null && refDelta != null && refBeat != null) {
                            Text(
                                "%+.1f ¢ vs ref   ·   %.2f Hz beat".format(refDelta, refBeat),
                                color = Color(Brand.ORANGE),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            if (soundingHz == null) "" else buildString {
                                append("%+.1f ¢ vs ET".format(Notes.centsOff(soundingHz, etTarget)))
                                append("   ·   A4 %.1f".format(referenceHz))
                                model?.let { append("   ·   B = %.1e".format(it.b)) }
                                // Diagnostic: what the microphone is actually
                                // delivering, and what the detector hears,
                                // before any gating.
                                append("   ·   in %.0f dB".format(inputLevelDb))
                                rawDetectHz?.let { append(" · det %.0f Hz".format(it)) }
                                // Model quality: large residual = the stiff-string
                                // model is not describing this string well, so f0/B
                                // are not trustworthy to their printed precision.
                                fitResidual?.let { append("   ·   res %.1f ¢".format(it)) }
                                append(if (fitResidual != null) "" else "   ·   res —")
                                if (held) append("   ·   HELD")
                            },
                            color = Color(Brand.WHITE_MUTED),
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Floating partial bar. Which partials are worth tuning on is a
            // property of the sounding note, not a fixed set: it is decided
            // by whether a partial sustains above the trust threshold long
            // enough to work on. Tunable ones are offered; the highest is
            // marked, because the beat at partial k is k times the beat at
            // the fundamental — the fundamental is the weakest detector of
            // a unison error and the highest reliable partial the strongest.
            val complete = captured.size == StringSlot.entries.size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
              ) {
                val f0 = temperament.frequencyOf(midi, referenceHz)
                // Offer the union after sampling, not the intersection: a
                // partial dropped by one string must be visible as dropped
                // and attributable, or it simply disappears and the operator
                // is left guessing which threshold to loosen.
                val offer = if (complete) candidates else (1..monitor.partialCount).toSet()
                for (k in offer.sorted()) {
                    val isTunable = k in usable
                    val isOn = k in enabledPartials
                    val isBest = k == recommended
                    val secs = sustain.getOrNull(k - 1) ?: 0.0
                    // pitch name of the partial, the handle a tuner thinks in
                    val label = Notes.name(Notes.nearestMidi(k * f0, referenceHz))
                    PartialChip(
                        index = k,
                        label = label,
                        enabled = isOn,
                        tunable = isTunable,
                        recommended = isBest,
                        sustainSeconds = secs,
                        beatHz = unisonBeats[k],
                        missingOn = missingOn[k].orEmpty(),
                        // After sampling the operator picks ONE partial to
                        // align on; before it, partials are toggles.
                        onClick = {
                            // Selecting a partial one string cannot deliver
                            // would give an alignment view with a missing mark.
                            if (complete && missingOn[k].orEmpty().isEmpty()) {
                                monitor.selectOnlyPartial(k)
                            } else if (!complete) {
                                monitor.togglePartial(k)
                            }
                        },
                    )
                }
              }
                // Pinned outside the scrolling row: its position must not
                // depend on how many partials qualified, nor on where the row
                // happens to be scrolled.
                if (complete) {
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = { monitor.selectOnlyPartial(1) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "⤺ 1",
                            fontSize = 11.sp,
                            color = Color(Brand.WHITE_MUTED),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Deviation display: x axis is cents deviation from each partial's harmonic
 * target, +-[CENTS_RANGE] across the width; center = in tune. Each audible
 * partial with a refined frequency is drawn as a Gaussian bell at its
 * deviation, height = its meter level under the current gain/floor.
 */
@Composable
private fun PartialBellCanvas(
    monitor: PartialMonitor,
    armedSlot: StringSlot?,
    captured: Map<StringSlot, at.clavierhaus.unisonmaster.model.CapturedString>,
    referenceSlot: StringSlot?,
    liveSlot: StringSlot?,
    currentModels: Map<StringSlot, at.clavierhaus.unisonmaster.dsp.StringModel>,
    enabled: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val levels by monitor.levels.collectAsState()
    val enabled by monitor.enabledPartials.collectAsState()
    // read so bells rescale when gain/floor change
    monitor.gainDb.collectAsState().value
    monitor.floorDb.collectAsState().value

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val plotBottom = h * 0.86f
        // While a string is being sampled the bells take that string's hue,
        // so it is unambiguous which string the picture belongs to.
        val baseColor = when (armedSlot) {
            StringSlot.LEFT -> Color(Brand.STRING_LEFT)
            StringSlot.CENTER -> Color(Brand.STRING_CENTRE)
            StringSlot.RIGHT -> Color(Brand.STRING_RIGHT)
            null -> Color(Brand.ORANGE)
        }

        // --- Focus view: one partial, three strings -----------------------
        //
        // Once the strings are sampled the work is alignment at a single
        // partial: three marks that must be brought together. Everything
        // else is removed, and the axis is scaled to the spread actually
        // present, so the last fraction of a hertz is visible rather than
        // compressed into a pixel. The axis is in HERTZ — the beat — because
        // that is the quantity the ear nulls, and at partial k it is k times
        // the pitch difference.
        val samplingComplete = captured.size == StringSlot.entries.size
        // Focus view only once the three strings are sampled AND a partial
        // has been armed. Before that the operator is sampling and must be
        // able to watch the string being measured, so the live spectrum is
        // drawn as usual; after it, with nothing armed, the field is empty.
        // One partial armed -> alignment view. Several -> the whole usable
        // spectrum, each partial's three marks, which is the default after
        // sampling.
        val focusK = if (samplingComplete) enabled.singleOrNull() else null
        val refModel = referenceSlot?.let { captured[it]?.model }
        if (focusK != null && refModel != null && captured.isNotEmpty()) {
            val refHz = refModel.partialHz(focusK)

            fun hueOf(slot: StringSlot) = when (slot) {
                StringSlot.LEFT -> Color(Brand.STRING_LEFT)
                StringSlot.CENTER -> Color(Brand.STRING_CENTRE)
                StringSlot.RIGHT -> Color(Brand.STRING_RIGHT)
            }

            // Scale is set by the SAMPLED spread and then held. Including the
            // live mark would shrink the range as the string is pulled in, so
            // the mark would appear not to move — the scale would cancel out
            // exactly the progress it is meant to show. A unison also starts
            // near-even, so the outer strings are placed at a comfortable
            // fraction of the width rather than at the edge.
            val sampledSpread = captured.entries
                .filter { it.key != referenceSlot }
                .maxOfOrNull { abs(it.value.model.partialHz(focusK) - refHz) } ?: 0.0
            val range = maxOf(sampledSpread * 1.6, 0.5)
            val sigma = w / 55f

            // Reference last: it is the axis and must never be hidden behind
            // another mark, which arbitrary map order allowed.
            val drawOrder = captured.entries.sortedBy { it.key == referenceSlot }
            for ((slot, cap) in drawOrder) {
                val isRef = slot == referenceSlot
                val isLive = slot == liveSlot
                // The sounding string is one of these three, not a fourth
                // object: its own mark moves.
                // Live: where the string is this instant. Otherwise its most
                // recent good measurement, falling back to the capture. A
                // string already brought into tune must not spring back to
                // where it was when it was sampled.
                // The reference is pinned at zero by construction. It is the
                // axis, so it cannot be displaced from itself: drawing it
                // from a "current" position while the axis uses its captured
                // one put the orange mark off centre, which is exactly the
                // thing that must never happen.
                val offsetHz = when {
                    isRef -> 0.0
                    isLive -> levels.firstOrNull { it.index == focusK }?.measuredHz?.minus(refHz)
                        ?: ((currentModels[slot] ?: cap.model).partialHz(focusK) - refHz)
                    else -> (currentModels[slot] ?: cap.model).partialHz(focusK) - refHz
                }
                // White means aligned with the reference — and only that. The
                // reference keeps its own colour: it cannot be "aligned with
                // itself", and colouring it white would spend the one
                // unambiguous signal on the one mark that never moves.
                val aligned = !isRef && abs(offsetHz) < COINCIDENCE_HZ
                val col = if (aligned) Color(Brand.WHITE) else hueOf(slot)
                val xc = (w / 2f + (offsetHz / range).toFloat() * (w / 2f)).coerceIn(0f, w)
                // Height encodes WHICH PARTIAL is armed: the same for all
                // three marks, so position remains the only difference
                // between strings, but rising with the partial number so that
                // switching partials is unmistakable and the higher, more
                // sensitive partials stand tallest. Without this every
                // partial looked identical and only the label distinguished
                // them.
                val heightFraction =
                    (0.40f + 0.55f * (focusK - 1) / (monitor.partialCount - 1).toFloat())
                        .coerceIn(0.40f, 0.95f)
                val peak = heightFraction * plotBottom

                val path = Path()
                val x0 = (xc - 4.5f * sigma).coerceAtLeast(0f)
                val x1 = (xc + 4.5f * sigma).coerceAtMost(w)
                path.moveTo(x0, plotBottom)
                var x = x0
                while (x <= x1) {
                    val d = (x - xc) / sigma
                    path.lineTo(x, plotBottom - peak * exp((-0.5f * d * d).toDouble()).toFloat())
                    x += 2.5f
                }
                path.lineTo(x1, plotBottom)
                path.close()
                drawPath(path, col.copy(alpha = if (isLive) 0.34f else 0.20f))
                drawPath(path, col, style = Stroke(width = if (isLive) 3.5f else 2f))
            }
        } else {
            // Either sampling is still in progress, or several partials are
            // armed: draw each selected partial's marks. Empty only when
            // sampling is done and nothing at all is selected.

        // --- Sampling view -------------------------------------------------
        // Shown while the strings are still being sampled: the live spectrum
        // of the string under measurement, plus any strings already sampled
        // as fixed marks, so the operator can see what is being captured.
        // Anchored on whichever slot holds the reference role, or on the
        // first sampled string before a role exists.
        val anchor = referenceSlot?.let { captured[it]?.model }
            ?: captured.values.firstOrNull()?.model
        if (anchor != null) {
            val order = listOf(StringSlot.LEFT, StringSlot.CENTER, StringSlot.RIGHT)
            // Positions per partial, so coincidence can be detected.
            val positions = HashMap<StringSlot, MutableMap<Int, Double>>()
            for (slot in order) {
                val cap = captured[slot] ?: continue
                val m = HashMap<Int, Double>()
                for (k in enabled) m[k] = Notes.centsOff(cap.model.partialHz(k), anchor.partialHz(k))
                positions[slot] = m
            }
            for (slot in order) {
                val cap = captured[slot] ?: continue
                val hue = when (slot) {
                    StringSlot.LEFT -> Color(Brand.STRING_LEFT)
                    StringSlot.CENTER -> Color(Brand.STRING_CENTRE)
                    StringSlot.RIGHT -> Color(Brand.STRING_RIGHT)
                }
                for (k in enabled.sorted()) {
                    val cents = positions[slot]?.get(k) ?: continue
                    // Coincidence: another sampled string sitting on this one
                    // within the tolerance is the state being tuned toward,
                    // so both render white.
                    val coincides = order.any { other ->
                        other != slot &&
                            positions[other]?.get(k)?.let { abs(it - cents) < COINCIDENCE_CENTS } == true
                    }
                    val db = cap.levelsDb[k] ?: -60.0
                    val frac = monitor.barFraction(db).toFloat().coerceAtLeast(0.18f)
                    val xc = (w / 2f + (cents / CENTS_RANGE).toFloat() * (w / 2f)).coerceIn(0f, w)
                    val sigma = w / 90f
                    val peak = frac * plotBottom * 0.9f
                    val col = if (coincides) Color(Brand.WHITE) else hue
                    val path = Path()
                    val x0 = (xc - 4.5f * sigma).coerceAtLeast(0f)
                    val x1 = (xc + 4.5f * sigma).coerceAtMost(w)
                    path.moveTo(x0, plotBottom)
                    var x = x0
                    while (x <= x1) {
                        val d = (x - xc) / sigma
                        path.lineTo(x, plotBottom - peak * exp((-0.5f * d * d).toDouble()).toFloat())
                        x += 2.5f
                    }
                    path.lineTo(x1, plotBottom)
                    path.close()
                    drawPath(path, col.copy(alpha = 0.20f))
                    drawPath(path, col, style = Stroke(width = 2.5f))
                }
            }
        }

            // --- Live measurement --------------------------------------------
            for (p in levels) {
                if (p.index !in enabled) continue
                val measured = p.measuredHz ?: continue
                val frac = monitor.barFraction(p.levelDb).toFloat()
                if (frac < 0.02f) continue
                val cents = Notes.centsOff(measured, p.frequencyHz)
                val xc = (w / 2f + (cents / CENTS_RANGE).toFloat() * (w / 2f))
                    .coerceIn(0f, w)
                val sigma = w / 80f
                val peak = frac * plotBottom * 0.94f
                // Multi-string partials render white, single-string orange
                val bellColor = if (p.multiString) Color(Brand.WHITE) else baseColor

                val path = Path()
                val x0 = (xc - 4.5f * sigma).coerceAtLeast(0f)
                val x1 = (xc + 4.5f * sigma).coerceAtMost(w)
                path.moveTo(x0, plotBottom)
                var x = x0
                while (x <= x1) {
                    val d = (x - xc) / sigma
                    val y = plotBottom - peak * exp((-0.5f * d * d).toDouble()).toFloat()
                    path.lineTo(x, y)
                    x += 2.5f
                }
                path.lineTo(x1, plotBottom)
                path.close()
                drawPath(path, bellColor.copy(alpha = 0.22f))
                drawPath(path, bellColor, style = Stroke(width = 2f))
            }

            }

        // Center line on top of the bells; starts below the frequency label
        drawLine(
            color = Color(Brand.WHITE),
            start = Offset(w / 2f, h * 0.13f),
            end = Offset(w / 2f, plotBottom),
            strokeWidth = 1.5f,
        )
        // Baseline
        drawLine(
            color = Color(Brand.WHITE).copy(alpha = 0.25f),
            start = Offset(0f, plotBottom),
            end = Offset(w, plotBottom),
            strokeWidth = 1f,
        )
    }
}

/** Two sampled strings within this are treated as coincident. */
private const val COINCIDENCE_CENTS = 0.6

/** In the focus view, a string within this of the reference is aligned.
    0.05 Hz is roughly one beat per twenty seconds — beyond hearing. */
private const val COINCIDENCE_HZ = 0.05

private const val CENTS_RANGE = 20.0 // half-width in cents: unison scale, 1 cent clearly visible

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun HallBackground() {
    Image(
        painter = painterResource(R.drawable.clavierhaus_hall),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.70f),
                    0.55f to Color.Black.copy(alpha = 0.40f),
                    1f to Color.Black.copy(alpha = 0.25f),
                )
            )
    )
}

@Composable
private fun AppTitle(compact: Boolean = false) {
    Column(horizontalAlignment = if (compact) Alignment.End else Alignment.Start) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color(Brand.ORANGE))) { append("Unison") }
                withStyle(SpanStyle(color = Color(Brand.WHITE))) { append("Master") }
            },
            fontSize = if (compact) 18.sp else 26.sp,
            fontWeight = FontWeight.Bold,
        )
        if (!compact) {
            Text(
                "clavierhaus.at — partial analysis C2–C6",
                fontSize = 12.sp,
                color = Color(Brand.WHITE_MUTED),
            )
        }
    }
}

@Composable
private fun StringSlotButton(
    slot: StringSlot,
    isSet: Boolean,
    isArmed: Boolean,
    isSuspect: Boolean,
    isReference: Boolean,
    blows: Int,
    blowsRequired: Int,
    onClick: () -> Unit,
) {
    // Hue carries identity: which string. Never magnitude.
    val hue = when (slot) {
        StringSlot.LEFT -> Color(Brand.STRING_LEFT)
        StringSlot.CENTER -> Color(Brand.STRING_CENTRE)
        StringSlot.RIGHT -> Color(Brand.STRING_RIGHT)
    }
    val name = when (slot) {
        StringSlot.LEFT -> "Left"
        StringSlot.CENTER -> "Center"
        StringSlot.RIGHT -> "Right"
    }
    when {
        // State 3 — captured. Solid in its own colour; tap releases.
        isSet -> Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                // A capture whose inharmonicity does not match the other
                // strings did not measure this string cleanly; say so rather
                // than let it sit there looking authoritative.
                containerColor = if (isSuspect) Color(Brand.ALERT_RED) else hue,
                contentColor = if (isSuspect) Color(Brand.WHITE) else Color(Brand.BLACK),
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 3.dp),
        ) {
            Text(
                when {
                    isSuspect -> "RE-DO ⚠"
                    // The reference role can sit on any of the three; it is
                    // assigned at comparison time, not by position.
                    isReference -> "REF ◎"
                    else -> "SET ✓"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }

        // State 2 — listening. Must be unmistakable against state 1: the
        // whole button pulses, not just the label, and it counts the strikes
        // so the operator can see progress rather than guess.
        isArmed -> {
            val pulse = rememberInfiniteTransition(label = "arm")
            val fill by pulse.animateFloat(
                initialValue = 0.18f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(560, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "armFill",
            )
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = hue.copy(alpha = fill),
                    contentColor = Color(Brand.WHITE),
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 3.dp),
            ) {
                Text(
                    "$name  ${blows.coerceAtMost(blowsRequired)}/$blowsRequired",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        // State 1 — idle.
        else -> OutlinedButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 3.dp),
        ) {
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = hue, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun PartialChip(
    index: Int,
    label: String,
    enabled: Boolean,
    tunable: Boolean,
    recommended: Boolean,
    sustainSeconds: Double,
    beatHz: Double?,
    missingOn: Set<StringSlot>,
    onClick: () -> Unit,
) {
    // Grey/desaturated = below the trust gate or too short-lived to tune on.
    // This reads independently of any brightness ramp used elsewhere.
    // Orange marks an ACTIVE partial — one of the set the software and the
    // ear agree is usable. White is usable but not selected; grey is not
    // usable. The recommendation loses its own colour: once the usable set
    // is the statement, a second highlight inside it only competes with it.
    val tint = when {
        missingOn.isNotEmpty() -> Color(Brand.WHITE).copy(alpha = 0.28f)
        !tunable -> Color(Brand.WHITE).copy(alpha = 0.28f)
        enabled -> Color(Brand.ORANGE)
        else -> Color(Brand.WHITE)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (enabled) {
            Button(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tint,
                    contentColor = Color(Brand.BLACK),
                ),
            ) {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(label, fontSize = 11.sp, color = tint, maxLines = 1, softWrap = false)
            }
        }
        Text(
            when {
                // Dropped by a string: name which, so the cause is visible
                // instead of the partial silently disappearing.
                missingOn.isNotEmpty() -> "$index ✕ " + missingOn.joinToString("") {
                    when (it) {
                        StringSlot.LEFT -> "L"
                        StringSlot.CENTER -> "C"
                        StringSlot.RIGHT -> "R"
                    }
                }
                // Once the unison is captured, the beat at this partial is
                // the number that matters — it is what the ear hears.
                beatHz != null -> "$index · %.1f Hz".format(beatHz)
                recommended -> "★ $index"
                !tunable -> "$index"
                sustainSeconds.isFinite() -> "$index · ${sustainSeconds.toInt()}s"
                else -> "$index"
            },
            fontSize = 8.sp,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        modifier = Modifier.width(40.dp),
    ) {
        Text(label, maxLines = 1, fontSize = 16.sp)
    }
}

@Composable
private fun ReferencePitchSection(
    referenceHz: Double,
    measuring: Boolean,
    measuredHz: Double?,
    dispersionHz: Double?,
    estimateCount: Int,
    onSet: (Double) -> Unit,
    onMeasure: () -> Unit,
) {
    var text by remember(referenceHz) { mutableStateOf(formatHz(referenceHz)) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Reference A4", color = Color(Brand.WHITE), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(
            "${formatHz(referenceHz)} Hz",
            color = Color(Brand.ORANGE),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    Slider(
        value = referenceHz.toFloat(),
        onValueChange = { onSet((it * 10).roundToInt() / 10.0) }, // 0.1 Hz steps
        valueRange = TuningController.MIN_REFERENCE_HZ.toFloat()..TuningController.MAX_REFERENCE_HZ.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = Color(Brand.ORANGE),
            activeTrackColor = Color(Brand.ORANGE),
            inactiveTrackColor = Color(Brand.WHITE).copy(alpha = 0.25f),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("exact", maxLines = 1) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { text.toDoubleOrNull()?.let(onSet) }) {
            Text("Set", maxLines = 1)
        }
        OutlinedButton(onClick = onMeasure) {
            Text(if (measuring) "Accept" else "Measure", maxLines = 1, softWrap = false)
        }
    }
    val status = when {
        // Only invite Accept once it will actually be honoured. Inviting it
        // earlier and then refusing silently is how a measured 441.29 Hz was
        // discarded and the reference reverted to 440.
        measuring && measuredHz != null && estimateCount >= TuningController.MIN_ADOPTABLE_ESTIMATES ->
            "${"%.2f".format(measuredHz)} Hz · n=$estimateCount · tap Accept to take it"
        measuring && measuredHz != null ->
            "${"%.2f".format(measuredHz)} Hz · n=$estimateCount · keep it ringing"
        measuring -> "waiting for tone — play A4, one string"
        measuredHz != null && dispersionHz != null ->
            "measured: ${"%.2f".format(measuredHz)} Hz · spread ${"%.3f".format(dispersionHz)} Hz"
        else -> null
    }
    if (status != null) {
        Text(
            status,
            color = Color(Brand.ORANGE).copy(alpha = 0.9f),
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

private fun formatHz(hz: Double): String {
    val tenths = (hz * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

@Composable
fun ScopeScreen(monitor: PartialMonitor, controller: TuningController, onBack: () -> Unit) {
    val listening by monitor.listening.collectAsState()
    val detected by monitor.rawDetectHz.collectAsState()
    val refA4 by controller.referenceA4Hz.collectAsState()
    var frame by remember { mutableStateOf(0) }
    var heldHz by remember { mutableStateOf(0.0) }

    LaunchedEffect(detected) {
        val d = detected
        if (d != null && d > 0.0) { heldHz = d; monitor.scope.tune(d) }
    }
    LaunchedEffect(Unit) {
        monitor.start()
        while (true) { withFrameNanos { }; frame++ }
    }
    DisposableEffect(Unit) { onDispose { monitor.stop() } }

    Box(Modifier.fillMaxSize()) {
        HallBackground()
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("\u2039 Hub", color = Color(Brand.ORANGE), fontSize = 18.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    (if (heldHz > 0.0) heldHz else detected)?.let {
                        "${Notes.name(Notes.nearestMidi(it, refA4))}  \u00b7  %.1f Hz".format(it)
                    } ?: "listening\u2026",
                    color = Color(Brand.WHITE), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (listening) monitor.stop() else monitor.start() }) {
                    Text(if (listening) "Stop" else "Listen", maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxSize()) {
                frame.let { }
                val w = size.width; val h = size.height; val mid = h / 2f
                drawLine(Color(Brand.WHITE_MUTED).copy(alpha = 0.3f), Offset(0f, mid), Offset(w, mid), 1f)
                val hz = if (heldHz > 0.0) heldHz else (detected ?: return@Canvas)
                val span = (monitor.scope.sampleRateForDraw() / hz * 4.0).toInt().coerceIn(200, 6000)
                val wnd = monitor.scope.window(span) ?: return@Canvas
                val path = Path()
                for (i in wnd.indices) {
                    val x = w * i / (wnd.size - 1).toFloat()
                    val y = mid - wnd[i] * (h * 0.42f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Color(Brand.ORANGE), style = Stroke(width = 2.5f))
            }
        }
    }
}
