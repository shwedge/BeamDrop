package com.example.model

sealed interface TransferState {
    object Idle : TransferState

    object SearchingPeers : TransferState

    data class TouchGestureActive(
        val peerName: String,
        val auraIntensity: Float // 0f to 1f for the expanding liquid wave
    ) : TransferState

    data class HandshakeReady(
        val peer: BeamPeer,
        val stagedItems: List<SharableItem>
    ) : TransferState

    data class Transferring(
        val peer: BeamPeer,
        val currentItem: SharableItem,
        val currentItemIndex: Int,
        val totalItemCount: Int,
        val progress: Float, // 0f to 1f
        val speedMbps: Float,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val etaSeconds: Int
    ) : TransferState

    data class Completed(
        val peer: BeamPeer,
        val itemsTransferred: List<SharableItem>,
        val totalBytes: Long,
        val averageSpeedMbps: Float,
        val durationMs: Long
    ) : TransferState

    data class Failed(
        val peer: BeamPeer?,
        val reason: String
    ) : TransferState
}
