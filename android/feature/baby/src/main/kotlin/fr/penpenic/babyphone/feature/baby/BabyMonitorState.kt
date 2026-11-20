package fr.penpenic.babyphone.feature.baby

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** État de la surveillance, partagé entre le service et l'écran (même processus). */
object BabyMonitorState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _levelDbfs = MutableStateFlow(-120.0)
    val levelDbfs: StateFlow<Double> = _levelDbfs.asStateFlow()

    private val _lastTriggerAtMillis = MutableStateFlow<Long?>(null)
    val lastTriggerAtMillis: StateFlow<Long?> = _lastTriggerAtMillis.asStateFlow()

    internal fun setRunning(value: Boolean) {
        _running.value = value
        if (!value) _levelDbfs.value = -120.0
    }

    internal fun setLevel(value: Double) {
        _levelDbfs.value = value
    }

    internal fun triggered(atMillis: Long) {
        _lastTriggerAtMillis.value = atMillis
    }
}
