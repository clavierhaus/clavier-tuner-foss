package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.ui.ClavierhausTitle
import at.clavierhaus.unisonmaster.ui.DoneButton
import at.clavierhaus.unisonmaster.ui.FundamentalBell

/** Hub: title, the fundamental on the target line, and Done. */
@Composable
fun BasicHub(controller: TuningController) {
    val hz by controller.liveHz.collectAsState()
    val level by controller.liveLevel.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(Brand.BLACK))
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ClavierhausTitle(Modifier.align(Alignment.TopStart))
        FundamentalBell(
            hz = hz,
            level = level.toFloat(),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, bottom = 64.dp),
        )
        DoneButton(
            onClick = { controller.acceptLive() },
            enabled = hz != null,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
