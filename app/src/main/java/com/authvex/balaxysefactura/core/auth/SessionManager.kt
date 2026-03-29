package com.authvex.balaxysefactura.core.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SessionManager {
    private val _sessionExpiredEvent = MutableSharedFlow<Unit>()
    val sessionExpiredEvent = _sessionExpiredEvent.asSharedFlow()

    suspend fun emitSessionExpired() {
        _sessionExpiredEvent.emit(Unit)
    }
}
