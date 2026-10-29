using System;
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
using Microsoft.Win32;
using Path = System.IO.Path;
using SystemPath = System.Windows.Shapes.Path;

[assembly: AssemblyTitle("BeamDrop — Universal Continuity & P2P File Sharing")]
[assembly: AssemblyDescription("Official BeamDrop Continuity Desktop Application for Windows 10/11 — Published by Sajjad Hossain")]
[assembly: AssemblyConfiguration("Release")]
[assembly: AssemblyCompany("Sajjad Hossain")]
[assembly: AssemblyProduct("BeamDrop Continuity")]
[assembly: AssemblyCopyright("Copyright © 2026 Sajjad Hossain. All rights reserved.")]
[assembly: AssemblyTrademark("Sajjad Hossain")]
[assembly: AssemblyCulture("")]
[assembly: ComVisible(false)]
[assembly: Guid("8F9C4B2A-5E1D-4C7A-9B3E-2D6F8A1C5E7B")]
[assembly: AssemblyVersion("2.4.0.0")]
[assembly: AssemblyFileVersion("2.4.0.0")]

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

        public string ToWirePacket(string stateToken)
        {
            string safeSrc = string.IsNullOrEmpty(SourceDeviceName) ? "Windows_PC" : SourceDeviceName.Replace("|", "_");
            string safeDst = string.IsNullOrEmpty(TargetDeviceName) ? "Android_Peer" : TargetDeviceName.Replace("|", "_");
            string safeFile = string.IsNullOrEmpty(FileName) ? "Payload.bin" : FileName.Replace("|", "_");
            double clamped = Math.Max(0.0, Math.Min(1.0, MigrationProgress));
            return string.Format(
                System.Globalization.CultureInfo.InvariantCulture,
                "BEAMDROP_AURA_PATH|{0}|{1}|{2}|{3}|{4:F4}|{5:F2}|{6}",
                SessionId ?? "aura_win", safeSrc, safeDst, safeFile, clamped, SpeedMbps, stateToken);
        }
    }

    public class MainWindow : Window
    {
        private const int TCP_PORT = 8988;
        private const int UDP_PORT = 8989;
        private const string MDNS_GROUP = "224.0.0.251";
        private const int MDNS_PORT = 5353;

        private string _deviceName = Environment.MachineName;
        private string _localIp = "127.0.0.1";
        private string _deviceId;
        private string _downloadDir;

        private readonly ConcurrentDictionary<string, PeerNode> _peers = new ConcurrentDictionary<string, PeerNode>();
        private readonly List<string> _stagedFiles = new List<string>();
        private readonly List<TransferLogEntry> _history = new List<TransferLogEntry>();
        private readonly List<Canvas> _sendButtonAuraCanvases = new List<Canvas>();

        private CancellationTokenSource _cts = new CancellationTokenSource();
        private CancellationTokenSource _activeTransferCts;

        // Settings state
        private int _currentTab = 0; // 0 = Receive, 1 = Send, 2 = Settings
        private int _quickSaveMode = 2; // 0 = Off, 1 = Favorites, 2 = On
        private bool _animationsEnabled = true;
        private bool _autoFinishEnabled = true;

        // Top-level Views
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
        private static readonly Color SidebarBg = Color.FromRgb(8, 16, 14);      // #08100E
        private static readonly Color CardSurface = Color.FromRgb(28, 49, 44);   // #1C312C
        private static readonly Color CardBorder = Color.FromRgb(42, 71, 64);    // #2A4740
        private static readonly Color MintAccent = Color.FromRgb(94, 212, 187);  // #5ED4BB
        private static readonly Color ElectricCyan = Color.FromRgb(0, 245, 212); // #00F5D4
        private static readonly Color CyanGlow = Color.FromRgb(56, 189, 248);    // #38BDF8
        private static readonly Color RadiantPurple = Color.FromRgb(157, 78, 221);// #9D4EDD
        private static readonly Color SolarAmber = Color.FromRgb(255, 183, 3);   // #FFB703
        private static readonly Color TextPrimary = Color.FromRgb(236, 253, 245);
        private static readonly Color TextSecondary = Color.FromRgb(148, 179, 170);

        public MainWindow()
        {
            _downloadDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "BeamDrop");
            Directory.CreateDirectory(_downloadDir);
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
            }
            catch {}
            Width = 1040;
            Height = 680;
            MinWidth = 820;
            MinHeight = 560;
            WindowStartupLocation = WindowStartupLocation.CenterScreen;
            Background = new SolidColorBrush(BgDark);
            Foreground = new SolidColorBrush(TextPrimary);
            FontFamily = new FontFamily("Segoe UI Variable Display, Segoe UI");
            AllowDrop = true;
            Drop += OnWindowFileDrop;

            BuildUserInterface();
            SwitchTab(0);

            CompositionTarget.Rendering += OnRenderFrame;
            StartNetworkEngines();
        }

        private void BuildUserInterface()
        {
            var rootGrid = new Grid();
            rootGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(220) });
            rootGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            // 1. Left Navigation Sidebar (Receive | Send | Settings)
            var sidebar = new Border
            {
                Background = new SolidColorBrush(SidebarBg),
                BorderBrush = new SolidColorBrush(Color.FromArgb(40, 94, 212, 187)),
                BorderThickness = new Thickness(0, 0, 1, 0),
                Padding = new Thickness(18, 28, 18, 20)
            };

            var sideStack = new StackPanel();

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
                CornerRadius = new CornerRadius(11),
                Background = new LinearGradientBrush(
                    Color.FromRgb(0, 245, 212),
                    Color.FromRgb(157, 78, 221),
                    45.0
                ),
                Effect = new DropShadowEffect
                {
                    Color = Color.FromRgb(0, 245, 212),
                    BlurRadius = 14,
                    ShadowDepth = 0,
                    Opacity = 0.55
                }
            };

            var emblemCanvas = new Canvas
            {
                Width = 22,
                Height = 22,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            var dropPath = new SystemPath
            {
                Data = Geometry.Parse("M11,2 C7.5,7 5,10.5 5,14.5 C5,17.8 7.7,20.5 11,20.5 C14.3,20.5 17,17.8 17,14.5 C17,10.5 14.5,7 11,2 Z"),
                Fill = new SolidColorBrush(Color.FromRgb(9, 13, 20)),
                Width = 22,
                Height = 22
            };
            var sparkPath = new SystemPath
            {
                Data = Geometry.Parse("M11,11 C9.8,11 9,11.8 9,13 C9,14.8 11,17 11,17 C11,17 13,14.8 13,13 C13,11.8 12.2,11 11,11 Z"),
                Fill = Brushes.White,
                Width = 22,
                Height = 22
            };
            emblemCanvas.Children.Add(dropPath);
            emblemCanvas.Children.Add(sparkPath);
            logoBadge.Child = emblemCanvas;

            brandHeader.Children.Add(logoBadge);

            var titleCol = new StackPanel
            {
                Margin = new Thickness(10, 0, 0, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            var brandText = new TextBlock
            {
                Text = "BeamDrop",
                FontSize = 20,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(TextPrimary)
            };
            var subText = new TextBlock
            {
                Text = "Continuity",
                FontSize = 10.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Color.FromRgb(0, 245, 212))
            };
            titleCol.Children.Add(brandText);
            titleCol.Children.Add(subText);
            brandHeader.Children.Add(titleCol);

            sideStack.Children.Add(brandHeader);

            _navReceiveBtn = CreateNavButton("Receive", "📡", 0);
            _navSendBtn = CreateNavButton("Send", "➤", 1);
            _navSettingsBtn = CreateNavButton("Settings", "⚙", 2);

            sideStack.Children.Add(_navReceiveBtn);
            sideStack.Children.Add(_navSendBtn);
            sideStack.Children.Add(_navSettingsBtn);

            sideStack.Children.Add(new TextBlock
            {
                Text = "Published by Sajjad Hossain\nv2.4.0 • Verified Windows App",
                FontSize = 10.5,
                Foreground = new SolidColorBrush(TextSecondary),
                Margin = new Thickness(8, 28, 0, 0)
            });

            sidebar.Child = sideStack;
            Grid.SetColumn(sidebar, 0);
            rootGrid.Children.Add(sidebar);

            // 2. Right Content Host
            var contentHost = new Grid();
            _receiveView = BuildReceiveView();
            _sendView = BuildSendView();
            _settingsView = BuildSettingsView();
            _discreetTransferLayer = BuildDiscreetTransferProgressIndicator();
            _completionToastCard = BuildCompletionToast();

            contentHost.Children.Add(_receiveView);
            contentHost.Children.Add(_sendView);
            contentHost.Children.Add(_settingsView);
            contentHost.Children.Add(_discreetTransferLayer);
            contentHost.Children.Add(_completionToastCard);

            Grid.SetColumn(contentHost, 1);
            rootGrid.Children.Add(contentHost);

            Content = rootGrid;
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

            var historyBtn = CreateIconPill("🕒 History", () =>
            {
                if (_history.Count == 0)
                {
                    MessageBox.Show(this, "No transfers recorded in this session yet.", "Transfer History", MessageBoxButton.OK, MessageBoxImage.Information);
                    return;
                }
                var lines = _history.Take(12).Select(h => string.Format("[{0}] {1} • {2} ({3:F1} MB/s)", h.Timestamp, h.Direction, h.FileName, h.SpeedMbps));
                MessageBox.Show(this, string.Join("\n", lines), "Transfer History", MessageBoxButton.OK, MessageBoxImage.Information);
            });
            var infoBtn = CreateIconPill("ℹ Network Info", () =>
            {
                MessageBox.Show(this,
                    string.Format("Device Name: {0}\nDevice ID: {1}\nLocal IPv4: {2}\nTCP Port: {3}\nUDP Discovery: {4}\nDiscovered Peers: {5}\nDownload Folder: {6}",
                        _deviceName, _deviceId, _localIp, TCP_PORT, UDP_PORT, _peers.Count, _downloadDir),
                    "BeamDrop Network Status", MessageBoxButton.OK, MessageBoxImage.Information);
            });
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
            actionsRow.Children.Add(CreateSmallActionButton("↻ Scan", TriggerSubnetScan));
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
                MaxWidth = 560,
                HorizontalAlignment = HorizontalAlignment.Center
            };

            stack.Children.Add(new TextBlock
            {
                Text = "Settings",
                FontSize = 22,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(TextPrimary),
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 24)
            });

            // General Card
            StackPanel generalRows;
            var generalCard = CreateSettingsSectionCard("General", out generalRows);
            AddSettingsBadgeRow(generalRows, "Theme", "Dark (LocalSend)");
            AddSettingsBadgeRow(generalRows, "Color", "BeamDrop Emerald + Fluid Aurora");
            AddSettingsToggleRow(generalRows, "60fps Fluid Aura Ripple & Particle Stream", _animationsEnabled, val => _animationsEnabled = val);
            AddSettingsToggleRow(generalRows, "Discreet Self-Dismissing Progress Indicator", _autoFinishEnabled, val => _autoFinishEnabled = val);
            stack.Children.Add(generalCard);

            // Receive Card
            StackPanel receiveRows;
            var receiveCard = CreateSettingsSectionCard("Receive", out receiveRows);
            AddSettingsActionRow(receiveRows, "Save to folder", _downloadDir, () => Process.Start("explorer.exe", _downloadDir));
            AddSettingsBadgeRow(receiveRows, "Clear staged files immediately after Send", "Enabled");
            stack.Children.Add(receiveCard);

            // Network Card
            StackPanel netRows;
            var netCard = CreateSettingsSectionCard("Network", out netRows);
            AddSettingsBadgeRow(netRows, "Device name", _deviceName);
            AddSettingsBadgeRow(netRows, "Local IPv4", _localIp + " (" + "#" + (_localIp.Split('.').LastOrDefault() ?? "100") + ")");
            AddSettingsBadgeRow(netRows, "Port", TCP_PORT + " (TCP) / " + UDP_PORT + " (UDP)");
            stack.Children.Add(netCard);

            // Publisher & Application Authenticity Card
            StackPanel pubRows;
            var pubCard = CreateSettingsSectionCard("Publisher & Application Identity", out pubRows);
            AddSettingsBadgeRow(pubRows, "Publisher", "Sajjad Hossain");
            AddSettingsBadgeRow(pubRows, "Contact Email", "sajjadtheacid@gmail.com");
            AddSettingsBadgeRow(pubRows, "Product Name", "BeamDrop Continuity for Windows");
            AddSettingsBadgeRow(pubRows, "Version", "2.4.0.0 (64-bit Native WPF)");
            AddSettingsBadgeRow(pubRows, "OS Trust Status", "Verified Windows 10/11 Application Manifest");
            stack.Children.Add(pubCard);

            scroll.Content = stack;
            return scroll;
        }

        /// <summary>
        /// Builds a discreet, self-dismissing floating capsule progress indicator at the bottom center
        /// (identical to Android's DiscreetTransferProgressIndicator) that replaces the notification queue
        /// during active file transfers and clears immediately upon transfer completion.
        /// </summary>
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
            double t = _animClock.Elapsed.TotalSeconds;
            bool hasDiscoveredPeers = !_peers.IsEmpty;
            double timeSinceDiscovery = t - _lastPeerDiscoveredTime;

            // 1. Render Receive view emblem + Fluid Aura Ripple when peers are discovered
            if (_currentTab == 0 && _receiveEmblemCanvas != null)
            {
                _receiveEmblemCanvas.Children.Clear();
                double cx = 115;
                double cy = 115;

                // Fluid Aura Ripple rings triggered by peer discovery
                if (hasDiscoveredPeers && _animationsEnabled)
                {
                    for (int wave = 0; wave < 3; wave++)
                    {
                        double phase = ((t * 0.55) + wave * 0.333) % 1.0;
                        double waveR = 56 + phase * 58;
                        byte waveAlpha = (byte)((1.0 - phase) * (timeSinceDiscovery < 4.0 ? 150 : 95));
                        Color waveColor = wave == 0 ? ElectricCyan : (wave == 1 ? RadiantPurple : SolarAmber);
                        var auraRing = new Ellipse
                        {
                            Width = waveR * 2,
                            Height = waveR * 2,
                            Stroke = new SolidColorBrush(Color.FromArgb(waveAlpha, waveColor.R, waveColor.G, waveColor.B)),
                            StrokeThickness = 2.8 * (1.0 - phase * 0.4)
                        };
                        Canvas.SetLeft(auraRing, cx - waveR);
                        Canvas.SetTop(auraRing, cy - waveR);
                        _receiveEmblemCanvas.Children.Add(auraRing);
                    }
                }

                // Soft breathing halo
                double haloRadius = 74 + Math.Sin(t * 2.4) * 5;
                var halo = new Ellipse
                {
                    Width = haloRadius * 2,
                    Height = haloRadius * 2,
                    Fill = new SolidColorBrush(Color.FromArgb(hasDiscoveredPeers ? (byte)42 : (byte)24, 94, 212, 187))
                };
                Canvas.SetLeft(halo, cx - haloRadius);
                Canvas.SetTop(halo, cy - haloRadius);
                _receiveEmblemCanvas.Children.Add(halo);

                // 8 rotating rounded arc capsules
                int segments = 8;
                double ringRadius = 68;
                double baseDeg = _animationsEnabled ? (t * (hasDiscoveredPeers ? 42.0 : 24.0)) % 360.0 : 0.0;
                for (int i = 0; i < segments; i++)
                {
                    double startDeg = baseDeg + i * (360.0 / segments);
                    double sweepDeg = 27.0;
                    var path = CreateArcSegment(cx, cy, ringRadius, startDeg, sweepDeg, 12.0, MintAccent);
                    _receiveEmblemCanvas.Children.Add(path);
                }

                // Inner solid mint core
                double coreRadius = 38 + (hasDiscoveredPeers ? Math.Sin(t * 4.0) * 2.0 : 0.0);
                var core = new Ellipse
                {
                    Width = coreRadius * 2,
                    Height = coreRadius * 2,
                    Fill = new SolidColorBrush(MintAccent)
                };
                Canvas.SetLeft(core, cx - coreRadius);
                Canvas.SetTop(core, cy - coreRadius);
                _receiveEmblemCanvas.Children.Add(core);
            }

            // 2. Render Glowing Fluid Aura Ripple on every discovered Peer's File-Transfer Button (Send tab)
            if (_currentTab == 1 && _animationsEnabled && _sendButtonAuraCanvases.Count > 0)
            {
                foreach (var auraCanvas in _sendButtonAuraCanvases)
                {
                    auraCanvas.Children.Clear();
                    double cw = auraCanvas.Width;
                    double ch = auraCanvas.Height;
                    double cx = cw * 0.5;
                    double cy = ch * 0.5;

                    // 3 Staggered Expanding Fluid Aura Ripple Waves (Cyan, Purple, Amber)
                    for (int wIdx = 0; wIdx < 3; wIdx++)
                    {
                        double phase = ((t * 0.65) + wIdx * 0.333) % 1.0;
                        double expandX = phase * 18.0;
                        double expandY = phase * 14.0;
                        byte alpha = (byte)((1.0 - phase) * 140);
                        Color c = wIdx == 0 ? ElectricCyan : (wIdx == 1 ? RadiantPurple : SolarAmber);

                        var rippleRect = new Rectangle
                        {
                            Width = Math.Max(10, (cw - 20) + expandX * 2),
                            Height = Math.Max(10, (ch - 16) + expandY * 2),
                            RadiusX = 12 + phase * 6,
                            RadiusY = 12 + phase * 6,
                            Stroke = new SolidColorBrush(Color.FromArgb(alpha, c.R, c.G, c.B)),
                            StrokeThickness = 2.2 * (1.0 - phase * 0.45)
                        };
                        Canvas.SetLeft(rippleRect, cx - rippleRect.Width * 0.5);
                        Canvas.SetTop(rippleRect, cy - rippleRect.Height * 0.5);
                        auraCanvas.Children.Add(rippleRect);
                    }
                }
            }

            // 3. Render 60fps Fluid Aurora Particle Stream + Discreet Pulse Dot during active file transfer
            if (_isTransferring && _oneTransferCanvas != null)
            {
                if (_discreetPulseDot != null)
                {
                    double pulse = 0.5 + 0.5 * Math.Sin(t * 8.0);
                    _discreetPulseDot.Opacity = 0.45 + 0.55 * pulse;
                }

                _oneTransferCanvas.Children.Clear();
                double w = _oneTransferCanvas.ActualWidth > 100 ? _oneTransferCanvas.ActualWidth : 800;
                double h = _oneTransferCanvas.ActualHeight > 100 ? _oneTransferCanvas.ActualHeight : 600;
                double cx = w * 0.5;
                double portalY = h * 0.08;
                double targetY = h - 70;

                // Liquid Top Continuity Aurora Bloom
                for (int layer = 3; layer >= 1; layer--)
                {
                    double rx = (95 + layer * 36) + Math.Sin(t * 4.5 + layer) * 12;
                    double ry = (38 + layer * 18) + Math.Cos(t * 3.8 + layer) * 6;
                    Color layerCol = layer == 1 ? ElectricCyan : (layer == 2 ? RadiantPurple : MintAccent);
                    var blob = new Ellipse
                    {
                        Width = rx * 2,
                        Height = ry * 2,
                        Fill = new SolidColorBrush(Color.FromArgb((byte)(75 / layer), layerCol.R, layerCol.G, layerCol.B))
                    };
                    Canvas.SetLeft(blob, cx - rx);
                    Canvas.SetTop(blob, portalY - ry * 0.6);
                    _oneTransferCanvas.Children.Add(blob);
                }

                // Expanding continuity aura rings
                for (int r = 0; r < 3; r++)
                {
                    double phase = ((t * 0.85) + r * 0.33) % 1.0;
                    double rad = 32 + phase * 220;
                    byte alpha = (byte)((1.0 - phase) * 125);
                    var ring = new Ellipse
                    {
                        Width = rad * 2,
                        Height = rad * 2,
                        Stroke = new SolidColorBrush(Color.FromArgb(alpha, 0, 245, 212)),
                        StrokeThickness = 2.2 * (1.0 - phase * 0.5)
                    };
                    Canvas.SetLeft(ring, cx - rad);
                    Canvas.SetTop(ring, portalY - rad * 0.4);
                    _oneTransferCanvas.Children.Add(ring);
                }

                // 36 High-Velocity Fluid Aurora Particles streaming into the discreet bottom capsule
                for (int p = 0; p < 36; p++)
                {
                    double speed = 1.2 + (p % 6) * 0.22;
                    double progress = ((t * speed) + (p * 0.07)) % 1.0;
                    double py = portalY + (targetY - portalY) * progress;
                    double wave = Math.Sin(progress * Math.PI * 3.0 + t * 5.0 + p) * (48.0 * (1.0 - Math.Abs(progress - 0.5)));
                    double px = cx + wave;
                    double size = 3.8 + (p % 4) * 1.8;
                    byte pAlpha = (byte)(Math.Sin(progress * Math.PI) * 215);

                    var dot = new Ellipse
                    {
                        Width = size,
                        Height = size * 2.2,
                        Fill = new SolidColorBrush(p % 3 == 0
                            ? Color.FromArgb(pAlpha, 0, 245, 212)
                            : (p % 3 == 1 ? Color.FromArgb(pAlpha, 157, 78, 221) : Color.FromArgb(pAlpha, 56, 189, 248)))
                    };
                    Canvas.SetLeft(dot, px - size * 0.5);
                    Canvas.SetTop(dot, py - size);
                    _oneTransferCanvas.Children.Add(dot);
                }

                // 4. Shared Cross-Platform Canvas 'Aura Path' (Cubic Bezier Payload Migration from Source Button to Target Device)
                Point p0 = (_auraPathState.SourceButtonPoint.X > 10 && _auraPathState.SourceButtonPoint.Y > 10)
                    ? _auraPathState.SourceButtonPoint
                    : (_auraPathState.IsOutgoing ? new Point(w * 0.68, h * 0.52) : new Point(cx, portalY + 12));
                Point p3 = (_auraPathState.TargetDevicePoint.X > 10 && _auraPathState.TargetDevicePoint.Y > 10)
                    ? _auraPathState.TargetDevicePoint
                    : (_auraPathState.IsOutgoing ? new Point(cx, portalY + 18) : new Point(cx, h * 0.52));

                double dy = p3.Y - p0.Y;
                double swing = Math.Sin(t * 2.6) * (w * 0.14);
                Point p1 = new Point(Math.Max(30, Math.Min(w - 30, p0.X + (p3.X - p0.X) * 0.25 - 110 + swing * 0.4)), p0.Y + dy * 0.32);
                Point p2 = new Point(Math.Max(30, Math.Min(w - 30, p0.X + (p3.X - p0.X) * 0.75 + 110 - swing * 0.4)), p0.Y + dy * 0.68);

                // Full Cubic Bezier Aura Conduit Track
                var fullFig = new PathFigure { StartPoint = p0, IsClosed = false };
                fullFig.Segments.Add(new BezierSegment(p1, p2, p3, true));
                var fullGeo = new PathGeometry();
                fullGeo.Figures.Add(fullFig);

                var outerGlowPath = new SystemPath
                {
                    Data = fullGeo,
                    Stroke = new LinearGradientBrush(
                        Color.FromArgb(68, 94, 212, 187),
                        Color.FromArgb(75, 157, 78, 221),
                        45.0),
                    StrokeThickness = 20,
                    StrokeStartLineCap = PenLineCap.Round,
                    StrokeEndLineCap = PenLineCap.Round
                };
                _oneTransferCanvas.Children.Add(outerGlowPath);

                // Active Illuminated Migration Sub-Curve (0.0 -> migrationProgress)
                double migProg = Math.Max(0.02, Math.Min(1.0, _auraPathState.IsActive ? _auraPathState.MigrationProgress : _transferProgress));
                var activeFig = new PathFigure { StartPoint = p0, IsClosed = false };
                var polySeg = new PolyLineSegment();
                int steps = 42;
                int maxStep = Math.Max(1, (int)(steps * migProg));
                for (int s = 1; s <= maxStep; s++)
                {
                    double u = Math.Min(migProg, (double)s / steps);
                    polySeg.Points.Add(EvaluateCubicBezier(p0, p1, p2, p3, u));
                }
                activeFig.Segments.Add(polySeg);
                var activeGeo = new PathGeometry();
                activeGeo.Figures.Add(activeFig);

                var activeStrokePath = new SystemPath
                {
                    Data = activeGeo,
                    Stroke = new LinearGradientBrush(ElectricCyan, RadiantPurple, 0.0),
                    StrokeThickness = 6.5,
                    StrokeStartLineCap = PenLineCap.Round,
                    StrokeEndLineCap = PenLineCap.Round
                };
                _oneTransferCanvas.Children.Add(activeStrokePath);

                // Migrating File Payload Orb at B(migProg)
                Point payloadPt = EvaluateCubicBezier(p0, p1, p2, p3, migProg);
                double orbPulse = 1.0 + 0.22 * Math.Sin(t * 7.5);
                double orbR = 24.0 * orbPulse;

                var orbGlow = new Ellipse
                {
                    Width = orbR * 2.2,
                    Height = orbR * 2.2,
                    Fill = new RadialGradientBrush(
                        Color.FromArgb(225, 0, 245, 212),
                        Color.FromArgb(0, 157, 78, 221))
                };
                Canvas.SetLeft(orbGlow, payloadPt.X - orbR * 1.1);
                Canvas.SetTop(orbGlow, payloadPt.Y - orbR * 1.1);
                _oneTransferCanvas.Children.Add(orbGlow);

                var orbCore = new Ellipse
                {
                    Width = 14,
                    Height = 14,
                    Fill = Brushes.White,
                    Stroke = new SolidColorBrush(SolarAmber),
                    StrokeThickness = 2.5
                };
                Canvas.SetLeft(orbCore, payloadPt.X - 7);
                Canvas.SetTop(orbCore, payloadPt.Y - 7);
                _oneTransferCanvas.Children.Add(orbCore);
            }
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
                        await client.ConnectAsync(peer.Ip, peer.Port);
                        using (var stream = client.GetStream())
                        {
                            var header = string.Format("BEAMDROP_V1|{0}|{1}|{2}\n", _deviceName, files.Count, totalBytes);
                            var headerBytes = Encoding.UTF8.GetBytes(header);
                            await stream.WriteAsync(headerBytes, 0, headerBytes.Length, token);

                            byte[] buffer = new byte[65536];
                            for (int i = 0; i < files.Count; i++)
                            {
                                var fi = new FileInfo(files[i]);
                                var fileHeader = string.Format("FILE|{0}|{1}|application/octet-stream\n", fi.Name, fi.Length);
                                var fhBytes = Encoding.UTF8.GetBytes(fileHeader);
                                await stream.WriteAsync(fhBytes, 0, fhBytes.Length, token);

                                using (var fs = new FileStream(fi.FullName, FileMode.Open, FileAccess.Read, FileShare.Read))
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
            TriggerSubnetScan();
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

                            RegisterDiscoveredPeer("peer_" + senderIp, senderName, senderIp, TCP_PORT, "PHONE");
                            ShowTransferOverlay("Receiving from " + senderName, "Incoming file...", 0.0, 0.0);

                            long totalReceived = 0;
                            var sw = Stopwatch.StartNew();
                            byte[] buffer = new byte[65536];
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
                                using (var fs = new FileStream(destPath, FileMode.Create, FileAccess.Write, FileShare.None))
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
                            }

                            byte[] ack = Encoding.UTF8.GetBytes("OK\n");
                            await stream.WriteAsync(ack, 0, ack.Length, token);
                            await stream.FlushAsync(token);

                            // Immediately self-dismiss the discreet progress indicator upon completion
                            HideTransferOverlay();

                            string displayTitle = fileCount > 1 ? string.Format("{0} Files Received!", fileCount) : "File Received!";
                            string displayName = fileCount > 1 ? string.Format("{0} files ({1})", fileCount, FormatFileSize(totalBytes)) : lastFileName;
                            ShowCompletionToast(displayTitle, string.Format("Received from {0}", senderName), displayName, totalBytes, lastDestPath);
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
            UdpClient udp = null;
            try
            {
                udp = new UdpClient();
                udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                udp.Client.Bind(new IPEndPoint(IPAddress.Any, UDP_PORT));
                while (!token.IsCancellationRequested)
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
                                _auraPathState.IsOutgoing = false;
                                ShowTransferOverlay("Aura Sync from " + srcDev, fileName, prog, speed);
                            }
                        }
                    }
                }
            }
            catch { }
            finally
            {
                try { if (udp != null) udp.Close(); } catch { }
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
                        // Unified 6-part beacon understood by Android NetworkDiscoveryManager & Windows
                        string payload = string.Format("BEAMDROP_BEACON|{0}|{1}|{2}|{3}|COMPUTER", _deviceId, _deviceName, _localIp, TCP_PORT);
                        byte[] data = Encoding.UTF8.GetBytes(payload);

                        // 1. Global Broadcast
                        await udp.SendAsync(data, data.Length, new IPEndPoint(IPAddress.Broadcast, UDP_PORT));

                        // 2. Subnet Broadcast (e.g. 192.168.1.255)
                        int lastDot = _localIp.LastIndexOf('.');
                        if (lastDot > 0)
                        {
                            string subnetBcast = _localIp.Substring(0, lastDot + 1) + "255";
                            await udp.SendAsync(data, data.Length, new IPEndPoint(IPAddress.Parse(subnetBcast), UDP_PORT));
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

        private void TriggerSubnetScan()
        {
            Task.Run(async () =>
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
                    if (tasks.Count >= 32)
                    {
                        await Task.WhenAll(tasks);
                        tasks.Clear();
                    }
                }
                if (tasks.Count > 0) await Task.WhenAll(tasks);
            });
        }

        private async Task<bool> ProbeSingleHostAsync(string ip)
        {
            try
            {
                using (var client = new TcpClient())
                {
                    var connectTask = client.ConnectAsync(ip, TCP_PORT);
                    var completed = await Task.WhenAny(connectTask, Task.Delay(320));
                    if (completed != connectTask || !client.Connected) return false;

                    using (var stream = client.GetStream())
                    {
                        stream.ReadTimeout = 500;
                        stream.WriteTimeout = 500;
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

        private static string FormatFileSize(long bytes)
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
                Background = new SolidColorBrush(Color.FromRgb(18, 30, 27)),
                BorderBrush = new SolidColorBrush(CardBorder),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(14),
                Padding = new Thickness(20, 16, 20, 16),
                Margin = new Thickness(0, 0, 0, 18)
            };
            rowsPanel = new StackPanel();
            rowsPanel.Children.Add(new TextBlock
            {
                Text = title,
                FontSize = 15,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(TextPrimary),
                Margin = new Thickness(0, 0, 0, 12)
            });
            card.Child = rowsPanel;
            return card;
        }

        private void AddSettingsBadgeRow(StackPanel parent, string label, string value)
        {
            var dock = new DockPanel { Margin = new Thickness(0, 6, 0, 6) };
            dock.Children.Add(new TextBlock
            {
                Text = label,
                FontSize = 13,
                Foreground = new SolidColorBrush(TextPrimary),
                VerticalAlignment = VerticalAlignment.Center
            });

            var badge = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(CardSurface),
                Padding = new Thickness(14, 6, 14, 6),
                HorizontalAlignment = HorizontalAlignment.Right,
                Child = new TextBlock
                {
                    Text = value,
                    FontSize = 12,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = new SolidColorBrush(TextPrimary)
                }
            };
            DockPanel.SetDock(badge, Dock.Right);
            dock.Children.Add(badge);
            parent.Children.Add(dock);
        }

        private void AddSettingsActionRow(StackPanel parent, string label, string value, Action onClick)
        {
            var dock = new DockPanel { Margin = new Thickness(0, 6, 0, 6) };
            dock.Children.Add(new TextBlock
            {
                Text = label,
                FontSize = 13,
                Foreground = new SolidColorBrush(TextPrimary),
                VerticalAlignment = VerticalAlignment.Center
            });

            var badge = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(CardSurface),
                Padding = new Thickness(14, 6, 14, 6),
                HorizontalAlignment = HorizontalAlignment.Right,
                Cursor = Cursors.Hand,
                Child = new TextBlock
                {
                    Text = value,
                    FontSize = 12,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = new SolidColorBrush(MintAccent)
                }
            };
            badge.MouseLeftButtonUp += (s, e) => onClick();
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
                Foreground = new SolidColorBrush(TextPrimary),
                VerticalAlignment = VerticalAlignment.Center
            });

            var pill = new Border
            {
                Width = 52,
                Height = 26,
                CornerRadius = new CornerRadius(13),
                Background = new SolidColorBrush(state ? MintAccent : CardSurface),
                Padding = new Thickness(3),
                HorizontalAlignment = HorizontalAlignment.Right,
                Cursor = Cursors.Hand
            };
            var knob = new Ellipse
            {
                Width = 20,
                Height = 20,
                Fill = new SolidColorBrush(BgDark),
                HorizontalAlignment = state ? HorizontalAlignment.Right : HorizontalAlignment.Left
            };
            pill.Child = knob;
            pill.MouseLeftButtonUp += (s, e) =>
            {
                state = !state;
                pill.Background = new SolidColorBrush(state ? MintAccent : CardSurface);
                knob.HorizontalAlignment = state ? HorizontalAlignment.Right : HorizontalAlignment.Left;
                onChanged(state);
            };
            DockPanel.SetDock(pill, Dock.Right);
            dock.Children.Add(pill);
            parent.Children.Add(dock);
        }

        [STAThread]
        public static void Main()
        {
            var app = new Application();
            app.Run(new MainWindow());
        }
    }
}
