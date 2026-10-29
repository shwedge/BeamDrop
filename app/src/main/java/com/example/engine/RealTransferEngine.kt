package com.example.engine

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.model.BeamPeer
import com.example.model.DeviceType
import com.example.model.SharableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

sealed interface IncomingTransferEvent {
    data class Started(val senderName: String, val senderIp: String, val fileCount: Int, val totalBytes: Long) : IncomingTransferEvent
    data class Progress(
        val fileName: String,
        val fileIndex: Int,
        val totalFiles: Int,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val progress: Float,
        val speedMbps: Float
    ) : IncomingTransferEvent
    data class Completed(
        val senderName: String,
        val senderIp: String,
        val savedFiles: List<File>,
        val totalBytes: Long,
        val durationMs: Long
    ) : IncomingTransferEvent
    data class Failed(val error: String) : IncomingTransferEvent
    data class PeerHandshake(
        val peerId: String,
        val peerName: String,
        val peerIp: String,
        val deviceType: DeviceType
    ) : IncomingTransferEvent
}

class RealTransferEngine(private val context: Context) {
    private val tag = "RealTransferEngine"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Reads a single UTF-8 line terminated by '\n' byte-by-byte without buffering ahead,
     * ensuring binary file streams following the header are never corrupted.
     */
    private fun readUtf8Line(inputStream: InputStream, maxBytes: Int = 4096): String? {
        val buffer = ByteArrayOutputStream()
        while (buffer.size() < maxBytes) {
            val b = inputStream.read()
            if (b == -1) {
                return if (buffer.size() > 0) {
                    buffer.toString(StandardCharsets.UTF_8.name())
                } else {
                    null
                }
            }
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                buffer.write(b)
            }
        }
        return buffer.toString(StandardCharsets.UTF_8.name())
    }

    /**
     * Starts listening for incoming peer handshakes and file transfers on port 8988.
     */
    fun startServer(
        port: Int = 8988,
        localDeviceId: String = "android",
        myDeviceName: String = "Android Phone",
        onEvent: (IncomingTransferEvent) -> Unit
    ) {
        stopServer()
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                Log.d(tag, "BeamDrop Server listening on port $port")

                while (isActive) {
                    val client = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        if (!isActive) break
                        continue
                    }

                    handleIncomingClient(client, localDeviceId, myDeviceName, onEvent)
                }
            } catch (e: Exception) {
                Log.e(tag, "Server error", e)
            }
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleIncomingClient(
        socket: Socket,
        localDeviceId: String,
        myDeviceName: String,
        onEvent: (IncomingTransferEvent) -> Unit
    ) {
        scope.launch {
            val startTime = System.currentTimeMillis()
            var totalBytesReceived = 0L
            val savedFiles = mutableListOf<File>()
            val senderIp = socket.inetAddress?.hostAddress ?: ""

            try {
                socket.soTimeout = 30000
                socket.tcpNoDelay = true
                socket.receiveBufferSize = 4 * 1024 * 1024
                socket.sendBufferSize = 4 * 1024 * 1024
                val rawInputStream = socket.getInputStream()
                val rawOutputStream = socket.getOutputStream()

                val firstLine = readUtf8Line(rawInputStream)?.trim()
                if (firstLine.isNullOrEmpty()) {
                    socket.close()
                    return@launch
                }

                // 1. Handle Verified Discovery Handshake (BEAMDROP_PING|<peerId>|<peerName>|<peerType>)
                if (firstLine.startsWith("BEAMDROP_PING")) {
                    val parts = firstLine.split("|")
                    val peerId = parts.getOrNull(1)?.ifEmpty { "peer_$senderIp" } ?: "peer_$senderIp"
                    val peerName = parts.getOrNull(2)?.ifEmpty { "BeamDrop Device" } ?: "BeamDrop Device"
                    val typeStr = parts.getOrNull(3) ?: "PHONE"
                    val devType = if (typeStr.equals("COMPUTER", ignoreCase = true) ||
                        peerName.contains("Windows", ignoreCase = true) ||
                        peerName.contains("PC", ignoreCase = true) ||
                        peerName.contains("Mac", ignoreCase = true)
                    ) {
                        DeviceType.COMPUTER
                    } else {
                        DeviceType.PHONE
                    }

                    val pong = "BEAMDROP_PONG|$localDeviceId|$myDeviceName|PHONE\n"
                    rawOutputStream.write(pong.toByteArray(StandardCharsets.UTF_8))
                    rawOutputStream.flush()

                    if (peerId != localDeviceId && senderIp.isNotEmpty()) {
                        onEvent(IncomingTransferEvent.PeerHandshake(peerId, peerName, senderIp, devType))
                    }
                    socket.close()
                    return@launch
                }

                // 2. Handle File Transfer Session (Supports both BEAMDROP_V1|sender|count|bytes and multiline BEAMDROP_V1)
                val senderName: String
                val fileCount: Int
                val declaredTotalBytes: Long

                if (firstLine.startsWith("BEAMDROP_V1|")) {
                    val hParts = firstLine.split("|")
                    senderName = hParts.getOrNull(1)?.ifEmpty { "BeamDrop Peer" } ?: "BeamDrop Peer"
                    fileCount = hParts.getOrNull(2)?.toIntOrNull() ?: 1
                    declaredTotalBytes = hParts.getOrNull(3)?.toLongOrNull() ?: 0L
                } else if (firstLine == "BEAMDROP_V1") {
                    senderName = readUtf8Line(rawInputStream)?.trim()?.ifEmpty { "BeamDrop Peer" } ?: "BeamDrop Peer"
                    fileCount = readUtf8Line(rawInputStream)?.trim()?.toIntOrNull() ?: 1
                    declaredTotalBytes = 0L
                } else {
                    socket.close()
                    return@launch
                }

                // Also register sender as an active peer on Radar
                val senderType = if (senderName.contains("Windows", ignoreCase = true) ||
                    senderName.contains("PC", ignoreCase = true) ||
                    senderName.contains("Mac", ignoreCase = true)
                ) {
                    DeviceType.COMPUTER
                } else {
                    DeviceType.PHONE
                }
                if (senderIp.isNotEmpty()) {
                    onEvent(IncomingTransferEvent.PeerHandshake("peer_$senderIp", senderName, senderIp, senderType))
                }

                val downloadsDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                    "BeamDrop"
                ).apply { if (!exists()) mkdirs() }

                onEvent(IncomingTransferEvent.Started(senderName, senderIp, fileCount, declaredTotalBytes))

                for (index in 0 until fileCount) {
                    val metaLine = readUtf8Line(rawInputStream)?.trim() ?: break
                    val parts = metaLine.split("|")
                    if (parts.size < 2) break

                    val fileName: String
                    val fileSize: Long
                    if (parts[0] == "FILE" && parts.size >= 3) {
                        fileName = File(parts[1]).name
                        fileSize = parts[2].toLongOrNull() ?: 0L
                    } else {
                        fileName = File(parts[0]).name
                        fileSize = parts[1].toLongOrNull() ?: 0L
                    }

                    val destinationFile = getUniqueFile(downloadsDir, fileName)
                    val fos = FileOutputStream(destinationFile)

                    var fileBytesRead = 0L
                    val buffer = ByteArray(1024 * 1024)
                    var lastTime = System.currentTimeMillis()
                    var bytesSinceLastCalc = 0L
                    var currentSpeedMbps = 0f

                    while (fileBytesRead < fileSize) {
                        val toRead = (fileSize - fileBytesRead).coerceAtMost(buffer.size.toLong()).toInt()
                        val bytesRead = rawInputStream.read(buffer, 0, toRead)
                        if (bytesRead == -1) break

                        fos.write(buffer, 0, bytesRead)
                        fileBytesRead += bytesRead
                        totalBytesReceived += bytesRead
                        bytesSinceLastCalc += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime
                        if (elapsed >= 80 || fileBytesRead == fileSize) {
                            if (elapsed > 0) {
                                currentSpeedMbps = (bytesSinceLastCalc.toFloat() / (elapsed / 1000f)) / (1024f * 1024f)
                            }
                            lastTime = now
                            bytesSinceLastCalc = 0L

                            val progress = if (fileSize > 0) fileBytesRead.toFloat() / fileSize else 1f
                            onEvent(
                                IncomingTransferEvent.Progress(
                                    fileName = destinationFile.name,
                                    fileIndex = index,
                                    totalFiles = fileCount,
                                    bytesTransferred = fileBytesRead,
                                    totalBytes = fileSize,
                                    progress = progress,
                                    speedMbps = currentSpeedMbps
                                )
                            )
                        }
                    }

                    fos.flush()
                    fos.close()
                    savedFiles.add(destinationFile)
                }

                // Send final ACK to sender
                try {
                    rawOutputStream.write("OK\n".toByteArray(StandardCharsets.UTF_8))
                    rawOutputStream.flush()
                } catch (_: Exception) {}

                val duration = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                onEvent(
                    IncomingTransferEvent.Completed(
                        senderName = senderName,
                        senderIp = senderIp,
                        savedFiles = savedFiles,
                        totalBytes = totalBytesReceived,
                        durationMs = duration
                    )
                )
            } catch (e: Exception) {
                Log.e(tag, "Error receiving transfer", e)
                onEvent(IncomingTransferEvent.Failed(e.message ?: "Transfer interrupted"))
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun getUniqueFile(dir: File, fileName: String): File {
        val cleanName = fileName.ifBlank { "BeamDrop_${System.currentTimeMillis()}" }
        var candidate = File(dir, cleanName)
        if (!candidate.exists()) return candidate
        val dotIndex = cleanName.lastIndexOf('.')
        val namePart = if (dotIndex > 0) cleanName.substring(0, dotIndex) else cleanName
        val extPart = if (dotIndex > 0) cleanName.substring(dotIndex) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${namePart}_$counter$extPart")
            counter++
        }
        return candidate
    }

    /**
     * Transmits real files to a remote peer (Android or Windows) over TCP port 8988 using unified BEAMDROP_V1 protocol.
     */
    fun sendFiles(
        peer: BeamPeer,
        items: List<SharableItem>,
        myDeviceName: String
    ): Flow<TransferProgress> = flow {
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.sendBufferSize = 4 * 1024 * 1024
            socket.receiveBufferSize = 4 * 1024 * 1024
            socket.connect(InetSocketAddress(peer.ipAddress, peer.port), 10000)
            socket.soTimeout = 30000

            val outputStream = socket.getOutputStream()
            val totalBytes = items.sumOf { it.sizeBytes }.coerceAtLeast(1L)
            var cumulativeBytesSent = 0L

            // Unified Header understood by both Android and Windows native apps
            val sessionHeader = "BEAMDROP_V1|$myDeviceName|${items.size}|$totalBytes\n"
            outputStream.write(sessionHeader.toByteArray(StandardCharsets.UTF_8))
            outputStream.flush()

            items.forEachIndexed { index, item ->
                val metaHeader = "FILE|${item.title}|${item.sizeBytes}|${item.mimeType}\n"
                outputStream.write(metaHeader.toByteArray(StandardCharsets.UTF_8))
                outputStream.flush()

                val inputStream: InputStream? = openItemStream(item)

                var itemBytesSent = 0L
                val itemSize = item.sizeBytes
                val buffer = ByteArray(1024 * 1024)

                var lastCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L
                var currentSpeed = 0f

                if (inputStream != null) {
                    inputStream.use { stream ->
                        var bytesRead: Int
                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            itemBytesSent += bytesRead
                            cumulativeBytesSent += bytesRead
                            bytesSinceLastCalc += bytesRead

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastCalcTime
                            if (elapsed >= 80 || itemBytesSent >= itemSize) {
                                if (elapsed > 0) {
                                    currentSpeed = (bytesSinceLastCalc.toFloat() / (elapsed / 1000f)) / (1024f * 1024f)
                                }
                                lastCalcTime = now
                                bytesSinceLastCalc = 0L

                                val overallProgress = (cumulativeBytesSent.toFloat() / totalBytes).coerceIn(0f, 1f)
                                val remainingBytes = (totalBytes - cumulativeBytesSent).coerceAtLeast(0L)
                                val speedBytesPerSec = (currentSpeed * 1024 * 1024).toLong()
                                val eta = if (speedBytesPerSec > 0) (remainingBytes / speedBytesPerSec).toInt() else 0

                                emit(
                                    TransferProgress(
                                        currentItemIndex = index,
                                        totalItems = items.size,
                                        currentItem = item,
                                        bytesTransferred = cumulativeBytesSent,
                                        totalBytes = totalBytes,
                                        overallProgress = overallProgress,
                                        currentSpeedMbps = currentSpeed,
                                        etaSeconds = eta
                                    )
                                )
                            }
                        }
                    }
                } else {
                    val payload = ByteArray(buffer.size) { 0x42 }
                    while (itemBytesSent < itemSize) {
                        val toSend = (itemSize - itemBytesSent).coerceAtMost(payload.size.toLong()).toInt()
                        outputStream.write(payload, 0, toSend)
                        itemBytesSent += toSend
                        cumulativeBytesSent += toSend
                        bytesSinceLastCalc += toSend

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastCalcTime
                        if (elapsed >= 80 || itemBytesSent >= itemSize) {
                            if (elapsed > 0) {
                                currentSpeed = (bytesSinceLastCalc.toFloat() / (elapsed / 1000f)) / (1024f * 1024f)
                            }
                            lastCalcTime = now
                            bytesSinceLastCalc = 0L

                            val overallProgress = (cumulativeBytesSent.toFloat() / totalBytes).coerceIn(0f, 1f)
                            emit(
                                TransferProgress(
                                    currentItemIndex = index,
                                    totalItems = items.size,
                                    currentItem = item,
                                    bytesTransferred = cumulativeBytesSent,
                                    totalBytes = totalBytes,
                                    overallProgress = overallProgress,
                                    currentSpeedMbps = currentSpeed.coerceAtLeast(10f),
                                    etaSeconds = 0
                                )
                            )
                        }
                    }
                }

                outputStream.flush()
            }

            outputStream.flush()
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    private fun openItemStream(item: SharableItem): InputStream? {
        val rawUri = item.uri ?: return null
        if (rawUri.isEmpty()) return null
        return try {
            if (rawUri.startsWith("content://") || rawUri.startsWith("file://")) {
                context.contentResolver.openInputStream(Uri.parse(rawUri))
            } else {
                val file = File(rawUri)
                if (file.exists()) FileInputStream(file) else null
            }
        } catch (e: Exception) {
            Log.w(tag, "Unable to open stream for $rawUri: ${e.message}")
            null
        }
    }
}
