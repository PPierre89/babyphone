package fr.penpenic.babyphone.feature.parent

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.penpenic.babyphone.core.network.lan.BabyEndpoint
import fr.penpenic.babyphone.core.network.lan.LanDiscovery
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Écran du téléphone parent : choisir le téléphone bébé, puis suivre l'écoute. */
@Composable
fun ParentScreen(modifier: Modifier = Modifier) {
    val snapshot by ParentListenState.snapshot.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.parent_title), style = MaterialTheme.typography.headlineMedium)
        if (snapshot.status == ListenStatus.IDLE) BabyPicker() else Listening(snapshot)
    }
}

@Composable
private fun BabyPicker() {
    val context = LocalContext.current
    val discovery = remember { LanDiscovery(context) }
    val babies by discovery.discover().collectAsStateWithLifecycle(initialValue = emptyList())
    var manual by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<BabyEndpoint?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // L'écoute fonctionne même si les notifications sont refusées ; seule l'alerte de coupure en dépend.
        pending?.let { ParentListenService.start(context, it.host, it.port, it.name) }
    }
    fun listen(endpoint: BabyEndpoint) {
        pending = endpoint
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ParentListenService.start(context, endpoint.host, endpoint.port, endpoint.name)
        }
    }

    Text(stringResource(R.string.parent_pick), style = MaterialTheme.typography.titleMedium)
    if (babies.isEmpty()) {
        Text(stringResource(R.string.parent_searching), style = MaterialTheme.typography.bodyMedium)
    }
    babies.forEach { baby ->
        Card(onClick = { listen(baby) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(baby.name, style = MaterialTheme.typography.titleMedium)
                Text("${baby.host}:${baby.port}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    HorizontalDivider()
    Text(stringResource(R.string.parent_manual), style = MaterialTheme.typography.bodyMedium)
    val parsed = parseAddress(manual)
    OutlinedTextField(
        value = manual,
        onValueChange = { manual = it.trim() },
        label = { Text(stringResource(R.string.parent_manual_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        isError = manual.isNotEmpty() && parsed == null,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { parsed?.let(::listen) }, enabled = parsed != null) {
        Text(stringResource(R.string.parent_listen))
    }
}

@Composable
private fun Listening(snapshot: ListenSnapshot) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val statusText = when (snapshot.status) {
        ListenStatus.CONNECTING -> stringResource(R.string.parent_status_connecting)
        ListenStatus.LISTENING -> stringResource(R.string.parent_status_listening)
        ListenStatus.RECONNECTING -> stringResource(R.string.parent_status_reconnecting)
        ListenStatus.UNREACHABLE -> stringResource(R.string.parent_status_unreachable)
        ListenStatus.IDLE -> ""
    }
    val alarming = snapshot.status == ListenStatus.UNREACHABLE
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (alarming) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(snapshot.babyName.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            Text(statusText, style = MaterialTheme.typography.headlineSmall)
        }
    }

    val dash = "—"
    Metric(stringResource(R.string.parent_metric_duration), snapshot.startedAtMillis?.let { formatDuration(now - it) } ?: dash)
    Metric(stringResource(R.string.parent_metric_latency), snapshot.latencyMs?.let { "$it ms" } ?: dash)
    Metric(stringResource(R.string.parent_metric_loss), snapshot.lossPercent?.let { String.format(Locale.FRANCE, "%.1f %%", it) } ?: dash)
    Metric(stringResource(R.string.parent_metric_interruptions), snapshot.interruptions.toString())
    Metric(
        stringResource(R.string.parent_metric_battery),
        snapshot.babyBattery?.let { level ->
            "$level %" + if (snapshot.babyCharging == true) " · " + stringResource(R.string.parent_charging) else ""
        } ?: dash,
    )
    Metric(
        stringResource(R.string.parent_metric_noise),
        snapshot.lastNoiseAtMillis?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: dash,
    )

    OutlinedButton(onClick = { ParentListenService.stop(context) }) {
        Text(stringResource(R.string.parent_stop))
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** « 192.168.1.20:41234 » → point d'accès, ou null si la saisie est invalide. */
internal fun parseAddress(text: String): BabyEndpoint? {
    val match = Regex("""^(\d{1,3}(?:\.\d{1,3}){3}):(\d{1,5})$""").matchEntire(text) ?: return null
    val (host, portText) = match.destructured
    if (host.split('.').any { it.toInt() > 255 }) return null
    val port = portText.toInt().takeIf { it in 1..65535 } ?: return null
    return BabyEndpoint(name = host, host = host, port = port)
}

internal fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return String.format(Locale.FRANCE, "%d h %02d min %02d s", totalSeconds / 3600, totalSeconds % 3600 / 60, totalSeconds % 60)
}
