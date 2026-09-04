package com.cody.home.network

/** Transport-level connection state — deliberately separate from [com.cody.home.state.CodyState]. */
sealed class GatewayConnectionState {
    data object NotPaired : GatewayConnectionState()
    data object Disconnected : GatewayConnectionState()
    data object Connecting : GatewayConnectionState()
    data object Authenticating : GatewayConnectionState()
    data object Connected : GatewayConnectionState()
    data class Error(val reason: String) : GatewayConnectionState()
}
