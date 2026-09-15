package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.tuning.PartialSelection
import at.clavierhaus.unisonmaster.ui.ClavierhausTitle
import at.clavierhaus.unisonmaster.ui.DoneButton
import at.clavierhaus.unisonmaster.ui.HubHint
import at.clavierhaus.unisonmaster.ui.PartialRow
import at.clavierhaus.unisonmaster.ui.SettingsGear
import at.clavierhaus.unisonmaster.ui.SpectrumToggle
import at.clavierhaus.unisonmaster.ui.ToneGraph

/** Hub: define A4 on one string; fundamental or full spectrum; Done. */
@Composable
fun BasicHub(controller: TuningController) {
    val hz by controller.liveHz.collectAsState()
    val level by controller.liveLevel.collectAsState()
    val partials by controller.livePartials.collectAsState()
    val audible by controller.liveAudible.collectAsState()
    val full by controller.fullSpectrum.collectAsState()
    val hidden by controller.hiddenPartials.collectAsState()
    val a4 by controller.referenceA4Hz.collectAsState()
    val shown = PartialSelection.shown(full, audible, hidden)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(Brand.BLACK))
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ToneGraph(
            hz = hz,
            level = level.toFloat(),
            fullSpectrum = full,
            partials = partials,
            shown = shown,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
        )
        Column(
            Modifier
                .align(Alignment.TopStart)
                .width(300.dp),
        ) {
            SettingsGear(onClick = { })
            Spacer(Modifier.height(10.dp))
            ClavierhausTitle()
            Spacer(Modifier.height(18.dp))
            HubHint("Define your A4 here by tuning a single string to the desired pitch.")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
        ) {
            SpectrumToggle(fullSpectrum = full, onClick = { controller.toggleFullSpectrum() })
            Spacer(Modifier.width(10.dp))
            PartialRow(
                a4Hz = a4,
                shown = shown,
                tappable = if (full) audible else emptySet(),
                onTap = { k -> controller.tapPartial(k) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            DoneButton(onClick = { controller.acceptLive() }, enabled = hz != null)
        }
    }
}
