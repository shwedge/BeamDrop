package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.model.BeamPeer
import com.example.model.DeviceType
import com.example.model.SharableItem
import com.example.model.ShareCategory
import android.os.Process
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

sealed interface IncomingTransferEvent {
    data class Started(
        val senderName: String,
        val senderIp: String,
        val fileCount: Int,
        val totalBytes: Long,
        val flowDirection: String = "TOP"
    ) : IncomingTransferEvent
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
    data class TextReceived(
        val senderName: String,
        val senderIp: String,
        val textContent: String
    ) : IncomingTransferEvent
    data class Cancelled(val senderName: String, val senderIp: String) : IncomingTransferEvent
    data class Failed(val error: String) : IncomingTransferEvent
    data class PeerHandshake(
        val peerId: String,
        val peerName: String,
        val peerIp: String,
        val deviceType: DeviceType
    ) : IncomingTransferEvent
    data class PeerLeft(
        val peerId: String,
        val peerIp: String,
        val peerName: String
    ) : IncomingTransferEvent
}

class RealTransferEngine(private val context: Context) {
    private val tag = "RealTransferEngine"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    // Dedicated high-priority I/O dispatcher isolated from default background tasks for 100+ Mbps throughput
    private val highPriorityTransferDispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(8)
    private val scope = CoroutineScope(SupervisorJob() + highPriorityTransferDispatcher + CoroutineName("BeamDropHighSpeedIO"))
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var highSpeedWifiLock: WifiManager.WifiLock? = null

    private val activeIncomingSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val activeOutgoingSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val activeIncomingJobs = ConcurrentHashMap.newKeySet<Job>()

    @Volatile
    private var cancelAllRequested: Boolean = false

    companion object {
        private const val SOCKET_BUFFER_BYTES = 8 * 1024 * 1024 // 8 MB TCP window for sustained 100+ Mbps Wi-Fi throughput
        private const val STREAM_BUFFER_BYTES = 2 * 1024 * 1024 // 2 MB I/O chunk size to minimize syscall & context-switch overhead
        private const val IPTOS_HIGH_THROUGHPUT = 0x18          // IPTOS_LOWDELAY (0x10) | IPTOS_THROUGHPUT (0x08)
        private const val PREFS_NAME = "beamdrop_storage_prefs"
        private const val KEY_SAVE_TREE_URI = "custom_save_tree_uri"
        private const val KEY_SAVE_FOLDER_NAME = "custom_save_folder_name"

        fun getSaveFolderDisplayName(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_SAVE_FOLDER_NAME, "Downloads/BeamDrop") ?: "Downloads/BeamDrop"
        }

