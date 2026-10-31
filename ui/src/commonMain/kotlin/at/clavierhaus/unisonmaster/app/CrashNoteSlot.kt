package at.clavierhaus.unisonmaster.app

import androidx.compose.runtime.Composable

/** The note of the last crash, shown once on start (Android's CrashNote); a platform without it draws nothing. */
@Composable
expect fun CrashNoteDialog()
