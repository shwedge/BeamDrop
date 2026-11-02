using System.Windows.Media.Animation;
﻿using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Effects;
using System.Windows.Shapes;
using System.Windows.Threading;
using System.Windows.Interop;
using System.Media;
using System.Text.RegularExpressions;
using Microsoft.Win32;
using Path = System.IO.Path;
using SystemPath = System.Windows.Shapes.Path;

[assembly: AssemblyTitle("BeamDrop — Universal Continuity & P2P File Sharing")]
[assembly: AssemblyDescription("Official BeamDrop Continuity Desktop Application for Windows 10/11 — Developed by Sajjad Hossain Mridul")]
[assembly: AssemblyConfiguration("Release")]
[assembly: AssemblyCompany("Sajjad Hossain Mridul")]
[assembly: AssemblyProduct("BeamDrop Continuity")]
[assembly: AssemblyCopyright("Copyright © 2026 Sajjad Hossain Mridul. All rights reserved.")]
[assembly: AssemblyTrademark("Sajjad Hossain Mridul")]
[assembly: AssemblyCulture("")]
[assembly: ComVisible(false)]
[assembly: Guid("8F9C4B2A-5E1D-4C7A-9B3E-2D6F8A1C5E7B")]
[assembly: AssemblyVersion("2.5.0.0")]
[assembly: AssemblyFileVersion("2.5.0.0")]

namespace BeamDropWindows
{
    public class PeerNode
    {
        public string Id { get; set; }
        public string Name { get; set; }
        public string Ip { get; set; }
        public int Port { get; set; }
        public string Type { get; set; }
        public bool IsFavorite { get; set; }
        public DateTime LastSeen { get; set; }
        public double DiscoveredAtSeconds { get; set; }

        public string OctetTag
        {
            get
            {
                if (string.IsNullOrEmpty(Ip)) return "#100";
                var parts = Ip.Split('.');
                return "#" + parts[parts.Length - 1];
            }
        }

        public string BrandTag
        {
            get
            {
                if (!string.IsNullOrEmpty(Type) && Type.IndexOf("COMPUTER", StringComparison.OrdinalIgnoreCase) >= 0)
                    return "Windows";
                if (!string.IsNullOrEmpty(Name))
                {
                    var first = Name.Split(new[] { ' ', '-', '_' }, StringSplitOptions.RemoveEmptyEntries).FirstOrDefault();
                    if (!string.IsNullOrEmpty(first) && first.Length >= 2)
                        return char.ToUpper(first[0]) + first.Substring(1).ToLower();
                }
                return "Android";
            }
        }
    }

    public class TransferLogEntry
    {
        public string FileName { get; set; }
        public string Direction { get; set; }
        public string PeerName { get; set; }
        public long SizeBytes { get; set; }
        public double SpeedMbps { get; set; }
        public string Status { get; set; }
        public string SavedPath { get; set; }
        public string Timestamp { get; set; }
    }

    public class ReceivedFileRecord
    {
        public string Id { get; set; }
        public string FileName { get; set; }
        public string FilePath { get; set; }
        public long FileSize { get; set; }
        public string DeviceName { get; set; }
        public string SenderIp { get; set; }
        public DateTime Timestamp { get; set; }
        public string FormattedDate { get { return Timestamp.ToString("MMM d, yyyy"); } }
        public string FormattedExactTime { get { return Timestamp.ToString("hh:mm:ss tt"); } }
        public string FormattedSize { get { return MainWindow.FormatFileSize(FileSize); } }
    }

    public static class ReceivingHistoryManager
    {
        private static readonly object _syncLock = new object();
        private static List<ReceivedFileRecord> _cachedHistory = null;

