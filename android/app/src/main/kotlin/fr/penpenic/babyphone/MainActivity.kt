package fr.penpenic.babyphone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.penpenic.babyphone.feature.baby.BabyScreen
import fr.penpenic.babyphone.feature.parent.ParentScreen
import fr.penpenic.babyphone.role.Role
import fr.penpenic.babyphone.role.RoleSelectionScreen
import fr.penpenic.babyphone.role.RoleUiState
import fr.penpenic.babyphone.role.RoleViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val roleViewModel: RoleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Thème sombre par défaut la nuit : moins de lumière dans la chambre.
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                val state by roleViewModel.state.collectAsStateWithLifecycle()
                BabyphoneRoot(state = state, onRoleChosen = roleViewModel::choose)
            }
        }
    }
}

@Composable
private fun BabyphoneRoot(state: RoleUiState, onRoleChosen: (Role?) -> Unit) {
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                RoleUiState.Loading -> Unit
                is RoleUiState.Ready -> when (state.role) {
                    null -> RoleSelectionScreen(onRoleChosen = onRoleChosen)
                    Role.BABY -> BabyScreen()
                    Role.PARENT -> ParentScreen()
                }
            }
            if (state is RoleUiState.Ready && state.role != null) {
                TextButton(onClick = { onRoleChosen(null) }, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Text(stringResource(R.string.role_change))
                }
            }
        }
    }
}
