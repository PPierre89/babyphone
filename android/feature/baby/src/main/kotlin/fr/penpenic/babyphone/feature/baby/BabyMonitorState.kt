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

    /** Nombre de téléphones parents qui écoutent en ce moment. */
    private val _listeners = MutableStateFlow(0)
    val listeners: StateFlow<Int> = _listeners.asStateFlow()

    /** Adresse à saisir sur le téléphone parent si la découverte automatique échoue (ex. 192.168.1.20:41234). */
    private val _address = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = _address.asStateFlow()

    internal fun setRunning(value: Boolean) {
        _running.value = value
        if (!value) {
            _levelDbfs.value = -120.0
            _listeners.value = 0
            _address.value = null
        }
    }

    internal fun setLevel(value: Double) {
        _levelDbfs.value = value
    }

    internal fun triggered(atMillis: Long) {
        _lastTriggerAtMillis.value = atMillis
    }

    internal fun setListeners(count: Int) {
        _listeners.value = count
    }

    internal fun setAddress(value: String?) {
        _address.value = value
    }
}
