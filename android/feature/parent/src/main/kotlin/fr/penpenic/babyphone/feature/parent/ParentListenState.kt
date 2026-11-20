package fr.penpenic.babyphone.feature.parent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ListenStatus { IDLE, CONNECTING, LISTENING, RECONNECTING, UNREACHABLE }

data class ListenSnapshot(
    val status: ListenStatus = ListenStatus.IDLE,
    val babyName: String? = null,
    /** Début de l'écoute (pour la durée affichée et le test de 8 h du jalon G1). */
    val startedAtMillis: Long? = null,
    /** Nombre de coupures depuis le début de l'écoute. */
    val interruptions: Int = 0,
    val latencyMs: Int? = null,
    val lossPercent: Double? = null,
    val babyBattery: Int? = null,
    val babyCharging: Boolean? = null,
    val lastNoiseAtMillis: Long? = null,
)

/** État de l'écoute, partagé entre le service et l'écran (même processus). */
object ParentListenState {
    private val _snapshot = MutableStateFlow(ListenSnapshot())
    val snapshot: StateFlow<ListenSnapshot> = _snapshot.asStateFlow()

    internal fun update(transform: (ListenSnapshot) -> ListenSnapshot) = _snapshot.update(transform)

    internal fun reset() {
        _snapshot.value = ListenSnapshot()
    }
}
