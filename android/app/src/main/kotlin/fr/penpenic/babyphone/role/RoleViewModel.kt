package fr.penpenic.babyphone.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RoleUiState {
    data object Loading : RoleUiState
    data class Ready(val role: Role?) : RoleUiState
}

@HiltViewModel
class RoleViewModel @Inject constructor(private val repository: RoleRepository) : ViewModel() {

    val state: StateFlow<RoleUiState> = repository.role
        .map<Role?, RoleUiState> { RoleUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoleUiState.Loading)

    fun choose(role: Role?) {
        viewModelScope.launch { repository.setRole(role) }
    }
}
