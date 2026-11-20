package fr.penpenic.babyphone.feature.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Écran du téléphone parent.
 * Phase 0 : état vide. L'appairage (phase 2) et l'écoute (phase 1) viendront s'y brancher.
 */
@Composable
fun ParentScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.parent_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.parent_no_device))
        Button(onClick = {}, enabled = false) {
            Text(stringResource(R.string.parent_pair))
        }
        Text(stringResource(R.string.parent_coming_soon), style = MaterialTheme.typography.bodySmall)
    }
}