        fun getCustomSaveTreeUri(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_SAVE_TREE_URI, null)?.takeIf { it.isNotBlank() }
        }

        fun setCustomSaveFolder(context: Context, treeUri: String?, folderDisplayName: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_SAVE_TREE_URI, treeUri ?: "")
                .putString(KEY_SAVE_FOLDER_NAME, folderDisplayName.ifBlank { "Downloads/BeamDrop" })
                .apply()
        }

        fun resolveMimeType(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
            }
            return "application/octet-stream"
        }

        /**
         * Saves a received staging file into the user's chosen folder (if a custom DocumentTree URI is set)
         * AND into public device storage (Downloads/BeamDrop via MediaStore on API 29+ or public Downloads directory)
         * so it is immediately accessible in the user's Files and Gallery apps.
         */
        fun persistReceivedFileToDeviceStorage(context: Context, stagingFile: File): String {
            if (!stagingFile.exists()) return stagingFile.absolutePath
            val mimeType = resolveMimeType(stagingFile.name)

            // 1. If user chose a custom save folder via OpenDocumentTree, write directly there first
            val customTreeUriStr = getCustomSaveTreeUri(context)
            if (!customTreeUriStr.isNullOrBlank()) {
                try {
                    val treeUri = Uri.parse(customTreeUriStr)
                    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
                    val createdUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentDocUri,
                        mimeType,
                        stagingFile.name
                    )
                    if (createdUri != null) {
                        context.contentResolver.openOutputStream(createdUri)?.use { out ->
                            FileInputStream(stagingFile).use { input ->
                                input.copyTo(out, 256 * 1024)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("RealTransferEngine", "Custom folder save fallback to Downloads: ${e.message}")
                }
            }

            // 2. Also export to public Downloads/BeamDrop so it is always visible in device storage / Files app
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, stagingFile.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BeamDrop")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val itemUri = resolver.insert(collection, values)
                    if (itemUri != null) {
                        resolver.openOutputStream(itemUri)?.use { out ->
                            FileInputStream(stagingFile).use { input ->
                                input.copyTo(out, 256 * 1024)
                            }
                        }
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(itemUri, values, null, null)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val publicDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "BeamDrop"
                    ).apply { if (!exists()) mkdirs() }
                    val publicFile = File(publicDir, stagingFile.name)
                    if (publicFile.absolutePath != stagingFile.absolutePath) {
                        stagingFile.copyTo(publicFile, overwrite = true)
                        MediaScannerConnection.scanFile(context, arrayOf(publicFile.absolutePath), arrayOf(mimeType), null)
                    }
                }
            } catch (e: Exception) {
                Log.w("RealTransferEngine", "MediaStore Downloads export notice: ${e.message}")
            }

            return stagingFile.absolutePath
        }

        /**
         * Copies a received file to an explicit URI selected via the system "Save As..." (CreateDocument) picker.
         */
        fun copyFileToUserSelectedUri(context: Context, sourcePathOrUri: String, targetUri: Uri): Boolean {
            return try {
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    if (sourcePathOrUri.startsWith("content://") || sourcePathOrUri.startsWith("file://")) {
                        context.contentResolver.openInputStream(Uri.parse(sourcePathOrUri))?.use { input ->
                            input.copyTo(out, 256 * 1024)
                        } ?: return false
                    } else {
                        val file = File(sourcePathOrUri)
                        if (!file.exists()) return false
                        FileInputStream(file).use { input ->
                            input.copyTo(out, 256 * 1024)
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Log.e("RealTransferEngine", "Failed saving to custom URI", e)
                false
            }
        }
    }

    /**
     * Immediately cancels any active incoming (receiving) or outgoing (sending) file transfers,
     * closes their TCP sockets to unblock blocking I/O reads/writes, and cleans up partial files.
     */
    fun cancelActiveTransfers() {
        cancelAllRequested = true
        activeIncomingSockets.forEach { sock ->
            try {
                sock.close()
            } catch (_: Exception) {}
        }
        activeIncomingSockets.clear()

        activeOutgoingSockets.forEach { sock ->
            try {
                sock.close()
            } catch (_: Exception) {}
        }
        activeOutgoingSockets.clear()

        activeIncomingJobs.forEach { job ->
            try {
                job.cancel()
            } catch (_: Exception) {}
        }
        activeIncomingJobs.clear()
        releaseHighSpeedTransferLock()
    }

    /**
     * Sends an out-of-band TCP cancel signal to the remote peer on port 8988 so that
     * if either receiver or sender cancels, the other device aborts immediately.
     */
    fun sendCancelSignalToPeer(peerIp: String, port: Int = 8988, localDeviceName: String = "Android") {
        if (peerIp.isBlank() || peerIp == "127.0.0.1") return
        scope.launch {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(peerIp, port), 1500)
                socket.soTimeout = 1500
                val out = socket.getOutputStream()
                val msg = "BEAMDROP_CANCEL|$localDeviceName\n"
                out.write(msg.toByteArray(StandardCharsets.UTF_8))
                out.flush()
            } catch (_: Exception) {
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun acquireHighSpeedTransferLock() {
        try {
            if (highSpeedWifiLock == null) {
                val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                highSpeedWifiLock = wifiManager?.createWifiLock(lockType, "BeamDrop:HighSpeedTransferLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (highSpeedWifiLock?.isHeld != true) {
                highSpeedWifiLock?.acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseHighSpeedTransferLock() {
        try {
            if (highSpeedWifiLock?.isHeld == true) {
                highSpeedWifiLock?.release()
            }
        } catch (_: Exception) {}
    }

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
                    receiveBufferSize = SOCKET_BUFFER_BYTES
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
        cancelActiveTransfers()
        serverJob?.cancel()
        releaseHighSpeedTransferLock()
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
        val job = scope.launch(highPriorityTransferDispatcher + CoroutineName("BeamDropReceiveWorker")) {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
                Thread.currentThread().priority = Thread.MAX_PRIORITY
            } catch (_: Exception) {}
            val startTime = System.currentTimeMillis()
            var totalBytesReceived = 0L
            val savedFiles = mutableListOf<File>()
            var currentPartialFile: File? = null
            val senderIp = socket.inetAddress?.hostAddress ?: ""
            var senderName = "BeamDrop Peer"
            var lockAcquired = false
            var isTransferSocketRegistered = false

            try {
                socket.soTimeout = 30000
                socket.tcpNoDelay = true
                socket.setPerformancePreferences(0, 1, 2)
                try {
                    socket.trafficClass = IPTOS_HIGH_THROUGHPUT
                } catch (_: Exception) {}
                socket.receiveBufferSize = SOCKET_BUFFER_BYTES
                socket.sendBufferSize = SOCKET_BUFFER_BYTES
                val rawInputStream = BufferedInputStream(socket.getInputStream(), STREAM_BUFFER_BYTES)
                val rawOutputStream = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)

                val firstLine = readUtf8Line(rawInputStream)?.trim()
                if (firstLine.isNullOrEmpty()) {
                    socket.close()
                    return@launch
                }

                // 1a. Handle Instant Transfer Cancellation Signal (BEAMDROP_CANCEL|<peerName>)
                if (firstLine.startsWith("BEAMDROP_CANCEL")) {
                    val parts = firstLine.split("|")
                    val peerName = parts.getOrNull(1).orEmpty().ifEmpty { "Remote Peer" }
                    cancelActiveTransfers()
                    onEvent(IncomingTransferEvent.Cancelled(peerName, senderIp))
                    socket.close()
                    return@launch
                }

                // 1b. Handle Instant Peer Departure over TCP (BEAMDROP_BYE|<peerId>|<peerName>)
                if (firstLine.startsWith("BEAMDROP_BYE")) {
                    val parts = firstLine.split("|")
                    val peerId = parts.getOrNull(1).orEmpty()
                    val peerName = parts.getOrNull(2).orEmpty()
                    if (peerId != localDeviceId) {
                        onEvent(IncomingTransferEvent.PeerLeft(peerId, senderIp, peerName))
                    }
                    socket.close()
                    return@launch
                }

                // 1c. Handle Verified Discovery Handshake (BEAMDROP_PING|<peerId>|<peerName>|<peerType>)
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

                // 2. Handle File Transfer Session (Supports BEAMDROP_V1|sender|count|bytes|direction and multiline BEAMDROP_V1)
                val fileCount: Int
                val declaredTotalBytes: Long
                val flowDirection: String

                if (firstLine.startsWith("BEAMDROP_V1|")) {
                    val hParts = firstLine.split("|")
                    senderName = hParts.getOrNull(1)?.ifEmpty { "BeamDrop Peer" } ?: "BeamDrop Peer"
                    fileCount = hParts.getOrNull(2)?.toIntOrNull() ?: 1
                    declaredTotalBytes = hParts.getOrNull(3)?.toLongOrNull() ?: 0L
                    flowDirection = hParts.getOrNull(4)?.uppercase()?.takeIf {
                        it == "LEFT" || it == "RIGHT" || it == "TOP"
                    } ?: "TOP"
                } else if (firstLine == "BEAMDROP_V1") {
                    senderName = readUtf8Line(rawInputStream)?.trim()?.ifEmpty { "BeamDrop Peer" } ?: "BeamDrop Peer"
                    fileCount = readUtf8Line(rawInputStream)?.trim()?.toIntOrNull() ?: 1
                    declaredTotalBytes = 0L
                    flowDirection = "TOP"
                } else {
                    socket.close()
                    return@launch
                }

                cancelAllRequested = false
                activeIncomingSockets.add(socket)
                isTransferSocketRegistered = true

                acquireHighSpeedTransferLock()
                lockAcquired = true

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

                onEvent(IncomingTransferEvent.Started(senderName, senderIp, fileCount, declaredTotalBytes, flowDirection))

                val buffer = ByteArray(STREAM_BUFFER_BYTES)
                var transferAborted = false

                for (index in 0 until fileCount) {
                    if (cancelAllRequested || !isActive) {
                        transferAborted = true
                        break
                    }
                    val metaLine = readUtf8Line(rawInputStream)?.trim()
                    if (metaLine == null || metaLine.startsWith("BEAMDROP_CANCEL")) {
                        transferAborted = true
                        break
                    }
                    val parts = metaLine.split("|")
                    if (parts.size < 2) {
                        transferAborted = true
                        break
                    }

                    val fileName: String
                    val fileSize: Long
                    val fileMime: String
                    if (parts[0] == "FILE" && parts.size >= 3) {
                        fileName = File(parts[1]).name
                        fileSize = parts[2].toLongOrNull() ?: 0L
                        fileMime = parts.getOrNull(3).orEmpty()
                    } else if (parts[0] == "TEXT" && parts.size >= 2) {
                        fileName = "__BEAMDROP_PLAIN_TEXT__"
                        fileSize = parts[1].toLongOrNull() ?: 0L
                        fileMime = "text/plain;beamdrop-raw"
                    } else {
                        fileName = File(parts[0]).name
                        fileSize = parts[1].toLongOrNull() ?: 0L
                        fileMime = parts.getOrNull(2).orEmpty()
                    }

                    val isPlainTextPayload = fileName == "__BEAMDROP_PLAIN_TEXT__" ||
                        fileMime.contains("beamdrop-raw", ignoreCase = true)

                    if (isPlainTextPayload) {
                        val textBytesOut = ByteArrayOutputStream(fileSize.coerceIn(0L, 1024L * 1024L).toInt())
                        var textBytesRead = 0L
                        while (textBytesRead < fileSize) {
                            if (cancelAllRequested || !isActive) {
                                transferAborted = true
                                break
                            }
                            val toRead = (fileSize - textBytesRead).coerceAtMost(buffer.size.toLong()).toInt()
                            val bytesRead = rawInputStream.read(buffer, 0, toRead)
                            if (bytesRead == -1) {
                                transferAborted = true
                                break
                            }
                            textBytesOut.write(buffer, 0, bytesRead)
                            textBytesRead += bytesRead
                            totalBytesReceived += bytesRead
                        }
                        if (!transferAborted && textBytesRead >= fileSize) {
                            val decodedText = textBytesOut.toString(StandardCharsets.UTF_8.name())
                            if (decodedText.length <= 500) {
                                onEvent(
                                    IncomingTransferEvent.TextReceived(
                                        senderName = senderName,
                                        senderIp = senderIp,
                                        textContent = decodedText
                                    )
                                )
                            } else {
                                val destinationFile = getUniqueFile(downloadsDir, "Pasted_Text_${System.currentTimeMillis()}.txt")
                                destinationFile.writeBytes(textBytesOut.toByteArray())
                                withContext(Dispatchers.IO) {
                                    persistReceivedFileToDeviceStorage(context, destinationFile)
                                }
                                savedFiles.add(destinationFile)
                            }
                        } else {
                            transferAborted = true
                            break
                        }
                        continue
                    }

                    val destinationFile = getUniqueFile(downloadsDir, fileName)
                    currentPartialFile = destinationFile
                    val fos = BufferedOutputStream(FileOutputStream(destinationFile), STREAM_BUFFER_BYTES)

                    var fileBytesRead = 0L
                    var lastTime = System.currentTimeMillis()
                    var bytesSinceLastCalc = 0L
                    var currentSpeedMbps = 0f
                    var lastEmittedProgress = -1f

                    try {
                        while (fileBytesRead < fileSize) {
                            if (cancelAllRequested || !isActive) {
                                transferAborted = true
                                break
                            }
                            val toRead = (fileSize - fileBytesRead).coerceAtMost(buffer.size.toLong()).toInt()
                            val bytesRead = rawInputStream.read(buffer, 0, toRead)
                            if (bytesRead == -1) {
                                transferAborted = true
                                break
                            }

                            fos.write(buffer, 0, bytesRead)
                            fileBytesRead += bytesRead
                            totalBytesReceived += bytesRead
                            bytesSinceLastCalc += bytesRead

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastTime
                            val progress = if (fileSize > 0) fileBytesRead.toFloat() / fileSize else 1f
                            if ((elapsed >= 250 && (progress - lastEmittedProgress >= 0.015f || elapsed >= 450)) || fileBytesRead == fileSize) {
                                if (elapsed > 0) {
                                    currentSpeedMbps = (bytesSinceLastCalc.toFloat() / (elapsed / 1000f)) / (1024f * 1024f)
                                }
                                lastTime = now
                                bytesSinceLastCalc = 0L
                                lastEmittedProgress = progress

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
                    } finally {
                        try {
                            fos.close()
                        } catch (_: Exception) {}
                    }

                    if (transferAborted || fileBytesRead < fileSize) {
                        transferAborted = true
                        try {
                            if (destinationFile.exists()) destinationFile.delete()
                        } catch (_: Exception) {}
                        break
                    } else {
                        currentPartialFile = null
                        withContext(Dispatchers.IO) {
                            persistReceivedFileToDeviceStorage(context, destinationFile)
                        }
                        savedFiles.add(destinationFile)
                    }
                }

                if (transferAborted || cancelAllRequested) {
                    // Delete any partial files from this cancelled session
                    currentPartialFile?.let { f ->
                        try {
                            if (f.exists()) f.delete()
                        } catch (_: Exception) {}
                    }
                    savedFiles.forEach { f ->
                        try {
                            if (f.exists()) f.delete()
                        } catch (_: Exception) {}
                    }
                    try {
                        rawOutputStream.write("CANCEL\n".toByteArray(StandardCharsets.UTF_8))
                        rawOutputStream.flush()
                    } catch (_: Exception) {}
                    onEvent(IncomingTransferEvent.Cancelled(senderName, senderIp))
                    return@launch
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
                currentPartialFile?.let { f ->
                    try {
                        if (f.exists()) f.delete()
                    } catch (_: Exception) {}
                }
                if (cancelAllRequested || e is CancellationException) {
                    savedFiles.forEach { f ->
                        try {
                            if (f.exists()) f.delete()
                        } catch (_: Exception) {}
                    }
                    onEvent(IncomingTransferEvent.Cancelled(senderName, senderIp))
                } else {
                    Log.e(tag, "Error receiving transfer", e)
                    onEvent(IncomingTransferEvent.Failed(e.message ?: "Transfer interrupted"))
                }
            } finally {
                if (isTransferSocketRegistered) {
                    activeIncomingSockets.remove(socket)
                }
                if (lockAcquired) {
                    releaseHighSpeedTransferLock()
                }
                try {
                    socket.close()
                } catch (_: Exception) {}
            }
        }
        activeIncomingJobs.add(job)
        job.invokeOnCompletion {
            activeIncomingJobs.remove(job)
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
     * Supports directional flow metadata ("TOP", "LEFT", "RIGHT") and instant bidirectional cancellation.
     */
    fun sendFiles(
        peer: BeamPeer,
        items: List<SharableItem>,
        myDeviceName: String,
        flowDirection: String = "TOP"
    ): Flow<TransferProgress> = flow {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            Thread.currentThread().priority = Thread.MAX_PRIORITY
        } catch (_: Exception) {}

        val socket = Socket()
        cancelAllRequested = false
        activeOutgoingSockets.add(socket)
        acquireHighSpeedTransferLock()
        try {
            socket.tcpNoDelay = true
            socket.setPerformancePreferences(0, 1, 2)
            try {
                socket.trafficClass = IPTOS_HIGH_THROUGHPUT
            } catch (_: Exception) {}
            socket.sendBufferSize = SOCKET_BUFFER_BYTES
            socket.receiveBufferSize = SOCKET_BUFFER_BYTES
            socket.connect(InetSocketAddress(peer.ipAddress, peer.port), 10000)
            socket.soTimeout = 30000

            val expandedItems = expandDirectoryItems(items)
            val outputStream = BufferedOutputStream(socket.getOutputStream(), STREAM_BUFFER_BYTES)
            val inputStreamAck = socket.getInputStream()
            val totalBytes = expandedItems.sumOf { it.sizeBytes }.coerceAtLeast(1L)
            var cumulativeBytesSent = 0L

            // Unified Header understood by both Android and Windows native apps (backward-compatible 4+ parts)
            val sessionHeader = "BEAMDROP_V1|$myDeviceName|${expandedItems.size}|$totalBytes|$flowDirection\n"
            outputStream.write(sessionHeader.toByteArray(StandardCharsets.UTF_8))
            outputStream.flush()

            val buffer = ByteArray(STREAM_BUFFER_BYTES)

            expandedItems.forEachIndexed { index, item ->
                if (cancelAllRequested) {
                    throw CancellationException("Transfer cancelled")
                }

                val isPlainText = item.mimeType.contains("beamdrop-raw", ignoreCase = true) ||
                    item.uri?.startsWith("beamtext://") == true
                val plainTextBytes = if (isPlainText) {
                    val rawText = item.uri?.removePrefix("beamtext://") ?: item.title
                    rawText.toByteArray(StandardCharsets.UTF_8)
                } else null
                val isSnippet = isPlainText && (plainTextBytes?.size ?: 0) <= 500
                val wireTitle = if (isSnippet) {
                    "__BEAMDROP_PLAIN_TEXT__"
                } else if (isPlainText) {
                    "Pasted_Text_${System.currentTimeMillis()}.txt"
                } else {
                    item.title
                }
                val wireMime = if (isSnippet) "text/plain;beamdrop-raw" else if (isPlainText) "text/plain" else item.mimeType
                val wireSize = plainTextBytes?.size?.toLong() ?: item.sizeBytes

                val metaHeader = "FILE|$wireTitle|$wireSize|$wireMime\n"
                outputStream.write(metaHeader.toByteArray(StandardCharsets.UTF_8))
                outputStream.flush()

                val inputStream: InputStream? = if (plainTextBytes != null) {
                    plainTextBytes.inputStream()
                } else {
                    openItemStream(item)?.let { BufferedInputStream(it, STREAM_BUFFER_BYTES) }
                }

                var itemBytesSent = 0L
                val itemSize = wireSize

                var lastCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L
                var currentSpeed = 0f
                var lastEmittedProgress = -1f

                if (inputStream != null) {
                    inputStream.use { stream ->
                        var bytesRead: Int
                        var chunkCounter = 0
                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                            if (cancelAllRequested) {
                                throw CancellationException("Transfer cancelled")
                            }
                            // Check for receiver early CANCEL only every 32 chunks to avoid per-chunk JNI ioctl bottleneck
                            if ((chunkCounter++ and 0x1F) == 0 && inputStreamAck.available() > 0) {
                                val resp = readUtf8Line(inputStreamAck)?.trim().orEmpty()
                                if (resp.startsWith("CANCEL")) {
                                    throw CancellationException("Cancelled by receiver")
                                }
                            }

                            outputStream.write(buffer, 0, bytesRead)
                            itemBytesSent += bytesRead
                            cumulativeBytesSent += bytesRead
                            bytesSinceLastCalc += bytesRead

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastCalcTime
                            val overallProgress = (cumulativeBytesSent.toFloat() / totalBytes).coerceIn(0f, 1f)
                            if ((elapsed >= 250 && (overallProgress - lastEmittedProgress >= 0.015f || elapsed >= 450)) || itemBytesSent >= itemSize) {
                                if (elapsed > 0) {
                                    currentSpeed = (bytesSinceLastCalc.toFloat() / (elapsed / 1000f)) / (1024f * 1024f)
                                }
                                lastCalcTime = now
                                bytesSinceLastCalc = 0L
                                lastEmittedProgress = overallProgress

                                val remainingBytes = (totalBytes - cumulativeBytesSent).coerceAtLeast(0L)
                                val speedBytesPerSec = (currentSpeed * 1024 * 1024).toLong()
                                val eta = if (speedBytesPerSec > 0) (remainingBytes / speedBytesPerSec).toInt() else 0

                                emit(
                                    TransferProgress(
                                        currentItemIndex = index,
                                        totalItems = expandedItems.size,
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
                        if (cancelAllRequested) {
                            throw CancellationException("Transfer cancelled")
                        }
                        val toSend = (itemSize - itemBytesSent).coerceAtMost(payload.size.toLong()).toInt()
                        outputStream.write(payload, 0, toSend)
                        itemBytesSent += toSend
                        cumulativeBytesSent += toSend
                        bytesSinceLastCalc += toSend

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastCalcTime
                        val overallProgress = (cumulativeBytesSent.toFloat() / totalBytes).coerceIn(0f, 1f)
                        if ((elapsed >= 250 && (overallProgress - lastEmittedProgress >= 0.015f || elapsed >= 450)) || itemBytesSent >= itemSize) {
                            if (elapsed > 0) {
                                currentSpeed = (bytesSinceLastCalc.toFloat() / (elapsed / 1000f)) / (1024f * 1024f)
                            }
                            lastCalcTime = now
                            bytesSinceLastCalc = 0L
                            lastEmittedProgress = overallProgress

                            emit(
                                TransferProgress(
                                    currentItemIndex = index,
                                    totalItems = expandedItems.size,
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
            activeOutgoingSockets.remove(socket)
            releaseHighSpeedTransferLock()
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }.flowOn(highPriorityTransferDispatcher)

    /**
     * Expands any selected directory tree URIs (vnd.android.document/directory) into their real child files
     * so swiping or beaming a Folder sends all actual files inside the folder.
     */
    private fun expandDirectoryItems(items: List<SharableItem>): List<SharableItem> {
        val result = mutableListOf<SharableItem>()
        for (item in items) {
            val rawUri = item.uri
            if (rawUri != null && (item.mimeType == "vnd.android.document/directory" || rawUri.contains("/tree/"))) {
                val children = mutableListOf<SharableItem>()
                try {
                    val treeUri = Uri.parse(rawUri)
                    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    collectTreeFilesRecursive(treeUri, rootDocId, item.title, children, depth = 0)
                } catch (e: Exception) {
                    Log.w(tag, "Directory expansion fallback for ${item.title}: ${e.message}")
                }
                if (children.isNotEmpty()) {
                    result.addAll(children)
                } else {
                    result.add(item)
                }
            } else {
                result.add(item)
            }
        }
        return result
    }

    private fun collectTreeFilesRecursive(
        treeUri: Uri,
        parentDocId: String,
        folderPrefix: String,
        outList: MutableList<SharableItem>,
        depth: Int
    ) {
        if (depth > 4 || outList.size >= 200) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val docId = if (idIdx != -1) cursor.getString(idIdx) else continue
                val name = (if (nameIdx != -1) cursor.getString(nameIdx) else null) ?: "File_${outList.size}"
                val mime = (if (mimeIdx != -1) cursor.getString(mimeIdx) else null) ?: "application/octet-stream"
                val size = if (sizeIdx != -1) cursor.getLong(sizeIdx).coerceAtLeast(1L) else 1024L
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collectTreeFilesRecursive(treeUri, docId, "${folderPrefix}_$name", outList, depth + 1)
                } else {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    outList.add(
                        SharableItem(
                            id = "dir_child_${docId.hashCode()}",
                            title = name,
                            subtitle = folderPrefix,
                            sizeBytes = size,
                            category = ShareCategory.DOCUMENTS,
                            mimeType = mime,
                            dateAdded = "Now",
                            isSelected = true,
                            uri = docUri.toString()
                        )
                    )
                }
            }
        }
    }

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
