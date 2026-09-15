package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand

/*
 * The settings screen in the clavierhaus convention (as in clavierhaus
 * backgammon): header with back arrow, title and subtitle; a row of tabs;
 * sections in small caps; rows with name, description and the control at
 * the right edge; thin dividers. Black, orange accent. Each app assembles
 * its own tabs from these parts.
 */

private val TAB = Color(0xFF1E1E1E)
private val TAB_ACTIVE = Color(0xFF333333)
private val CONTROL = Color(0xFF2A2A2A)

class SettingsTab(val title: String, val accent: Boolean = false, val content: @Composable ColumnScope.() -> Unit)

typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope

@Composable
fun SettingsScreen(
    subtitle: String,
    tabs: List<SettingsTab>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(
        modifier
            .fillMaxSize()
            .background(Color(Brand.BLACK))
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "<-",
                color = Color(Brand.WHITE),
                fontSize = 22.sp,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(end = 20.dp, top = 8.dp, bottom = 8.dp),
            )
            Column {
                Text("Settings", color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 26.sp)
                Text(subtitle, color = Color(Brand.WHITE_MUTED), fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(if (active) TAB_ACTIVE else TAB, RoundedCornerShape(8.dp))
                        .clickable { selected = i },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tab.title,
                        color = if (tab.accent) Color(Brand.ORANGE) else Color(Brand.WHITE),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            tabs[selected.coerceIn(tabs.indices)].content(this)
        }
    }
}

@Composable
fun SettingsSection(title: String) {
    Spacer(Modifier.height(18.dp))
    Text(title.uppercase(), color = Color(Brand.WHITE_MUTED), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(4.dp))
}

/** Explanatory paragraph inside a tab (e.g. why a section is stored but not yet applied). */
@Composable
fun SettingsNote(text: String) {
    Text(text, color = Color(Brand.WHITE_MUTED), fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun SettingsRow(name: String, description: String, control: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Color(Brand.WHITE), fontSize = 17.sp)
            if (description.isNotEmpty()) Text(description, color = Color(Brand.WHITE_MUTED), fontSize = 13.sp)
        }
        Spacer(Modifier.width(16.dp))
        control()
    }
    HorizontalDivider(color = Color(0xFF2A2A2A))
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .background(if (enabled) CONTROL else Color(0xFF181818), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color(Brand.WHITE) else Color(Brand.WHITE_MUTED), fontSize = 18.sp)
    }
}

/** − value + */
@Composable
fun StepperRow(
    name: String,
    description: String,
    value: String,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    SettingsRow(name, description) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", canDecrease, onDecrease)
            Text(
                value,
                color = Color(Brand.WHITE),
                fontFamily = DejaVuSerif,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(96.dp),
            )
            StepButton("+", canIncrease, onIncrease)
        }
    }
}

/** A small set of options, the selected one in orange. */
@Composable
fun ChoiceRow(name: String, description: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SettingsRow(name, description) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier
                        .height(36.dp)
                        .width(56.dp)
                        .background(if (on) Color(Brand.ORANGE) else CONTROL, RoundedCornerShape(6.dp))
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (on) Color(Brand.BLACK) else Color(Brand.WHITE), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun SwitchRow(name: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(name, description) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(Brand.ORANGE), checkedThumbColor = Color(Brand.BLACK)),
        )
    }
}

@Composable
fun ReadOnlyRow(name: String, description: String, value: String) {
    SettingsRow(name, description) {
        Text(value, color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 17.sp)
    }
}