        public static string GetHistoryFilePath()
        {
            try
            {
                string dir = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "Programs", "Sajjad Hossain Mridul", "BeamDrop");
                if (Directory.Exists(dir)) return Path.Combine(dir, "receiving_history.json");

                string appData = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "BeamDrop");
                if (!Directory.Exists(appData)) Directory.CreateDirectory(appData);
                return Path.Combine(appData, "receiving_history.json");
            }
            catch
            {
                return Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "receiving_history.json");
            }
        }

        public static List<ReceivedFileRecord> LoadHistory(string downloadDir = null)
        {
            lock (_syncLock)
            {
                if (_cachedHistory != null) return _cachedHistory;

                string filePath = GetHistoryFilePath();
                _cachedHistory = new List<ReceivedFileRecord>();

                if (File.Exists(filePath))
                {
                    try
                    {
                        string json = File.ReadAllText(filePath, Encoding.UTF8);
                        _cachedHistory = DeserializeRecords(json);
                    }
                    catch { }
                }

                // If empty, seed from existing downloaded files so the user immediately has history
                if (_cachedHistory.Count == 0 && !string.IsNullOrEmpty(downloadDir) && Directory.Exists(downloadDir))
                {
                    try
                    {
                        var di = new DirectoryInfo(downloadDir);
                        var recentFiles = di.GetFiles("*", SearchOption.TopDirectoryOnly)
                            .Where(f => !f.Name.StartsWith(".") && f.Extension != ".tmp")
                            .OrderByDescending(f => f.LastWriteTime)
                            .Take(25);

                        foreach (var f in recentFiles)
                        {
                            _cachedHistory.Add(new ReceivedFileRecord
                            {
                                Id = "rec_" + f.CreationTime.Ticks,
                                FileName = f.Name,
                                FilePath = f.FullName,
                                FileSize = f.Length,
                                DeviceName = "Connected Device",
                                SenderIp = "192.168.1.*",
                                Timestamp = f.LastWriteTime
                            });
                        }

                        if (_cachedHistory.Count > 0)
                        {
                            SaveHistory(_cachedHistory);
                        }
                    }
                    catch { }
                }

                return _cachedHistory;
            }
        }

        public static void AddRecord(string fileName, string filePath, long fileSize, string deviceName, string senderIp, DateTime? time = null)
        {
            lock (_syncLock)
            {
                if (_cachedHistory == null) _cachedHistory = LoadHistory();

                var rec = new ReceivedFileRecord
                {
                    Id = "rec_" + DateTime.UtcNow.Ticks,
                    FileName = fileName,
                    FilePath = filePath,
                    FileSize = fileSize,
                    DeviceName = !string.IsNullOrEmpty(deviceName) ? deviceName : "Remote Peer",
                    SenderIp = !string.IsNullOrEmpty(senderIp) ? senderIp : "127.0.0.1",
                    Timestamp = time ?? DateTime.Now
                };

                _cachedHistory.Insert(0, rec);
                if (_cachedHistory.Count > 150)
                {
                    _cachedHistory = _cachedHistory.Take(150).ToList();
                }

                SaveHistory(_cachedHistory);
            }
        }

        public static void ClearHistory()
        {
            lock (_syncLock)
            {
                if (_cachedHistory == null) _cachedHistory = new List<ReceivedFileRecord>();
                _cachedHistory.Clear();
                SaveHistory(_cachedHistory);
            }
        }

        public static void SaveHistory(List<ReceivedFileRecord> records)
        {
            try
            {
                string filePath = GetHistoryFilePath();
                string dir = Path.GetDirectoryName(filePath);
                if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir)) Directory.CreateDirectory(dir);

                string json = SerializeRecords(records);
                File.WriteAllText(filePath, json, Encoding.UTF8);
            }
            catch { }
        }

        private static string SerializeRecords(List<ReceivedFileRecord> list)
        {
            var sb = new StringBuilder();
            sb.AppendLine("[");
            for (int i = 0; i < list.Count; i++)
            {
                var r = list[i];
                sb.AppendLine("  {");
                sb.AppendLine(string.Format("    \"id\": \"{0}\",", EscapeJson(r.Id)));
                sb.AppendLine(string.Format("    \"fileName\": \"{0}\",", EscapeJson(r.FileName)));
                sb.AppendLine(string.Format("    \"filePath\": \"{0}\",", EscapeJson(r.FilePath)));
                sb.AppendLine(string.Format("    \"fileSize\": {0},", r.FileSize));
                sb.AppendLine(string.Format("    \"deviceName\": \"{0}\",", EscapeJson(r.DeviceName)));
                sb.AppendLine(string.Format("    \"senderIp\": \"{0}\",", EscapeJson(r.SenderIp)));
                sb.AppendLine(string.Format("    \"timestamp\": \"{0}\"", r.Timestamp.ToString("yyyy-MM-ddTHH:mm:ss")));
                sb.Append("  }");
                if (i < list.Count - 1) sb.Append(",");
                sb.AppendLine();
            }
            sb.AppendLine("]");
            return sb.ToString();
        }

        private static List<ReceivedFileRecord> DeserializeRecords(string json)
        {
            var list = new List<ReceivedFileRecord>();
            if (string.IsNullOrEmpty(json)) return list;

            var matches = System.Text.RegularExpressions.Regex.Matches(json, @"\{[^{}]*\}");
            foreach (System.Text.RegularExpressions.Match m in matches)
            {
                string block = m.Value;
                var rec = new ReceivedFileRecord();
                rec.Id = ExtractJsonVal(block, "id");
                rec.FileName = ExtractJsonVal(block, "fileName");
                rec.FilePath = ExtractJsonVal(block, "filePath");
                long size;
                if (long.TryParse(ExtractJsonVal(block, "fileSize"), out size)) rec.FileSize = size;
                rec.DeviceName = ExtractJsonVal(block, "deviceName");
                rec.SenderIp = ExtractJsonVal(block, "senderIp");
                DateTime dt;
                if (DateTime.TryParse(ExtractJsonVal(block, "timestamp"), out dt))
                    rec.Timestamp = dt;
                else
                    rec.Timestamp = DateTime.Now;

                if (!string.IsNullOrEmpty(rec.FileName))
                {
                    list.Add(rec);
                }
            }
            return list;
        }

        private static string ExtractJsonVal(string block, string key)
        {
            var match = System.Text.RegularExpressions.Regex.Match(block, "\"" + key + "\"\\s*:\\s*\"?([^\"\\r\\n,}]+)\"?");
            return match.Success ? match.Groups[1].Value.Trim() : "";
        }

        private static string EscapeJson(string s)
        {
            if (string.IsNullOrEmpty(s)) return "";
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\n", "\\n").Replace("\r", "\\r");
        }
    }

    /// <summary>
    /// Shared cross-platform animation state between Android and Windows that drives the
    /// Canvas-based 'Aura Path' representing the file payload migrating from the source
    /// device's transfer button to the target device.
    /// </summary>
    public class SharedAuraPathState
    {
        public bool IsActive { get; set; }
        public string SessionId { get; set; }
        public string SourceDeviceName { get; set; }
        public string TargetDeviceName { get; set; }
        public string FileName { get; set; }
        public double MigrationProgress { get; set; }
        public double SpeedMbps { get; set; }
        public bool IsOutgoing { get; set; }
        public Point SourceButtonPoint { get; set; }
        public Point TargetDevicePoint { get; set; }

        private string _flowDirection = "TOP";
        public string FlowDirection
        {
            get { return _flowDirection ?? "TOP"; }
            set { _flowDirection = value; }
        }

        public string ToWirePacket(string stateToken)
        {
            string safeSrc = string.IsNullOrEmpty(SourceDeviceName) ? "Windows_PC" : SourceDeviceName.Replace("|", "_");
            string safeDst = string.IsNullOrEmpty(TargetDeviceName) ? "Android_Peer" : TargetDeviceName.Replace("|", "_");
            string safeFile = string.IsNullOrEmpty(FileName) ? "Payload.bin" : FileName.Replace("|", "_");
            string safeDir = string.IsNullOrEmpty(FlowDirection) ? "TOP" : FlowDirection.Trim().ToUpperInvariant();
            double clamped = Math.Max(0.0, Math.Min(1.0, MigrationProgress));
            return string.Format(
                System.Globalization.CultureInfo.InvariantCulture,
                "BEAMDROP_AURA_PATH|{0}|{1}|{2}|{3}|{4:F4}|{5:F2}|{6}|{7}",
                SessionId ?? "aura_win", safeSrc, safeDst, safeFile, clamped, SpeedMbps, stateToken, safeDir);
        }
    }


    public class BeamDropSettings
    {
        public string DownloadDir { get; set; }
        public string DeviceName { get; set; }
        public bool IsDarkMode { get; set; }
        public bool RunOnStartup { get; set; }
        public bool MinimizeToTray { get; set; }
        public bool SoundChimeOnComplete { get; set; }
        public bool AutoAcceptFiles { get; set; }
        public bool TurboSpeedMode { get; set; }
        public bool AnimationsEnabled { get; set; }
        public bool AutoFinishEnabled { get; set; }

        public BeamDropSettings()
        {
            DownloadDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "BeamDrop");
            DeviceName = Environment.MachineName;
            IsDarkMode = true;
            RunOnStartup = false;
            MinimizeToTray = true;
            SoundChimeOnComplete = true;
            AutoAcceptFiles = true;
            TurboSpeedMode = true;
            AnimationsEnabled = true;
            AutoFinishEnabled = true;
        }

        private static string GetConfigPath()
        {
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string dir = Path.Combine(appData, "BeamDrop");
            if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);
            return Path.Combine(dir, "settings.json");
        }

        public static BeamDropSettings Load()
        {
            var s = new BeamDropSettings();
            try
            {
                string path = GetConfigPath();
                if (File.Exists(path))
                {
                    string json = File.ReadAllText(path, Encoding.UTF8);
                    s.DownloadDir = ExtractJsonString(json, "DownloadDir", s.DownloadDir);
                    s.DeviceName = ExtractJsonString(json, "DeviceName", s.DeviceName);
                    s.IsDarkMode = ExtractJsonBool(json, "IsDarkMode", s.IsDarkMode);
                    s.RunOnStartup = ExtractJsonBool(json, "RunOnStartup", s.RunOnStartup);
                    s.MinimizeToTray = ExtractJsonBool(json, "MinimizeToTray", s.MinimizeToTray);
                    s.SoundChimeOnComplete = ExtractJsonBool(json, "SoundChimeOnComplete", s.SoundChimeOnComplete);
                    s.AutoAcceptFiles = ExtractJsonBool(json, "AutoAcceptFiles", s.AutoAcceptFiles);
                    s.TurboSpeedMode = ExtractJsonBool(json, "TurboSpeedMode", s.TurboSpeedMode);
                    s.AnimationsEnabled = ExtractJsonBool(json, "AnimationsEnabled", s.AnimationsEnabled);
                    s.AutoFinishEnabled = ExtractJsonBool(json, "AutoFinishEnabled", s.AutoFinishEnabled);
                }
            }
            catch { }
            return s;
        }

        public void Save()
        {
            try
            {
                string path = GetConfigPath();
                var sb = new StringBuilder();
                sb.AppendLine("{");
                sb.AppendLine(string.Format("  \"DownloadDir\": \"{0}\",", EscapeJson(DownloadDir)));
                sb.AppendLine(string.Format("  \"DeviceName\": \"{0}\",", EscapeJson(DeviceName)));
                sb.AppendLine(string.Format("  \"IsDarkMode\": {0},", IsDarkMode.ToString().ToLowerInvariant()));
                sb.AppendLine(string.Format("  \"RunOnStartup\": {0},", RunOnStartup.ToString().ToLowerInvariant()));
                sb.AppendLine(string.Format("  \"MinimizeToTray\": {0},", MinimizeToTray.ToString().ToLowerInvariant()));
                sb.AppendLine(string.Format("  \"SoundChimeOnComplete\": {0},", SoundChimeOnComplete.ToString().ToLowerInvariant()));
                sb.AppendLine(string.Format("  \"AutoAcceptFiles\": {0},", AutoAcceptFiles.ToString().ToLowerInvariant()));
                sb.AppendLine(string.Format("  \"TurboSpeedMode\": {0},", TurboSpeedMode.ToString().ToLowerInvariant()));
                sb.AppendLine(string.Format("  \"AnimationsEnabled\": {0},", AnimationsEnabled.ToString().ToLowerInvariant()));
                sb.AppendLine(string.Format("  \"AutoFinishEnabled\": {0}", AutoFinishEnabled.ToString().ToLowerInvariant()));
                sb.AppendLine("}");
                File.WriteAllText(path, sb.ToString(), Encoding.UTF8);
            }
            catch { }
        }

        private static string EscapeJson(string val)
        {
            if (val == null) return "";
            return val.Replace("\\", "\\\\").Replace("\"", "\\\"");
        }

        private static string ExtractJsonString(string json, string key, string fallback)
        {
            string pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
            var m = Regex.Match(json, pattern);
            return m.Success ? m.Groups[1].Value.Replace("\\\\", "\\") : fallback;
        }

        private static bool ExtractJsonBool(string json, string key, bool fallback)
        {
            string pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
            var m = Regex.Match(json, pattern, RegexOptions.IgnoreCase);
            return m.Success ? bool.Parse(m.Groups[1].Value) : fallback;
        }
    }

    public class PromptInputDialog : Window
    {
        public string InputText { get; private set; }

        public PromptInputDialog(string title, string prompt, string initialValue, bool isDark)
        {
            Title = title;
            Width = 430;
            Height = 220;
            WindowStartupLocation = WindowStartupLocation.CenterOwner;
            ResizeMode = ResizeMode.NoResize;
            WindowStyle = WindowStyle.ToolWindow;

            Color bg = isDark ? Color.FromRgb(11, 21, 18) : Color.FromRgb(245, 249, 247);
            Color textCol = isDark ? Color.FromRgb(236, 253, 245) : Color.FromRgb(15, 41, 34);
            Color boxBg = isDark ? Color.FromRgb(28, 49, 44) : Colors.White;
            Color boxBorder = isDark ? Color.FromRgb(42, 71, 64) : Color.FromRgb(208, 227, 220);

            Background = new SolidColorBrush(bg);
            Foreground = new SolidColorBrush(textCol);
            FontFamily = new FontFamily("Segoe UI Variable Display, Segoe UI");

            var stack = new StackPanel { Margin = new Thickness(24, 20, 24, 20) };

            var label = new TextBlock
            {
                Text = prompt,
                FontSize = 13.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(textCol),
                Margin = new Thickness(0, 0, 0, 12)
            };
            stack.Children.Add(label);

            var box = new TextBox
            {
                Text = initialValue,
                FontSize = 14,
                Padding = new Thickness(10, 8, 10, 8),
                Background = new SolidColorBrush(boxBg),
                Foreground = new SolidColorBrush(textCol),
                BorderBrush = new SolidColorBrush(boxBorder),
                BorderThickness = new Thickness(1.5),
                Margin = new Thickness(0, 0, 0, 18)
            };
            stack.Children.Add(box);

            var btnRow = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };

            var cancelBtn = new Button
            {
                Content = "Cancel",
                Width = 85,
                Height = 32,
                Margin = new Thickness(0, 0, 10, 0),
                Background = new SolidColorBrush(isDark ? Color.FromRgb(28, 49, 44) : Color.FromRgb(230, 235, 232)),
                Foreground = new SolidColorBrush(textCol),
                BorderThickness = new Thickness(0)
            };
            cancelBtn.Click += (s, e) => { DialogResult = false; Close(); };
            btnRow.Children.Add(cancelBtn);

            var okBtn = new Button
            {
                Content = "Save",
                Width = 85,
                Height = 32,
                Background = new SolidColorBrush(Color.FromRgb(94, 212, 187)),
                Foreground = new SolidColorBrush(Color.FromRgb(15, 23, 42)),
                FontWeight = FontWeights.Bold,
                BorderThickness = new Thickness(0)
            };
            okBtn.Click += (s, e) =>
            {
                InputText = box.Text.Trim();
                DialogResult = true;
                Close();
            };
            btnRow.Children.Add(okBtn);

            stack.Children.Add(btnRow);
            Content = stack;

            Loaded += (s, e) => { box.Focus(); box.SelectAll(); };
        }
    }

    public class NetworkInfoDialog : Window
    {
        private readonly Action _onTriggerScan;
        private readonly ConcurrentDictionary<string, PeerNode> _peers;
        private readonly TextBlock _peersCountText;
        private readonly StackPanel _peersListStack;
        private readonly TextBlock _scanStatusText;
        private readonly string _deviceName;
        private readonly string _deviceId;
        private readonly string _localIp;
        private readonly int _tcpPort;
        private readonly int _udpPort;
        private readonly string _downloadDir;
        private readonly bool _isDark;

        public NetworkInfoDialog(
            bool isDark,
            string deviceName,
            string deviceId,
            string localIp,
            int tcpPort,
            int udpPort,
            ConcurrentDictionary<string, PeerNode> peers,
            string downloadDir,
            Action onTriggerScan)
        {
            _isDark = isDark;
            _deviceName = deviceName;
            _deviceId = deviceId;
            _localIp = localIp;
            _tcpPort = tcpPort;
            _udpPort = udpPort;
            _peers = peers;
            _downloadDir = downloadDir;
            _onTriggerScan = onTriggerScan;

            Title = "BeamDrop â€” Network & Discovery Diagnostics";
            Width = 630;
            Height = 670;
            WindowStartupLocation = WindowStartupLocation.CenterOwner;
            ResizeMode = ResizeMode.NoResize;
            WindowStyle = WindowStyle.ToolWindow;

            Color bg = isDark ? Color.FromRgb(11, 18, 30) : Color.FromRgb(248, 250, 252);
            Color textCol = isDark ? Color.FromRgb(241, 245, 249) : Color.FromRgb(15, 23, 42);
            Color textMuted = isDark ? Color.FromRgb(148, 163, 184) : Color.FromRgb(100, 116, 139);
            Color cardBg = isDark ? Color.FromArgb(170, 18, 28, 44) : Color.FromArgb(220, 255, 255, 255);
            Color cardBorder = isDark ? Color.FromArgb(60, 58, 228, 204) : Color.FromArgb(80, 200, 210, 220);
            Color cyanAccent = Color.FromRgb(58, 228, 204);
            Color purpleAccent = Color.FromRgb(172, 92, 246);

            Background = new SolidColorBrush(bg);
            Foreground = new SolidColorBrush(textCol);
            FontFamily = new FontFamily("Segoe UI Variable Display, Segoe UI");

            SourceInitialized += (s, e) =>
            {
                try
                {
                    var helper = new System.Windows.Interop.WindowInteropHelper(this);
                    MainWindow.ApplyHwndTitleBar(helper.Handle, _isDark);
                }
                catch { }
            };

            var rootDock = new DockPanel { Margin = new Thickness(24, 20, 24, 20) };

            // 1. Header Section
            var headerDock = new DockPanel { Margin = new Thickness(0, 0, 0, 16) };
            DockPanel.SetDock(headerDock, Dock.Top);

            var logoBadge = new Border
            {
                Width = 44,
                Height = 44,
                CornerRadius = new CornerRadius(12),
                Background = new LinearGradientBrush(cyanAccent, purpleAccent, new Point(0, 0), new Point(1, 1)),
                Margin = new Thickness(0, 0, 14, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            logoBadge.Child = new TextBlock
            {
                Text = "ðŸ“¡",
                FontSize = 22,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            DockPanel.SetDock(logoBadge, Dock.Left);
            headerDock.Children.Add(logoBadge);

            var titleCol = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            titleCol.Children.Add(new TextBlock
            {
                Text = "Network Diagnostics & Status",
                FontSize = 20,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(textCol)
            });
            titleCol.Children.Add(new TextBlock
            {
                Text = "Live Wi-Fi Subnet Topology, Ports & Multi-Device Discovery",
                FontSize = 12,
                Foreground = new SolidColorBrush(textMuted),
                Margin = new Thickness(0, 2, 0, 0)
            });
            headerDock.Children.Add(titleCol);
            rootDock.Children.Add(headerDock);

            // 2. Footer Section: Action Buttons
            var footerDock = new DockPanel { Margin = new Thickness(0, 16, 0, 0) };
            DockPanel.SetDock(footerDock, Dock.Bottom);

            var closeBtn = new Border
            {
                Height = 38,
                Padding = new Thickness(24, 0, 24, 0),
                CornerRadius = new CornerRadius(10),
                Background = new LinearGradientBrush(cyanAccent, Color.FromRgb(45, 212, 191), new Point(0, 0), new Point(1, 1)),
                Cursor = Cursors.Hand,
                HorizontalAlignment = HorizontalAlignment.Right
            };
            var closeText = new TextBlock
            {
                Text = "Done",
                FontWeight = FontWeights.Bold,
                FontSize = 13,
                Foreground = new SolidColorBrush(Color.FromRgb(11, 18, 30)),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            closeBtn.Child = closeText;
            closeBtn.MouseLeftButtonUp += (s, e) => Close();
            DockPanel.SetDock(closeBtn, Dock.Right);
            footerDock.Children.Add(closeBtn);

            var leftActions = new StackPanel { Orientation = Orientation.Horizontal };

            var scanBtn = new Border
            {
                Height = 38,
                Padding = new Thickness(16, 0, 16, 0),
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromArgb(40, cyanAccent.R, cyanAccent.G, cyanAccent.B)),
                BorderBrush = new SolidColorBrush(Color.FromArgb(90, cyanAccent.R, cyanAccent.G, cyanAccent.B)),
                BorderThickness = new Thickness(1),
                Cursor = Cursors.Hand,
                Margin = new Thickness(0, 0, 10, 0)
            };
            scanBtn.Child = new TextBlock
            {
                Text = "ðŸ”„ Probe Subnet Now",
                FontWeight = FontWeights.SemiBold,
                FontSize = 12.5,
                Foreground = new SolidColorBrush(cyanAccent),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            scanBtn.MouseLeftButtonUp += (s, e) =>
            {
                if (_onTriggerScan != null)
                {
                    _onTriggerScan();
                    _scanStatusText.Text = "Scanning 254 subnet hosts on ports " + _tcpPort + " & " + _udpPort + "...";
                    _scanStatusText.Foreground = new SolidColorBrush(cyanAccent);
                }
            };
            leftActions.Children.Add(scanBtn);

            var copyDiagBtn = new Border
            {
                Height = 38,
                Padding = new Thickness(14, 0, 14, 0),
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromArgb(25, 255, 255, 255)),
                BorderBrush = new SolidColorBrush(cardBorder),
                BorderThickness = new Thickness(1),
                Cursor = Cursors.Hand
            };
            copyDiagBtn.Child = new TextBlock
            {
                Text = "ðŸ“‹ Copy Diagnostics",
                FontWeight = FontWeights.SemiBold,
                FontSize = 12.5,
                Foreground = new SolidColorBrush(textCol),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            copyDiagBtn.MouseLeftButtonUp += (s, e) =>
            {
                try
                {
                    string report = GenerateDiagnosticReport();
                    Clipboard.SetText(report);
                    MessageBox.Show(this, "Network diagnostic report copied to clipboard!", "Copied", MessageBoxButton.OK, MessageBoxImage.Information);
                }
                catch { }
            };
            leftActions.Children.Add(copyDiagBtn);
            footerDock.Children.Add(leftActions);
            rootDock.Children.Add(footerDock);

            // 3. Center Section: Scrollable Diagnostic Cards
            var scroll = new ScrollViewer
            {
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
                Focusable = false
            };
            var cardsStack = new StackPanel { Margin = new Thickness(0, 0, 4, 0) };

            // Card 1: Device & Local Network Interface
            var card1 = CreateCard(cardBg, cardBorder);
            var c1Stack = new StackPanel();
            c1Stack.Children.Add(CreateSectionTitle("Local Device & Network Adapter", cyanAccent));
            AddDetailRow(c1Stack, "Device Name", _deviceName, textCol, textMuted);
            AddDetailRow(c1Stack, "Unique Device ID", _deviceId, textCol, textMuted);

            string adapterName = "Realtek / Intel Gaming Controller";
            string subnetMask = "255.255.255.0";
            string gateway = "192.168.1.1";
            string macAddress = "N/A";
            GetAdapterDetails(out adapterName, out subnetMask, out gateway, out macAddress);

            AddDetailRow(c1Stack, "Active Adapter", adapterName, textCol, textMuted);
            AddDetailRow(c1Stack, "Primary IPv4", _localIp + " (#" + (_localIp.Split('.').LastOrDefault() ?? "100") + ")", cyanAccent, textMuted, true);
            AddDetailRow(c1Stack, "Subnet Mask / CIDR", subnetMask + " (" + GetSubnetRange(_localIp, subnetMask) + ")", textCol, textMuted);
            AddDetailRow(c1Stack, "Default Gateway", gateway, textCol, textMuted);
            AddDetailRow(c1Stack, "Physical MAC", macAddress, textCol, textMuted);
            card1.Child = c1Stack;
            cardsStack.Children.Add(card1);

            // Card 2: Continuity Engine Protocols & Ports
            var card2 = CreateCard(cardBg, cardBorder);
            var c2Stack = new StackPanel();
            c2Stack.Children.Add(CreateSectionTitle("BeamDrop Continuity Protocol Engines", purpleAccent));
            AddDetailRow(c2Stack, "High-Speed TCP Socket", "Port " + _tcpPort + " (Active â€¢ 4MB Turbo Socket Window)", cyanAccent, textMuted);
            AddDetailRow(c2Stack, "UDP Multicast Beacon", "Port " + _udpPort + " (Active â€¢ 2.5s Subnet Broadcast)", cyanAccent, textMuted);
            AddDetailRow(c2Stack, "Zero-Config mDNS", "_beamdrop._tcp.local (Broadcasting on Subnet)", cyanAccent, textMuted);
            AddDetailRow(c2Stack, "Security & Handshake", "P2P Token Handshake â€¢ Zero Cloud Relay", textCol, textMuted);
            card2.Child = c2Stack;
            cardsStack.Children.Add(card2);

            // Card 3: Discovered Peers
            var card3 = CreateCard(cardBg, cardBorder);
            var c3Stack = new StackPanel();

            var peersTitleRow = new DockPanel { Margin = new Thickness(0, 0, 0, 8) };
            _peersCountText = new TextBlock
            {
                Text = _peers.Count + " Online",
                FontSize = 11,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(cyanAccent)
            };
            var peersPill = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(Color.FromArgb(40, cyanAccent.R, cyanAccent.G, cyanAccent.B)),
                Padding = new Thickness(8, 2, 8, 2),
                Child = _peersCountText
            };
            DockPanel.SetDock(peersPill, Dock.Right);
            peersTitleRow.Children.Add(peersPill);
            peersTitleRow.Children.Add(CreateSectionTitle("Discovered Devices on Local Wi-Fi", cyanAccent));
            c3Stack.Children.Add(peersTitleRow);

            _scanStatusText = new TextBlock
            {
                Text = _peers.Count > 0 ? "Real-time discovery active. Tap 'Probe Subnet Now' to refresh." : "No peers detected yet. Ensure your Android phone is on the same Wi-Fi.",
                FontSize = 11.5,
                Foreground = new SolidColorBrush(textMuted),
                Margin = new Thickness(0, 0, 0, 10),
                TextWrapping = TextWrapping.Wrap
            };
            c3Stack.Children.Add(_scanStatusText);

            _peersListStack = new StackPanel();
            PopulatePeersListUi(_peersListStack, textCol, textMuted, cyanAccent);
            c3Stack.Children.Add(_peersListStack);
            card3.Child = c3Stack;
            cardsStack.Children.Add(card3);

            // Card 4: Destination & Storage
            var card4 = CreateCard(cardBg, cardBorder);
            var c4Stack = new StackPanel();
            c4Stack.Children.Add(CreateSectionTitle("Storage Destination", cyanAccent));
            AddDetailRow(c4Stack, "Download Folder", _downloadDir, textCol, textMuted);

            string driveSpace = GetDriveFreeSpace(_downloadDir);
            AddDetailRow(c4Stack, "Storage Available", driveSpace, cyanAccent, textMuted);
            card4.Child = c4Stack;
            cardsStack.Children.Add(card4);

            scroll.Content = cardsStack;
            rootDock.Children.Add(scroll);

            Content = rootDock;
        }

        private static Border CreateCard(Color bg, Color border)
        {
            return new Border
            {
                Background = new SolidColorBrush(bg),
                BorderBrush = new SolidColorBrush(border),
                BorderThickness = new Thickness(1.2),
                CornerRadius = new CornerRadius(14),
                Padding = new Thickness(16, 14, 16, 14),
                Margin = new Thickness(0, 0, 0, 14)
            };
        }

        private static TextBlock CreateSectionTitle(string text, Color accent)
        {
            return new TextBlock
            {
                Text = text,
                FontSize = 13.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(accent),
                Margin = new Thickness(0, 0, 0, 8)
            };
        }

        private static void AddDetailRow(StackPanel parent, string label, string value, Color valColor, Color labelColor, bool isIp = false)
        {
            var dock = new DockPanel { Margin = new Thickness(0, 4, 0, 4) };
            dock.Children.Add(new TextBlock
            {
                Text = label,
                FontSize = 12.5,
                Foreground = new SolidColorBrush(labelColor),
                VerticalAlignment = VerticalAlignment.Center
            });

            var valStack = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
            var valText = new TextBlock
            {
                Text = value,
                FontSize = 12.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(valColor),
                VerticalAlignment = VerticalAlignment.Center,
                TextTrimming = TextTrimming.CharacterEllipsis,
                MaxWidth = 340
            };
            valStack.Children.Add(valText);

            if (isIp)
            {
                var copyBtn = new Border
                {
                    CornerRadius = new CornerRadius(6),
                    Background = new SolidColorBrush(Color.FromArgb(40, 58, 228, 204)),
                    Padding = new Thickness(6, 2, 6, 2),
                    Margin = new Thickness(8, 0, 0, 0),
                    Cursor = Cursors.Hand
                };
                copyBtn.Child = new TextBlock { Text = "Copy", FontSize = 10, FontWeight = FontWeights.Bold, Foreground = new SolidColorBrush(Color.FromRgb(58, 228, 204)) };
                copyBtn.MouseLeftButtonUp += (s, e) =>
                {
                    try { Clipboard.SetText(value.Split(' ')[0]); } catch { }
                };
                valStack.Children.Add(copyBtn);
            }

            DockPanel.SetDock(valStack, Dock.Right);
            dock.Children.Add(valStack);
            parent.Children.Add(dock);
        }

        private void PopulatePeersListUi(StackPanel parent, Color textCol, Color mutedCol, Color accent)
        {
            parent.Children.Clear();
            if (_peers.Count == 0)
            {
                parent.Children.Add(new TextBlock
                {
                    Text = "No external devices found on " + _localIp.Substring(0, Math.Max(0, _localIp.LastIndexOf('.') + 1)) + "*",
                    FontSize = 12,
                    Foreground = new SolidColorBrush(mutedCol),
                    FontStyle = FontStyles.Italic,
                    Margin = new Thickness(0, 2, 0, 4)
                });
                return;
            }

            foreach (var kvp in _peers)
            {
                var p = kvp.Value;
                var pCard = new Border
                {
                    CornerRadius = new CornerRadius(8),
                    Background = new SolidColorBrush(Color.FromArgb(40, 255, 255, 255)),
                    Padding = new Thickness(10, 8, 10, 8),
                    Margin = new Thickness(0, 0, 0, 6)
                };
                var pDock = new DockPanel();

                string glyph = p.Name.Contains("PC") || p.Name.Contains("Windows") ? "ðŸ’» " : "ðŸ“± ";
                pDock.Children.Add(new TextBlock
                {
                    Text = glyph + p.Name,
                    FontSize = 12.5,
                    FontWeight = FontWeights.Bold,
                    Foreground = new SolidColorBrush(textCol),
                    VerticalAlignment = VerticalAlignment.Center
                });

                var rightStack = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
                rightStack.Children.Add(new TextBlock
                {
                    Text = p.Ip + ":" + p.Port,
                    FontSize = 11.5,
                    Foreground = new SolidColorBrush(accent),
                    Margin = new Thickness(0, 0, 8, 0),
                    VerticalAlignment = VerticalAlignment.Center
                });
                rightStack.Children.Add(new TextBlock
                {
                    Text = "ðŸŸ¢ Connected",
                    FontSize = 10.5,
                    Foreground = new SolidColorBrush(Color.FromRgb(34, 197, 94)),
                    VerticalAlignment = VerticalAlignment.Center
                });
                DockPanel.SetDock(rightStack, Dock.Right);
                pDock.Children.Add(rightStack);
                pCard.Child = pDock;
                parent.Children.Add(pCard);
            }
        }

        private static void GetAdapterDetails(out string name, out string mask, out string gw, out string mac)
        {
            name = "Ethernet / Wi-Fi Adapter";
            mask = "255.255.255.0";
            gw = "192.168.1.1";
            mac = "N/A";
            try
            {
                foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (ni.OperationalStatus != OperationalStatus.Up || ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                    var ipProp = ni.GetIPProperties();
                    foreach (var ua in ipProp.UnicastAddresses)
                    {
                        if (ua.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ua.Address))
                        {
                            name = ni.Name + " (" + ni.Description + ")";
                            if (ua.IPv4Mask != null) mask = ua.IPv4Mask.ToString();
                            foreach (var g in ipProp.GatewayAddresses)
                            {
                                if (g.Address.AddressFamily == AddressFamily.InterNetwork) { gw = g.Address.ToString(); break; }
                            }
                            var rawMac = ni.GetPhysicalAddress().GetAddressBytes();
                            if (rawMac.Length > 0)
                            {
                                mac = string.Join(":", rawMac.Select(b => b.ToString("X2")));
                            }
                            return;
                        }
                    }
                }
            }
            catch { }
        }

        private static string GetSubnetRange(string ip, string mask)
        {
            try
            {
                var ipParts = ip.Split('.').Select(int.Parse).ToArray();
                return string.Format("{0}.{1}.{2}.1 - {0}.{1}.{2}.254", ipParts[0], ipParts[1], ipParts[2]);
            }
            catch
            {
                return "Class C Subnet (254 Hosts)";
            }
        }

        private static string GetDriveFreeSpace(string path)
        {
            try
            {
                string root = Path.GetPathRoot(path);
                var drive = new DriveInfo(root);
                double freeGb = drive.AvailableFreeSpace / (1024.0 * 1024.0 * 1024.0);
                double totalGb = drive.TotalSize / (1024.0 * 1024.0 * 1024.0);
                return string.Format("{0} ({1:F1} GB Free of {2:F0} GB)", root, freeGb, totalGb);
            }
            catch
            {
                return "Local Storage Partition";
            }
        }

        private string GenerateDiagnosticReport()
        {
            var sb = new StringBuilder();
            sb.AppendLine("=================================================================");
            sb.AppendLine("  BeamDrop Continuity â€” Network Diagnostic & Topology Report");
            sb.AppendLine("  Generated: " + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));
            sb.AppendLine("=================================================================");
            sb.AppendLine("Device Name:       " + _deviceName);
            sb.AppendLine("Device ID:         " + _deviceId);
            sb.AppendLine("Local IPv4:        " + _localIp);
            sb.AppendLine("TCP Transfer Port: " + _tcpPort + " (Active)");
            sb.AppendLine("UDP Beacon Port:   " + _udpPort + " (Active)");
            sb.AppendLine("Discovered Peers:  " + _peers.Count);
            foreach (var kvp in _peers)
            {
                sb.AppendLine("  â€¢ Peer: " + kvp.Value.Name + " @ " + kvp.Value.Ip + ":" + kvp.Value.Port);
            }
            sb.AppendLine("Download Folder:   " + _downloadDir);
            sb.AppendLine("Storage Status:    " + GetDriveFreeSpace(_downloadDir));
            sb.AppendLine("=================================================================");
            return sb.ToString();
        }
    }

    public class SupportDonateDialog : Window
    {
        public SupportDonateDialog(bool isDark)
        {
            Title = "Support & Donate — BeamDrop Continuity";
            Width = 530;
            Height = 475;
            WindowStartupLocation = WindowStartupLocation.CenterOwner;
            ResizeMode = ResizeMode.NoResize;
            WindowStyle = WindowStyle.ToolWindow;

            Color bg = isDark ? Color.FromRgb(11, 21, 18) : Color.FromRgb(245, 249, 247);
            Color textCol = isDark ? Color.FromRgb(236, 253, 245) : Color.FromRgb(15, 41, 34);
            Color textMuted = isDark ? Color.FromRgb(153, 178, 170) : Color.FromRgb(90, 115, 107);
            Color cardBg = isDark ? Color.FromArgb(190, 20, 36, 32) : Color.FromArgb(220, 255, 255, 255);
            Color cardBorder = isDark ? Color.FromRgb(42, 71, 64) : Color.FromRgb(208, 227, 220);
            Color mintAccent = Color.FromRgb(0, 245, 212);

            Background = new SolidColorBrush(bg);
            Foreground = new SolidColorBrush(textCol);
            FontFamily = new FontFamily("Segoe UI Variable Display, Segoe UI");

            var stack = new StackPanel { Margin = new Thickness(26, 20, 26, 22) };

            // Title row with Heart
            var headerRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 8) };
            headerRow.Children.Add(new TextBlock
            {
                Text = "❤️",
                FontSize = 22,
                Margin = new Thickness(0, 0, 10, 0),
                VerticalAlignment = VerticalAlignment.Center
            });
            headerRow.Children.Add(new TextBlock
            {
                Text = "Support & Donate",
                FontSize = 20,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(textCol),
                VerticalAlignment = VerticalAlignment.Center
            });
            stack.Children.Add(headerRow);

            // Intro text matching Android BeamSettingsView
            stack.Children.Add(new TextBlock
            {
                Text = "BeamDrop is built with passion by Sajjad Hossain Mridul and the BeamDrop Staff — 100% free, ad-free, and with zero cloud tracking.",
                FontSize = 12.5,
                Foreground = new SolidColorBrush(textMuted),
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 14),
                LineHeight = 18
            });

            // Frosted Glass Card for Ways to Support
            var waysCard = new Border
            {
                Background = new SolidColorBrush(cardBg),
                BorderBrush = new SolidColorBrush(cardBorder),
                BorderThickness = new Thickness(1.5),
                CornerRadius = new CornerRadius(14),
                Padding = new Thickness(16, 14, 16, 14),
                Margin = new Thickness(0, 0, 0, 18)
            };
            var waysStack = new StackPanel();
            waysStack.Children.Add(new TextBlock
            {
                Text = "Ways to Support Sajjad Hossain Mridul:",
                FontSize = 13.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(mintAccent),
                Margin = new Thickness(0, 0, 0, 8)
            });
            waysStack.Children.Add(new TextBlock
            {
                Text = "• Sponsor Development: github.com/sponsors/shwedge\n• Buy a Coffee for the Staff: buymeacoffee.com/sajjadhossainmridul\n• Star & Share BeamDrop with friends on Android & Windows!",
                FontSize = 12.5,
                Foreground = new SolidColorBrush(textCol),
                LineHeight = 20,
                TextWrapping = TextWrapping.Wrap
            });
            waysCard.Child = waysStack;
            stack.Children.Add(waysCard);

            // Donation action buttons
            var btnGrid = new UniformGrid { Columns = 2, Margin = new Thickness(0, 0, 0, 12) };

            var bmcBtn = new Border
            {
                Height = 40,
                Margin = new Thickness(0, 0, 6, 0),
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromRgb(255, 183, 3)),
                Cursor = Cursors.Hand
            };
            var bmcText = new TextBlock
            {
                Text = "☕ Buy Me a Coffee",
                FontWeight = FontWeights.Bold,
                FontSize = 13,
                Foreground = new SolidColorBrush(Color.FromRgb(15, 23, 42)),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            bmcBtn.Child = bmcText;
            bmcBtn.MouseLeftButtonUp += (s, e) => LaunchUrl("https://buymeacoffee.com/sajjadhossainmridul");
            btnGrid.Children.Add(bmcBtn);

            var ghBtn = new Border
            {
                Height = 40,
                Margin = new Thickness(6, 0, 0, 0),
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromRgb(219, 39, 119)),
                Cursor = Cursors.Hand
            };
            var ghText = new TextBlock
            {
                Text = "❤️ GitHub Sponsors",
                FontWeight = FontWeights.Bold,
                FontSize = 13,
                Foreground = new SolidColorBrush(Colors.White),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            ghBtn.Child = ghText;
            ghBtn.MouseLeftButtonUp += (s, e) => LaunchUrl("https://github.com/sponsors/shwedge");
            btnGrid.Children.Add(ghBtn);

            stack.Children.Add(btnGrid);

            // Secondary row: Copy links & Thank you
            var bottomDock = new DockPanel { Margin = new Thickness(0, 4, 0, 0) };

            var copyBtn = new Border
            {
                Height = 34,
                Padding = new Thickness(14, 0, 14, 0),
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(isDark ? Color.FromRgb(28, 49, 44) : Color.FromRgb(230, 235, 232)),
                BorderBrush = new SolidColorBrush(cardBorder),
                BorderThickness = new Thickness(1),
                Cursor = Cursors.Hand
            };
            var copyText = new TextBlock
            {
                Text = "📋 Copy Donation Links",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(textCol),
                VerticalAlignment = VerticalAlignment.Center
            };
            copyBtn.Child = copyText;
            copyBtn.MouseLeftButtonUp += (s, e) =>
            {
                try
                {
                    Clipboard.SetText("Buy Me a Coffee: https://buymeacoffee.com/sajjadhossainmridul\nGitHub Sponsors: https://github.com/sponsors/shwedge");
                    copyText.Text = "✓ Links Copied!";
                }
                catch { }
            };
            DockPanel.SetDock(copyBtn, Dock.Left);
            bottomDock.Children.Add(copyBtn);

            var thankBtn = new Border
            {
                Width = 115,
                Height = 34,
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(mintAccent),
                Cursor = Cursors.Hand,
                HorizontalAlignment = HorizontalAlignment.Right
            };
            var thankText = new TextBlock
            {
                Text = "Thank You ♥",
                FontWeight = FontWeights.Bold,
                FontSize = 12.5,
                Foreground = new SolidColorBrush(Color.FromRgb(15, 23, 42)),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            thankBtn.Child = thankText;
            thankBtn.MouseLeftButtonUp += (s, e) => { DialogResult = true; Close(); };
            DockPanel.SetDock(thankBtn, Dock.Right);
            bottomDock.Children.Add(thankBtn);

            stack.Children.Add(bottomDock);
            Content = stack;

            SourceInitialized += (s, e) =>
            {
                var helper = new WindowInteropHelper(this);
                MainWindow.ApplyHwndTitleBar(helper.Handle, isDark);
            };
        }

        private static void LaunchUrl(string url)
        {
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = url,
                    UseShellExecute = true
                });
            }
            catch
            {
                try { Process.Start(url); } catch { }
            }
        }
    }

    public class AboutStaffDialog : Window
    {
        public AboutStaffDialog(bool isDark)
        {
            Title = "About & Staff — BeamDrop Continuity";
            Width = 530;
            Height = 490;
            WindowStartupLocation = WindowStartupLocation.CenterOwner;
            ResizeMode = ResizeMode.NoResize;
            WindowStyle = WindowStyle.ToolWindow;

            Color bg = isDark ? Color.FromRgb(11, 21, 18) : Color.FromRgb(245, 249, 247);
            Color textCol = isDark ? Color.FromRgb(236, 253, 245) : Color.FromRgb(15, 41, 34);
            Color textMuted = isDark ? Color.FromRgb(153, 178, 170) : Color.FromRgb(90, 115, 107);
            Color cardBg = isDark ? Color.FromArgb(190, 20, 36, 32) : Color.FromArgb(220, 255, 255, 255);
            Color cardBorder = isDark ? Color.FromRgb(42, 71, 64) : Color.FromRgb(208, 227, 220);
            Color mintAccent = Color.FromRgb(0, 245, 212);

            Background = new SolidColorBrush(bg);
            Foreground = new SolidColorBrush(textCol);
            FontFamily = new FontFamily("Segoe UI Variable Display, Segoe UI");

            var stack = new StackPanel { Margin = new Thickness(26, 20, 26, 22) };

            // Title row
            var headerRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 8) };
            headerRow.Children.Add(new TextBlock
            {
                Text = "💧",
                FontSize = 22,
                Margin = new Thickness(0, 0, 10, 0),
                VerticalAlignment = VerticalAlignment.Center
            });
            var titleStack = new StackPanel();
            titleStack.Children.Add(new TextBlock
            {
                Text = "About BeamDrop",
                FontSize = 19,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(textCol)
            });
            titleStack.Children.Add(new TextBlock
            {
                Text = "Created & Developed by Sajjad Hossain Mridul",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(mintAccent)
            });
            headerRow.Children.Add(titleStack);
            stack.Children.Add(headerRow);

            // Intro text matching Android
            stack.Children.Add(new TextBlock
            {
                Text = "BeamDrop is a high-speed, zero-cloud local file sharing and continuity ecosystem for Android and Windows 10/11 featuring iOS 26 Clear Glass aesthetics, simultaneous directional swipe waves, QR & NFC pairing, and 100+ Mbps local Wi-Fi throughput.",
                FontSize = 12.5,
                Foreground = new SolidColorBrush(textMuted),
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 14),
                LineHeight = 18
            });

            // Frosted Glass Card for Staff & Core Team
            var staffCard = new Border
            {
                Background = new SolidColorBrush(cardBg),
                BorderBrush = new SolidColorBrush(cardBorder),
                BorderThickness = new Thickness(1.5),
                CornerRadius = new CornerRadius(14),
                Padding = new Thickness(16, 14, 16, 14),
                Margin = new Thickness(0, 0, 0, 20)
            };
            var staffStack = new StackPanel();
            staffStack.Children.Add(new TextBlock
            {
                Text = "Our Staff & Core Team",
                FontSize = 13.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(mintAccent),
                Margin = new Thickness(0, 0, 0, 8)
            });
            staffStack.Children.Add(new TextBlock
            {
                Text = "• Sajjad Hossain Mridul — Founder, Lead Architect & Principal Developer\n• BeamDrop Systems Engineering Staff — High-Speed TCP/UDP Socket Engine\n• BeamDrop Liquid Glass Design Staff — iOS 26 Clear Glass & Continuity\n• BeamDrop Security & QA Staff — Local LAN Verification & Code Signing",
                FontSize = 12,
                Foreground = new SolidColorBrush(textCol),
                LineHeight = 20,
                TextWrapping = TextWrapping.Wrap
            });
            staffCard.Child = staffStack;
            stack.Children.Add(staffCard);

            // Close button
            var closeBtn = new Border
            {
                Width = 110,
                Height = 34,
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(mintAccent),
                Cursor = Cursors.Hand,
                HorizontalAlignment = HorizontalAlignment.Right
            };
            closeBtn.Child = new TextBlock
            {
                Text = "Close",
                FontWeight = FontWeights.Bold,
                FontSize = 12.5,
                Foreground = new SolidColorBrush(Color.FromRgb(15, 23, 42)),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            closeBtn.MouseLeftButtonUp += (s, e) => { DialogResult = true; Close(); };
            stack.Children.Add(closeBtn);

            Content = stack;

            SourceInitialized += (s, e) =>
            {
                var helper = new WindowInteropHelper(this);
                MainWindow.ApplyHwndTitleBar(helper.Handle, isDark);
            };
        }
    }



    public static class ModernFolderPicker
    {
        [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
        private static extern void SHCreateItemFromParsingName(
            [MarshalAs(UnmanagedType.LPWStr)] string pszPath,
            IntPtr pbc,
            [In, MarshalAs(UnmanagedType.LPStruct)] Guid riid,
            [Out, MarshalAs(UnmanagedType.Interface)] out IShellItem ppv);

        private const uint FOS_PICKFOLDERS = 0x00000020;
        private const uint FOS_FORCEFILESYSTEM = 0x00000040;
        private const uint FOS_PATHMUSTEXIST = 0x00000800;
        private const uint SIGDN_FILESYSPATH = 0x80058000;

        public static string Show(Window owner, string title, string initialFolder)
        {
            try
            {
                var dialog = (IFileOpenDialog)new FileOpenDialogClass();
                uint options;
                dialog.GetOptions(out options);
                options |= FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM | FOS_PATHMUSTEXIST;
                dialog.SetOptions(options);

                if (!string.IsNullOrEmpty(title))
                {
                    dialog.SetTitle(title);
                }

                if (!string.IsNullOrEmpty(initialFolder) && Directory.Exists(initialFolder))
                {
                    try
                    {
                        IShellItem item;
                        Guid guid = typeof(IShellItem).GUID;
                        SHCreateItemFromParsingName(initialFolder, IntPtr.Zero, guid, out item);
                        if (item != null)
                        {
                            dialog.SetFolder(item);
                            dialog.SetDefaultFolder(item);
                        }
                    }
                    catch { }
                }

                IntPtr hwnd = owner != null ? new WindowInteropHelper(owner).Handle : IntPtr.Zero;
                int hr = dialog.Show(hwnd);
                if (hr == 0) // S_OK
                {
                    IShellItem resultItem;
                    dialog.GetResult(out resultItem);
                    if (resultItem != null)
                    {
                        string path;
                        resultItem.GetDisplayName(SIGDN_FILESYSPATH, out path);
                        return path;
                    }
                }
            }
            catch
            {
                // Fallback to WinForms if COM fails
                try
                {
                    var fb = new System.Windows.Forms.FolderBrowserDialog
                    {
                        Description = title,
                        SelectedPath = initialFolder,
                        ShowNewFolderButton = true
                    };
                    if (fb.ShowDialog() == System.Windows.Forms.DialogResult.OK)
                    {
                        return fb.SelectedPath;
                    }
                }
                catch { }
            }
            return null;
        }

        [ComImport]
        [Guid("DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7")]
        [CoClass(typeof(FileOpenDialogClass))]
        private interface NativeFileOpenDialog : IFileOpenDialog { }

        [ComImport]
        [Guid("DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7")]
        private class FileOpenDialogClass { }

        [ComImport]
        [Guid("42f85136-db7e-439c-85f1-e4075d135fc8")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IFileOpenDialog
        {
            [PreserveSig] int Show(IntPtr parent);
            void SetFileTypes();
            void SetFileTypeIndex();
            void GetFileTypeIndex();
            void Advise();
            void Unadvise();
            void SetOptions(uint fos);
            void GetOptions(out uint fos);
            void SetDefaultFolder(IShellItem psi);
            void SetFolder(IShellItem psi);
            void GetFolder(out IShellItem ppsi);
            void GetCurrentSelection(out IShellItem ppsi);
            void SetFileName([MarshalAs(UnmanagedType.LPWStr)] string pszName);
            void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string pszName);
            void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string pszTitle);
            void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string pszText);
            void SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string pszLabel);
            void GetResult(out IShellItem ppsi);
            void AddPlace(IShellItem psi, int fdap);
            void SetDefaultExtension([MarshalAs(UnmanagedType.LPWStr)] string pszDefaultExtension);
            void Close(int hr);
            void SetClientGuid();
            void ClearClientData();
            void SetFilter();
            void GetResults();
            void GetSelectedItems();
        }

        [ComImport]
        [Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IShellItem
        {
            void BindToHandler();
            void GetParent();
            void GetDisplayName(uint sigdnName, [MarshalAs(UnmanagedType.LPWStr)] out string ppszName);
            void GetAttributes();
            void Compare();
        }
    }

        public class MainWindow : Window
    {
        [DllImport("dwmapi.dll", PreserveSig = true)]
        private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

        private const int DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19;
        private const int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
        private const int DWMWA_CAPTION_COLOR = 35;
        private const int DWMWA_TEXT_COLOR = 36;

        private const int TCP_PORT = 8988;
        private const int UDP_PORT = 8989;
        private const string MDNS_GROUP = "224.0.0.251";
        private const int MDNS_PORT = 5353;

        private BeamDropSettings _settings;
        private bool _isDarkMode = true;
        private string _deviceName = Environment.MachineName;
        private string _localIp = "127.0.0.1";
        private string _deviceId;
        private string _downloadDir;

        private readonly ConcurrentDictionary<string, PeerNode> _peers = new ConcurrentDictionary<string, PeerNode>();
        private readonly List<string> _stagedFiles = new List<string>();
        private readonly List<TransferLogEntry> _history = new List<TransferLogEntry>();
        private Grid _historyOverlayHost;
        private Border _historyScrim;
        private Border _historyDrawer;
        private TranslateTransform _historyDrawerTranslate;
        private StackPanel _historyCardsStack;
        private TextBlock _historyCountText;
        private Border _historyEmptyContainer;
        private bool _isHistoryOpen = false;
        private Border _navHistoryBtn;
        private readonly List<Canvas> _sendButtonAuraCanvases = new List<Canvas>();

        private CancellationTokenSource _cts = new CancellationTokenSource();
        private CancellationTokenSource _activeTransferCts;
        private DispatcherTimer _peerPruneTimer;
        private System.Windows.Forms.NotifyIcon _trayIcon;

        // Settings & Navigation state
        private int _currentTab = 0; // 0 = Receive, 1 = Send, 2 = Settings
        private int _quickSaveMode = 2; // 0 = Off, 1 = Favorites, 2 = On
        private bool _animationsEnabled = true;
        private bool _autoFinishEnabled = true;

        // Top-level layout & Views
        private Grid _rootGrid;
        private Border _sidebar;
        private StackPanel _sideStack;
        private Grid _contentHost;
        private TextBlock _brandText;
        private TextBlock _authorInfoText;

        private Grid _receiveView;
        private ScrollViewer _sendView;
        private ScrollViewer _settingsView;

        // Sidebar buttons
        private Border _navReceiveBtn;
        private Border _navSendBtn;
        private Border _navSettingsBtn;

        // Receive View controls
        private Canvas _receiveEmblemCanvas;
        private TextBlock _receiveDeviceNameText;
        private TextBlock _receiveOctetText;
        private Border[] _quickSaveButtons = new Border[3];

        // Send View controls
        private Border _stagedSelectionCard;
        private TextBlock _stagedSummaryText;
        private StackPanel _stagedListPanel;
        private StackPanel _peersListPanel;
        private Border _scanButton;
        private TextBlock _scanButtonText;
        private bool _isScanning = false;

        // Discreet, Self-Dismissing Progress Indicator + Non-Blocking Fluid Aurora Stream
        private Grid _discreetTransferLayer;
        private Canvas _oneTransferCanvas;
        private Border _discreetCapsuleCard;
        private Ellipse _discreetPulseDot;
        private TextBlock _transferTitleText;
        private TextBlock _transferFileText;
        private TextBlock _transferMetricsText;
        private TextBlock _transferPercentBadge;
        private Border _transferProgressFill;

        // Non-blocking Desktop Completion Toast Card
        private Border _completionToastCard;
        private TextBlock _toastTitleText;
        private TextBlock _toastSubText;
        private TextBlock _toastFileText;
        private string _lastCompletedFilePath;
        private DispatcherTimer _toastAutoDismissTimer;

        // Animation & Shared Cross-Platform Aura Path state
        private readonly Stopwatch _animClock = Stopwatch.StartNew();
        private bool _isTransferring = false;
        private double _transferProgress = 0.0;
        private double _transferSpeedMbps = 0.0;
        private double _lastPeerDiscoveredTime = -10.0;
        private readonly SharedAuraPathState _auraPathState = new SharedAuraPathState();

        // Colors matching LocalSend & BeamDrop Fluid Aurora palette
        private static readonly Color BgDark = Color.FromRgb(11, 21, 18);        // #0B1512
        private static readonly Color SidebarBgDark = Color.FromRgb(8, 16, 14);  // #08100E
        private static readonly Color CardSurfaceDark = Color.FromRgb(28, 49, 44);   // #1C312C
        private static readonly Color CardBorderDark = Color.FromRgb(42, 71, 64);    // #2A4740
        private static readonly Color TextPrimaryDark = Color.FromRgb(236, 253, 245);
        private static readonly Color TextSecondaryDark = Color.FromRgb(148, 179, 170);

        // Light Theme Palette
        private static readonly Color BgLight = Color.FromRgb(245, 249, 247);      // #F5F9F7
        private static readonly Color SidebarBgLight = Color.FromRgb(232, 241, 237); // #E8F1ED
        private static readonly Color CardSurfaceLight = Colors.White;               // #FFFFFF
        private static readonly Color CardBorderLight = Color.FromRgb(208, 227, 220);// #D0E3DC
        private static readonly Color TextPrimaryLight = Color.FromRgb(15, 41, 34);  // #0F2922
        private static readonly Color TextSecondaryLight = Color.FromRgb(77, 119, 108);

        // Universal Accents
        private static readonly Color MintAccent = Color.FromRgb(94, 212, 187);  // #5ED4BB
        private static readonly Color ElectricCyan = Color.FromRgb(0, 245, 212); // #00F5D4
        private static readonly Color CyanGlow = Color.FromRgb(56, 189, 248);    // #38BDF8
        private static readonly Color RadiantPurple = Color.FromRgb(157, 78, 221);// #9D4EDD
        private static readonly Color SolarAmber = Color.FromRgb(255, 183, 3);   // #FFB703

        // Dynamic Active Theme Colors
        private Color _currentBg;
        private Color _currentSidebarBg;
        private Color _currentCardSurface;
        private Color _currentCardBorder;
        private Color _currentTextPrimary;
        private Color _currentTextSecondary;
        private Color _currentMintAccent;

        // Dynamic Theme Color Accessors
        private Color CardSurface { get { return _currentCardSurface; } }
        private Color CardBorder { get { return _currentCardBorder; } }
        private Color TextPrimary { get { return _currentTextPrimary; } }
        private Color TextSecondary { get { return _currentTextSecondary; } }

        private void SetThemeColors(bool dark)
        {
            _isDarkMode = dark;
            if (dark)
            {
                _currentBg = BgDark;
                _currentSidebarBg = SidebarBgDark;
                _currentCardSurface = CardSurfaceDark;
                _currentCardBorder = CardBorderDark;
                _currentTextPrimary = TextPrimaryDark;
                _currentTextSecondary = TextSecondaryDark;
                _currentMintAccent = MintAccent;
            }
            else
            {
                _currentBg = BgLight;
                _currentSidebarBg = SidebarBgLight;
                _currentCardSurface = CardSurfaceLight;
                _currentCardBorder = CardBorderLight;
                _currentTextPrimary = TextPrimaryLight;
                _currentTextSecondary = TextSecondaryLight;
                _currentMintAccent = Color.FromRgb(13, 148, 136); // #0D9488 high contrast on light
            }
        }

        protected override void OnSourceInitialized(EventArgs e)
        {
            base.OnSourceInitialized(e);
            ApplyTitleBarTheme(_isDarkMode);
        }

        public static void ApplyHwndTitleBar(IntPtr hwnd, bool dark)
        {
            try
            {
                if (hwnd == IntPtr.Zero) return;
                int useDark = dark ? 1 : 0;
                if (DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, ref useDark, sizeof(int)) != 0)
                {
                    DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1, ref useDark, sizeof(int));
                }

                // Windows 11 custom caption color (0x00BBGGRR format)
                int captionColor = dark ? 0x0012150B : 0x00F7F9F5;
                DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, ref captionColor, sizeof(int));

                int textColor = dark ? 0x00F5FDEC : 0x0022290F;
                DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, ref textColor, sizeof(int));
            }
            catch { }
        }

        private void ApplyTitleBarTheme(bool dark)
        {
            var helper = new WindowInteropHelper(this);
            ApplyHwndTitleBar(helper.Handle, dark);
        }

        private void ApplyTheme(bool dark)
        {
            SetThemeColors(dark);
            _settings.IsDarkMode = dark;
            _settings.Save();

            Background = new SolidColorBrush(_currentBg);
            Foreground = new SolidColorBrush(_currentTextPrimary);

            if (_sidebar != null)
            {
                _sidebar.Background = new SolidColorBrush(_currentSidebarBg);
                _sidebar.BorderBrush = new SolidColorBrush(Color.FromArgb(40, _currentMintAccent.R, _currentMintAccent.G, _currentMintAccent.B));
            }
            if (_brandText != null)
            {
                _brandText.Foreground = new SolidColorBrush(_currentTextPrimary);
            }
            if (_authorInfoText != null)
            {
                _authorInfoText.Foreground = new SolidColorBrush(_currentTextSecondary);
            }

            ApplyTitleBarTheme(dark);
            RebuildViews();
        }

        private void RebuildViews()
        {
            if (_contentHost == null) return;
            _contentHost.Children.Clear();
            _receiveView = BuildReceiveView();
            _sendView = BuildSendView();
            _settingsView = BuildSettingsView();
            _discreetTransferLayer = BuildDiscreetTransferProgressIndicator();
            _completionToastCard = BuildCompletionToast();

            _contentHost.Children.Add(_receiveView);
            _contentHost.Children.Add(_sendView);
            _contentHost.Children.Add(_settingsView);
            _contentHost.Children.Add(_discreetTransferLayer);
            _contentHost.Children.Add(_completionToastCard);

            SwitchTab(_currentTab);
            RefreshPeersListUi();
        }

        private void SetupSystemTray()
        {
            try
            {
                _trayIcon = new System.Windows.Forms.NotifyIcon();
                string icoFile = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "BeamDrop.ico");
                if (File.Exists(icoFile))
                {
                    _trayIcon.Icon = new System.Drawing.Icon(icoFile);
                }
                else
                {
                    try
                    {
                        string exePath = System.Diagnostics.Process.GetCurrentProcess().MainModule.FileName;
                        _trayIcon.Icon = System.Drawing.Icon.ExtractAssociatedIcon(exePath) ?? System.Drawing.SystemIcons.Application;
                    }
                    catch
                    {
                        _trayIcon.Icon = System.Drawing.SystemIcons.Application;
                    }
                }
                _trayIcon.Text = "BeamDrop Continuity (Sajjad Hossain Mridul)";
                _trayIcon.Visible = _settings.MinimizeToTray;
                _trayIcon.DoubleClick += (s, e) =>
                {
                    Show();
                    WindowState = WindowState.Normal;
                    Activate();
                };

                var menu = new System.Windows.Forms.ContextMenu();
                menu.MenuItems.Add("Open BeamDrop", (s, e) => { Show(); WindowState = WindowState.Normal; Activate(); });
                menu.MenuItems.Add("Open Downloads Folder", (s, e) => { if (Directory.Exists(_downloadDir)) Process.Start("explorer.exe", _downloadDir); });
                menu.MenuItems.Add("-");
                menu.MenuItems.Add("Exit BeamDrop", (s, e) => { if (_trayIcon != null) _trayIcon.Visible = false; Application.Current.Shutdown(); });
                _trayIcon.ContextMenu = menu;
            }
            catch { }
        }

        protected override void OnStateChanged(EventArgs e)
        {
            base.OnStateChanged(e);
            if (WindowState == WindowState.Minimized && _settings != null && _settings.MinimizeToTray)
            {
                Hide();
                if (_trayIcon != null)
                {
                    _trayIcon.Visible = true;
                    _trayIcon.ShowBalloonTip(2000, "BeamDrop is active", "Listening for incoming files in the background.", System.Windows.Forms.ToolTipIcon.Info);
                }
            }
        }

        protected override void OnClosing(System.ComponentModel.CancelEventArgs e)
        {
            if (_trayIcon != null)
            {
                _trayIcon.Visible = false;
                _trayIcon.Dispose();
            }
            base.OnClosing(e);
        }

        private static void SetRunOnStartup(bool enable)
        {
            try
            {
                using (var key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run", true))
                {
                    if (key != null)
                    {
                        string exePath = Process.GetCurrentProcess().MainModule.FileName;
                        if (enable)
                        {
                            key.SetValue("BeamDrop", "\"" + exePath + "\"");
                        }
                        else
                        {
                            key.DeleteValue("BeamDrop", false);
                        }
                    }
                }
            }
            catch { }
        }

        private void PlayCompletionChime()
        {
            try
            {
                if (_settings != null && _settings.SoundChimeOnComplete)
                {
                    SystemSounds.Asterisk.Play();
                }
            }
            catch { }
        }

        private void PruneStalePeers()
        {
            var now = DateTime.UtcNow;
            var staleKeys = new List<string>();

            foreach (var kvp in _peers)
            {
                var peer = kvp.Value;
                // If peer has not broadcast a beacon or packet in the last 10 seconds, it left Wi-Fi
                if ((now - peer.LastSeen).TotalSeconds > 10.0)
                {
                    staleKeys.Add(kvp.Key);
                }
            }

            if (staleKeys.Count > 0)
            {
                foreach (var key in staleKeys)
                {
                    PeerNode removed;
                    _peers.TryRemove(key, out removed);
                }
                RefreshPeersListUi();
            }
        }
        public MainWindow()
        {
            _settings = BeamDropSettings.Load();
            _downloadDir = _settings.DownloadDir;
            if (!Directory.Exists(_downloadDir)) Directory.CreateDirectory(_downloadDir);
            _deviceName = !string.IsNullOrEmpty(_settings.DeviceName) ? _settings.DeviceName : Environment.MachineName;
            _isDarkMode = _settings.IsDarkMode;
            _animationsEnabled = _settings.AnimationsEnabled;
            _autoFinishEnabled = _settings.AutoFinishEnabled;

            SetThemeColors(_isDarkMode);

            _localIp = ResolveBestLocalIp();
            _deviceId = "win_" + Math.Abs((_deviceName + "_" + _localIp).GetHashCode() % 1000000);

            Title = "BeamDrop";
            try
            {
                string icoFile = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "BeamDrop.ico");
                if (File.Exists(icoFile))
                {
                    Icon = new System.Windows.Media.Imaging.BitmapImage(new Uri(icoFile, UriKind.Absolute));
                }
                else
                {
                    string exePath = System.Diagnostics.Process.GetCurrentProcess().MainModule.FileName;
                    using (var sysIco = System.Drawing.Icon.ExtractAssociatedIcon(exePath))
                    {
                        if (sysIco != null)
                        {
                            Icon = System.Windows.Interop.Imaging.CreateBitmapSourceFromHIcon(
                                sysIco.Handle,
                                System.Windows.Int32Rect.Empty,
                                System.Windows.Media.Imaging.BitmapSizeOptions.FromEmptyOptions());
                        }
                    }
                }
            }
            catch {}

            Width = 1040;
            Height = 680;
            MinWidth = 820;
            MinHeight = 560;
            WindowStartupLocation = WindowStartupLocation.CenterScreen;
            Background = new SolidColorBrush(_currentBg);
            Foreground = new SolidColorBrush(_currentTextPrimary);
            FontFamily = new FontFamily("Segoe UI Variable Display, Segoe UI");
            AllowDrop = true;
            Drop += OnWindowFileDrop;
            KeyDown += (s, e) => { if (e.Key == Key.Escape && _isHistoryOpen) { CloseHistoryDrawer(); e.Handled = true; } };

            BuildUserInterface();
            SwitchTab(0);

            SetupSystemTray();

            _peerPruneTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(2.5) };
            _peerPruneTimer.Tick += (s, e) => PruneStalePeers();
            _peerPruneTimer.Start();

            CompositionTarget.Rendering += OnRenderFrame;
            StartNetworkEngines();
        }

        private void BuildUserInterface()
        {
            _rootGrid = new Grid();
            _rootGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(220) });
            _rootGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            // 1. Left Navigation Sidebar (Receive | Send | Settings)
            _sidebar = new Border
            {
                Background = new SolidColorBrush(_currentSidebarBg),
                BorderBrush = new SolidColorBrush(Color.FromArgb(40, _currentMintAccent.R, _currentMintAccent.G, _currentMintAccent.B)),
                BorderThickness = new Thickness(0, 0, 1, 0),
                Padding = new Thickness(18, 28, 18, 20)
            };

            _sideStack = new StackPanel();

            // Brand Header with Android-style glowing gradient logo badge
            var brandHeader = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                Margin = new Thickness(6, 0, 0, 28),
                VerticalAlignment = VerticalAlignment.Center
            };

            var logoBadge = new Border
            {
                Width = 38,
                Height = 38,
                CornerRadius = new CornerRadius(10),
                ClipToBounds = true,
                BorderBrush = new LinearGradientBrush(ElectricCyan, RadiantPurple, 45.0),
                BorderThickness = new Thickness(1.2),
                Effect = new DropShadowEffect
                {
                    Color = ElectricCyan,
                    BlurRadius = 14,
                    ShadowDepth = 0,
                    Opacity = 0.55
                }
            };

            var viewbox = new Viewbox
            {
                Width = 38,
                Height = 38,
                Stretch = Stretch.Uniform
            };

            var logoCanvas = new Canvas
            {
                Width = 108,
                Height = 108,
                ClipToBounds = true
            };

            // 1. Android Brand Gradient Squircle Background
            var bgGrad = new LinearGradientBrush(
                Color.FromRgb(58, 228, 204),
                Color.FromRgb(172, 92, 246),
                new Point(0, 0),
                new Point(1, 1)
            );
            bgGrad.GradientStops.Add(new GradientStop(Color.FromRgb(58, 228, 204), 0.0));
            bgGrad.GradientStops.Add(new GradientStop(Color.FromRgb(68, 180, 248), 0.33));
            bgGrad.GradientStops.Add(new GradientStop(Color.FromRgb(126, 142, 248), 0.68));
            bgGrad.GradientStops.Add(new GradientStop(Color.FromRgb(172, 92, 246), 1.0));

            var bgRect = new System.Windows.Shapes.Rectangle
            {
                Width = 108,
                Height = 108,
                RadiusX = 30,
                RadiusY = 30,
                Fill = bgGrad
            };
            logoCanvas.Children.Add(bgRect);

            // 2. 3-Node Connected Share Glyph matching Android
            var shareBars = new SystemPath
            {
                Data = Geometry.Parse("M70,35 L38,54 L70,73"),
                Stroke = new SolidColorBrush(Color.FromRgb(11, 18, 30)),
                StrokeThickness = 7.0,
                StrokeLineJoin = PenLineJoin.Round,
                StrokeStartLineCap = PenLineCap.Round,
                StrokeEndLineCap = PenLineCap.Round
            };
            logoCanvas.Children.Add(shareBars);

            var glyphColor = new SolidColorBrush(Color.FromRgb(11, 18, 30));
            var leftNode = new System.Windows.Shapes.Ellipse
            {
                Width = 21,
                Height = 21,
                Fill = glyphColor
            };
            Canvas.SetLeft(leftNode, 38 - 10.5);
            Canvas.SetTop(leftNode, 54 - 10.5);
            logoCanvas.Children.Add(leftNode);

            var trNode = new System.Windows.Shapes.Ellipse
            {
                Width = 21,
                Height = 21,
                Fill = glyphColor
            };
            Canvas.SetLeft(trNode, 70 - 10.5);
            Canvas.SetTop(trNode, 35 - 10.5);
            logoCanvas.Children.Add(trNode);

            var brNode = new System.Windows.Shapes.Ellipse
            {
                Width = 21,
                Height = 21,
                Fill = glyphColor
            };
            Canvas.SetLeft(brNode, 70 - 10.5);
            Canvas.SetTop(brNode, 73 - 10.5);
            logoCanvas.Children.Add(brNode);

            viewbox.Child = logoCanvas;
            logoBadge.Child = viewbox;

            brandHeader.Children.Add(logoBadge);

            var titleCol = new StackPanel
            {
                Margin = new Thickness(10, 0, 0, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            _brandText = new TextBlock
            {
                Text = "BeamDrop",
                FontSize = 20,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(_currentTextPrimary)
            };
            var subText = new TextBlock
            {
                Text = "Continuity",
                FontSize = 10.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(ElectricCyan)
            };
            titleCol.Children.Add(_brandText);
            titleCol.Children.Add(subText);
            brandHeader.Children.Add(titleCol);

            _sideStack.Children.Add(brandHeader);

            _navReceiveBtn = CreateNavButton("Receive", "📡", 0);
            _navSendBtn = CreateNavButton("Send", "➤", 1);
            _navSettingsBtn = CreateNavButton("Settings", "⚙", 2);

            _sideStack.Children.Add(_navReceiveBtn);
            _sideStack.Children.Add(_navSendBtn);
            _sideStack.Children.Add(_navSettingsBtn);
            _navHistoryBtn = CreateNavButton("History", "ðŸ•’", -1);
            _navHistoryBtn.MouseLeftButtonUp += (s, e) => OpenHistoryDrawer();
            _sideStack.Children.Add(_navHistoryBtn);

            _authorInfoText = new TextBlock
            {
                Text = "Published by Sajjad Hossain Mridul\nv2.5.0 • Verified Windows App",
                FontSize = 10.5,
                Foreground = new SolidColorBrush(_currentTextSecondary),
                Margin = new Thickness(8, 28, 0, 0)
            };
            _sideStack.Children.Add(_authorInfoText);

            var donateSidebarBtn = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(Color.FromArgb(35, 0, 245, 212)),
                BorderBrush = new SolidColorBrush(Color.FromArgb(90, 0, 245, 212)),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(10, 6, 10, 6),
                Margin = new Thickness(6, 14, 6, 0),
                Cursor = Cursors.Hand,
                HorizontalAlignment = HorizontalAlignment.Left
            };
            donateSidebarBtn.Child = new TextBlock
            {
                Text = "❤️ Support & Donate",
                FontSize = 11,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(_currentTextPrimary)
            };
            donateSidebarBtn.MouseLeftButtonUp += (s, e) => ShowSupportAndDonateDialog();
            _sideStack.Children.Add(donateSidebarBtn);

            _sidebar.Child = _sideStack;
            Grid.SetColumn(_sidebar, 0);
            _rootGrid.Children.Add(_sidebar);

            // 2. Right Content Host
            _contentHost = new Grid();
            _receiveView = BuildReceiveView();
            _sendView = BuildSendView();
            _settingsView = BuildSettingsView();
            _discreetTransferLayer = BuildDiscreetTransferProgressIndicator();
            _completionToastCard = BuildCompletionToast();

            _contentHost.Children.Add(_receiveView);
            _contentHost.Children.Add(_sendView);
            _contentHost.Children.Add(_settingsView);
            _contentHost.Children.Add(_discreetTransferLayer);
            _contentHost.Children.Add(_completionToastCard);

            Grid.SetColumn(_contentHost, 1);
            _rootGrid.Children.Add(_contentHost);

            BuildHistoryDrawer();
            Content = _rootGrid;
        }

        private Border CreateNavButton(string label, string icon, int tabIndex)
        {
            var border = new Border
            {
                CornerRadius = new CornerRadius(20),
                Padding = new Thickness(16, 11, 16, 11),
                Margin = new Thickness(0, 0, 0, 8),
                Cursor = Cursors.Hand,
                Background = Brushes.Transparent
            };

            var row = new StackPanel { Orientation = Orientation.Horizontal };
            row.Children.Add(new TextBlock
            {
                Text = icon,
                FontSize = 15,
                Foreground = new SolidColorBrush(TextPrimary),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 14, 0)
            });
            row.Children.Add(new TextBlock
            {
                Text = label,
                FontSize = 14,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(TextPrimary),
                VerticalAlignment = VerticalAlignment.Center
            });

            border.Child = row;
            border.MouseLeftButtonUp += (s, e) => SwitchTab(tabIndex);
            return border;
        }

        private void SwitchTab(int tabIndex)
        {
            _currentTab = tabIndex;
            _receiveView.Visibility = tabIndex == 0 ? Visibility.Visible : Visibility.Collapsed;
            _sendView.Visibility = tabIndex == 1 ? Visibility.Visible : Visibility.Collapsed;
            _settingsView.Visibility = tabIndex == 2 ? Visibility.Visible : Visibility.Collapsed;

            var activeBrush = new SolidColorBrush(Color.FromRgb(30, 58, 52));
            _navReceiveBtn.Background = tabIndex == 0 ? activeBrush : Brushes.Transparent;
            _navSendBtn.Background = tabIndex == 1 ? activeBrush : Brushes.Transparent;
            _navSettingsBtn.Background = tabIndex == 2 ? activeBrush : Brushes.Transparent;
        }

        private Grid BuildReceiveView()
        {
            var grid = new Grid();

            // Top-right action icons (History & Network Info)
            var topRight = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Right,
                VerticalAlignment = VerticalAlignment.Top,
                Margin = new Thickness(0, 22, 28, 0)
            };

            var historyBtn = CreateIconPill("Receiving History", () => OpenHistoryDrawer());
            var infoBtn = CreateIconPill("Network Info", () => ShowNetworkInfoDialog());
            topRight.Children.Add(historyBtn);
            topRight.Children.Add(infoBtn);
            grid.Children.Add(topRight);

            // Center Emblem + Device Name + #Octet
            var centerStack = new StackPanel
            {
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, -30, 0, 0)
            };

            _receiveEmblemCanvas = new Canvas
            {
                Width = 230,
                Height = 230,
                HorizontalAlignment = HorizontalAlignment.Center,
                Cursor = Cursors.Hand
            };
            _receiveEmblemCanvas.MouseLeftButtonUp += (s, e) => TriggerSubnetScan();
            centerStack.Children.Add(_receiveEmblemCanvas);

            _receiveDeviceNameText = new TextBlock
            {
                Text = _deviceName.ToUpperInvariant(),
                FontSize = 36,
                FontWeight = FontWeights.Normal,
                Foreground = new SolidColorBrush(TextPrimary),
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 14, 0, 4)
            };
            centerStack.Children.Add(_receiveDeviceNameText);

            var lastOctet = _localIp.Split('.').LastOrDefault() ?? "100";
            _receiveOctetText = new TextBlock
            {
                Text = "#" + lastOctet,
                FontSize = 18,
                Foreground = new SolidColorBrush(TextSecondary),
                HorizontalAlignment = HorizontalAlignment.Center
            };
            centerStack.Children.Add(_receiveOctetText);

            grid.Children.Add(centerStack);

            // Bottom Quick Save segmented control (Off | Favorites | On)
            var bottomStack = new StackPanel
            {
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Bottom,
                Margin = new Thickness(0, 0, 0, 38)
            };

            bottomStack.Children.Add(new TextBlock
            {
                Text = "Quick Save",
                FontSize = 13,
                Foreground = new SolidColorBrush(TextPrimary),
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 10)
            });

            var segOuter = new Border
            {
                CornerRadius = new CornerRadius(18),
                BorderBrush = new SolidColorBrush(Color.FromRgb(75, 105, 96)),
                BorderThickness = new Thickness(1),
                Background = new SolidColorBrush(Color.FromRgb(15, 29, 25))
            };

            var segGrid = new Grid { Width = 240, Height = 34 };
            segGrid.ColumnDefinitions.Add(new ColumnDefinition());
            segGrid.ColumnDefinitions.Add(new ColumnDefinition());
            segGrid.ColumnDefinitions.Add(new ColumnDefinition());

            string[] modes = { "Off", "Favorites", "On" };
            for (int i = 0; i < 3; i++)
            {
                int idx = i;
                var segBtn = new Border
                {
                    CornerRadius = new CornerRadius(17),
                    Cursor = Cursors.Hand,
                    Background = idx == _quickSaveMode ? new SolidColorBrush(Color.FromRgb(42, 75, 67)) : Brushes.Transparent
                };
                segBtn.Child = new TextBlock
                {
                    Text = modes[i],
                    FontSize = 12,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = new SolidColorBrush(TextPrimary),
                    HorizontalAlignment = HorizontalAlignment.Center,
                    VerticalAlignment = VerticalAlignment.Center
                };
                segBtn.MouseLeftButtonUp += (s, e) =>
                {
                    _quickSaveMode = idx;
                    for (int k = 0; k < 3; k++)
                    {
                        _quickSaveButtons[k].Background = k == _quickSaveMode
                            ? new SolidColorBrush(Color.FromRgb(42, 75, 67))
                            : Brushes.Transparent;
                    }
                };
                _quickSaveButtons[i] = segBtn;
                Grid.SetColumn(segBtn, i);
                segGrid.Children.Add(segBtn);
            }

            segOuter.Child = segGrid;
            bottomStack.Children.Add(segOuter);
            grid.Children.Add(bottomStack);

            return grid;
        }

        private ScrollViewer BuildSendView()
        {
            var scroll = new ScrollViewer
            {
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                Padding = new Thickness(40, 32, 40, 32)
            };

            var container = new StackPanel
            {
                MaxWidth = 580,
                HorizontalAlignment = HorizontalAlignment.Center
            };

            // Top Right Action Pills (Receiving History & Network Info)
            var sendTopRight = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Right,
                Margin = new Thickness(0, 0, 0, 16)
            };
            sendTopRight.Children.Add(CreateIconPill("ðŸ•’ Receiving History", () => OpenHistoryDrawer()));
            sendTopRight.Children.Add(CreateIconPill("â„¹ Network Info", () => ShowNetworkInfoDialog()));
            container.Children.Add(sendTopRight);

            // Selection Header
            container.Children.Add(new TextBlock
            {
                Text = "Selection",
                FontSize = 16,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(TextPrimary),
                Margin = new Thickness(0, 0, 0, 12)
            });

            // 4 Selection Tiles: File | Folder | Text | Paste
            var tileGrid = new UniformGrid { Columns = 4, Margin = new Thickness(0, 0, 0, 18) };
            tileGrid.Children.Add(CreateSelectionTile("📄", "File", OnPickFilesClicked));
            tileGrid.Children.Add(CreateSelectionTile("📁", "Folder", OnPickFolderClicked));
            tileGrid.Children.Add(CreateSelectionTile("≡", "Text", OnSendTextClicked));
            tileGrid.Children.Add(CreateSelectionTile("📋", "Paste", OnPasteClipboardClicked));
            container.Children.Add(tileGrid);

            // Staged Files Box (automatically cleared & hidden immediately after transfer completes)
            _stagedSelectionCard = new Border
            {
                Background = new SolidColorBrush(Color.FromRgb(20, 38, 34)),
                BorderBrush = new SolidColorBrush(Color.FromArgb(110, 94, 212, 187)),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(12),
                Padding = new Thickness(16, 12, 16, 12),
                Margin = new Thickness(0, 0, 0, 22),
                Visibility = Visibility.Collapsed
            };

            var stagedStack = new StackPanel();
            var stagedHeader = new DockPanel { Margin = new Thickness(0, 0, 0, 8) };
            _stagedSummaryText = new TextBlock
            {
                Text = "Selected (0)",
                FontSize = 13,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(MintAccent),
                VerticalAlignment = VerticalAlignment.Center
            };
            DockPanel.SetDock(_stagedSummaryText, Dock.Left);
            stagedHeader.Children.Add(_stagedSummaryText);

            var clearBtn = new TextBlock
            {
                Text = "Clear",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Color.FromRgb(248, 113, 113)),
                Cursor = Cursors.Hand,
                HorizontalAlignment = HorizontalAlignment.Right
            };
            clearBtn.MouseLeftButtonUp += (s, e) => ClearStagedSelection();
            DockPanel.SetDock(clearBtn, Dock.Right);
            stagedHeader.Children.Add(clearBtn);

            stagedStack.Children.Add(stagedHeader);
            _stagedListPanel = new StackPanel();
            stagedStack.Children.Add(_stagedListPanel);
            _stagedSelectionCard.Child = stagedStack;
            container.Children.Add(_stagedSelectionCard);

            // Nearby devices Header + Action Icons
            var nearbyHeader = new DockPanel { Margin = new Thickness(0, 4, 0, 12) };
            nearbyHeader.Children.Add(new TextBlock
            {
                Text = "Nearby devices",
                FontSize = 16,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(TextPrimary),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 16, 0)
            });

            var actionsRow = new StackPanel { Orientation = Orientation.Horizontal };

            _scanButton = new Border
            {
                Background = new SolidColorBrush(_currentCardSurface),
                BorderBrush = new SolidColorBrush(_currentCardBorder),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(10),
                Padding = new Thickness(12, 5, 12, 5),
                Margin = new Thickness(0, 0, 8, 0),
                Cursor = Cursors.Hand
            };
            _scanButtonText = new TextBlock
            {
                Text = "↻ Scan",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(_currentMintAccent)
            };
            _scanButton.Child = _scanButtonText;
            _scanButton.MouseLeftButtonUp += (s, e) => OnScanClicked();
            actionsRow.Children.Add(_scanButton);

            actionsRow.Children.Add(CreateSmallActionButton("⊕ Manual IP", PromptManualIpConnect));
            nearbyHeader.Children.Add(actionsRow);
            container.Children.Add(nearbyHeader);

            // Discovered Peer Cards List
            _peersListPanel = new StackPanel();
            container.Children.Add(_peersListPanel);

            // Troubleshoot & Helper text
            var troubleshoot = new TextBlock
            {
                Text = "Troubleshoot",
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(MintAccent),
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 22, 0, 10),
                Cursor = Cursors.Hand
            };
            troubleshoot.MouseLeftButtonUp += (s, e) =>
            {
                TriggerSubnetScan();
                MessageBox.Show(this,
                    string.Format("Scanning subnet around {0} on TCP port {1} and UDP port {2}.\n\nEnsure both your Android phone and Windows PC are connected to the same Wi-Fi network.",
                        _localIp, TCP_PORT, UDP_PORT),
                    "Troubleshoot Discovery", MessageBoxButton.OK, MessageBoxImage.Information);
            };
            container.Children.Add(troubleshoot);

            container.Children.Add(new TextBlock
            {
                Text = "Please ensure that the desired target is also on the same Wi-Fi network.",
                FontSize = 12,
                Foreground = new SolidColorBrush(TextSecondary),
                HorizontalAlignment = HorizontalAlignment.Center
            });

            scroll.Content = container;
            RefreshPeersListUi();
            return scroll;
        }

        private ScrollViewer BuildSettingsView()
        {
            var scroll = new ScrollViewer
            {
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                Padding = new Thickness(40, 28, 40, 36)
            };

            var stack = new StackPanel
            {
                MaxWidth = 620,
                HorizontalAlignment = HorizontalAlignment.Center
            };

            stack.Children.Add(new TextBlock
            {
                Text = "Settings",
                FontSize = 24,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(_currentTextPrimary),
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 24)
            });

            // 1. General & Appearance Card
            StackPanel generalRows;
            var generalCard = CreateSettingsSectionCard("Appearance & Display", out generalRows);
            AddSettingsThemeSelectorRow(generalRows);
            AddSettingsToggleRow(generalRows, "60fps Fluid Aura Ripple & Particle Stream", _animationsEnabled, val => { _animationsEnabled = val; _settings.AnimationsEnabled = val; _settings.Save(); });
            AddSettingsToggleRow(generalRows, "Discreet Self-Dismissing Progress Indicator", _autoFinishEnabled, val => { _autoFinishEnabled = val; _settings.AutoFinishEnabled = val; _settings.Save(); });
            stack.Children.Add(generalCard);

            // 2. Storage & Downloads Card
            StackPanel receiveRows;
            var receiveCard = CreateSettingsSectionCard("Storage & Downloads", out receiveRows);
            AddSettingsFolderRow(receiveRows, "Save to folder", _downloadDir);
            AddSettingsToggleRow(receiveRows, "Auto-Accept incoming files without prompt", _settings.AutoAcceptFiles, val => { _settings.AutoAcceptFiles = val; _settings.Save(); });
            stack.Children.Add(receiveCard);

            // 3. Device & Network Card
            StackPanel netRows;
            var netCard = CreateSettingsSectionCard("Device & Network Discovery", out netRows);
            AddSettingsDeviceNameRow(netRows, "Device name", _deviceName);
            AddSettingsBadgeRow(netRows, "Local IPv4", _localIp + " (" + "#" + (_localIp.Split('.').LastOrDefault() ?? "100") + ")");
            AddSettingsBadgeRow(netRows, "Port", TCP_PORT + " (TCP) / " + UDP_PORT + " (UDP)");
            AddSettingsBadgeRow(netRows, "Device Presence Timeout", "Auto-removes devices after leaving Wi-Fi");
            stack.Children.Add(netCard);

            // 4. System Integration & Performance Card
            StackPanel sysRows;
            var sysCard = CreateSettingsSectionCard("System Integration & Performance", out sysRows);
            AddSettingsToggleRow(sysRows, "Launch BeamDrop on Windows Startup", _settings.RunOnStartup, val => { _settings.RunOnStartup = val; SetRunOnStartup(val); _settings.Save(); });
            AddSettingsToggleRow(sysRows, "Minimize to System Tray (Keep alive in background)", _settings.MinimizeToTray, val => { _settings.MinimizeToTray = val; if (_trayIcon != null) _trayIcon.Visible = val; _settings.Save(); });
            AddSettingsToggleRow(sysRows, "Notification Sound Chime on Transfer Complete", _settings.SoundChimeOnComplete, val => { _settings.SoundChimeOnComplete = val; _settings.Save(); });
            AddSettingsToggleRow(sysRows, "Turbo Transfer Acceleration (100+ MB/s, 4MB TCP Windows)", _settings.TurboSpeedMode, val => { _settings.TurboSpeedMode = val; _settings.Save(); });
            stack.Children.Add(sysCard);

            // 5. Support BeamDrop & Community Card (matches Android Settings)
            StackPanel supportRows;
            var supportCard = CreateSettingsSectionCard("Support BeamDrop & Staff", out supportRows);
            AddSettingsButtonRow(supportRows, "Support Sajjad Hossain Mridul & Staff", "❤️ Donate", () => ShowSupportAndDonateDialog(), true);
            AddSettingsButtonRow(supportRows, "Buy a Coffee for Staff", "☕ buymeacoffee.com", () => LaunchUrl("https://buymeacoffee.com/sajjadhossainmridul"));
            AddSettingsButtonRow(supportRows, "Sponsor Development on GitHub", "★ github.com/sponsors", () => LaunchUrl("https://github.com/sponsors/shwedge"));
            AddSettingsButtonRow(supportRows, "About BeamDrop & Core Staff", "👥 View Staff", () => ShowAboutStaffDialog());
            stack.Children.Add(supportCard);

            // 6. Publisher & Authenticity Card
            StackPanel pubRows;
            var pubCard = CreateSettingsSectionCard("Publisher & Application Identity", out pubRows);
            AddSettingsBadgeRow(pubRows, "Publisher", "Sajjad Hossain Mridul");
            AddSettingsBadgeRow(pubRows, "Contact Email", "sajjadtheacid@gmail.com");
            AddSettingsBadgeRow(pubRows, "Product Name", "BeamDrop Continuity for Windows");
            AddSettingsBadgeRow(pubRows, "Version", "2.5.0.0 (64-bit Native WPF)");
            AddSettingsBadgeRow(pubRows, "OS Trust Status", "Verified Windows 10/11 Authenticode Signature");
            stack.Children.Add(pubCard);

            scroll.Content = stack;
            return scroll;
        }

        private Grid BuildDiscreetTransferProgressIndicator()
        {
            var layer = new Grid
            {
                Visibility = Visibility.Collapsed
            };

            _oneTransferCanvas = new Canvas
            {
                HorizontalAlignment = HorizontalAlignment.Stretch,
                VerticalAlignment = VerticalAlignment.Stretch,
                IsHitTestVisible = false
            };
            layer.Children.Add(_oneTransferCanvas);

            _discreetCapsuleCard = new Border
            {
                Width = 440,
                VerticalAlignment = VerticalAlignment.Bottom,
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 24),
                CornerRadius = new CornerRadius(22),
                Background = new SolidColorBrush(Color.FromArgb(245, 16, 30, 27)),
                BorderBrush = new LinearGradientBrush(ElectricCyan, RadiantPurple, 0.0),
                BorderThickness = new Thickness(1.2),
                Padding = new Thickness(18, 12, 18, 12),
                IsHitTestVisible = true,
                Effect = new DropShadowEffect { Color = ElectricCyan, BlurRadius = 24, ShadowDepth = 0, Opacity = 0.35 }
            };

            var stack = new StackPanel();
            var topRow = new DockPanel { Margin = new Thickness(0, 0, 0, 8) };

            // Pulsing glowing status dot
            _discreetPulseDot = new Ellipse
            {
                Width = 9,
                Height = 9,
                Fill = new SolidColorBrush(ElectricCyan),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 10, 0)
            };
            DockPanel.SetDock(_discreetPulseDot, Dock.Left);
            topRow.Children.Add(_discreetPulseDot);

            // Right side: Percent badge + Speed + Discreet Cancel button
            var rightRow = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center
            };
            DockPanel.SetDock(rightRow, Dock.Right);

            _transferMetricsText = new TextBlock
            {
                Text = "0.0 MB/s",
                FontSize = 11,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(MintAccent),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 8, 0)
            };
            rightRow.Children.Add(_transferMetricsText);

            var pctPill = new Border
            {
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromArgb(40, 0, 245, 212)),
                Padding = new Thickness(8, 2, 8, 2),
                Margin = new Thickness(0, 0, 8, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            _transferPercentBadge = new TextBlock
            {
                Text = "0%",
                FontSize = 11,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(ElectricCyan)
            };
            pctPill.Child = _transferPercentBadge;
            rightRow.Children.Add(pctPill);

            var cancelBtn = new Border
            {
                Width = 24,
                Height = 24,
                CornerRadius = new CornerRadius(12),
                Background = new SolidColorBrush(Color.FromArgb(45, 248, 113, 113)),
                Cursor = Cursors.Hand,
                VerticalAlignment = VerticalAlignment.Center
            };
            cancelBtn.Child = new TextBlock
            {
                Text = "✕",
                FontSize = 11,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(Color.FromRgb(248, 113, 113)),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            cancelBtn.MouseLeftButtonUp += (s, e) =>
            {
                if (_activeTransferCts != null) _activeTransferCts.Cancel();
                HideTransferOverlay();
            };
            rightRow.Children.Add(cancelBtn);
            topRow.Children.Add(rightRow);

            // Center title + filename
            var titleStack = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            _transferTitleText = new TextBlock
            {
                Text = "Beaming...",
                FontSize = 12.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(TextPrimary),
                TextTrimming = TextTrimming.CharacterEllipsis
            };
            _transferFileText = new TextBlock
            {
                Text = "Preparing file stream...",
                FontSize = 11,
                Foreground = new SolidColorBrush(TextSecondary),
                TextTrimming = TextTrimming.CharacterEllipsis
            };
            titleStack.Children.Add(_transferTitleText);
            titleStack.Children.Add(_transferFileText);
            topRow.Children.Add(titleStack);

            stack.Children.Add(topRow);

            // Sleek 4px Progress Bar
            var track = new Border
            {
                Height = 5,
                CornerRadius = new CornerRadius(2.5),
                Background = new SolidColorBrush(Color.FromRgb(10, 20, 18)),
                ClipToBounds = true
            };
            _transferProgressFill = new Border
            {
                Height = 5,
                CornerRadius = new CornerRadius(2.5),
                HorizontalAlignment = HorizontalAlignment.Left,
                Width = 0,
                Background = new LinearGradientBrush(ElectricCyan, RadiantPurple, 0.0)
            };
            track.Child = _transferProgressFill;
            stack.Children.Add(track);

            _discreetCapsuleCard.Child = stack;
            layer.Children.Add(_discreetCapsuleCard);

            return layer;
        }

        /// <summary>
        /// Builds the non-blocking desktop completion notification card on Windows,
        /// providing immediate "Open File", "Open Folder", and "Dismiss" controls.
        /// </summary>
        private Border BuildCompletionToast()
        {
            var card = new Border
            {
                Width = 390,
                VerticalAlignment = VerticalAlignment.Bottom,
                HorizontalAlignment = HorizontalAlignment.Right,
                Margin = new Thickness(0, 0, 24, 24),
                CornerRadius = new CornerRadius(16),
                Background = new SolidColorBrush(Color.FromArgb(246, 16, 28, 26)),
                BorderBrush = new LinearGradientBrush(MintAccent, ElectricCyan, 45.0),
                BorderThickness = new Thickness(1.4),
                Padding = new Thickness(16, 14, 16, 14),
                Visibility = Visibility.Collapsed,
                Effect = new DropShadowEffect
                {
                    Color = MintAccent,
                    BlurRadius = 24,
                    ShadowDepth = 2,
                    Opacity = 0.4
                }
            };

            var stack = new StackPanel();

            // Top header row: Checkmark badge + Title + Dismiss (✕)
            var headerRow = new DockPanel { Margin = new Thickness(0, 0, 0, 10) };

            var dismissBtn = new Border
            {
                Width = 24,
                Height = 24,
                CornerRadius = new CornerRadius(12),
                Background = new SolidColorBrush(Color.FromArgb(40, 255, 255, 255)),
                Cursor = Cursors.Hand,
                VerticalAlignment = VerticalAlignment.Center
            };
            dismissBtn.Child = new TextBlock
            {
                Text = "✕",
                FontSize = 11,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(TextSecondary),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            dismissBtn.MouseLeftButtonUp += (s, e) =>
            {
                card.Visibility = Visibility.Collapsed;
                if (_toastAutoDismissTimer != null) _toastAutoDismissTimer.Stop();
            };
            DockPanel.SetDock(dismissBtn, Dock.Right);
            headerRow.Children.Add(dismissBtn);

            var iconCircle = new Border
            {
                Width = 28,
                Height = 28,
                CornerRadius = new CornerRadius(14),
                Background = new SolidColorBrush(Color.FromArgb(50, 94, 212, 187)),
                Margin = new Thickness(0, 0, 10, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            iconCircle.Child = new TextBlock
            {
                Text = "✓",
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(MintAccent),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            DockPanel.SetDock(iconCircle, Dock.Left);
            headerRow.Children.Add(iconCircle);

            var textStack = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            _toastTitleText = new TextBlock
            {
                Text = "Transfer Completed!",
                FontSize = 13,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(TextPrimary)
            };
            _toastSubText = new TextBlock
            {
                Text = "Received from HONOR Phone",
                FontSize = 11,
                Foreground = new SolidColorBrush(MintAccent),
                Margin = new Thickness(0, 2, 0, 0)
            };
            textStack.Children.Add(_toastTitleText);
            textStack.Children.Add(_toastSubText);
            headerRow.Children.Add(textStack);

            stack.Children.Add(headerRow);

            // File details box
            var fileBox = new Border
            {
                Background = new SolidColorBrush(Color.FromArgb(120, 10, 22, 20)),
                CornerRadius = new CornerRadius(10),
                Padding = new Thickness(10, 8, 10, 8),
                Margin = new Thickness(0, 0, 0, 12)
            };
            _toastFileText = new TextBlock
            {
                Text = "file.mp4 • 45.2 MB",
                FontSize = 12,
                FontWeight = FontWeights.Medium,
                Foreground = new SolidColorBrush(TextPrimary),
                TextTrimming = TextTrimming.CharacterEllipsis
            };
            fileBox.Child = _toastFileText;
            stack.Children.Add(fileBox);

            // Action buttons row: [Open File] [Open Folder]
            var actionsRow = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Right
            };

            var openFileBtn = new Border
            {
                Background = new SolidColorBrush(MintAccent),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(14, 6, 14, 6),
                Margin = new Thickness(0, 0, 8, 0),
                Cursor = Cursors.Hand
            };
            openFileBtn.Child = new TextBlock
            {
                Text = "Open File",
                FontSize = 11.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(Color.FromRgb(15, 23, 42))
            };
            openFileBtn.MouseLeftButtonUp += (s, e) =>
            {
                try
                {
                    if (!string.IsNullOrEmpty(_lastCompletedFilePath) && File.Exists(_lastCompletedFilePath))
                    {
                        Process.Start(new ProcessStartInfo(_lastCompletedFilePath) { UseShellExecute = true });
                    }
                }
                catch { }
            };
            actionsRow.Children.Add(openFileBtn);

            var openFolderBtn = new Border
            {
                Background = new SolidColorBrush(Color.FromArgb(45, 255, 255, 255)),
                BorderBrush = new SolidColorBrush(Color.FromArgb(80, 255, 255, 255)),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(14, 6, 14, 6),
                Cursor = Cursors.Hand
            };
            openFolderBtn.Child = new TextBlock
            {
                Text = "Open Folder",
                FontSize = 11.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(TextPrimary)
            };
            openFolderBtn.MouseLeftButtonUp += (s, e) =>
            {
                try
                {
                    if (!string.IsNullOrEmpty(_lastCompletedFilePath) && File.Exists(_lastCompletedFilePath))
                    {
                        Process.Start("explorer.exe", string.Format("/select,\"{0}\"", _lastCompletedFilePath));
                    }
                    else if (Directory.Exists(_downloadDir))
                    {
                        Process.Start("explorer.exe", _downloadDir);
                    }
                }
                catch { }
            };
            actionsRow.Children.Add(openFolderBtn);

            stack.Children.Add(actionsRow);

            card.Child = stack;
            return card;
        }

        private void ShowCompletionToast(string title, string peerName, string fileName, long sizeBytes, string filePath)
        {
            Dispatcher.BeginInvoke(new Action(() =>
            {
                _lastCompletedFilePath = filePath;
                _toastTitleText.Text = title;
                _toastSubText.Text = !string.IsNullOrEmpty(peerName) ? peerName : "BeamDrop Transfer";
                _toastFileText.Text = string.Format("{0} • {1}", fileName, FormatFileSize(sizeBytes));
                _completionToastCard.Visibility = Visibility.Visible;

                if (_toastAutoDismissTimer == null)
                {
                    _toastAutoDismissTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(9) };
                    _toastAutoDismissTimer.Tick += (s, e) =>
                    {
                        _completionToastCard.Visibility = Visibility.Collapsed;
                        _toastAutoDismissTimer.Stop();
                    };
                }
                else
                {
                    _toastAutoDismissTimer.Stop();
                }
                _toastAutoDismissTimer.Start();
            }));
        }

        private void OnRenderFrame(object sender, EventArgs e)
        {
            // Render static Apple WWDC25 Liquid Glass Locate Squircle Emblem on Receive screen (0% idle CPU)
            if (_currentTab == 0 && _receiveEmblemCanvas != null && _receiveEmblemCanvas.Children.Count == 0)
            {
                double cx = 115;
                double cy = 115;

                // 1. 3D Pillowed Sapphire-Cobalt Squircle Base
                var squircle = new Border
                {
                    Width = 186,
                    Height = 186,
                    CornerRadius = new CornerRadius(44),
                    Background = new LinearGradientBrush(
                        Color.FromRgb(96, 165, 250),
                        Color.FromRgb(29, 78, 216),
                        34.0),
                    BorderBrush = new LinearGradientBrush(
                        Colors.White,
                        Color.FromRgb(186, 230, 253),
                        34.0),
                    BorderThickness = new Thickness(2.2),
                    Effect = new DropShadowEffect
                    {
                        Color = Color.FromRgb(37, 99, 235),
                        BlurRadius = 28,
                        ShadowDepth = 4,
                        Opacity = 0.45
                    }
                };
                Canvas.SetLeft(squircle, cx - 93);
                Canvas.SetTop(squircle, cy - 93);
                _receiveEmblemCanvas.Children.Add(squircle);

                // 2. Outer Frosted Milky-White Glass Disc
                double rOuter = 66;
                var outerDisc = new Ellipse
                {
                    Width = rOuter * 2,
                    Height = rOuter * 2,
                    Fill = new SolidColorBrush(Color.FromArgb(95, 255, 255, 255)),
                    Stroke = new SolidColorBrush(Color.FromArgb(200, 255, 255, 255)),
                    StrokeThickness = 1.6
                };
                Canvas.SetLeft(outerDisc, cx - rOuter);
                Canvas.SetTop(outerDisc, cy - rOuter - 6);
                _receiveEmblemCanvas.Children.Add(outerDisc);

                // 3. Middle Frosted Milky-White Glass Disc
                double rMid = 42;
                var midDisc = new Ellipse
                {
                    Width = rMid * 2,
                    Height = rMid * 2,
                    Fill = new SolidColorBrush(Color.FromArgb(135, 255, 255, 255)),
                    Stroke = new SolidColorBrush(Color.FromArgb(230, 255, 255, 255)),
                    StrokeThickness = 1.8
                };
                Canvas.SetLeft(midDisc, cx - rMid);
                Canvas.SetTop(midDisc, cy - rMid - 6);
                _receiveEmblemCanvas.Children.Add(midDisc);

                // 4. Glowing Milky-White Core + Rounded Frosted Glass Stem
                double coreRadius = 18;
                var core = new Ellipse
                {
                    Width = coreRadius * 2,
                    Height = coreRadius * 2,
                    Fill = new SolidColorBrush(Colors.White)
                };
                Canvas.SetLeft(core, cx - coreRadius);
                Canvas.SetTop(core, cy - coreRadius - 6);
                _receiveEmblemCanvas.Children.Add(core);

                var stem = new Border
                {
                    Width = 30,
                    Height = 52,
                    CornerRadius = new CornerRadius(15),
                    Background = new LinearGradientBrush(
                        Color.FromArgb(235, 255, 255, 255),
                        Color.FromArgb(180, 186, 230, 253),
                        90.0),
                    BorderBrush = new SolidColorBrush(Colors.White),
                    BorderThickness = new Thickness(1.5)
                };
                Canvas.SetLeft(stem, cx - 15);
                Canvas.SetTop(stem, cy + 14);
                _receiveEmblemCanvas.Children.Add(stem);
            }

            if ((!_isTransferring && !_auraPathState.IsActive) || _oneTransferCanvas == null)
            {
                return;
            }

            double t = _animClock.Elapsed.TotalSeconds;

            if (_discreetPulseDot != null)
            {
                double pulse = 0.5 + 0.5 * Math.Sin(t * 8.0);
                _discreetPulseDot.Opacity = 0.45 + 0.55 * pulse;
            }

            _oneTransferCanvas.Children.Clear();
            double w = _oneTransferCanvas.ActualWidth > 100 ? _oneTransferCanvas.ActualWidth : 800;
            double h = _oneTransferCanvas.ActualHeight > 100 ? _oneTransferCanvas.ActualHeight : 600;

            double migProg = Math.Max(0.02, Math.Min(1.0, _auraPathState.IsActive ? _auraPathState.MigrationProgress : _transferProgress));
            string flowDir = string.IsNullOrEmpty(_auraPathState.FlowDirection) ? "TOP" : _auraPathState.FlowDirection.ToUpperInvariant();

            // A. DIRECTIONAL APPLE WWDC25 LIQUID GLASS WAVEFRONT (LEFT, RIGHT, or TOP)
            double locateAnchorX = flowDir == "LEFT" ? 0.0 : (flowDir == "RIGHT" ? w : w * 0.5);
            double locateAnchorY = (flowDir == "LEFT" || flowDir == "RIGHT") ? h * 0.46 : 0.0;
            double locateMaxR = Math.Min(w, h) * 0.46;
            double normCycle = (t * 0.55) % 1.0;
            for (int rIdx = 0; rIdx < 3; rIdx++)
            {
                double rProg = (normCycle + rIdx * 0.33) % 1.0;
                double rad = locateMaxR * (0.16 + 0.84 * rProg);
                double fade = (1.0 - rProg) * (1.0 - rProg * 0.4);
                byte fillA = (byte)(68 * fade);
                byte rimA = (byte)(215 * fade);
                var locateDisc = new Ellipse
                {
                    Width = rad * 2,
                    Height = rad * 2,
                    Fill = new SolidColorBrush(Color.FromArgb(fillA, 0, 245, 212)),
                    Stroke = new SolidColorBrush(Color.FromArgb(rimA, 255, 255, 255)),
                    StrokeThickness = 2.2 - rIdx * 0.4
                };
                Canvas.SetLeft(locateDisc, locateAnchorX - rad);
                Canvas.SetTop(locateDisc, locateAnchorY - rad);
                _oneTransferCanvas.Children.Add(locateDisc);
            }

            if (flowDir == "LEFT" || flowDir == "RIGHT")
            {
                // Multi-layered Apple Liquid Glass Fluid Wave along vertical edge
                bool isLeft = (flowDir == "LEFT");
                double edgeX = isLeft ? 0.0 : w;
                double dirSign = isLeft ? 1.0 : -1.0;
                double baseReach = w * (0.14 + 0.12 * migProg);
                double phase = t * Math.PI * 2.4;

                var sideGlowBrush = new LinearGradientBrush(
                    Color.FromArgb(55, 0, 245, 212),
                    Color.FromArgb(0, 157, 78, 221),
                    isLeft ? 0.0 : 180.0);
                var sideGlowRect = new System.Windows.Shapes.Rectangle
                {
                    Width = baseReach * 1.9,
                    Height = h,
                    Fill = sideGlowBrush
                };
                Canvas.SetLeft(sideGlowRect, isLeft ? 0 : w - baseReach * 1.9);
                Canvas.SetTop(sideGlowRect, 0);
                _oneTransferCanvas.Children.Add(sideGlowRect);

                for (int layerIdx = 0; layerIdx < 3; layerIdx++)
                {
                    double layerPhase = phase + layerIdx * 1.15;
                    double layerScale = 1.0 - layerIdx * 0.22;
                    double amp = w * 0.022 * (1.0 - layerIdx * 0.18);

                    var fig = new PathFigure { StartPoint = new Point(edgeX, 0), IsClosed = true };
                    int steps = 28;
                    for (int i = 0; i <= steps; i++)
                    {
                        double frac = (double)i / steps;
                        double y = h * frac;
                        double envelope = Math.Sin(frac * Math.PI);
                        double wave = Math.Sin(frac * Math.PI * 2.5 + layerPhase) * amp
                                    + Math.Cos(frac * Math.PI * 1.5 - layerPhase * 0.7) * (amp * 0.55);
                        double x = edgeX + dirSign * (baseReach * layerScale * (0.35 + 0.65 * envelope) + wave * envelope);
                        fig.Segments.Add(new LineSegment(new Point(x, y), true));
                    }
                    fig.Segments.Add(new LineSegment(new Point(edgeX, h), true));

                    var geo = new PathGeometry();
                    geo.Figures.Add(fig);

                    byte fillAlphaTop = (byte)(56 - layerIdx * 12);
                    byte fillAlphaMid = (byte)(40 - layerIdx * 8);
                    var waveFill = new LinearGradientBrush(
                        Color.FromArgb(fillAlphaTop, 255, 255, 255),
                        Color.FromArgb(fillAlphaMid, 0, 245, 212),
                        isLeft ? 0.0 : 180.0);

                    byte rimAlpha = (byte)(185 - layerIdx * 45);
                    var wavePath = new SystemPath
                    {
                        Data = geo,
                        Fill = waveFill,
                        Stroke = new LinearGradientBrush(
                            Color.FromArgb(rimAlpha, 255, 255, 255),
                            Color.FromArgb((byte)(130 - layerIdx * 30), 157, 78, 221),
                            90.0),
                        StrokeThickness = (2.0 - layerIdx * 0.4)
                    };
                    _oneTransferCanvas.Children.Add(wavePath);
                }
            }
            else
            {
                // Multi-layered Apple Liquid Glass Fluid Wave along TOP edge
                double baseDepth = h * (0.08 + 0.08 * migProg);
                double phase = t * Math.PI * 2.0;

                var topGlowBrush = new LinearGradientBrush(
                    Color.FromArgb(55, 0, 245, 212),
                    Color.FromArgb(0, 157, 78, 221),
                    90.0);
                var topGlowRect = new System.Windows.Shapes.Rectangle
                {
                    Width = w,
                    Height = baseDepth * 1.9,
                    Fill = topGlowBrush
                };
                Canvas.SetLeft(topGlowRect, 0);
                Canvas.SetTop(topGlowRect, 0);
                _oneTransferCanvas.Children.Add(topGlowRect);

                for (int layerIdx = 0; layerIdx < 3; layerIdx++)
                {
                    double layerPhase = phase + layerIdx * 1.15;
                    double layerScale = 1.0 - layerIdx * 0.22;
                    double amp = h * 0.018 * (1.0 - layerIdx * 0.18);

                    var fig = new PathFigure { StartPoint = new Point(0, 0), IsClosed = true };
                    int steps = 28;
                    for (int i = 0; i <= steps; i++)
                    {
                        double frac = (double)i / steps;
                        double x = w * frac;
                        double envelope = Math.Sin(frac * Math.PI);
                        double wave = Math.Sin(frac * Math.PI * 2.5 + layerPhase) * amp
                                    + Math.Cos(frac * Math.PI * 1.5 - layerPhase * 0.7) * (amp * 0.55);
                        double y = baseDepth * layerScale * (0.35 + 0.65 * envelope) + wave * envelope;
                        fig.Segments.Add(new LineSegment(new Point(x, Math.Max(2.0, y)), true));
                    }
                    fig.Segments.Add(new LineSegment(new Point(w, 0), true));

                    var geo = new PathGeometry();
                    geo.Figures.Add(fig);

                    byte fillAlphaTop = (byte)(56 - layerIdx * 12);
                    byte fillAlphaMid = (byte)(40 - layerIdx * 8);
                    var waveFill = new LinearGradientBrush(
                        Color.FromArgb(fillAlphaTop, 255, 255, 255),
                        Color.FromArgb(fillAlphaMid, 0, 245, 212),
                        90.0);

                    byte rimAlpha = (byte)(185 - layerIdx * 45);
                    var wavePath = new SystemPath
                    {
                        Data = geo,
                        Fill = waveFill,
                        Stroke = new LinearGradientBrush(
                            Color.FromArgb(rimAlpha, 255, 255, 255),
                            Color.FromArgb((byte)(130 - layerIdx * 30), 157, 78, 221),
                            0.0),
                        StrokeThickness = (2.0 - layerIdx * 0.4)
                    };
                    _oneTransferCanvas.Children.Add(wavePath);
                }
            }

            // B. THE SHARED CROSS-PLATFORM CUBIC BEZIER CONDUIT
            Point p0 = (_auraPathState.SourceButtonPoint.X > 10 && _auraPathState.SourceButtonPoint.Y > 10)
                ? _auraPathState.SourceButtonPoint
                : (_auraPathState.IsOutgoing ? new Point(w * 0.65, h * 0.55) : new Point(locateAnchorX, locateAnchorY));

            Point p3 = (_auraPathState.TargetDevicePoint.X > 10 && _auraPathState.TargetDevicePoint.Y > 10)
                ? _auraPathState.TargetDevicePoint
                : (_auraPathState.IsOutgoing ? new Point(locateAnchorX, locateAnchorY) : new Point(w - 180, 110));

            double dy = p3.Y - p0.Y;
            double swing = Math.Sin(t * 2.8) * (w * 0.08);
            Point p1 = new Point(Math.Max(30, Math.Min(w - 30, p0.X + (p3.X - p0.X) * 0.28 - 70 + swing * 0.3)), p0.Y + dy * 0.30);
            Point p2 = new Point(Math.Max(30, Math.Min(w - 30, p0.X + (p3.X - p0.X) * 0.72 + 70 - swing * 0.3)), p0.Y + dy * 0.70);

            // Full Bezier Conduit Glow
            var fullFig = new PathFigure { StartPoint = p0, IsClosed = false };
            fullFig.Segments.Add(new BezierSegment(p1, p2, p3, true));
            var fullGeo = new PathGeometry();
            fullGeo.Figures.Add(fullFig);

            var outerGlowPath = new SystemPath
            {
                Data = fullGeo,
                Stroke = new LinearGradientBrush(
                    Color.FromArgb(70, 0, 245, 212),
                    Color.FromArgb(75, 157, 78, 221),
                    45.0),
                StrokeThickness = 22,
                StrokeStartLineCap = PenLineCap.Round,
                StrokeEndLineCap = PenLineCap.Round
            };
            _oneTransferCanvas.Children.Add(outerGlowPath);

            // Active Illuminated Migration Conduit
            var activeFig = new PathFigure { StartPoint = p0, IsClosed = false };
            var polySeg = new PolyLineSegment();
            int stepsConduit = 44;
            int maxStep = Math.Max(1, (int)(stepsConduit * migProg));
            for (int s = 1; s <= maxStep; s++)
            {
                double u = Math.Min(migProg, (double)s / stepsConduit);
                polySeg.Points.Add(EvaluateCubicBezier(p0, p1, p2, p3, u));
            }
            activeFig.Segments.Add(polySeg);
            var activeGeo = new PathGeometry();
            activeGeo.Figures.Add(activeFig);

            var activeStrokePath = new SystemPath
            {
                Data = activeGeo,
                Stroke = new LinearGradientBrush(ElectricCyan, RadiantPurple, 0.0),
                StrokeThickness = 6.0,
                StrokeStartLineCap = PenLineCap.Round,
                StrokeEndLineCap = PenLineCap.Round
            };
            _oneTransferCanvas.Children.Add(activeStrokePath);

            // 24 Luminescent Cometary Micro-Filaments trailing behind payload
            for (int p = 0; p < 24; p++)
            {
                double trailOffset = (p * 0.035);
                double trailProg = Math.Max(0.0, migProg - trailOffset);
                if (trailProg > 0.01)
                {
                    Point trailPt = EvaluateCubicBezier(p0, p1, p2, p3, trailProg);
                    double wobble = Math.Sin(t * 8.0 + p) * (14.0 * (1.0 - (double)p / 24.0));
                    byte trailAlpha = (byte)((1.0 - (double)p / 24.0) * 180);
                    double pSize = 3.5 + (p % 3) * 1.8;

                    var particle = new Ellipse
                    {
                        Width = pSize,
                        Height = pSize,
                        Fill = new SolidColorBrush(p % 2 == 0
                            ? Color.FromArgb(trailAlpha, 0, 245, 212)
                            : Color.FromArgb(trailAlpha, 157, 78, 221))
                    };
                    Canvas.SetLeft(particle, trailPt.X + wobble - pSize * 0.5);
                    Canvas.SetTop(particle, trailPt.Y + wobble * 0.5 - pSize * 0.5);
                    _oneTransferCanvas.Children.Add(particle);
                }
            }

            // C. THE MIGRATING LIQUID FROSTED GLASS FILE CARD
            Point payloadPt = EvaluateCubicBezier(p0, p1, p2, p3, migProg);
            string fileName = !string.IsNullOrEmpty(_auraPathState.FileName)
                ? _auraPathState.FileName
                : (!string.IsNullOrEmpty(_transferFileText.Text) ? _transferFileText.Text : "Incoming_File.bin");

            double cardW = 126;
            double cardH = 76;
            double cardX = payloadPt.X - cardW * 0.5;
            double cardY = payloadPt.Y - cardH * 0.5;

            // Outer Glass Halo Glow
            double haloR = 48 + Math.Sin(t * 7.0) * 6;
            var cardGlow = new Ellipse
            {
                Width = haloR * 2.4,
                Height = haloR * 2.0,
                Fill = new RadialGradientBrush(
                    Color.FromArgb(195, 0, 245, 212),
                    Color.FromArgb(0, 157, 78, 221))
            };
            Canvas.SetLeft(cardGlow, payloadPt.X - haloR * 1.2);
            Canvas.SetTop(cardGlow, payloadPt.Y - haloR);
            _oneTransferCanvas.Children.Add(cardGlow);

            // Frosted Glass Card Container
            var glassCard = new Border
            {
                Width = cardW,
                Height = cardH,
                CornerRadius = new CornerRadius(14),
                Background = new SolidColorBrush(Color.FromArgb(220, 18, 32, 28)),
                BorderBrush = new LinearGradientBrush(
                    Color.FromArgb(240, 255, 255, 255),
                    Color.FromArgb(180, 0, 245, 212),
                    45.0),
                BorderThickness = new Thickness(1.4),
                Padding = new Thickness(10, 8, 10, 8),
                Effect = new DropShadowEffect
                {
                    Color = ElectricCyan,
                    BlurRadius = 24,
                    ShadowDepth = 3,
                    Opacity = 0.55
                }
            };

            var cardStack = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            var cardHeader = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Center };

            cardHeader.Children.Add(new TextBlock
            {
                Text = GetFileCategoryEmoji(fileName),
                FontSize = 18,
                Margin = new Thickness(0, 0, 6, 0),
                VerticalAlignment = VerticalAlignment.Center
            });

            cardHeader.Children.Add(new TextBlock
            {
                Text = string.Format("{0:F0}%", migProg * 100.0),
                FontSize = 11.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(ElectricCyan),
                VerticalAlignment = VerticalAlignment.Center
            });
            cardStack.Children.Add(cardHeader);

            cardStack.Children.Add(new TextBlock
            {
                Text = fileName,
                FontSize = 10.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Colors.White),
                TextAlignment = TextAlignment.Center,
                TextTrimming = TextTrimming.CharacterEllipsis,
                Margin = new Thickness(0, 4, 0, 4)
            });

            // Mini Glass Progress Bar inside the card
            var miniTrack = new Border
            {
                Height = 3.5,
                CornerRadius = new CornerRadius(1.75),
                Background = new SolidColorBrush(Color.FromArgb(80, 255, 255, 255)),
                ClipToBounds = true
            };
            miniTrack.Child = new Border
            {
                Height = 3.5,
                CornerRadius = new CornerRadius(1.75),
                Width = Math.Max(4, (cardW - 20) * migProg),
                HorizontalAlignment = HorizontalAlignment.Left,
                Background = new LinearGradientBrush(ElectricCyan, SolarAmber, 0.0)
            };
            cardStack.Children.Add(miniTrack);

            glassCard.Child = cardStack;
            Canvas.SetLeft(glassCard, cardX);
            Canvas.SetTop(glassCard, cardY);
            _oneTransferCanvas.Children.Add(glassCard);

            // D. ARRIVAL SHOCKWAVE AT DESTINATION (progress > 0.85)
            if (migProg > 0.85)
            {
                double arrPhase = (migProg - 0.85) / 0.15;
                for (int aw = 0; aw < 2; aw++)
                {
                    double awRad = 20 + (arrPhase + aw * 0.4) * 55;
                    byte awAlpha = (byte)((1.0 - Math.Min(1.0, arrPhase)) * 175);
                    var arrRing = new Ellipse
                    {
                        Width = awRad * 2,
                        Height = awRad * 2,
                        Stroke = new SolidColorBrush(aw == 0 ? Color.FromArgb(awAlpha, 255, 183, 3) : Color.FromArgb(awAlpha, 0, 245, 212)),
                        StrokeThickness = 2.0
                    };
                    Canvas.SetLeft(arrRing, p3.X - awRad);
                    Canvas.SetTop(arrRing, p3.Y - awRad);
                    _oneTransferCanvas.Children.Add(arrRing);
                }
            }
        }
        private static string GetFileCategoryEmoji(string fileName)
        {
            if (string.IsNullOrEmpty(fileName)) return "📄";
            string ext = System.IO.Path.GetExtension(fileName).ToLowerInvariant();
            if (ext == ".jpg" || ext == ".jpeg" || ext == ".png" || ext == ".webp" || ext == ".gif") return "🖼";
            if (ext == ".mp4" || ext == ".mkv" || ext == ".mov" || ext == ".avi") return "🎬";
            if (ext == ".mp3" || ext == ".wav" || ext == ".flac" || ext == ".m4a") return "🎵";
            if (ext == ".zip" || ext == ".rar" || ext == ".7z" || ext == ".apk" || ext == ".exe") return "📦";
            return "📄";
        }

        private static Point EvaluateCubicBezier(Point p0, Point p1, Point p2, Point p3, double t)
        {
            double u = 1.0 - t;
            double tt = t * t;
            double uu = u * u;
            double uuu = uu * u;
            double ttt = tt * t;
            double x = uuu * p0.X + 3.0 * uu * t * p1.X + 3.0 * u * tt * p2.X + ttt * p3.X;
            double y = uuu * p0.Y + 3.0 * uu * t * p1.Y + 3.0 * u * tt * p2.Y + ttt * p3.Y;
            return new Point(x, y);
        }

        private SystemPath CreateArcSegment(double cx, double cy, double r, double startDeg, double sweepDeg, double thickness, Color color)
        {
            double startRad = startDeg * Math.PI / 180.0;
            double endRad = (startDeg + sweepDeg) * Math.PI / 180.0;

            var startPt = new Point(cx + r * Math.Cos(startRad), cy + r * Math.Sin(startRad));
            var endPt = new Point(cx + r * Math.Cos(endRad), cy + r * Math.Sin(endRad));

            var fig = new PathFigure { StartPoint = startPt, IsClosed = false };
            fig.Segments.Add(new ArcSegment
            {
                Point = endPt,
                Size = new Size(r, r),
                IsLargeArc = sweepDeg > 180.0,
                SweepDirection = SweepDirection.Clockwise
            });

            var geo = new PathGeometry();
            geo.Figures.Add(fig);

            return new SystemPath
            {
                Data = geo,
                Stroke = new SolidColorBrush(color),
                StrokeThickness = thickness,
                StrokeStartLineCap = PenLineCap.Round,
                StrokeEndLineCap = PenLineCap.Round
            };
        }

        private Border CreateSelectionTile(string icon, string label, Action onClick)
        {
            var card = new Border
            {
                Background = new SolidColorBrush(CardSurface),
                BorderBrush = new SolidColorBrush(CardBorder),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(12),
                Height = 84,
                Margin = new Thickness(5, 0, 5, 0),
                Cursor = Cursors.Hand
            };

            var stack = new StackPanel
            {
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            stack.Children.Add(new TextBlock
            {
                Text = icon,
                FontSize = 22,
                Foreground = new SolidColorBrush(TextPrimary),
                HorizontalAlignment = HorizontalAlignment.Center
            });
            stack.Children.Add(new TextBlock
            {
                Text = label,
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(TextPrimary),
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 6, 0, 0)
            });

            card.Child = stack;
            card.MouseLeftButtonUp += (s, e) => onClick();
            return card;
        }

        private void RefreshStagedSelectionUi()
        {
            Dispatcher.BeginInvoke(new Action(() =>
            {
                if (_stagedFiles.Count == 0)
                {
                    _stagedSelectionCard.Visibility = Visibility.Collapsed;
                    _stagedListPanel.Children.Clear();
                    return;
                }

                long totalBytes = 0;
                foreach (var f in _stagedFiles)
                {
                    if (File.Exists(f)) totalBytes += new FileInfo(f).Length;
                }

                _stagedSelectionCard.Visibility = Visibility.Visible;
                _stagedSummaryText.Text = string.Format("Selected ({0}) • {1}", _stagedFiles.Count, FormatFileSize(totalBytes));
                _stagedListPanel.Children.Clear();

                foreach (var path in _stagedFiles.Take(5))
                {
                    var itemRow = new TextBlock
                    {
                        Text = "• " + Path.GetFileName(path),
                        FontSize = 12,
                        Foreground = new SolidColorBrush(TextPrimary),
                        Margin = new Thickness(0, 2, 0, 2)
                    };
                    _stagedListPanel.Children.Add(itemRow);
                }
            }));
        }

        private void ClearStagedSelection()
        {
            _stagedFiles.Clear();
            RefreshStagedSelectionUi();
        }

        private void RefreshPeersListUi()
        {
            Dispatcher.BeginInvoke(new Action(() =>
            {
                if (_peersListPanel == null) return;
                _peersListPanel.Children.Clear();
                _sendButtonAuraCanvases.Clear();

                var peers = _peers.Values.OrderByDescending(p => p.IsFavorite).ThenBy(p => p.Name).ToList();
                if (peers.Count == 0)
                {
                    var emptyCard = new Border
                    {
                        Background = new SolidColorBrush(CardSurface),
                        BorderBrush = new SolidColorBrush(CardBorder),
                        BorderThickness = new Thickness(1),
                        CornerRadius = new CornerRadius(14),
                        Padding = new Thickness(20, 22, 20, 22)
                    };
                    var emptyStack = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center };
                    emptyStack.Children.Add(new TextBlock
                    {
                        Text = "Scanning local Wi-Fi for Android & Windows devices...",
                        FontSize = 14,
                        FontWeight = FontWeights.SemiBold,
                        Foreground = new SolidColorBrush(TextPrimary),
                        HorizontalAlignment = HorizontalAlignment.Center
                    });
                    emptyStack.Children.Add(new TextBlock
                    {
                        Text = "Listening on TCP 8988 & UDP 8989 • Click ↻ Scan or ⊕ Manual IP above",
                        FontSize = 12,
                        Foreground = new SolidColorBrush(TextSecondary),
                        HorizontalAlignment = HorizontalAlignment.Center,
                        Margin = new Thickness(0, 4, 0, 0)
                    });
                    emptyCard.Child = emptyStack;
                    _peersListPanel.Children.Add(emptyCard);
                    return;
                }

                foreach (var peer in peers)
                {
                    var peerCard = new Border
                    {
                        Background = new SolidColorBrush(CardSurface),
                        BorderBrush = new SolidColorBrush(Color.FromArgb(140, 94, 212, 187)),
                        BorderThickness = new Thickness(1.2),
                        CornerRadius = new CornerRadius(16),
                        Padding = new Thickness(18, 16, 18, 16),
                        Margin = new Thickness(0, 0, 0, 12),
                        Cursor = Cursors.Hand
                    };

                    var dock = new DockPanel();

                    // Device Icon
                    var iconText = new TextBlock
                    {
                        Text = peer.BrandTag == "Windows" ? "💻" : "📱",
                        FontSize = 26,
                        VerticalAlignment = VerticalAlignment.Center,
                        Margin = new Thickness(0, 0, 16, 0)
                    };
                    DockPanel.SetDock(iconText, Dock.Left);
                    dock.Children.Add(iconText);

                    // Right Controls: Favorite Heart + Glowing Fluid Aura Ripple Send Button
                    var rightControls = new StackPanel
                    {
                        Orientation = Orientation.Horizontal,
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    DockPanel.SetDock(rightControls, Dock.Right);

                    var heartText = new TextBlock
                    {
                        Text = peer.IsFavorite ? "♥" : "♡",
                        FontSize = 20,
                        Foreground = new SolidColorBrush(peer.IsFavorite ? MintAccent : TextSecondary),
                        VerticalAlignment = VerticalAlignment.Center,
                        Cursor = Cursors.Hand,
                        Margin = new Thickness(8, 0, 12, 0)
                    };
                    heartText.MouseLeftButtonUp += (s, e) =>
                    {
                        e.Handled = true;
                        peer.IsFavorite = !peer.IsFavorite;
                        RefreshPeersListUi();
                    };
                    rightControls.Children.Add(heartText);

                    // File-Transfer Send Button wrapped in a 60fps Fluid Aura Ripple Canvas
                    var sendHostGrid = new Grid
                    {
                        Width = 108,
                        Height = 46,
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    var auraCanvas = new Canvas
                    {
                        Width = 108,
                        Height = 46,
                        IsHitTestVisible = false
                    };
                    _sendButtonAuraCanvases.Add(auraCanvas);
                    sendHostGrid.Children.Add(auraCanvas);

                    var sendBtnBorder = new Border
                    {
                        Width = 88,
                        Height = 36,
                        CornerRadius = new CornerRadius(12),
                        Background = new LinearGradientBrush(MintAccent, ElectricCyan, 0.0),
                        HorizontalAlignment = HorizontalAlignment.Center,
                        VerticalAlignment = VerticalAlignment.Center,
                        Effect = new DropShadowEffect { Color = ElectricCyan, BlurRadius = 14, ShadowDepth = 0, Opacity = 0.45 }
                    };
                    sendBtnBorder.Child = new TextBlock
                    {
                        Text = "➤ Send",
                        FontSize = 12.5,
                        FontWeight = FontWeights.Bold,
                        Foreground = new SolidColorBrush(BgDark),
                        HorizontalAlignment = HorizontalAlignment.Center,
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    sendHostGrid.Children.Add(sendBtnBorder);
                    rightControls.Children.Add(sendHostGrid);

                    dock.Children.Add(rightControls);

                    // Center Name + [#Octet] [Brand] pills
                    var infoStack = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
                    infoStack.Children.Add(new TextBlock
                    {
                        Text = peer.Name,
                        FontSize = 17,
                        FontWeight = FontWeights.SemiBold,
                        Foreground = new SolidColorBrush(TextPrimary)
                    });

                    var badgesRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 6, 0, 0) };
                    badgesRow.Children.Add(CreateTagPill(peer.OctetTag));
                    badgesRow.Children.Add(CreateTagPill(peer.BrandTag));
                    badgesRow.Children.Add(CreateTagPill(peer.Ip));
                    infoStack.Children.Add(badgesRow);

                    dock.Children.Add(infoStack);
                    peerCard.Child = dock;

                    var targetPeer = peer;
                    peerCard.MouseLeftButtonUp += (s, e) =>
                    {
                        try
                        {
                            Point btnPt = sendHostGrid.TranslatePoint(new Point(sendHostGrid.Width * 0.5, sendHostGrid.Height * 0.5), _discreetTransferLayer);
                            Point cardPt = peerCard.TranslatePoint(new Point(peerCard.ActualWidth * 0.3, peerCard.ActualHeight * 0.5), _discreetTransferLayer);
                            _auraPathState.SourceButtonPoint = btnPt;
                            _auraPathState.TargetDevicePoint = new Point(cardPt.X, Math.Max(48, cardPt.Y - 140));
                        }
                        catch { }
                        OnPeerCardClicked(targetPeer);
                    };
                    _peersListPanel.Children.Add(peerCard);
                }
            }));
        }

        private Border CreateTagPill(string text)
        {
            return new Border
            {
                CornerRadius = new CornerRadius(5),
                Background = new SolidColorBrush(Color.FromRgb(17, 34, 30)),
                BorderBrush = new SolidColorBrush(Color.FromRgb(56, 92, 82)),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(8, 2, 8, 2),
                Margin = new Thickness(0, 0, 6, 0),
                Child = new TextBlock
                {
                    Text = text,
                    FontSize = 11,
                    Foreground = new SolidColorBrush(TextSecondary)
                }
            };
        }

        private async void OnPeerCardClicked(PeerNode peer)
        {
            if (_stagedFiles.Count == 0)
            {
                OnPickFilesClicked();
                if (_stagedFiles.Count == 0) return;
            }

            var filesToSend = _stagedFiles.Where(File.Exists).ToList();
            if (filesToSend.Count == 0) return;

            await ExecuteSendFilesAsync(peer, filesToSend);
        }

        private async Task ExecuteSendFilesAsync(PeerNode peer, List<string> files)
        {
            _activeTransferCts = new CancellationTokenSource();
            var token = _activeTransferCts.Token;

            long totalBytes = files.Sum(f => new FileInfo(f).Length);
            long sentBytes = 0;
            var sw = Stopwatch.StartNew();
            string sessionId = "aura_win_" + (DateTime.Now.Ticks % 1000000);

            _auraPathState.IsActive = true;
            _auraPathState.SessionId = sessionId;
            _auraPathState.SourceDeviceName = _deviceName;
            _auraPathState.TargetDeviceName = peer.Name;
            _auraPathState.FileName = Path.GetFileName(files[0]);
            _auraPathState.MigrationProgress = 0.03;
            _auraPathState.SpeedMbps = 0.0;
            _auraPathState.IsOutgoing = true;
            BroadcastAuraPathSync(peer.Ip, "START");

            ShowTransferOverlay("Beaming to " + peer.Name, Path.GetFileName(files[0]), 0.0, 0.0);

            try
            {
                await Task.Run(async () =>
                {
                    using (var client = new TcpClient())
                    {
                        client.NoDelay = true;
                        client.SendBufferSize = 4 * 1024 * 1024;
                        client.ReceiveBufferSize = 4 * 1024 * 1024;
                        await client.ConnectAsync(peer.Ip, peer.Port);
                        using (var stream = client.GetStream())
                        {
                            var header = string.Format("BEAMDROP_V1|{0}|{1}|{2}|{3}\n", _deviceName, files.Count, totalBytes, _auraPathState.FlowDirection ?? "TOP");
                            var headerBytes = Encoding.UTF8.GetBytes(header);
                            await stream.WriteAsync(headerBytes, 0, headerBytes.Length, token);

                            int chunkSize = (_settings != null && _settings.TurboSpeedMode) ? (1024 * 1024) : 65536;
                            byte[] buffer = new byte[chunkSize];
                            for (int i = 0; i < files.Count; i++)
                            {
                                var fi = new FileInfo(files[i]);
                                var fileHeader = string.Format("FILE|{0}|{1}|application/octet-stream\n", fi.Name, fi.Length);
                                var fhBytes = Encoding.UTF8.GetBytes(fileHeader);
                                await stream.WriteAsync(fhBytes, 0, fhBytes.Length, token);

                                using (var fs = new FileStream(fi.FullName, FileMode.Open, FileAccess.Read, FileShare.Read, chunkSize, FileOptions.SequentialScan | FileOptions.Asynchronous))
                                {
                                    int read;
                                    while ((read = await fs.ReadAsync(buffer, 0, buffer.Length, token)) > 0)
                                    {
                                        await stream.WriteAsync(buffer, 0, read, token);
                                        sentBytes += read;
                                        double elapsedSec = Math.Max(0.05, sw.Elapsed.TotalSeconds);
                                        double mbps = (sentBytes / (1024.0 * 1024.0)) / elapsedSec;
                                        double prog = totalBytes > 0 ? (double)sentBytes / totalBytes : 1.0;
                                        _auraPathState.FileName = fi.Name;
                                        _auraPathState.MigrationProgress = prog;
                                        _auraPathState.SpeedMbps = mbps;
                                        BroadcastAuraPathSync(peer.Ip, "MIGRATING");
                                        UpdateTransferOverlay(fi.Name, prog, mbps);
                                    }
                                }
                            }
                            await stream.FlushAsync(token);
                        }
                    }
                }, token);

                double finalSpeed = (totalBytes / (1024.0 * 1024.0)) / Math.Max(0.05, sw.Elapsed.TotalSeconds);
                foreach (var f in files)
                {
                    _history.Insert(0, new TransferLogEntry
                    {
                        FileName = Path.GetFileName(f),
                        Direction = "SENT",
                        PeerName = peer.Name,
                        SizeBytes = new FileInfo(f).Length,
                        SpeedMbps = finalSpeed,
                        Status = "COMPLETED",
                        SavedPath = f,
                        Timestamp = DateTime.Now.ToString("HH:mm:ss")
                    });
                }

                _auraPathState.MigrationProgress = 1.0;
                _auraPathState.IsActive = false;
                BroadcastAuraPathSync(peer.Ip, "COMPLETED");

                // Immediately clear staged files AND immediately self-dismiss the discreet progress indicator
                ClearStagedSelection();
                HideTransferOverlay();

                string firstFile = files.FirstOrDefault();
                string displayTitle = files.Count > 1 ? string.Format("{0} Files Sent!", files.Count) : "File Sent!";
                string displayName = files.Count > 1 ? string.Format("{0} files", files.Count) : Path.GetFileName(firstFile);
                ShowCompletionToast(displayTitle, string.Format("Delivered to {0}", peer.Name), displayName, totalBytes, firstFile);
                PlayCompletionChime();
            }
            catch (Exception ex)
            {
                _auraPathState.IsActive = false;
                BroadcastAuraPathSync(peer.Ip, "CANCELLED");
                HideTransferOverlay();
                if (!(ex is OperationCanceledException))
                {
                    MessageBox.Show(this, "Transfer failed: " + ex.Message, "BeamDrop Error", MessageBoxButton.OK, MessageBoxImage.Warning);
                }
            }
        }

        private void ShowTransferOverlay(string title, string fileName, double progress, double mbps)
        {
            Dispatcher.BeginInvoke(new Action(() =>
            {
                _isTransferring = true;
                _transferTitleText.Text = title;
                _transferFileText.Text = fileName;
                _discreetTransferLayer.Visibility = Visibility.Visible;
                UpdateTransferOverlay(fileName, progress, mbps);
            }));
        }

        private void UpdateTransferOverlay(string fileName, double progress, double mbps)
        {
            Dispatcher.BeginInvoke(new Action(() =>
            {
                _transferProgress = Math.Max(0.0, Math.Min(1.0, progress));
                _transferSpeedMbps = mbps;
                _auraPathState.MigrationProgress = _transferProgress;
                _auraPathState.SpeedMbps = mbps;
                _transferFileText.Text = fileName;
                _transferProgressFill.Width = 400 * _transferProgress;
                _transferPercentBadge.Text = string.Format("{0:F0}%", _transferProgress * 100.0);
                _transferMetricsText.Text = string.Format("{0:F1} MB/s", mbps);
            }));
        }

        private void HideTransferOverlay()
        {
            Dispatcher.BeginInvoke(new Action(() =>
            {
                _isTransferring = false;
                _transferProgress = 0.0;
                _auraPathState.IsActive = false;
                _auraPathState.MigrationProgress = 0.0;
                if (_oneTransferCanvas != null) _oneTransferCanvas.Children.Clear();
                _discreetTransferLayer.Visibility = Visibility.Collapsed;
            }));
        }

        private void OnPickFilesClicked()
        {
            var dlg = new OpenFileDialog
            {
                Multiselect = true,
                Title = "Select files to send via BeamDrop"
            };
            if (dlg.ShowDialog(this) == true)
            {
                foreach (var f in dlg.FileNames)
                {
                    if (!_stagedFiles.Contains(f)) _stagedFiles.Add(f);
                }
                RefreshStagedSelectionUi();
            }
        }

        private void OnPickFolderClicked()
        {
            var dlg = new OpenFileDialog
            {
                Multiselect = true,
                Title = "Select files inside folder to send"
            };
            if (dlg.ShowDialog(this) == true)
            {
                foreach (var f in dlg.FileNames)
                {
                    if (!_stagedFiles.Contains(f)) _stagedFiles.Add(f);
                }
                RefreshStagedSelectionUi();
            }
        }

        private void OnSendTextClicked()
        {
            var prompt = new Window
            {
                Title = "Send Text Snippet",
                Width = 420,
                Height = 240,
                WindowStartupLocation = WindowStartupLocation.CenterOwner,
                Owner = this,
                Background = new SolidColorBrush(BgDark),
                Foreground = new SolidColorBrush(TextPrimary),
                ResizeMode = ResizeMode.NoResize
            };
            var sp = new StackPanel { Margin = new Thickness(18) };
            sp.Children.Add(new TextBlock { Text = "Enter text to beam:", FontSize = 14, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 0, 0, 8) });
            var box = new TextBox
            {
                Height = 96,
                AcceptsReturn = true,
                TextWrapping = TextWrapping.Wrap,
                Background = new SolidColorBrush(CardSurface),
                Foreground = new SolidColorBrush(TextPrimary),
                BorderBrush = new SolidColorBrush(CardBorder),
                Padding = new Thickness(8)
            };
            sp.Children.Add(box);
            var sendBtn = new Button
            {
                Content = "Stage Text",
                Margin = new Thickness(0, 12, 0, 0),
                Height = 34,
                Background = new SolidColorBrush(MintAccent),
                Foreground = new SolidColorBrush(BgDark),
                FontWeight = FontWeights.Bold
            };
            sendBtn.Click += (s, e) =>
            {
                if (!string.IsNullOrWhiteSpace(box.Text))
                {
                    var tmp = Path.Combine(Path.GetTempPath(), "BeamText_" + (DateTime.Now.Ticks % 100000) + ".txt");
                    File.WriteAllText(tmp, box.Text);
                    _stagedFiles.Add(tmp);
                    RefreshStagedSelectionUi();
                }
                prompt.Close();
            };
            sp.Children.Add(sendBtn);
            prompt.Content = sp;
            prompt.ShowDialog();
        }

        private void OnPasteClipboardClicked()
        {
            if (Clipboard.ContainsFileDropList())
            {
                foreach (string f in Clipboard.GetFileDropList())
                {
                    if (File.Exists(f) && !_stagedFiles.Contains(f)) _stagedFiles.Add(f);
                }
                RefreshStagedSelectionUi();
            }
            else if (Clipboard.ContainsText())
            {
                var txt = Clipboard.GetText();
                if (!string.IsNullOrWhiteSpace(txt))
                {
                    var tmp = Path.Combine(Path.GetTempPath(), "Clipboard_" + (DateTime.Now.Ticks % 100000) + ".txt");
                    File.WriteAllText(tmp, txt);
                    _stagedFiles.Add(tmp);
                    RefreshStagedSelectionUi();
                }
            }
        }

        private void OnWindowFileDrop(object sender, DragEventArgs e)
        {
            if (e.Data.GetDataPresent(DataFormats.FileDrop))
            {
                var files = (string[])e.Data.GetData(DataFormats.FileDrop);
                foreach (var f in files)
                {
                    if (File.Exists(f) && !_stagedFiles.Contains(f)) _stagedFiles.Add(f);
                }
                SwitchTab(1);
                RefreshStagedSelectionUi();
            }
        }

        private void PromptManualIpConnect()
        {
            var prompt = new Window
            {
                Title = "Connect to Peer IP",
                Width = 380,
                Height = 185,
                WindowStartupLocation = WindowStartupLocation.CenterOwner,
                Owner = this,
                Background = new SolidColorBrush(BgDark),
                Foreground = new SolidColorBrush(TextPrimary),
                ResizeMode = ResizeMode.NoResize
            };
            var sp = new StackPanel { Margin = new Thickness(18) };
            sp.Children.Add(new TextBlock { Text = "Enter Android or Windows IPv4 address (or #Octet):", FontSize = 13, Margin = new Thickness(0, 0, 0, 8) });
            var box = new TextBox
            {
                Text = _localIp.Substring(0, _localIp.LastIndexOf('.') + 1),
                Height = 32,
                VerticalContentAlignment = VerticalAlignment.Center,
                Background = new SolidColorBrush(CardSurface),
                Foreground = new SolidColorBrush(TextPrimary),
                BorderBrush = new SolidColorBrush(CardBorder),
                Padding = new Thickness(8, 0, 8, 0)
            };
            sp.Children.Add(box);
            var btn = new Button
            {
                Content = "Verify & Add Peer",
                Margin = new Thickness(0, 14, 0, 0),
                Height = 34,
                Background = new SolidColorBrush(MintAccent),
                Foreground = new SolidColorBrush(BgDark),
                FontWeight = FontWeights.Bold
            };
            btn.Click += async (s, e) =>
            {
                string raw = box.Text.Trim().TrimStart('#');
                prompt.Close();
                if (!string.IsNullOrEmpty(raw))
                {
                    string ip = (!raw.Contains(".") && _localIp.Contains("."))
                        ? _localIp.Substring(0, _localIp.LastIndexOf('.') + 1) + raw
                        : raw;
                    bool ok = await ProbeSingleHostAsync(ip);
                    if (!ok)
                    {
                        RegisterDiscoveredPeer("peer_" + ip, "Android Device (" + ip + ")", ip, TCP_PORT, "PHONE");
                    }
                    SwitchTab(1);
                }
            };
            sp.Children.Add(btn);
            prompt.Content = sp;
            prompt.ShowDialog();
        }

        // =====================================================================
        // NETWORKING: Unified TCP 8988 + UDP 8989 Beacon + mDNS + Subnet Probe
        // =====================================================================
        private void StartNetworkEngines()
        {
            var token = _cts.Token;
            Task.Run(() => RunTcpServerLoopAsync(token), token);
            Task.Run(() => RunUdpBeaconListenerAsync(token), token);
            Task.Run(() => RunUdpBeaconBroadcasterAsync(token), token);
            
            // Immediate flurry of UDP discovery
            BroadcastBeaconBurst();

            // Run initial discovery sweep gently after UI stabilizes
            Task.Delay(1500, token).ContinueWith(t =>
            {
                if (!token.IsCancellationRequested)
                {
                    Dispatcher.BeginInvoke(new Action(() => OnScanClicked()));
                }
            });
        }

        private async Task RunTcpServerLoopAsync(CancellationToken token)
        {
            TcpListener listener = null;
            try
            {
                listener = new TcpListener(IPAddress.Any, TCP_PORT);
                listener.Start();
                while (!token.IsCancellationRequested)
                {
                    var client = await listener.AcceptTcpClientAsync();
                    Task.Run(() => HandleIncomingTcpClientAsync(client, token), token);
                }
            }
            catch { }
            finally
            {
                try { if (listener != null) listener.Stop(); } catch { }
            }
        }

        private async Task HandleIncomingTcpClientAsync(TcpClient client, CancellationToken token)
        {
            using (client)
            {
                try
                {
                    var remoteEp = client.Client.RemoteEndPoint as IPEndPoint;
                    string senderIp = remoteEp != null ? remoteEp.Address.ToString() : "";
                    using (var stream = client.GetStream())
                    {
                        string firstLine = ReadUtf8Line(stream);
                        if (string.IsNullOrEmpty(firstLine)) return;

                        // 1. Unified Handshake Verification Ping
                        // Supports both BEAMDROP_PING|<deviceId>|<deviceName>|<type> AND BEAMDROP_PING|<deviceName>|<port>|<type>
                        if (firstLine.StartsWith("BEAMDROP_PING|"))
                        {
                            var parts = firstLine.Split('|');
                            if (parts.Length >= 4)
                            {
                                int parsedPort;
                                if (int.TryParse(parts[2], out parsedPort))
                                {
                                    string peerName = parts[1];
                                    string peerType = parts[3];
                                    RegisterDiscoveredPeer("peer_" + senderIp, peerName, senderIp, parsedPort, peerType);
                                }
                                else
                                {
                                    string peerId = parts[1];
                                    string peerName = parts[2];
                                    string peerType = parts[3];
                                    if (peerId != _deviceId)
                                    {
                                        RegisterDiscoveredPeer(peerId, peerName, senderIp, TCP_PORT, peerType);
                                    }
                                }
                            }
                            string pong = string.Format("BEAMDROP_PONG|{0}|{1}|COMPUTER\n", _deviceId, _deviceName);
                            byte[] pongBytes = Encoding.UTF8.GetBytes(pong);
                            await stream.WriteAsync(pongBytes, 0, pongBytes.Length, token);
                            await stream.FlushAsync(token);
                            return;
                        }

                        // 2. Incoming File Transfer Stream (BEAMDROP_V1|senderName|fileCount|totalBytes)
                        if (firstLine.StartsWith("BEAMDROP_V1|"))
                        {
                            var parts = firstLine.Split('|');
                            string senderName = parts.Length > 1 ? parts[1] : "Android Device";
                            int fileCount = parts.Length > 2 ? int.Parse(parts[2]) : 1;
                            long totalBytes = parts.Length > 3 ? long.Parse(parts[3]) : 0L;
                            string flowDirection = parts.Length > 4 ? parts[4].Trim().ToUpperInvariant() : "TOP";
                            _auraPathState.FlowDirection = flowDirection;

                            RegisterDiscoveredPeer("peer_" + senderIp, senderName, senderIp, TCP_PORT, "PHONE");
                            ShowTransferOverlay("Receiving from " + senderName, "Incoming file...", 0.0, 0.0);

                            client.NoDelay = true;
                            client.ReceiveBufferSize = 4 * 1024 * 1024;
                            client.SendBufferSize = 4 * 1024 * 1024;
                            long totalReceived = 0;
                            var sw = Stopwatch.StartNew();
                            int chunkSize = (_settings != null && _settings.TurboSpeedMode) ? (1024 * 1024) : 65536;
                            byte[] buffer = new byte[chunkSize];
                            string lastDestPath = null;
                            string lastFileName = null;

                            for (int i = 0; i < fileCount; i++)
                            {
                                string fileHdr = ReadUtf8Line(stream);
                                if (string.IsNullOrEmpty(fileHdr) || !fileHdr.StartsWith("FILE|")) break;
                                var fParts = fileHdr.Split('|');
                                string fileName = SanitizeFileName(fParts.Length > 1 ? fParts[1] : ("Received_" + i));
                                long fileSize = fParts.Length > 2 ? long.Parse(fParts[2]) : 0L;

                                string destPath = GetUniqueFilePath(_downloadDir, fileName);
                                lastDestPath = destPath;
                                lastFileName = fileName;
                                long remaining = fileSize;
                                using (var fs = new FileStream(destPath, FileMode.Create, FileAccess.Write, FileShare.None, chunkSize, FileOptions.SequentialScan | FileOptions.Asynchronous))
                                {
                                    while (remaining > 0)
                                    {
                                        int toRead = (int)Math.Min(buffer.Length, remaining);
                                        int read = await stream.ReadAsync(buffer, 0, toRead, token);
                                        if (read <= 0) break;
                                        await fs.WriteAsync(buffer, 0, read, token);
                                        remaining -= read;
                                        totalReceived += read;

                                        double elapsed = Math.Max(0.05, sw.Elapsed.TotalSeconds);
                                        double mbps = (totalReceived / (1024.0 * 1024.0)) / elapsed;
                                        double prog = totalBytes > 0 ? (double)totalReceived / totalBytes : 1.0;
                                        UpdateTransferOverlay(fileName, prog, mbps);
                                    }
                                }

                                double speedMbps = (totalReceived / (1024.0 * 1024.0)) / Math.Max(0.05, sw.Elapsed.TotalSeconds);
                                _history.Insert(0, new TransferLogEntry
                                {
                                    FileName = fileName,
                                    Direction = "RECEIVED",
                                    PeerName = senderName,
                                    SizeBytes = fileSize,
                                    SpeedMbps = speedMbps,
                                    Status = "COMPLETED",
                                    SavedPath = destPath,
                                    Timestamp = DateTime.Now.ToString("HH:mm:ss")
                                });
                                ReceivingHistoryManager.AddRecord(fileName, destPath, fileSize, senderName, senderIp, DateTime.Now);
                                Dispatcher.Invoke(() => { if (_isHistoryOpen) RefreshHistoryListUi(); });
                            }

                            byte[] ack = Encoding.UTF8.GetBytes("OK\n");
                            await stream.WriteAsync(ack, 0, ack.Length, token);
                            await stream.FlushAsync(token);

                            // Immediately self-dismiss the discreet progress indicator upon completion
                            HideTransferOverlay();

                            string displayTitle = fileCount > 1 ? string.Format("{0} Files Received!", fileCount) : "File Received!";
                            string displayName = fileCount > 1 ? string.Format("{0} files ({1})", fileCount, FormatFileSize(totalBytes)) : lastFileName;
                            ShowCompletionToast(displayTitle, string.Format("Received from {0}", senderName), displayName, totalBytes, lastDestPath);
                            PlayCompletionChime();
                        }
                    }
                }
                catch
                {
                    HideTransferOverlay();
                }
            }
        }

        private async Task RunUdpBeaconListenerAsync(CancellationToken token)
        {
            while (!token.IsCancellationRequested)
            {
                UdpClient udp = null;
                try
                {
                    udp = new UdpClient();
                    udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                    const int SIO_UDP_CONNRESET = -1744830452;
                    try
                    {
                        udp.Client.IOControl((IOControlCode)SIO_UDP_CONNRESET, new byte[] { 0, 0, 0, 0 }, null);
                    }
                    catch { }

                    udp.Client.Bind(new IPEndPoint(IPAddress.Any, UDP_PORT));

                    while (!token.IsCancellationRequested)
                    {
                        try
                        {
                            var res = await udp.ReceiveAsync();
                            string senderSocketIp = res.RemoteEndPoint != null ? res.RemoteEndPoint.Address.ToString() : "";
                            string msg = Encoding.UTF8.GetString(res.Buffer).Trim();

                            if (msg.StartsWith("BEAMDROP_BEACON|"))
                            {
                                var parts = msg.Split('|');
                                // Unified 6-part format: BEAMDROP_BEACON|<id>|<name>|<ip>|<port>|<type>
                                if (parts.Length >= 6)
                                {
                                    string peerId = parts[1];
                                    string name = parts[2];
                                    string rawIp = parts[3];
                                    string ip = (!string.IsNullOrEmpty(rawIp) && rawIp != "127.0.0.1" && rawIp != "0.0.0.0") ? rawIp : senderSocketIp;
                                    int port = TCP_PORT;
                                    int.TryParse(parts[4], out port);
                                    string type = parts[5];

                                    if (peerId != _deviceId && ip != _localIp)
                                    {
                                        RegisterDiscoveredPeer(peerId, name, ip, port, type);
                                        SendUnicastBeaconReply(ip);
                                    }
                                }
                                // Legacy 5-part format: BEAMDROP_BEACON|<name>|<ip>|<port>|<type>
                                else if (parts.Length == 5)
                                {
                                    string name = parts[1];
                                    string rawIp = parts[2];
                                    string ip = (!string.IsNullOrEmpty(rawIp) && rawIp != "127.0.0.1" && rawIp != "0.0.0.0") ? rawIp : senderSocketIp;
                                    int port = TCP_PORT;
                                    int.TryParse(parts[3], out port);
                                    string type = parts[4];

                                    if (ip != _localIp && name != _deviceName)
                                    {
                                        RegisterDiscoveredPeer("udp_" + ip, name, ip, port, type);
                                        SendUnicastBeaconReply(ip);
                                    }
                                }
                            }
                            else if (msg.StartsWith("BEAMDROP_AURA_PATH|"))
                            {
                                var parts = msg.Split('|');
                                if (parts.Length >= 8 && senderSocketIp != _localIp)
                                {
                                    string sessionId = parts[1];
                                    string srcDev = parts[2];
                                    string dstDev = parts[3];
                                    string fileName = parts[4];
                                    double prog = 0.0;
                                    double.TryParse(parts[5], System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out prog);
                                    double speed = 0.0;
                                    double.TryParse(parts[6], System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out speed);
                                    string stateToken = parts[7];
                                    string flowDirection = parts.Length >= 9 ? parts[8].Trim().ToUpperInvariant() : "TOP";

                                    if (stateToken == "COMPLETED" || stateToken == "CANCELLED" || prog >= 1.0)
                                    {
                                        HideTransferOverlay();
                                    }
                                    else
                                    {
                                        _auraPathState.IsActive = true;
                                        _auraPathState.SessionId = sessionId;
                                        _auraPathState.SourceDeviceName = srcDev;
                                        _auraPathState.TargetDeviceName = dstDev;
                                        _auraPathState.FileName = fileName;
                                        _auraPathState.MigrationProgress = prog;
                                        _auraPathState.SpeedMbps = speed;
                                        _auraPathState.FlowDirection = flowDirection;
                                        _auraPathState.IsOutgoing = false;
                                        ShowTransferOverlay("Aura Sync from " + srcDev, fileName, prog, speed);
                                    }
                                }
                            }
                        }
                        catch (SocketException)
                        {
                            Thread.Sleep(50);
                        }
                        catch (Exception)
                        {
                            if (token.IsCancellationRequested) break;
                            Thread.Sleep(100);
                        }
                    }
                }
                catch (Exception)
                {
                    if (token.IsCancellationRequested) break;
                    Thread.Sleep(500);
                }
                finally
                {
                    try { if (udp != null) udp.Close(); } catch { }
                }
            }
        }

        private void BroadcastAuraPathSync(string targetIp, string stateToken)
        {
            Task.Run(async () =>
            {
                try
                {
                    using (var udp = new UdpClient())
                    {
                        udp.EnableBroadcast = true;
                        string wire = _auraPathState.ToWirePacket(stateToken);
                        byte[] bytes = Encoding.UTF8.GetBytes(wire);
                        if (!string.IsNullOrEmpty(targetIp) && targetIp != "127.0.0.1")
                        {
                            await udp.SendAsync(bytes, bytes.Length, new IPEndPoint(IPAddress.Parse(targetIp), UDP_PORT));
                        }
                        await udp.SendAsync(bytes, bytes.Length, new IPEndPoint(IPAddress.Broadcast, UDP_PORT));
                    }
                }
                catch { }
            });
        }

        private void SendUnicastBeaconReply(string targetIp)
        {
            Task.Run(async () =>
            {
                try
                {
                    using (var udp = new UdpClient())
                    {
                        string payload = string.Format("BEAMDROP_BEACON|{0}|{1}|{2}|{3}|COMPUTER", _deviceId, _deviceName, _localIp, TCP_PORT);
                        byte[] data = Encoding.UTF8.GetBytes(payload);
                        await udp.SendAsync(data, data.Length, new IPEndPoint(IPAddress.Parse(targetIp), UDP_PORT));
                    }
                }
                catch { }
            });
        }

        private void BroadcastBeaconBurst()
        {
            Task.Run(async () =>
            {
                try
                {
                    using (var udp = new UdpClient())
                    {
                        udp.EnableBroadcast = true;
                        string payload = string.Format("BEAMDROP_BEACON|{0}|{1}|{2}|{3}|COMPUTER", _deviceId, _deviceName, _localIp, TCP_PORT);
                        byte[] data = Encoding.UTF8.GetBytes(payload);

                        // 1. Global Broadcast
                        await udp.SendAsync(data, data.Length, new IPEndPoint(IPAddress.Broadcast, UDP_PORT));

                        // 2. All interface subnet broadcasts
                        foreach (var bcast in GetAllSubnetBroadcastAddresses())
                        {
                            try { await udp.SendAsync(data, data.Length, new IPEndPoint(bcast, UDP_PORT)); } catch { }
                        }

                        // 3. Known peers unicast
                        foreach (var peer in _peers.Values)
                        {
                            try { await udp.SendAsync(data, data.Length, new IPEndPoint(IPAddress.Parse(peer.Ip), UDP_PORT)); } catch { }
                        }
                    }
                }
                catch { }
            });
        }

        private static List<IPAddress> GetAllSubnetBroadcastAddresses()
        {
            var list = new List<IPAddress>();
            try
            {
                foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (ni.OperationalStatus != OperationalStatus.Up || ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                    foreach (var ua in ni.GetIPProperties().UnicastAddresses)
                    {
                        if (ua.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ua.Address))
                        {
                            string ipStr = ua.Address.ToString();
                            int dot = ipStr.LastIndexOf('.');
                            if (dot > 0)
                            {
                                string bcast = ipStr.Substring(0, dot + 1) + "255";
                                IPAddress parsed;
                                if (IPAddress.TryParse(bcast, out parsed) && !list.Contains(parsed))
                                {
                                    list.Add(parsed);
                                }
                            }
                        }
                    }
                }
            }
            catch { }
            if (list.Count == 0) list.Add(IPAddress.Broadcast);
            return list;
        }

        private async Task RunUdpBeaconBroadcasterAsync(CancellationToken token)
        {
            using (var udp = new UdpClient())
            {
                udp.EnableBroadcast = true;
                while (!token.IsCancellationRequested)
                {
                    try
                    {
                        _localIp = ResolveBestLocalIp();
                        string payload = string.Format("BEAMDROP_BEACON|{0}|{1}|{2}|{3}|COMPUTER", _deviceId, _deviceName, _localIp, TCP_PORT);
                        byte[] data = Encoding.UTF8.GetBytes(payload);

                        // 1. Global Broadcast
                        await udp.SendAsync(data, data.Length, new IPEndPoint(IPAddress.Broadcast, UDP_PORT));

                        // 2. All interface subnet broadcasts
                        foreach (var bcast in GetAllSubnetBroadcastAddresses())
                        {
                            try { await udp.SendAsync(data, data.Length, new IPEndPoint(bcast, UDP_PORT)); } catch { }
                        }

                        // 3. Direct Unicast refresh to known peers
                        foreach (var peer in _peers.Values)
                        {
                            try
                            {
                                await udp.SendAsync(data, data.Length, new IPEndPoint(IPAddress.Parse(peer.Ip), UDP_PORT));
                            }
                            catch { }
                        }

                        // 4. Multicast mDNS group notification
                        try
                        {
                            await udp.SendAsync(data, data.Length, new IPEndPoint(IPAddress.Parse(MDNS_GROUP), MDNS_PORT));
                        }
                        catch { }
                    }
                    catch { }
                    await Task.Delay(2000, token);
                }
            }
        }

        private void UpdateScanButtonUi(bool scanning)
        {
            try
            {
                if (Dispatcher.CheckAccess())
                {
                    if (_scanButtonText != null) _scanButtonText.Text = scanning ? "↻ Scanning..." : "↻ Scan";
                }
                else
                {
                    Dispatcher.BeginInvoke(new Action(() =>
                    {
                        if (_scanButtonText != null) _scanButtonText.Text = scanning ? "↻ Scanning..." : "↻ Scan";
                    }));
                }
            }
            catch { }
        }

        private async void OnScanClicked()
        {
            if (_isScanning) return;
            _isScanning = true;
            UpdateScanButtonUi(true);

            try
            {
                await Task.Run(async () =>
                {
                    try
                    {
                        // 1. Immediately prune any unresponsive known peers
                        var currentPeers = _peers.Values.ToList();
                        foreach (var peer in currentPeers)
                        {
                            if ((DateTime.UtcNow - peer.LastSeen).TotalSeconds > 6.0)
                            {
                                bool alive = await ProbeSingleHostAsync(peer.Ip);
                                if (!alive)
                                {
                                    PeerNode removed;
                                    _peers.TryRemove(peer.Ip, out removed);
                                }
                            }
                        }
                        RefreshPeersListUi();

                        // 2. Immediate flurry of UDP broadcast beacons across all local interfaces
                        BroadcastBeaconBurst();

                        // 3. Parallel TCP Subnet Scan with 750ms timeout
                        await RunSubnetScanAsync();
                    }
                    catch { }
                });
            }
            catch { }
            finally
            {
                _isScanning = false;
                UpdateScanButtonUi(false);
                RefreshPeersListUi();
            }
        }

        private void TriggerSubnetScan()
        {
            OnScanClicked();
        }

        private async Task RunSubnetScanAsync()
        {
            try
            {
                _localIp = ResolveBestLocalIp();
                int lastDot = _localIp.LastIndexOf('.');
                if (lastDot <= 0) return;
                string prefix = _localIp.Substring(0, lastDot + 1);

                var tasks = new List<Task>();
                for (int i = 1; i <= 254; i++)
                {
                    string candidateIp = prefix + i;
                    if (candidateIp == _localIp) continue;
                    tasks.Add(ProbeSingleHostAsync(candidateIp));
                    if (tasks.Count >= 48)
                    {
                        await Task.WhenAll(tasks);
                        tasks.Clear();
                    }
                }
                if (tasks.Count > 0) await Task.WhenAll(tasks);
            }
            catch { }
        }

        private async Task<bool> ProbeSingleHostAsync(string ip)
        {
            try
            {
                using (var client = new TcpClient())
                {
                    var connectTask = client.ConnectAsync(ip, TCP_PORT);
                    var completed = await Task.WhenAny(connectTask, Task.Delay(750));
                    if (completed != connectTask || !client.Connected)
                    {
                        connectTask.ContinueWith(t => { var ign = t.Exception; }, TaskContinuationOptions.OnlyOnFaulted);
                        return false;
                    }

                    using (var stream = client.GetStream())
                    {
                        stream.ReadTimeout = 650;
                        stream.WriteTimeout = 650;
                        string ping = string.Format("BEAMDROP_PING|{0}|{1}|COMPUTER\n", _deviceId, _deviceName);
                        byte[] pingBytes = Encoding.UTF8.GetBytes(ping);
                        await stream.WriteAsync(pingBytes, 0, pingBytes.Length);
                        await stream.FlushAsync();

                        string pong = ReadUtf8Line(stream);
                        if (!string.IsNullOrEmpty(pong) && pong.StartsWith("BEAMDROP_PONG|"))
                        {
                            var parts = pong.Split('|');
                            if (parts.Length >= 4)
                            {
                                int parsedPort;
                                if (int.TryParse(parts[2], out parsedPort))
                                {
                                    RegisterDiscoveredPeer("peer_" + ip, parts[1], ip, parsedPort, parts[3]);
                                    return true;
                                }
                                else
                                {
                                    string peerId = parts[1];
                                    string peerName = parts[2];
                                    string peerType = parts[3];
                                    if (peerId != _deviceId)
                                    {
                                        RegisterDiscoveredPeer(peerId, peerName, ip, TCP_PORT, peerType);
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            catch { }
            return false;
        }

        private void RegisterDiscoveredPeer(string id, string name, string ip, int port, string type)
        {
            if (string.IsNullOrEmpty(ip) || ip == _localIp || ip == "127.0.0.1") return;
            bool isNewOrUpdated = false;
            double nowSec = _animClock.Elapsed.TotalSeconds;

            _peers.AddOrUpdate(ip,
                ipKey =>
                {
                    isNewOrUpdated = true;
                    _lastPeerDiscoveredTime = nowSec;
                    return new PeerNode
                    {
                        Id = id,
                        Name = name,
                        Ip = ip,
                        Port = port,
                        Type = type,
                        LastSeen = DateTime.UtcNow,
                        DiscoveredAtSeconds = nowSec
                    };
                },
                (ipKey, existing) =>
                {
                    if (existing.Name != name || existing.Type != type) isNewOrUpdated = true;
                    existing.Id = id;
                    existing.Name = name;
                    existing.Port = port;
                    existing.Type = type;
                    existing.LastSeen = DateTime.UtcNow;
                    return existing;
                });

            if (isNewOrUpdated)
            {
                RefreshPeersListUi();
            }
        }
        private static string ReadUtf8Line(Stream stream)
        {
            var bytes = new List<byte>(128);
            while (bytes.Count < 4096)
            {
                int b = stream.ReadByte();
                if (b == -1) return bytes.Count > 0 ? Encoding.UTF8.GetString(bytes.ToArray()).TrimEnd('\r') : null;
                if (b == '\n') break;
                bytes.Add((byte)b);
            }
            return Encoding.UTF8.GetString(bytes.ToArray()).TrimEnd('\r');
        }

        private static string ResolveBestLocalIp()
        {
            try
            {
                foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (ni.OperationalStatus != OperationalStatus.Up) continue;
                    if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                    foreach (var ua in ni.GetIPProperties().UnicastAddresses)
                    {
                        if (ua.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ua.Address))
                        {
                            string ip = ua.Address.ToString();
                            if (ip.StartsWith("192.168.") || ip.StartsWith("10.") || ip.StartsWith("172."))
                                return ip;
                        }
                    }
                }
            }
            catch { }
            return "127.0.0.1";
        }

        private static string SanitizeFileName(string name)
        {
            foreach (char c in Path.GetInvalidFileNameChars())
                name = name.Replace(c, '_');
            return string.IsNullOrWhiteSpace(name) ? "Received_File.bin" : name;
        }

        private static string GetUniqueFilePath(string dir, string fileName)
        {
            string candidate = Path.Combine(dir, fileName);
            if (!File.Exists(candidate)) return candidate;
            string stem = Path.GetFileNameWithoutExtension(fileName);
            string ext = Path.GetExtension(fileName);
            int counter = 1;
            while (File.Exists(candidate))
            {
                candidate = Path.Combine(dir, string.Format("{0} ({1}){2}", stem, counter++, ext));
            }
            return candidate;
        }

        public static string FormatFileSize(long bytes)
        {
            if (bytes >= 1024L * 1024L * 1024L) return string.Format("{0:F2} GB", bytes / (1024.0 * 1024.0 * 1024.0));
            if (bytes >= 1024L * 1024L) return string.Format("{0:F1} MB", bytes / (1024.0 * 1024.0));
            if (bytes >= 1024L) return string.Format("{0:F0} KB", bytes / 1024.0);
            return bytes + " B";
        }

        private Border CreateIconPill(string text, Action onClick)
        {
            var pill = new Border
            {
                Background = new SolidColorBrush(CardSurface),
                BorderBrush = new SolidColorBrush(CardBorder),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(14),
                Padding = new Thickness(12, 6, 12, 6),
                Margin = new Thickness(8, 0, 0, 0),
                Cursor = Cursors.Hand,
                Child = new TextBlock
                {
                    Text = text,
                    FontSize = 12,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = new SolidColorBrush(TextPrimary)
                }
            };
            pill.MouseLeftButtonUp += (s, e) => onClick();
            return pill;
        }

        private Border CreateSmallActionButton(string text, Action onClick)
        {
            var btn = new Border
            {
                Background = new SolidColorBrush(CardSurface),
                BorderBrush = new SolidColorBrush(CardBorder),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(10),
                Padding = new Thickness(10, 4, 10, 4),
                Margin = new Thickness(0, 0, 8, 0),
                Cursor = Cursors.Hand,
                Child = new TextBlock
                {
                    Text = text,
                    FontSize = 12,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = new SolidColorBrush(TextPrimary)
                }
            };
            btn.MouseLeftButtonUp += (s, e) => onClick();
            return btn;
        }

        private Border CreateSettingsSectionCard(string title, out StackPanel rowsPanel)
        {
            var card = new Border
            {
                Background = new SolidColorBrush(_currentCardSurface),
                BorderBrush = new SolidColorBrush(_currentCardBorder),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(14),
                Padding = new Thickness(20, 16, 20, 16),
                Margin = new Thickness(0, 0, 0, 18),
                Effect = new DropShadowEffect
                {
                    Color = _isDarkMode ? Color.FromArgb(40, 0, 0, 0) : Color.FromArgb(30, 0, 0, 0),
                    BlurRadius = 12,
                    ShadowDepth = 2,
                    Opacity = 0.25
                }
            };
            rowsPanel = new StackPanel();
            rowsPanel.Children.Add(new TextBlock
            {
                Text = title,
                FontSize = 14.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(_currentTextPrimary),
                Margin = new Thickness(0, 0, 0, 12)
            });
            card.Child = rowsPanel;
            return card;
        }

        private void AddSettingsThemeSelectorRow(StackPanel parent)
        {
            var dock = new DockPanel { Margin = new Thickness(0, 6, 0, 6) };
            dock.Children.Add(new TextBlock
            {
                Text = "Theme",
                FontSize = 13,
                Foreground = new SolidColorBrush(_currentTextPrimary),
                VerticalAlignment = VerticalAlignment.Center
            });

            var segStack = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };

            var darkPill = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(_isDarkMode ? _currentMintAccent : Color.FromArgb(35, 128, 128, 128)),
                BorderBrush = new SolidColorBrush(_currentCardBorder),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(12, 6, 12, 6),
                Margin = new Thickness(0, 0, 8, 0),
                Cursor = Cursors.Hand
            };
            darkPill.Child = new TextBlock
            {
                Text = "🌙 Dark Theme",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(_isDarkMode ? Color.FromRgb(15, 23, 42) : _currentTextPrimary)
            };
            darkPill.MouseLeftButtonUp += (s, e) =>
            {
                if (!_isDarkMode) ApplyTheme(true);
            };

            var lightPill = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(!_isDarkMode ? _currentMintAccent : Color.FromArgb(35, 128, 128, 128)),
                BorderBrush = new SolidColorBrush(_currentCardBorder),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(12, 6, 12, 6),
                Cursor = Cursors.Hand
            };
            lightPill.Child = new TextBlock
            {
                Text = "☀️ Light Theme",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(!_isDarkMode ? Colors.White : _currentTextPrimary)
            };
            lightPill.MouseLeftButtonUp += (s, e) =>
            {
                if (_isDarkMode) ApplyTheme(false);
            };

            segStack.Children.Add(darkPill);
            segStack.Children.Add(lightPill);
            DockPanel.SetDock(segStack, Dock.Right);
            dock.Children.Add(segStack);
            parent.Children.Add(dock);
        }

        private void AddSettingsFolderRow(StackPanel parent, string label, string path)
        {
            var dock = new DockPanel { Margin = new Thickness(0, 6, 0, 6) };
            var textStack = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            textStack.Children.Add(new TextBlock
            {
                Text = label,
                FontSize = 13,
                Foreground = new SolidColorBrush(_currentTextPrimary)
            });

            var pathLabel = new TextBlock
            {
                Text = path,
                FontSize = 11,
                Foreground = new SolidColorBrush(_currentTextSecondary),
                TextTrimming = TextTrimming.CharacterEllipsis,
                MaxWidth = 310,
                Margin = new Thickness(0, 2, 0, 0)
            };
            textStack.Children.Add(pathLabel);
            dock.Children.Add(textStack);

            var btnsRow = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };

            var changeBtn = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(_currentMintAccent),
                Padding = new Thickness(12, 6, 12, 6),
                Margin = new Thickness(0, 0, 8, 0),
                Cursor = Cursors.Hand
            };
            changeBtn.Child = new TextBlock
            {
                Text = "📁 Change...",
                FontSize = 11.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(Color.FromRgb(15, 23, 42))
            };
            changeBtn.MouseLeftButtonUp += (s, e) =>
            {
                try
                {
                    string selected = ModernFolderPicker.Show(this, "Select destination folder for received files (Any Drive / Folder)", _downloadDir);
                    if (!string.IsNullOrEmpty(selected) && Directory.Exists(selected))
                    {
                        _downloadDir = selected;
                        _settings.DownloadDir = _downloadDir;
                        _settings.Save();
                        pathLabel.Text = _downloadDir;
                    }
                }
                catch (Exception ex)
                {
                    MessageBox.Show(this, "Could not set folder: " + ex.Message, "BeamDrop", MessageBoxButton.OK, MessageBoxImage.Warning);
                }
            };
            btnsRow.Children.Add(changeBtn);

            var openBtn = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(Color.FromArgb(40, 128, 128, 128)),
                BorderBrush = new SolidColorBrush(_currentCardBorder),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(12, 6, 12, 6),
                Cursor = Cursors.Hand
            };
            openBtn.Child = new TextBlock
            {
                Text = "↗ Open",
                FontSize = 11.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(_currentTextPrimary)
            };
            openBtn.MouseLeftButtonUp += (s, e) =>
            {
                try
                {
                    if (Directory.Exists(_downloadDir)) Process.Start("explorer.exe", _downloadDir);
                }
                catch { }
            };
            btnsRow.Children.Add(openBtn);

            DockPanel.SetDock(btnsRow, Dock.Right);
            dock.Children.Add(btnsRow);
            parent.Children.Add(dock);
        }

        private void AddSettingsDeviceNameRow(StackPanel parent, string label, string name)
        {
            var dock = new DockPanel { Margin = new Thickness(0, 6, 0, 6) };
            dock.Children.Add(new TextBlock
            {
                Text = label,
                FontSize = 13,
                Foreground = new SolidColorBrush(_currentTextPrimary),
                VerticalAlignment = VerticalAlignment.Center
            });

            var rightStack = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };

            var nameText = new TextBlock
            {
                Text = name,
                FontSize = 12.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(_currentTextPrimary),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 12, 0)
            };
            rightStack.Children.Add(nameText);

            var renameBtn = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(_currentMintAccent),
                Padding = new Thickness(12, 6, 12, 6),
                Cursor = Cursors.Hand
            };
            renameBtn.Child = new TextBlock
            {
                Text = "✏ Rename...",
                FontSize = 11.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(Color.FromRgb(15, 23, 42))
            };
            renameBtn.MouseLeftButtonUp += (s, e) =>
            {
                var prompt = new PromptInputDialog("Rename Device", "Enter a custom name for this PC:", _deviceName, _isDarkMode);
                prompt.Owner = this;
                if (prompt.ShowDialog() == true && !string.IsNullOrWhiteSpace(prompt.InputText))
                {
                    _deviceName = prompt.InputText.Trim();
                    _deviceId = "win_" + Math.Abs((_deviceName + "_" + _localIp).GetHashCode() % 1000000);
                    _settings.DeviceName = _deviceName;
                    _settings.Save();
                    nameText.Text = _deviceName;
                    if (_receiveDeviceNameText != null) _receiveDeviceNameText.Text = _deviceName.ToUpperInvariant();
                    BroadcastBeaconBurst();
                }
            };
            rightStack.Children.Add(renameBtn);

            DockPanel.SetDock(rightStack, Dock.Right);
            dock.Children.Add(rightStack);
            parent.Children.Add(dock);
        }


        public static void LaunchUrl(string url)
        {
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = url,
                    UseShellExecute = true
                });
            }
            catch
            {
                try { Process.Start(url); } catch { }
            }
        }

        public void ShowSupportAndDonateDialog()
        {
            try
            {
                var dlg = new SupportDonateDialog(_isDarkMode);
                dlg.Owner = this;
                dlg.ShowDialog();
            }
            catch (Exception)
            {
                LaunchUrl("https://buymeacoffee.com/sajjadhossainmridul");
            }
        }

        public void ShowAboutStaffDialog()
        {
            try
            {
                var dlg = new AboutStaffDialog(_isDarkMode);
                dlg.Owner = this;
                dlg.ShowDialog();
            }
            catch (Exception) { }
        }

        private void AddSettingsButtonRow(StackPanel parent, string label, string buttonText, Action onClick, bool isPrimary = false)
        {
            var dock = new DockPanel { Margin = new Thickness(0, 6, 0, 6) };
            dock.Children.Add(new TextBlock
            {
                Text = label,
                FontSize = 13,
                Foreground = new SolidColorBrush(_currentTextPrimary),
                VerticalAlignment = VerticalAlignment.Center,
                MaxWidth = 360,
                TextWrapping = TextWrapping.Wrap
            });

            var btn = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(isPrimary ? _currentMintAccent : Color.FromArgb(40, 128, 128, 128)),
                BorderBrush = new SolidColorBrush(isPrimary ? Colors.Transparent : _currentCardBorder),
                BorderThickness = new Thickness(isPrimary ? 0 : 1),
                Padding = new Thickness(14, 6, 14, 6),
                Cursor = Cursors.Hand,
                HorizontalAlignment = HorizontalAlignment.Right
            };
            btn.Child = new TextBlock
            {
                Text = buttonText,
                FontSize = 11.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(isPrimary ? Color.FromRgb(15, 23, 42) : _currentTextPrimary)
            };
            btn.MouseLeftButtonUp += (s, e) => onClick();

            DockPanel.SetDock(btn, Dock.Right);
            dock.Children.Add(btn);
            parent.Children.Add(dock);
        }

        private void AddSettingsBadgeRow(StackPanel parent, string label, string value)
        {
            var dock = new DockPanel { Margin = new Thickness(0, 6, 0, 6) };
            dock.Children.Add(new TextBlock
            {
                Text = label,
                FontSize = 13,
                Foreground = new SolidColorBrush(_currentTextPrimary),
                VerticalAlignment = VerticalAlignment.Center
            });

            var badge = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(Color.FromArgb(35, 128, 128, 128)),
                BorderBrush = new SolidColorBrush(_currentCardBorder),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(12, 5, 12, 5),
                HorizontalAlignment = HorizontalAlignment.Right,
                Child = new TextBlock
                {
                    Text = value,
                    FontSize = 11.5,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = new SolidColorBrush(_currentTextPrimary)
                }
            };
            DockPanel.SetDock(badge, Dock.Right);
            dock.Children.Add(badge);
            parent.Children.Add(dock);
        }

        private void AddSettingsToggleRow(StackPanel parent, string label, bool initialValue, Action<bool> onChanged)
        {
            bool state = initialValue;
            var dock = new DockPanel { Margin = new Thickness(0, 6, 0, 6) };
            dock.Children.Add(new TextBlock
            {
                Text = label,
                FontSize = 13,
                Foreground = new SolidColorBrush(_currentTextPrimary),
                VerticalAlignment = VerticalAlignment.Center,
                MaxWidth = 360,
                TextWrapping = TextWrapping.Wrap
            });

            var pill = new Border
            {
                Width = 50,
                Height = 26,
                CornerRadius = new CornerRadius(13),
                Background = new SolidColorBrush(state ? _currentMintAccent : Color.FromArgb(60, 128, 128, 128)),
                Padding = new Thickness(3),
                HorizontalAlignment = HorizontalAlignment.Right,
                Cursor = Cursors.Hand
            };
            var knob = new Ellipse
            {
                Width = 20,
                Height = 20,
                Fill = new SolidColorBrush(_currentBg),
                HorizontalAlignment = state ? HorizontalAlignment.Right : HorizontalAlignment.Left
            };
            pill.Child = knob;
            pill.MouseLeftButtonUp += (s, e) =>
            {
                state = !state;
                pill.Background = new SolidColorBrush(state ? _currentMintAccent : Color.FromArgb(60, 128, 128, 128));
                knob.HorizontalAlignment = state ? HorizontalAlignment.Right : HorizontalAlignment.Left;
                onChanged(state);
            };
            DockPanel.SetDock(pill, Dock.Right);
            dock.Children.Add(pill);
            parent.Children.Add(dock);
        }

        public void ShowNetworkInfoDialog()
        {
            try
            {
                var dlg = new NetworkInfoDialog(
                    _isDarkMode,
                    _deviceName,
                    _deviceId,
                    _localIp,
                    TCP_PORT,
                    UDP_PORT,
                    _peers,
                    _downloadDir,
                    () => TriggerSubnetScan()
                );
                dlg.Owner = this;
                dlg.ShowDialog();
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, "Could not open Network Diagnostics: " + ex.Message, "Error", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BuildHistoryDrawer()
        {
            _historyOverlayHost = new Grid
            {
                Visibility = Visibility.Collapsed,
                HorizontalAlignment = HorizontalAlignment.Stretch,
                VerticalAlignment = VerticalAlignment.Stretch
            };
            Panel.SetZIndex(_historyOverlayHost, 900);
            Grid.SetColumn(_historyOverlayHost, 0);
            Grid.SetColumnSpan(_historyOverlayHost, 2);

            // 1. Semi-transparent dark scrim backdrop
            _historyScrim = new Border
            {
                Background = new SolidColorBrush(Color.FromArgb(145, 5, 10, 20)),
                Opacity = 0.0,
                Cursor = Cursors.Hand
            };
            _historyScrim.MouseLeftButtonUp += (s, e) => CloseHistoryDrawer();
            _historyOverlayHost.Children.Add(_historyScrim);

            // 2. Sliding Drawer Panel (docked to Right, slides Right to Left)
            _historyDrawer = new Border
            {
                Width = 470,
                HorizontalAlignment = HorizontalAlignment.Right,
                VerticalAlignment = VerticalAlignment.Stretch,
                Background = new LinearGradientBrush(
                    _isDarkMode ? Color.FromRgb(11, 18, 30) : Color.FromRgb(248, 250, 252),
                    _isDarkMode ? Color.FromRgb(15, 23, 42) : Color.FromRgb(241, 245, 249),
                    new Point(0, 0),
                    new Point(0, 1)
                ),
                BorderBrush = new LinearGradientBrush(
                    Color.FromArgb(140, 58, 228, 204),
                    Color.FromArgb(140, 172, 92, 246),
                    new Point(0, 0),
                    new Point(0, 1)
                ),
                BorderThickness = new Thickness(1.5, 0, 0, 0),
                Effect = new DropShadowEffect
                {
                    Color = Colors.Black,
                    BlurRadius = 40,
                    ShadowDepth = -6,
                    Opacity = 0.70
                }
            };

            _historyDrawerTranslate = new TranslateTransform(490, 0);
            _historyDrawer.RenderTransform = _historyDrawerTranslate;

            var drawerDock = new DockPanel { Margin = new Thickness(24, 20, 24, 20) };

            // --- Header Section ---
            var headerStack = new StackPanel { Margin = new Thickness(0, 0, 0, 16) };
            DockPanel.SetDock(headerStack, Dock.Top);

            // Top Header Row: Title + Count Badge + Close Button
            var topRow = new DockPanel { Margin = new Thickness(0, 0, 0, 6) };

            var closeBtn = new Border
            {
                Width = 32,
                Height = 32,
                CornerRadius = new CornerRadius(16),
                Background = new SolidColorBrush(_isDarkMode ? Color.FromArgb(60, 255, 255, 255) : Color.FromArgb(40, 0, 0, 0)),
                Cursor = Cursors.Hand,
                HorizontalAlignment = HorizontalAlignment.Right,
                ToolTip = "Close History (Esc)"
            };
            var closeTxt = new TextBlock
            {
                Text = "âœ•",
                FontSize = 13,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(_currentTextPrimary),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            closeBtn.Child = closeTxt;
            closeBtn.MouseEnter += (s, e) => closeBtn.Background = new SolidColorBrush(Color.FromArgb(120, 239, 68, 68));
            closeBtn.MouseLeave += (s, e) => closeBtn.Background = new SolidColorBrush(_isDarkMode ? Color.FromArgb(60, 255, 255, 255) : Color.FromArgb(40, 0, 0, 0));
            closeBtn.MouseLeftButtonUp += (s, e) => CloseHistoryDrawer();
            DockPanel.SetDock(closeBtn, Dock.Right);
            topRow.Children.Add(closeBtn);

            var titleStack = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            titleStack.Children.Add(new TextBlock
            {
                Text = "Receiving History",
                FontSize = 20,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(_currentTextPrimary)
            });

            _historyCountText = new TextBlock
            {
                Text = "0 Files",
                FontSize = 11,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(Color.FromRgb(58, 228, 204)),
                VerticalAlignment = VerticalAlignment.Center
            };
            var badgePill = new Border
            {
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromArgb(40, 58, 228, 204)),
                BorderBrush = new SolidColorBrush(Color.FromArgb(100, 58, 228, 204)),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(8, 3, 8, 3),
                Margin = new Thickness(10, 0, 0, 0),
                Child = _historyCountText
            };
            titleStack.Children.Add(badgePill);
            topRow.Children.Add(titleStack);
            headerStack.Children.Add(topRow);

            // Subtitle & Action Links
            var subRow = new DockPanel { Margin = new Thickness(0, 4, 0, 0) };
            subRow.Children.Add(new TextBlock
            {
                Text = "Recent files transferred to this Windows PC",
                FontSize = 11.5,
                Foreground = new SolidColorBrush(_currentTextSecondary),
                VerticalAlignment = VerticalAlignment.Center
            });

            var actionLinks = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };

            var openFolderBtn = new TextBlock
            {
                Text = "ðŸ“‚ Open Folder",
                FontSize = 11.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Color.FromRgb(58, 228, 204)),
                Cursor = Cursors.Hand,
                Margin = new Thickness(0, 0, 14, 0)
            };
            openFolderBtn.MouseLeftButtonUp += (s, e) =>
            {
                try { Process.Start("explorer.exe", _downloadDir); } catch { }
            };
            actionLinks.Children.Add(openFolderBtn);

            var clearBtn = new TextBlock
            {
                Text = "ðŸ—‘ Clear All",
                FontSize = 11.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Color.FromArgb(200, 248, 113, 113)),
                Cursor = Cursors.Hand
            };
            clearBtn.MouseLeftButtonUp += (s, e) =>
            {
                var result = MessageBox.Show(this, "Are you sure you want to clear receiving history?", "Clear History", MessageBoxButton.YesNo, MessageBoxImage.Question);
                if (result == MessageBoxResult.Yes)
                {
                    ReceivingHistoryManager.ClearHistory();
                    RefreshHistoryListUi();
                }
            };
            actionLinks.Children.Add(clearBtn);
            DockPanel.SetDock(actionLinks, Dock.Right);
            subRow.Children.Add(actionLinks);
            headerStack.Children.Add(subRow);

            drawerDock.Children.Add(headerStack);

            // --- Scrollable Cards Section ---
            var scroll = new ScrollViewer
            {
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
                Focusable = false
            };

            _historyCardsStack = new StackPanel { Margin = new Thickness(0, 4, 4, 12) };

            // Empty state container
            _historyEmptyContainer = new Border
            {
                Margin = new Thickness(0, 80, 0, 0),
                HorizontalAlignment = HorizontalAlignment.Center
            };
            var emptyStack = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center };
            emptyStack.Children.Add(new TextBlock
            {
                Text = "ðŸ“¡",
                FontSize = 42,
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 12)
            });
            emptyStack.Children.Add(new TextBlock
            {
                Text = "No Files Received Yet",
                FontSize = 16,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(_currentTextPrimary),
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 6)
            });
            emptyStack.Children.Add(new TextBlock
            {
                Text = "When your Android phone or another PC sends files via BeamDrop, they will appear here with exact timestamp, sender, and file size.",
                FontSize = 12,
                Foreground = new SolidColorBrush(_currentTextSecondary),
                TextAlignment = TextAlignment.Center,
                TextWrapping = TextWrapping.Wrap,
                MaxWidth = 320
            });
            _historyEmptyContainer.Child = emptyStack;
            _historyCardsStack.Children.Add(_historyEmptyContainer);

            scroll.Content = _historyCardsStack;
            drawerDock.Children.Add(scroll);

            _historyDrawer.Child = drawerDock;
            _historyOverlayHost.Children.Add(_historyDrawer);
            _rootGrid.Children.Add(_historyOverlayHost);
        }

        public void OpenHistoryDrawer()
        {
            if (_isHistoryOpen) return;
            _isHistoryOpen = true;

            RefreshHistoryListUi();
            _historyOverlayHost.Visibility = Visibility.Visible;

            // Scrim Fade-In
            var scrimAnim = new DoubleAnimation
            {
                From = 0.0,
                To = 1.0,
                Duration = TimeSpan.FromMilliseconds(240)
            };
            _historyScrim.BeginAnimation(UIElement.OpacityProperty, scrimAnim);

            // Drawer Slide Right-to-Left (490 -> 0)
            var slideAnim = new DoubleAnimation
            {
                From = 490.0,
                To = 0.0,
                Duration = TimeSpan.FromMilliseconds(280),
                EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
            };
            _historyDrawerTranslate.BeginAnimation(TranslateTransform.XProperty, slideAnim);
        }

        public void CloseHistoryDrawer()
        {
            if (!_isHistoryOpen) return;
            _isHistoryOpen = false;

            // Scrim Fade-Out
            var scrimAnim = new DoubleAnimation
            {
                From = 1.0,
                To = 0.0,
                Duration = TimeSpan.FromMilliseconds(180)
            };
            _historyScrim.BeginAnimation(UIElement.OpacityProperty, scrimAnim);

            // Drawer Slide Left-to-Right (0 -> 490)
            var slideAnim = new DoubleAnimation
            {
                From = 0.0,
                To = 490.0,
                Duration = TimeSpan.FromMilliseconds(220),
                EasingFunction = new CubicEase { EasingMode = EasingMode.EaseIn }
            };
            slideAnim.Completed += (s, e) =>
            {
                if (!_isHistoryOpen)
                {
                    _historyOverlayHost.Visibility = Visibility.Collapsed;
                }
            };
            _historyDrawerTranslate.BeginAnimation(TranslateTransform.XProperty, slideAnim);
        }

        public void RefreshHistoryListUi()
        {
            if (_historyCardsStack == null) return;
            _historyCardsStack.Children.Clear();

            var records = ReceivingHistoryManager.LoadHistory(_downloadDir);
            if (_historyCountText != null)
            {
                _historyCountText.Text = records.Count + (records.Count == 1 ? " File" : " Files");
            }

            if (records.Count == 0)
            {
                _historyCardsStack.Children.Add(_historyEmptyContainer);
                return;
            }

            foreach (var r in records)
            {
                var card = CreateHistoryItemCard(r);
                _historyCardsStack.Children.Add(card);
            }
        }

        private Border CreateHistoryItemCard(ReceivedFileRecord item)
        {
            var card = new Border
            {
                CornerRadius = new CornerRadius(12),
                Background = new SolidColorBrush(_isDarkMode ? Color.FromArgb(170, 20, 32, 48) : Color.FromArgb(220, 255, 255, 255)),
                BorderBrush = new SolidColorBrush(_isDarkMode ? Color.FromArgb(50, 58, 228, 204) : Color.FromArgb(80, 200, 210, 220)),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(14, 12, 14, 12),
                Margin = new Thickness(0, 0, 0, 10),
                Cursor = Cursors.Hand
            };

            var dock = new DockPanel();

            // 1. File Type Icon Badge
            string ext = Path.GetExtension(item.FileName).ToLowerInvariant();
            string iconGlyph;
            Color iconBg1, iconBg2;
            GetFileTypeVisuals(ext, out iconGlyph, out iconBg1, out iconBg2);

            var iconBadge = new Border
            {
                Width = 42,
                Height = 42,
                CornerRadius = new CornerRadius(10),
                Background = new LinearGradientBrush(iconBg1, iconBg2, new Point(0, 0), new Point(1, 1)),
                Margin = new Thickness(0, 0, 12, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            iconBadge.Child = new TextBlock
            {
                Text = iconGlyph,
                FontSize = 18,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            DockPanel.SetDock(iconBadge, Dock.Left);
            dock.Children.Add(iconBadge);

            // 2. Center Content
            var details = new StackPanel { VerticalAlignment = VerticalAlignment.Center };

            var nameRow = new DockPanel { Margin = new Thickness(0, 0, 0, 3) };

            string deviceDisplay = item.DeviceName;
            if (deviceDisplay.Contains("HONOR") || deviceDisplay.Contains("Android") || deviceDisplay.Contains("Phone") || deviceDisplay.Contains("Galaxy"))
                deviceDisplay = "ðŸ“± " + deviceDisplay;
            else if (deviceDisplay.Contains("VICTUS") || deviceDisplay.Contains("PC") || deviceDisplay.Contains("Laptop") || deviceDisplay.Contains("Windows"))
                deviceDisplay = "ðŸ’» " + deviceDisplay;
            else
                deviceDisplay = "ðŸ“¡ " + deviceDisplay;

            var devPill = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(Color.FromArgb(35, 58, 228, 204)),
                BorderBrush = new SolidColorBrush(Color.FromArgb(80, 58, 228, 204)),
                BorderThickness = new Thickness(0.8),
                Padding = new Thickness(6, 2, 6, 2),
                HorizontalAlignment = HorizontalAlignment.Right,
                VerticalAlignment = VerticalAlignment.Center
            };
            devPill.Child = new TextBlock
            {
                Text = deviceDisplay,
                FontSize = 10.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Color.FromRgb(58, 228, 204))
            };
            DockPanel.SetDock(devPill, Dock.Right);
            nameRow.Children.Add(devPill);

            var fileNameText = new TextBlock
            {
                Text = item.FileName,
                FontSize = 13.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(_currentTextPrimary),
                TextTrimming = TextTrimming.CharacterEllipsis,
                ToolTip = item.FilePath,
                Margin = new Thickness(0, 0, 8, 0)
            };
            nameRow.Children.Add(fileNameText);
            details.Children.Add(nameRow);

            // Exact Date, Time and File Size Metadata Row
            var metaRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 2, 0, 8) };
            metaRow.Children.Add(new TextBlock
            {
                Text = string.Format("ðŸ“… {0}  â€¢  ðŸ•’ {1}  â€¢  ðŸ’¾ {2}", item.FormattedDate, item.FormattedExactTime, item.FormattedSize),
                FontSize = 11,
                Foreground = new SolidColorBrush(_currentTextSecondary)
            });
            details.Children.Add(metaRow);

            // Actions Row: Open, Explorer, Copy Path
            var actionsRow = new StackPanel { Orientation = Orientation.Horizontal };

            var openBtn = CreateMiniActionPill("â–¶ Open", Color.FromArgb(40, 58, 228, 204), Color.FromRgb(58, 228, 204), () =>
            {
                try
                {
                    if (File.Exists(item.FilePath)) Process.Start(new ProcessStartInfo(item.FilePath) { UseShellExecute = true });
                    else MessageBox.Show(this, "File no longer exists at:\n" + item.FilePath, "File Not Found", MessageBoxButton.OK, MessageBoxImage.Warning);
                }
                catch (Exception ex) { MessageBox.Show(this, "Could not open file: " + ex.Message, "Error", MessageBoxButton.OK, MessageBoxImage.Error); }
            });
            actionsRow.Children.Add(openBtn);

            var revealBtn = CreateMiniActionPill("ðŸ“ Explorer", Color.FromArgb(30, 255, 255, 255), _currentTextSecondary, () =>
            {
                try
                {
                    if (File.Exists(item.FilePath)) Process.Start("explorer.exe", string.Format("/select,\"{0}\"", item.FilePath));
                    else Process.Start("explorer.exe", _downloadDir);
                }
                catch { }
            });
            actionsRow.Children.Add(revealBtn);

            var copyPathBtn = CreateMiniActionPill("ðŸ“‹ Copy Path", Color.FromArgb(30, 255, 255, 255), _currentTextSecondary, () =>
            {
                try
                {
                    Clipboard.SetText(item.FilePath);
                    ShowCompletionToast("Copied to Clipboard!", "Full file path copied to clipboard", item.FileName, item.FileSize, item.FilePath);
                }
                catch { }
            });
            actionsRow.Children.Add(copyPathBtn);

            details.Children.Add(actionsRow);
            dock.Children.Add(details);
            card.Child = dock;

            card.MouseEnter += (s, e) =>
            {
                card.BorderBrush = new SolidColorBrush(Color.FromArgb(160, 58, 228, 204));
                card.Background = new SolidColorBrush(_isDarkMode ? Color.FromArgb(210, 26, 40, 60) : Color.FromRgb(245, 250, 255));
            };
            card.MouseLeave += (s, e) =>
            {
                card.BorderBrush = new SolidColorBrush(_isDarkMode ? Color.FromArgb(50, 58, 228, 204) : Color.FromArgb(80, 200, 210, 220));
                card.Background = new SolidColorBrush(_isDarkMode ? Color.FromArgb(170, 20, 32, 48) : Color.FromArgb(220, 255, 255, 255));
            };

            card.MouseLeftButtonUp += (s, e) =>
            {
                if (e.ClickCount == 2)
                {
                    try { if (File.Exists(item.FilePath)) Process.Start(new ProcessStartInfo(item.FilePath) { UseShellExecute = true }); } catch { }
                }
            };

            return card;
        }

        private static Border CreateMiniActionPill(string label, Color bg, Color fg, Action onClick)
        {
            var pill = new Border
            {
                CornerRadius = new CornerRadius(6),
                Background = new SolidColorBrush(bg),
                Padding = new Thickness(8, 3, 8, 3),
                Margin = new Thickness(0, 0, 8, 0),
                Cursor = Cursors.Hand
            };
            pill.Child = new TextBlock
            {
                Text = label,
                FontSize = 11,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(fg)
            };
            pill.MouseLeftButtonUp += (s, e) =>
            {
                e.Handled = true;
                onClick();
            };
            return pill;
        }

        private static void GetFileTypeVisuals(string ext, out string glyph, out Color c1, out Color c2)
        {
            switch (ext)
            {
                case ".png":
                case ".jpg":
                case ".jpeg":
                case ".webp":
                case ".gif":
                case ".bmp":
                case ".svg":
                    glyph = "ðŸ–¼ï¸";
                    c1 = Color.FromRgb(16, 185, 129);
                    c2 = Color.FromRgb(59, 130, 246);
                    break;
                case ".mp4":
                case ".mkv":
                case ".mov":
                case ".avi":
                case ".webm":
                    glyph = "ðŸŽ¬";
                    c1 = Color.FromRgb(139, 92, 246);
                    c2 = Color.FromRgb(236, 72, 153);
                    break;
                case ".mp3":
                case ".wav":
                case ".flac":
                case ".m4a":
                case ".aac":
                case ".ogg":
                    glyph = "ðŸŽµ";
                    c1 = Color.FromRgb(244, 63, 94);
                    c2 = Color.FromRgb(251, 146, 60);
                    break;
                case ".pdf":
                case ".doc":
                case ".docx":
                case ".txt":
                case ".xlsx":
                case ".pptx":
                case ".md":
                    glyph = "ðŸ“„";
                    c1 = Color.FromRgb(59, 130, 246);
                    c2 = Color.FromRgb(99, 102, 241);
                    break;
                case ".zip":
                case ".rar":
                case ".7z":
                case ".tar":
                case ".gz":
                    glyph = "ðŸ“¦";
                    c1 = Color.FromRgb(245, 158, 11);
                    c2 = Color.FromRgb(234, 88, 12);
                    break;
                default:
                    glyph = "âš¡";
                    c1 = Color.FromRgb(58, 228, 204);
                    c2 = Color.FromRgb(172, 92, 246);
                    break;
            }
        }

        [STAThread]
        public static void Main()
        {
            AppDomain.CurrentDomain.UnhandledException += (s, e) =>
            {
                try
                {
                    string dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "BeamDrop");
                    if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);
                    File.AppendAllText(
                        Path.Combine(dir, "crash.log"),
                        string.Format("[{0}] UnhandledException: {1}\n", DateTime.Now, e.ExceptionObject)
                    );
                }
                catch { }
            };

            TaskScheduler.UnobservedTaskException += (s, e) =>
            {
                try { e.SetObserved(); } catch { }
            };

            var app = new Application();
            app.DispatcherUnhandledException += (s, e) =>
            {
                try
                {
                    string dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "BeamDrop");
                    if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);
                    File.AppendAllText(
                        Path.Combine(dir, "crash.log"),
                        string.Format("[{0}] DispatcherUnhandledException: {1}\n", DateTime.Now, e.Exception)
                    );
                }
                catch { }
                e.Handled = true; // Prevents process termination
            };

            app.Run(new MainWindow());
        }
    }
}