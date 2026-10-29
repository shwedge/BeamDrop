package com.example.model

enum class DeviceType(val label: String) {
    PHONE("Android Phone"),
    TABLET("Android Tablet"),
    LAPTOP("ChromeOS / Laptop"),
    COMPUTER("Windows PC / Desktop"),
    MACBOOK("MacBook (Nearby)")
}

data class BeamPeer(
    val id: String,
    val name: String,
    val deviceType: DeviceType = DeviceType.PHONE,
    val ipAddress: String = "192.168.49.1",
    val port: Int = 8988,
    val signalStrengthDbm: Int = -42, // strong nearby
    val isTopTouchDetected: Boolean = false,
    val isConnected: Boolean = false,
    val avatarTintHex: Long = 0xFF00F5D4
) {
    val signalPercent: Int
        get() = ((signalStrengthDbm + 100) * 1.5f).toInt().coerceIn(10, 100)
}
