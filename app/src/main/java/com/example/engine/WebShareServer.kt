package com.example.engine

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.data.model.TransferRecord
import com.example.model.SharableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Continuity & Native Windows App Distribution Server for BeamDrop.
 * Serves:
 *  1. Native Windows .EXE One-Click Installer (/download-windows-exe & /download-windows-cs)
 *  2. 60fps Fluid Aurora Desktop Continuity Hub (/) with live MB/s progress & bidirectional file beaming
 *  3. Real-time Transfer History Ledger API (/api/history) & Staged Files API (/api/staged)
 */
class WebShareServer(
    private val context: Context,
    val port: Int = 8990,
    private val deviceName: String,
    private val getLocalIp: () -> String = { "127.0.0.1" },
    private val onFileReceived: (file: File, size: Long, senderInfo: String) -> Unit,
    private val getStagedFiles: () -> List<SharableItem>,
    private val getTransferHistory: () -> List<TransferRecord> = { emptyList() }
) {
    private val tag = "WebShareServer"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        stop()

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                isRunning = true
                Log.d(tag, "WebShareServer listening on port $port")

                while (isActive) {
                    val client = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        if (!isActive) break
                        continue
                    }

                    scope.launch {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "WebShareServer error", e)
            } finally {
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 60000
            val rawIn = socket.getInputStream()
            val rawOut = BufferedOutputStream(socket.getOutputStream())

            val reader = BufferedReader(InputStreamReader(rawIn, StandardCharsets.US_ASCII))
            val requestLine = reader.readLine() ?: run {
                socket.close()
                return
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val method = parts[0].uppercase()
            val rawPath = parts[1]

            val headers = mutableMapOf<String, String>()
            var headerLine: String?
            while (reader.readLine().also { headerLine = it } != null) {
                if (headerLine.isNullOrEmpty()) break
                val colonIdx = headerLine!!.indexOf(':')
                if (colonIdx != -1) {
                    val key = headerLine!!.substring(0, colonIdx).trim().lowercase()
                    val value = headerLine!!.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L

            when {
                method == "GET" && (rawPath == "/" || rawPath.startsWith("/index")) -> {
                    serveHtmlPortal(rawOut)
                }
                method == "GET" && rawPath.startsWith("/api/status") -> {
                    val json = """{"device":"${escapeJson(deviceName)}","ip":"${escapeJson(getLocalIp())}","status":"online","port":$port,"tcpPort":8988}"""
                    sendResponse(rawOut, 200, "OK", "application/json", json.toByteArray(StandardCharsets.UTF_8))
                }
                method == "GET" && rawPath.startsWith("/api/staged") -> {
                    val items = getStagedFiles()
                    val itemsJson = items.joinToString(",", prefix = "[", postfix = "]") { item ->
                        """{"id":"${escapeJson(item.id)}","title":"${escapeJson(item.title)}","size":${item.sizeBytes},"mimeType":"${escapeJson(item.mimeType)}"}"""
                    }
                    sendResponse(rawOut, 200, "OK", "application/json", itemsJson.toByteArray(StandardCharsets.UTF_8))
                }
                method == "GET" && rawPath.startsWith("/api/history") -> {
                    val history = getTransferHistory().take(30)
                    val historyJson = history.joinToString(",", prefix = "[", postfix = "]") { rec ->
                        """{"id":${rec.id},"fileName":"${escapeJson(rec.fileName)}","fileSize":${rec.fileSize},"direction":"${escapeJson(rec.direction)}","peer":"${escapeJson(rec.peerDeviceName)}","timestamp":${rec.timestamp},"speed":${rec.transferRateMbps},"status":"${escapeJson(rec.status)}"}"""
                    }
                    sendResponse(rawOut, 200, "OK", "application/json", historyJson.toByteArray(StandardCharsets.UTF_8))
                }
                method == "GET" && (rawPath.startsWith("/download-windows-package") || rawPath.startsWith("/download-windows-installer") || rawPath.startsWith("/download-windows-binary") || rawPath.equals("/BeamDrop-Windows-Installer.zip", ignoreCase = true) || rawPath.equals("/BeamDrop.exe", ignoreCase = true)) -> {
                    serveAssetFile(rawOut, "BeamDrop-Windows-Installer.zip", "application/zip", "BeamDrop-Windows-Installer-v2.4.0.zip")
                }
                method == "GET" && (rawPath.startsWith("/download-certificate") || rawPath.endsWith("SajjadHossain_CodeSigning.cer") || rawPath.endsWith("SajjadH0ssain_CodeSigning.cer")) -> {
                    serveAssetFile(rawOut, "SajjadHossain_CodeSigning.cer", "application/x-x509-ca-cert", "SajjadHossain_CodeSigning.cer")
                }
                method == "GET" && (rawPath.startsWith("/download-windows-bat") || rawPath.startsWith("/download-windows-exe") || rawPath.equals("/Package_BeamDropmsi.bat", ignoreCase = true) || rawPath.equals("/Package-BeamDropMsi.bat", ignoreCase = true)) -> {
                    serveAssetFile(rawOut, "Package_BeamDropmsi.bat", "application/bat", "Package_BeamDropmsi.bat")
                }
                method == "GET" && rawPath.startsWith("/download-cert-installer") -> {
                    serveAssetFile(rawOut, "Install-Certificate.bat", "application/bat", "Install-Certificate.bat")
                }
                method == "GET" && rawPath.startsWith("/download-windows-cs") -> {
                    serveAssetFile(rawOut, "BeamDropWindows.cs", "text/plain; charset=utf-8", "BeamDropWindows.cs")
                }
                method == "GET" && rawPath.startsWith("/download-windows-ps1") -> {
                    serveAssetFile(rawOut, "Build-BeamDropMsi.ps1", "text/plain; charset=utf-8", "Build-BeamDropMsi.ps1")
                }
                method == "GET" && rawPath.startsWith("/download-windows-client") -> {
                    serveAssetFile(rawOut, "BeamDrop.py", "text/x-python", "BeamDrop.py")
                }
                method == "GET" && rawPath.startsWith("/download") -> {
                    handleDownload(rawPath, rawOut)
                }
                method == "POST" && rawPath.startsWith("/upload") -> {
                    handleUpload(rawPath, rawIn, contentLength, rawOut, socket.inetAddress.hostAddress ?: "Windows PC")
                }
                method == "OPTIONS" -> {
                    sendCorsPreflight(rawOut)
                }
                else -> {
                    val notFound = "Not Found"
                    sendResponse(rawOut, 404, "Not Found", "text/plain", notFound.toByteArray())
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Client request error", e)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun serveWindowsNativeInstallerBat(out: OutputStream) {
        val phoneIp = getLocalIp()
        val batScript = buildString {
            append("@echo off\r\n")
            append("title BeamDrop Native Windows App Setup and Launcher - Published by Sajjad Hossain\r\n")
            append("echo ============================================================================\r\n")
            append("echo   BeamDrop Continuity - Native Windows Desktop App (.EXE) Installer\r\n")
            append("echo   Publisher: Sajjad Hossain\r\n")
            append("echo   Connected Phone: $deviceName ($phoneIp)\r\n")
            append("echo ============================================================================\r\n")
            append("echo.\r\n")
            append("set \"APP_DIR=%LOCALAPPDATA%\\Programs\\Sajjad Hossain\\BeamDrop\"\r\n")
            append("if not exist \"%APP_DIR%\" mkdir \"%APP_DIR%\"\r\n")
            append("cd /d \"%APP_DIR%\"\r\n")
            append("echo [1/3] Downloading native WPF C# source from Android phone ($phoneIp:$port)...\r\n")
            append("powershell -NoProfile -ExecutionPolicy Bypass -Command \"Invoke-WebRequest -Uri 'http://$phoneIp:$port/download-windows-cs' -OutFile 'BeamDropWindows.cs'\"\r\n")
            append("set \"CSC_PATH=%WINDIR%\\Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe\"\r\n")
            append("if not exist \"%CSC_PATH%\" set \"CSC_PATH=%WINDIR%\\Microsoft.NET\\Framework\\v4.0.30319\\csc.exe\"\r\n")
            append("set \"WPF_DIR=%WINDIR%\\Microsoft.NET\\Framework64\\v4.0.30319\\WPF\"\r\n")
            append("if not exist \"%WPF_DIR%\" set \"WPF_DIR=%WINDIR%\\Microsoft.NET\\Framework\\v4.0.30319\\WPF\"\r\n")
            append("echo [2/3] Compiling hardware-accelerated native BeamDrop.exe - Publisher Sajjad Hossain...\r\n")
            append("\"%CSC_PATH%\" /nologo /target:winexe /optimize+ /out:\"BeamDrop.exe\" /r:System.dll /r:System.Core.dll /r:System.Xaml.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.Net.Http.dll /r:\"%WPF_DIR%\\PresentationFramework.dll\" /r:\"%WPF_DIR%\\PresentationCore.dll\" /r:\"%WPF_DIR%\\WindowsBase.dll\" \"BeamDropWindows.cs\"\r\n")
            append("if not exist \"BeamDrop.exe\" goto :ERR\r\n")
            append("echo [3/3] Launching BeamDrop.exe...\r\n")
            append("start \"\" \"BeamDrop.exe\"\r\n")
            append("exit /b 0\r\n")
            append(":ERR\r\n")
            append("echo Compilation failed. Make sure your PC is on the same Wi-Fi as $phoneIp.\r\n")
            append("pause\r\n")
        }
        val bytes = batScript.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/bat\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Content-Disposition: attachment; filename=\"Install-BeamDrop-Windows-Native.bat\"\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun serveAssetFile(out: OutputStream, assetName: String, contentType: String, downloadName: String) {
        try {
            val bytes = context.assets.open(assetName).use { it.readBytes() }
            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Content-Disposition: attachment; filename=\"$downloadName\"\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(header.toByteArray(StandardCharsets.US_ASCII))
            out.write(bytes)
            out.flush()
        } catch (e: Exception) {
            val fallbackFile = File("windows/$assetName")
            if (fallbackFile.exists()) {
                val bytes = fallbackFile.readBytes()
                val header = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: $contentType\r\n")
                    append("Content-Length: ${bytes.size}\r\n")
                    append("Content-Disposition: attachment; filename=\"$downloadName\"\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Connection: close\r\n\r\n")
                }
                out.write(header.toByteArray(StandardCharsets.US_ASCII))
                out.write(bytes)
                out.flush()
            } else {
                sendResponse(out, 404, "Not Found", "text/plain", "Asset $assetName not found".toByteArray())
            }
        }
    }

    private fun handleUpload(
        path: String,
        input: InputStream,
        contentLength: Long,
        out: OutputStream,
        clientIp: String
    ) {
        try {
            val queryIdx = path.indexOf('?')
            var fileName = "Upload_${System.currentTimeMillis()}"
            if (queryIdx != -1) {
                val query = path.substring(queryIdx + 1)
                for (param in query.split("&")) {
                    val kv = param.split("=")
                    if (kv.size == 2 && kv[0] == "filename") {
                        fileName = URLDecoder.decode(kv[1], "UTF-8")
                    }
                }
            }

            val downloadsDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "BeamDrop"
            ).apply { if (!exists()) mkdirs() }

            val targetFile = File(downloadsDir, fileName)
            val fos = FileOutputStream(targetFile)

            val buffer = ByteArray(64 * 1024)
            var bytesRemaining = contentLength
            var totalRead = 0L

            if (contentLength > 0) {
                while (bytesRemaining > 0) {
                    val toRead = bytesRemaining.coerceAtMost(buffer.size.toLong()).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    fos.write(buffer, 0, read)
                    bytesRemaining -= read
                    totalRead += read
                }
            } else {
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    fos.write(buffer, 0, read)
                    totalRead += read
                }
            }

            fos.flush()
            fos.close()

            onFileReceived(targetFile, totalRead, "Windows PC ($clientIp)")

            val resp = """{"success":true,"file":"${escapeJson(fileName)}","size":$totalRead}"""
            sendResponse(out, 200, "OK", "application/json", resp.toByteArray(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            Log.e(tag, "Upload processing failed", e)
            val err = """{"success":false,"error":"${escapeJson(e.message ?: "Failed")}"}"""
            sendResponse(out, 500, "Internal Server Error", "application/json", err.toByteArray())
        }
    }

    private fun handleDownload(path: String, out: OutputStream) {
        val queryIdx = path.indexOf('?')
        var targetId: String? = null
        if (queryIdx != -1) {
            val query = path.substring(queryIdx + 1)
            for (param in query.split("&")) {
                val kv = param.split("=")
                if (kv.size == 2 && kv[0] == "id") {
                    targetId = URLDecoder.decode(kv[1], "UTF-8")
                }
            }
        }

        val staged = getStagedFiles()
        val item = staged.find { it.id == targetId } ?: staged.firstOrNull()

        if (item == null || item.uri == null) {
            val notFound = "No file available"
            sendResponse(out, 404, "Not Found", "text/plain", notFound.toByteArray())
            return
        }

        try {
            val fileStream: InputStream = if (item.uri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(item.uri)) ?: throw Exception("Cannot open URI")
            } else {
                FileInputStream(File(item.uri))
            }

            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: ${item.mimeType}\r\n")
                append("Content-Length: ${item.sizeBytes}\r\n")
                append("Content-Disposition: attachment; filename=\"${item.title}\"\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(header.toByteArray(StandardCharsets.US_ASCII))

            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (fileStream.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
            }
            fileStream.close()
            out.flush()
        } catch (e: Exception) {
            Log.e(tag, "Download failed", e)
            sendResponse(out, 500, "Error", "text/plain", "Download error: ${e.message}".toByteArray())
        }
    }

    private fun serveHtmlPortal(out: OutputStream) {
        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>BeamDrop Continuity — Windows & Android Ecosystem</title>
  <style>
    :root {
      --bg: #090D14;
      --card: rgba(20, 27, 45, 0.82);
      --accent: #00F5D4;
      --purple: #9D4EDD;
      --amber: #FFB703;
      --teal: #10B981;
      --red: #F43F5E;
      --text: #F1F5F9;
      --text-sec: #94A3B8;
      --border: rgba(255, 255, 255, 0.10);
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, sans-serif; }
    body { background-color: var(--bg); color: var(--text); min-height: 100vh; overflow-x: hidden; position: relative; }
    #auroraCanvas { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; z-index: 50; pointer-events: none; }
    .container { position: relative; z-index: 1; max-width: 920px; margin: 0 auto; padding: 28px 20px 90px; }
    .header { display: flex; align-items: center; justify-content: space-between; background: var(--card); backdrop-filter: blur(18px); border: 1px solid var(--border); border-radius: 22px; padding: 18px 24px; margin-bottom: 22px; }
    .logo { font-size: 24px; font-weight: 800; background: linear-gradient(135deg, var(--accent), var(--purple)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
    .badge { padding: 6px 14px; background: rgba(0, 245, 212, 0.14); border: 1px solid var(--accent); color: var(--accent); border-radius: 20px; font-size: 12px; font-weight: 700; }
    .grid { display: grid; grid-template-columns: 1.15fr 1fr; gap: 20px; }
    @media (max-width: 760px) { .grid { grid-template-columns: 1fr; } }
    .card { background: var(--card); backdrop-filter: blur(18px); border: 1px solid var(--border); border-radius: 22px; padding: 24px; margin-bottom: 20px; box-shadow: 0 12px 40px rgba(0,0,0,0.45); }
    .card-title { font-size: 16px; font-weight: 800; margin-bottom: 14px; display: flex; align-items: center; justify-content: space-between; letter-spacing: 0.4px; }
    .drop-zone { border: 2px dashed rgba(0, 245, 212, 0.45); border-radius: 18px; padding: 42px 20px; text-align: center; cursor: pointer; transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1); background: rgba(0, 245, 212, 0.04); }
    .drop-zone.dragover { border-color: var(--accent); background: rgba(0, 245, 212, 0.14); transform: scale(1.02); box-shadow: 0 0 35px rgba(0, 245, 212, 0.25); }
    .btn { background: var(--accent); color: #090D14; border: none; padding: 11px 22px; border-radius: 12px; font-weight: 700; font-size: 13px; cursor: pointer; transition: 0.2s; display: inline-flex; align-items: center; gap: 8px; text-decoration: none; position: relative; }
    .btn:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0, 245, 212, 0.35); }
    @keyframes auraRipplePulse {
      0% { box-shadow: 0 0 0 0 rgba(0, 245, 212, 0.65), 0 0 0 0 rgba(157, 78, 221, 0.45); }
      70% { box-shadow: 0 0 0 14px rgba(0, 245, 212, 0), 0 0 0 24px rgba(157, 78, 221, 0); }
      100% { box-shadow: 0 0 0 0 rgba(0, 245, 212, 0), 0 0 0 0 rgba(157, 78, 221, 0); }
    }
    .aura-ripple-btn { animation: auraRipplePulse 2.1s infinite cubic-bezier(0.16, 1, 0.3, 1); }
    .discreet-capsule {
      position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
      width: min(440px, 92vw); background: rgba(16, 26, 42, 0.96); backdrop-filter: blur(20px);
      border: 1.5px solid var(--accent); border-radius: 999px; padding: 12px 20px;
      box-shadow: 0 12px 36px rgba(0, 245, 212, 0.28); z-index: 100; display: none;
    }
    .progress-bar-wrap { height: 5px; background: rgba(255,255,255,0.08); border-radius: 4px; overflow: hidden; margin-top: 8px; }
    .progress-bar { height: 100%; width: 0%; background: linear-gradient(90deg, var(--accent), var(--purple), var(--amber)); transition: width 0.08s linear; }
    .file-item { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; background: rgba(255,255,255,0.03); border: 1px solid var(--border); border-radius: 12px; margin-bottom: 8px; }
    .status-pill { padding: 3px 8px; border-radius: 6px; font-size: 10px; font-weight: 800; }
  </style>
</head>
<body>
  <canvas id="auroraCanvas"></canvas>
  <div class="container">
    <div class="header" id="targetPeerBadge">
      <div>
        <div class="logo">⚡ BeamDrop Continuity</div>
        <p style="color: var(--text-sec); font-size: 12.5px; margin-top: 3px;">Apple-Style Fluid Ecosystem for Windows 10/11 & Android</p>
      </div>
      <div class="badge">📱 Paired: $deviceName</div>
    </div>

    <!-- Native Windows Standalone MSI Installer & Desktop App Banner -->
    <div class="card" style="border-color: rgba(0, 245, 212, 0.45); background: linear-gradient(135deg, rgba(20,27,45,0.92), rgba(29,39,64,0.88));">
      <div class="card-title" style="color: var(--accent);">
        <span>💻 Standalone Signed Windows Installer (.MSI) & Desktop Client</span>
        <span style="font-size: 11px; color: var(--amber);">Publisher: Sajjad Hossain • v2.4.0.0</span>
      </div>
      <p style="color: var(--text-sec); font-size: 13px; margin-bottom: 12px; line-height: 1.5;">
        Official standalone Windows desktop version published by <b>Sajjad Hossain</b>. Features native P2P discovery (TCP 8988 + UDP 8989 + mDNS), 60 FPS glowing fluid aura ripple + Cubic Bezier Aura Path payload migration, and a self-dismissing progress indicator that clears transferred files immediately upon completion.
      </p>
      <div style="background: rgba(0,245,212,0.06); border: 1px solid rgba(0,245,212,0.22); border-radius: 10px; padding: 10px 14px; margin-bottom: 14px; font-size: 12px; color: var(--text-sec); line-height: 1.45;">
        <b style="color: var(--accent);">🛡️ Crash-Proof 1-Click Setup:</b> Extract the ZIP and double-click <code style="color: var(--accent);">Package_BeamDropmsi.bat</code> (or <code style="color: var(--accent);">Package-BeamDropMsi.bat</code>). It automatically unblocks Windows Zone.Identifier tags, installs to <code style="color: var(--accent);">%LOCALAPPDATA%\Programs\Sajjad Hossain\BeamDrop\BeamDrop.exe</code>, creates a Desktop shortcut, and launches the app!
      </div>
      <div style="display: flex; gap: 12px; flex-wrap: wrap;">
        <a class="btn aura-ripple-btn" style="background: linear-gradient(135deg, #00F5D4, #9D4EDD); color: #090D14;" href="/download-windows-package">
          ⬇ Download Windows Package (.ZIP)
        </a>
        <a class="btn" style="background: rgba(0, 245, 212, 0.14); color: var(--accent); border: 1px solid var(--accent);" href="/download-certificate">
          🛡️ Authenticode Certificate (.CER)
        </a>
        <a class="btn" style="background: rgba(255, 183, 3, 0.14); color: var(--amber); border: 1px solid var(--amber);" href="/Package_BeamDropmsi.bat">
          ⚡ 1-Click Setup (Package_BeamDropmsi.bat)
        </a>
        <a class="btn" style="background: rgba(255,255,255,0.08); color: var(--text); border: 1px solid var(--border);" href="/download-windows-cs">
          📄 C# WPF Source (.CS)
        </a>
      </div>
    </div>

    <div class="grid">
      <div class="card">
        <div class="card-title">
          <span>📤 Fluid Drop Zone (PC → Android)</span>
        </div>
        <div class="drop-zone" id="dropZone">
          <div style="font-size: 36px; margin-bottom: 10px;">✦</div>
          <p style="font-size: 17px; font-weight: 700; margin-bottom: 6px;">Drag & Drop Files Anywhere</p>
          <p style="color: var(--text-sec); font-size: 12.5px; margin-bottom: 18px;">Photos, 4K Videos, Folders, Documents, APKs</p>
          <input type="file" id="fileInput" multiple style="display: none;">
          <button class="btn aura-ripple-btn" id="sourceTransferBtn" onclick="document.getElementById('fileInput').click()">Select Files to Beam</button>
        </div>
      </div>

      <div>
        <div class="card">
          <div class="card-title">
            <span>📥 Staged on Phone (Android → PC)</span>
            <button class="btn" style="padding: 5px 12px; font-size: 11px;" onclick="fetchStagedFiles()">↻ Refresh</button>
          </div>
          <div id="stagedList">
            <p style="color: var(--text-sec); font-size: 13px;">Select files in BeamDrop on your phone to download them here.</p>
          </div>
        </div>

        <div class="card">
          <div class="card-title">
            <span>🕓 Live Transfer History Ledger</span>
          </div>
          <div id="historyList" style="max-height: 220px; overflow-y: auto;">
            <p style="color: var(--text-sec); font-size: 12.5px;">Loading transfer logs...</p>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Discreet, Self-Dismissing Floating Progress Capsule (Clears Immediately on Completion) -->
  <div class="discreet-capsule" id="discreetCapsule">
    <div style="display: flex; align-items: center; justify-content: space-between; font-size: 12.5px; font-weight: 700;">
      <span id="statusText" style="color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 240px;">Beaming...</span>
      <span style="display: flex; align-items: center; gap: 8px;">
        <span id="speedBadge" style="color: var(--accent); font-size: 11.5px;">0.0 MB/s</span>
        <span id="pctBadge" style="background: rgba(0,245,212,0.16); color: var(--accent); padding: 2px 8px; border-radius: 99px; font-size: 11px;">0%</span>
      </span>
    </div>
    <div class="progress-bar-wrap">
      <div class="progress-bar" id="progressBar"></div>
    </div>
  </div>

  <script>
    const canvas = document.getElementById('auroraCanvas');
    const ctx = canvas.getContext('2d');
    let phase = 0;
    let auraIntensity = 0.35;
    let targetIntensity = 0.35;
    let shockwave = -1;
    let auraPathActive = false;
    let auraPathProgress = 0;

    function resize() { canvas.width = window.innerWidth; canvas.height = window.innerHeight; }
    window.addEventListener('resize', resize);
    resize();

    function evalCubicBezier(t, p0, p1, p2, p3) {
      const u = 1 - t;
      return {
        x: u*u*u*p0.x + 3*u*u*t*p1.x + 3*u*t*t*p2.x + t*t*t*p3.x,
        y: u*u*u*p0.y + 3*u*u*t*p1.y + 3*u*t*t*p2.y + t*t*t*p3.y
      };
    }

    function renderAurora() {
      phase += 0.025;
      auraIntensity += (targetIntensity - auraIntensity) * 0.08;
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      const cx = canvas.width * 0.5 + Math.sin(phase * 0.7) * 90;
      const cy = canvas.height * 0.35 + Math.cos(phase * 0.5) * 60;
      const r1 = (280 + auraIntensity * 220);

      const g1 = ctx.createRadialGradient(cx, cy, 10, cx, cy, r1);
      g1.addColorStop(0, 'rgba(0, 245, 212, ' + (0.14 + auraIntensity * 0.22) + ')');
      g1.addColorStop(0.5, 'rgba(157, 78, 221, ' + (0.10 + auraIntensity * 0.16) + ')');
      g1.addColorStop(1, 'rgba(9, 13, 20, 0)');
      ctx.fillStyle = g1;
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      if (auraPathActive) {
        const srcBtn = document.getElementById('sourceTransferBtn');
        const dstBadge = document.getElementById('targetPeerBadge');
        if (srcBtn && dstBadge) {
          const sRect = srcBtn.getBoundingClientRect();
          const dRect = dstBadge.getBoundingClientRect();
          const p0 = { x: sRect.left + sRect.width / 2, y: sRect.top + sRect.height / 2 };
          const p3 = { x: dRect.right - 90, y: dRect.top + dRect.height / 2 };
          const midX = (p0.x + p3.x) / 2;
          const midY = Math.min(p0.y, p3.y) - 110;
          const p1 = { x: (p0.x + midX) / 2, y: midY + Math.sin(phase * 2) * 16 };
          const p2 = { x: (p3.x + midX) / 2, y: midY - Math.cos(phase * 2) * 16 };

          ctx.beginPath();
          ctx.moveTo(p0.x, p0.y);
          ctx.bezierCurveTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y);
          ctx.strokeStyle = 'rgba(0, 245, 212, 0.28)';
          ctx.lineWidth = 10;
          ctx.lineCap = 'round';
          ctx.stroke();

          ctx.beginPath();
          ctx.moveTo(p0.x, p0.y);
          const steps = 40;
          const maxStep = Math.max(1, Math.floor(steps * auraPathProgress));
          for (let i = 1; i <= maxStep; i++) {
            const pt = evalCubicBezier(i / steps, p0, p1, p2, p3);
            ctx.lineTo(pt.x, pt.y);
          }
          ctx.strokeStyle = '#00F5D4';
          ctx.lineWidth = 4;
          ctx.stroke();

          const orb = evalCubicBezier(auraPathProgress, p0, p1, p2, p3);
          const orbGlow = ctx.createRadialGradient(orb.x, orb.y, 2, orb.x, orb.y, 28);
          orbGlow.addColorStop(0, 'rgba(255, 255, 255, 1)');
          orbGlow.addColorStop(0.35, 'rgba(0, 245, 212, 0.9)');
          orbGlow.addColorStop(0.7, 'rgba(157, 78, 221, 0.5)');
          orbGlow.addColorStop(1, 'rgba(9, 13, 20, 0)');
          ctx.fillStyle = orbGlow;
          ctx.beginPath();
          ctx.arc(orb.x, orb.y, 28, 0, Math.PI * 2);
          ctx.fill();
        }
      }

      if (shockwave >= 0) {
        shockwave += 0.025;
        ctx.beginPath();
        ctx.arc(canvas.width * 0.5, 0, shockwave * Math.max(canvas.width, canvas.height), 0, Math.PI * 2);
        ctx.strokeStyle = 'rgba(0, 245, 212, ' + Math.max(0, 1 - shockwave) + ')';
        ctx.lineWidth = 3;
        ctx.stroke();
        if (shockwave >= 1) shockwave = -1;
      }
      requestAnimationFrame(renderAurora);
    }
    renderAurora();

    const dropZone = document.getElementById('dropZone');
    const fileInput = document.getElementById('fileInput');
    const discreetCapsule = document.getElementById('discreetCapsule');
    const progressBar = document.getElementById('progressBar');
    const statusText = document.getElementById('statusText');
    const speedBadge = document.getElementById('speedBadge');
    const pctBadge = document.getElementById('pctBadge');

    ['dragenter', 'dragover'].forEach(name => {
      dropZone.addEventListener(name, (e) => { e.preventDefault(); dropZone.classList.add('dragover'); targetIntensity = 0.9; });
    });
    ['dragleave', 'drop'].forEach(name => {
      dropZone.addEventListener(name, (e) => { e.preventDefault(); dropZone.classList.remove('dragover'); targetIntensity = 0.35; });
    });
    dropZone.addEventListener('drop', (e) => { if (e.dataTransfer.files.length) uploadFiles(e.dataTransfer.files); });
    fileInput.addEventListener('change', () => { if (fileInput.files.length) uploadFiles(fileInput.files); });

    function uploadFiles(files) {
      discreetCapsule.style.display = 'block';
      progressBar.style.width = '0%';
      targetIntensity = 0.95;
      auraPathActive = true;
      auraPathProgress = 0.02;
      shockwave = 0;
      let idx = 0;

      function sendNext() {
        if (idx >= files.length) {
          targetIntensity = 0.35;
          auraPathActive = false;
          auraPathProgress = 0;
          shockwave = 0;
          fileInput.value = '';
          discreetCapsule.style.display = 'none';
          progressBar.style.width = '0%';
          fetchHistory();
          return;
        }
        const file = files[idx++];
        const startTime = performance.now();
        const xhr = new XMLHttpRequest();
        xhr.open('POST', '/upload?filename=' + encodeURIComponent(file.name), true);
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            const pct = (e.loaded / e.total) * 100;
            auraPathProgress = Math.max(0.02, Math.min(1.0, e.loaded / e.total));
            progressBar.style.width = pct.toFixed(1) + '%';
            pctBadge.innerText = pct.toFixed(0) + '%';
            const elapsed = Math.max(0.05, (performance.now() - startTime) / 1000);
            const mbps = (e.loaded / (1024 * 1024)) / elapsed;
            speedBadge.innerText = mbps.toFixed(1) + ' MB/s';
            statusText.innerText = '● Beaming ' + file.name;
          }
        };
        xhr.onload = () => { sendNext(); };
        xhr.onerror = () => { sendNext(); };
        xhr.send(file);
      }
      sendNext();
    }

    async function fetchStagedFiles() {
      try {
        const resp = await fetch('/api/staged');
        const items = await resp.json();
        const list = document.getElementById('stagedList');
        if (!items || !items.length) {
          list.innerHTML = '<p style="color: var(--text-sec); font-size: 12.5px;">No files staged. Select files in the Files tab on Android.</p>';
          return;
        }
        list.innerHTML = items.map(item =>
          '<div class="file-item">' +
            '<div><div style="font-weight: 700; font-size: 13px;">' + item.title + '</div>' +
            '<div style="font-size: 11px; color: var(--text-sec);">' + (item.size / (1024*1024)).toFixed(2) + ' MB</div></div>' +
            '<a class="btn" style="padding: 6px 12px; font-size: 11px;" href="/download?id=' + encodeURIComponent(item.id) + '">Download</a>' +
          '</div>'
        ).join('');
      } catch (e) {}
    }

    async function fetchHistory() {
      try {
        const resp = await fetch('/api/history');
        const items = await resp.json();
        const list = document.getElementById('historyList');
        if (!items || !items.length) {
          list.innerHTML = '<p style="color: var(--text-sec); font-size: 12.5px;">No transfers recorded yet.</p>';
          return;
        }
        list.innerHTML = items.map(rec => {
          const d = new Date(rec.timestamp).toLocaleTimeString();
          const col = rec.status === 'COMPLETED' ? '#10B981' : (rec.status === 'CANCELLED' ? '#FFB703' : '#F43F5E');
          return '<div class="file-item">' +
            '<div><div style="font-weight: 700; font-size: 12.5px;">' + (rec.direction === 'SENT' ? '↑ ' : '↓ ') + rec.fileName + '</div>' +
            '<div style="font-size: 11px; color: var(--text-sec);">' + rec.peer + ' • ' + d + '</div></div>' +
            '<span class="status-pill" style="background:' + col + '22; color:' + col + '; border:1px solid ' + col + ';">' + rec.status + '</span>' +
          '</div>';
        }).join('');
      } catch (e) {}
    }

    fetchStagedFiles();
    fetchHistory();
    setInterval(() => { fetchStagedFiles(); fetchHistory(); }, 4000);
  </script>
</body>
</html>
""".trimIndent()
        sendResponse(out, 200, "OK", "text/html; charset=utf-8", html.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sendCorsPreflight(out: OutputStream) {
        val resp = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, Content-Length\r\n")
            append("Content-Length: 0\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(resp.toByteArray(StandardCharsets.US_ASCII))
        out.flush()
    }

    private fun sendResponse(
        out: OutputStream,
        statusCode: Int,
        statusMsg: String,
        contentType: String,
        body: ByteArray
    ) {
        val header = buildString {
            append("HTTP/1.1 $statusCode $statusMsg\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.US_ASCII))
        out.write(body)
        out.flush()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
