# ⚡ BeamDrop Continuity

> **Apple-Style Fluid Ecosystem for Android & Windows 10/11**  
> **Official Publisher:** Sajjad Hossain (`sajjadtheacid@gmail.com`)  
> **Product Version:** 2.4.0.0

---

## 🌟 Key Highlights

- **Shared Cross-Platform Aura Path**: Real-time Canvas-based glowing Cubic Bezier migration conduit synchronizing the payload orb migrating from the source device's transfer button to the target peer device in lockstep across Android & Windows over UDP port `8989`.
- **Peer-to-Peer Zero-Config Discovery**: Automatic peer discovery over local Wi-Fi / LAN (TCP `8988`, UDP `8989`), Wi-Fi Direct (`WifiP2pManager`), and NFC tap discovery.
- **Discreet Self-Dismissing Progress**: High-speed, self-dismissing progress indicator that replaces notification queues during active transfers and clears completely upon transfer completion.
- **Hardware-Accelerated Windows Client**: Native 64-bit WPF application (`BeamDropWindows.cs`) running at 60 FPS with hardware-accelerated fluid aurora ripples, dark theme, and dual-mode transfer server/client.
- **Crash-Proof 1-Click Windows MSI & App Setup**: Includes `Package_BeamDropmsi.bat` (and `Package-BeamDropMsi.bat`) with automatic Mark-of-the-Web unblocking, Authenticode certificate registration (`Install-Certificate.bat`), local installation to `%LOCALAPPDATA%\Programs\Sajjad Hossain\BeamDrop\BeamDrop.exe`, Desktop shortcut generation, and `BeamDropInstaller.msi` builder.

---

## 📁 Repository Structure

```
BeamDrop/
├── app/                               # Android Native Application (Kotlin + Jetpack Compose)
│   ├── src/main/java/com/example/
│   │   ├── discovery/                 # NetworkDiscoveryManager, WifiP2pManager, NFC adapter
│   │   ├── engine/                    # WebShareServer, RealTransferEngine, SocketTransferEngine
│   │   ├── model/                     # AuraPathState, BeamPeer, SharableItem, TransferRecord
│   │   └── ui/                        # BeamDropScreen, BeamDropViewModel, FluidAuroraRipple
│   └── build.gradle.kts               # Dedicated packageWindowsInstaller Gradle tasks
├── windows/                           # Standalone Windows Desktop Client & MSI Installer
│   ├── BeamDropWindows.cs             # Native 64-bit C# WPF Continuity Desktop application
│   ├── BeamDrop.ico                   # Official High-Definition Windows Application Logo
│   ├── Package_BeamDropmsi.bat        # 1-Click Crash-Proof Installer & Launcher
│   ├── Package-BeamDropMsi.bat        # Alias for 1-Click Installer
│   ├── Run-BeamDrop.bat               # Direct native compiler & runner
│   ├── Build-BeamDropMsi.ps1          # Authenticode signing & MSI package generator
│   ├── Install-Certificate.bat        # Registers Sajjad Hossain certificate into Trusted Store
│   ├── SajjadHossain_CodeSigning.cer  # Authenticode Public Certificate
│   ├── SajjadHossain_CodeSigning.pfx  # Code Signing Certificate
│   └── BeamDrop.exe.manifest          # Windows DPI-Aware & Trust Manifest
├── public/                            # Public distribution assets
└── gradle/                            # Gradle wrapper configuration
```

---

## 🚀 Getting Started

### Windows Desktop Client

1. Clone this repository:
   ```cmd
   git clone <REPO_URL>
   cd BeamDrop\windows
   ```
2. Double-click `Package_BeamDropmsi.bat` (or `Package-BeamDropMsi.bat`):
   - Automatically unblocks Windows Mark-of-the-Web flags.
   - Installs the app to `%LOCALAPPDATA%\Programs\Sajjad Hossain\BeamDrop\BeamDrop.exe`.
   - Creates a Desktop shortcut and launches BeamDrop Continuity.
3. To eliminate Windows Defender / SmartScreen false alarms, run `Install-Certificate.bat` as Administrator once to trust the `Sajjad Hossain` publisher certificate.

### Android Application

1. Open the project in Android Studio or build via command line:
   ```cmd
   gradlew assembleDebug
   ```
2. Deploy the APK to your Android device running Android 7.0 (API 24) or higher.

---

## 🛡️ License & Copyright

Published & Maintained by **Sajjad Hossain** (`sajjadtheacid@gmail.com`). All rights reserved.
