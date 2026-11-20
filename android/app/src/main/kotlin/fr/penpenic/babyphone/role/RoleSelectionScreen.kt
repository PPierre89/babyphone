package fr.penpenic.babyphone.role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.penpenic.babyphone.R

@Composable
fun RoleSelectionScreen(onRoleChosen: (Role) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.role_title), style = MaterialTheme.typography.headlineSmall)
        RoleCard(stringResource(R.string.role_baby), stringResource(R.string.role_baby_desc)) { onRoleChosen(Role.BABY) }
        RoleCard(stringResource(R.string.role_parent), stringResource(R.string.role_parent_desc)) { onRoleChosen(Role.PARENT) }
    }
}

@Composable
private fun RoleCard(title: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
