package fr.penpenic.babyphone.feature.baby

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun BabyScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val running by BabyMonitorState.running.collectAsStateWithLifecycle()
    val level by BabyMonitorState.levelDbfs.collectAsStateWithLifecycle()
    val lastTrigger by BabyMonitorState.lastTriggerAtMillis.collectAsStateWithLifecycle()
    var micDenied by remember { mutableStateOf(false) }

    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        micDenied = result[Manifest.permission.RECORD_AUDIO] != true
        if (!micDenied) BabyMonitorService.start(context)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.baby_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.baby_plug_hint), style = MaterialTheme.typography.bodyMedium)

        if (running) {
            // -60 dBFS (calme) à 0 dBFS (très fort) ramené sur 0..1
            LinearProgressIndicator(
                progress = { ((level + 60) / 60).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.baby_level, level.roundToInt()))
            Text(
                lastTrigger?.let {
                    stringResource(R.string.baby_last_trigger, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)))
                } ?: stringResource(R.string.baby_no_trigger),
            )
            OutlinedButton(onClick = { BabyMonitorService.stop(context) }) {
                Text(stringResource(R.string.baby_stop))
            }
        } else {
            Button(onClick = { launcher.launch(permissions) }) {
                Text(stringResource(R.string.baby_start))
            }
            if (micDenied) {
                Text(stringResource(R.string.baby_mic_denied), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
