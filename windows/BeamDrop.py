#!/usr/bin/env python3
"""
BeamDrop for Windows
Native desktop peer-to-peer file sharing client for Windows (and Mac/Linux).
Seamlessly discovers and transfers files with BeamDrop for Android at full Wi-Fi speeds.
Includes:
  - Multi-layer Peer-to-Peer Discovery (mDNS + UDP 8989 Beacon + TCP 8988 Handshake)
  - 60 FPS Glowing Fluid Aura Ripple on the File-Transfer Button when a peer is discovered
  - Discreet, Self-Dismissing Progress Indicator that clears immediately upon transfer completion
"""

import os
import sys
import time
import math
import socket
import threading
import platform
from pathlib import Path

try:
    import tkinter as tk
    from tkinter import ttk, filedialog, messagebox
except ImportError:
    print("Tkinter is required. Please install python3-tk or ensure Tkinter is checked in Python Windows installer.")
    sys.exit(1)

try:
    from mdns_discovery import MDNSDiscoveryService
except ImportError:
    try:
        from windows.mdns_discovery import MDNSDiscoveryService
    except ImportError:
        MDNSDiscoveryService = None

TCP_PORT = 8988
UDP_PORT = 8989
MAGIC_HEADER = "BEAMDROP_V1"

# Cyber & Fluid Aurora Theme Palette (Identical to Android Jetpack Compose Theme)
BG_COLOR = "#090D14"
CARD_COLOR = "#141B2D"
CARD_HOVER = "#1E2742"
CYAN = "#00F5D4"
MINT = "#5EEAD4"
PURPLE = "#9D4EDD"
AMBER = "#FFB703"
TEXT_PRIMARY = "#F1F5F9"
TEXT_SEC = "#94A3B8"
BORDER_COLOR = "#222C42"


def get_local_ip():
    """Detect the active local Wi-Fi / LAN IP address."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip


class BeamDropWindowsApp:
    def __init__(self, root):
        self.root = root
        self.root.title("BeamDrop — Universal File Sharing")
        self.root.geometry("660x780")
        self.root.configure(bg=BG_COLOR)
        self.root.minsize(560, 680)

        self.local_ip = get_local_ip()
        self.device_name = platform.node() or "Windows-PC"
        self.device_id = f"win_{abs(hash(self.local_ip + self.device_name)) % 1000000}"

        self.discovered_peers = {}  # ip -> {name, port, last_seen}
        self.selected_files = []
        self.selected_peer_ip = None

        self.running = True
        self.wave_phase = 0.0
        self.is_transferring = False
        self.active_cancel_requested = False

        downloads_dir = Path.home() / "Downloads" / "BeamDrop"
        downloads_dir.mkdir(parents=True, exist_ok=True)
        self.downloads_dir = downloads_dir

        self._build_ui()
        self._start_networking()
        self._animate_aurora()

    def _build_ui(self):
        # Header / Aurora Canvas
        self.aurora_canvas = tk.Canvas(self.root, height=120, bg=BG_COLOR, highlightthickness=0)
        self.aurora_canvas.pack(fill=tk.X, padx=0, pady=0)

        # Main container
        self.container = tk.Frame(self.root, bg=BG_COLOR)
        self.container.pack(fill=tk.BOTH, expand=True, padx=20, pady=(0, 16))

        # Status Bar Card
        status_card = tk.Frame(self.container, bg=CARD_COLOR, highlightbackground=BORDER_COLOR, highlightthickness=1)
        status_card.pack(fill=tk.X, pady=(0, 12))

        octet = self.local_ip.rsplit(".", 1)[-1] if "." in self.local_ip else "100"
        lbl_title = tk.Label(
            status_card,
            text=f"⚡ {self.device_name.upper()}  (#{octet})",
            font=("Segoe UI", 12, "bold"),
            fg=CYAN,
            bg=CARD_COLOR
        )
        lbl_title.pack(anchor="w", padx=14, pady=(10, 2))

        lbl_ip = tk.Label(
            status_card,
            text=f"Local Wi-Fi IP: {self.local_ip}  •  TCP: {TCP_PORT} / UDP: {UDP_PORT}  •  Auto-Save: Downloads\\BeamDrop",
            font=("Segoe UI", 9),
            fg=TEXT_SEC,
            bg=CARD_COLOR
        )
        lbl_ip.pack(anchor="w", padx=14, pady=(0, 10))

        # Discovered Android & Windows Peers Card
        peers_frame = tk.Frame(self.container, bg=CARD_COLOR, highlightbackground=BORDER_COLOR, highlightthickness=1)
        peers_frame.pack(fill=tk.BOTH, expand=True, pady=(0, 12))

        p_header = tk.Frame(peers_frame, bg=CARD_COLOR)
        p_header.pack(fill=tk.X, padx=14, pady=(12, 6))

        tk.Label(
            p_header,
            text="NEARBY DEVICES (P2P DISCOVERY)",
            font=("Segoe UI", 10, "bold"),
            fg=TEXT_SEC,
            bg=CARD_COLOR
        ).pack(side=tk.LEFT)
        btn_rescan = tk.Button(
            p_header,
            text="↻ Scan Wi-Fi",
            font=("Segoe UI", 9, "bold"),
            fg=CYAN,
            bg=CARD_COLOR,
            activebackground=CARD_HOVER,
            activeforeground=CYAN,
            bd=0,
            cursor="hand2",
            command=self._rescan_peers
        )
        btn_rescan.pack(side=tk.RIGHT)

        list_frame = tk.Frame(peers_frame, bg=CARD_COLOR)
        list_frame.pack(fill=tk.BOTH, expand=True, padx=14, pady=(0, 12))

        self.peer_listbox = tk.Listbox(
            list_frame,
            font=("Segoe UI", 10),
            bg=BG_COLOR,
            fg=TEXT_PRIMARY,
            selectbackground=CYAN,
            selectforeground=BG_COLOR,
            highlightthickness=1,
            highlightbackground=BORDER_COLOR,
            bd=0,
            activestyle="none",
            height=4
        )
        self.peer_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.peer_listbox.bind("<<ListboxSelect>>", self._on_peer_select)

        # Manual Direct IP Entry
        manual_frame = tk.Frame(self.container, bg=CARD_COLOR, highlightbackground=BORDER_COLOR, highlightthickness=1)
        manual_frame.pack(fill=tk.X, pady=(0, 12))

        tk.Label(
            manual_frame,
            text="Direct IP / #Octet:",
            font=("Segoe UI", 9, "bold"),
            fg=TEXT_SEC,
            bg=CARD_COLOR
        ).pack(side=tk.LEFT, padx=12, pady=10)
        self.entry_manual_ip = tk.Entry(
            manual_frame,
            font=("Segoe UI", 10),
            bg=BG_COLOR,
            fg=TEXT_PRIMARY,
            insertbackground=CYAN,
            highlightthickness=1,
            highlightbackground=BORDER_COLOR,
            bd=0
        )
        prefix = self.local_ip.rsplit(".", 1)[0] + "." if "." in self.local_ip else "192.168.1."
        self.entry_manual_ip.insert(0, prefix)
        self.entry_manual_ip.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=6, pady=8)

        btn_add_ip = tk.Button(
            manual_frame,
            text="Connect",
            font=("Segoe UI", 9, "bold"),
            fg=BG_COLOR,
            bg=CYAN,
            activebackground=CYAN,
            activeforeground=BG_COLOR,
            bd=0,
            padx=12,
            pady=4,
            cursor="hand2",
            command=self._add_manual_peer
        )
        btn_add_ip.pack(side=tk.RIGHT, padx=12, pady=8)

        # Staged Files Selection Card (Automatically clears immediately after transfer completes)
        self.files_card = tk.Frame(self.container, bg=CARD_COLOR, highlightbackground=BORDER_COLOR, highlightthickness=1)
        self.files_card.pack(fill=tk.X, pady=(0, 12))

        fc_header = tk.Frame(self.files_card, bg=CARD_COLOR)
        fc_header.pack(fill=tk.X, padx=14, pady=(10, 6))

        tk.Label(
            fc_header,
            text="SELECTION (CLEARS AUTOMATICALLY ON COMPLETION)",
            font=("Segoe UI", 9, "bold"),
            fg=TEXT_SEC,
            bg=CARD_COLOR
        ).pack(side=tk.LEFT)

        btn_clear = tk.Button(
            fc_header,
            text="Clear",
            font=("Segoe UI", 9, "bold"),
            fg="#F87171",
            bg=CARD_COLOR,
            activebackground=CARD_HOVER,
            bd=0,
            cursor="hand2",
            command=self._clear_selected_files
        )
        btn_clear.pack(side=tk.RIGHT, padx=(8, 0))

        btn_choose = tk.Button(
            fc_header,
            text="+ Choose Files...",
            font=("Segoe UI", 9, "bold"),
            fg=AMBER,
            bg=CARD_COLOR,
            activebackground=CARD_HOVER,
            activeforeground=AMBER,
            bd=0,
            cursor="hand2",
            command=self._choose_files
        )
        btn_choose.pack(side=tk.RIGHT)

        self.lbl_selected_files = tk.Label(
            self.files_card,
            text="No files staged. Click '+ Choose Files...' to stage files for beaming.",
            font=("Segoe UI", 9),
            fg=TEXT_SEC,
            bg=CARD_COLOR,
            wraplength=540,
            justify=tk.LEFT
        )
        self.lbl_selected_files.pack(anchor="w", padx=14, pady=(0, 10))

        # File-Transfer Button with 60 FPS Glowing Fluid Aura Ripple Canvas
        self.send_aura_canvas = tk.Canvas(self.container, height=74, bg=BG_COLOR, highlightthickness=0, cursor="hand2")
        self.send_aura_canvas.pack(fill=tk.X, pady=(0, 8))
        self.send_aura_canvas.bind("<Button-1>", lambda e: self._send_files())

        # Discreet, Self-Dismissing Progress Indicator Capsule (Hidden when idle; immediately clears on completion)
        self.discreet_capsule = tk.Frame(
            self.container,
            bg="#101E1B",
            highlightbackground=CYAN,
            highlightthickness=1,
            padx=14,
            pady=10
        )
        cap_top = tk.Frame(self.discreet_capsule, bg="#101E1B")
        cap_top.pack(fill=tk.X, pady=(0, 6))

        self.lbl_capsule_title = tk.Label(
            cap_top,
            text="● Beaming...",
            font=("Segoe UI", 9, "bold"),
            fg=CYAN,
            bg="#101E1B"
        )
        self.lbl_capsule_title.pack(side=tk.LEFT)

        btn_cancel_tx = tk.Button(
            cap_top,
            text="✕",
            font=("Segoe UI", 9, "bold"),
            fg="#F87171",
            bg="#101E1B",
            bd=0,
            cursor="hand2",
            command=self._cancel_active_transfer
        )
        btn_cancel_tx.pack(side=tk.RIGHT, padx=(8, 0))

        self.lbl_capsule_pct = tk.Label(
            cap_top,
            text="0% • 0.0 MB/s",
            font=("Segoe UI", 9, "bold"),
            fg=MINT,
            bg="#101E1B"
        )
        self.lbl_capsule_pct.pack(side=tk.RIGHT)

        self.progress_bar = ttk.Progressbar(self.discreet_capsule, orient="horizontal", mode="determinate")
        self.progress_bar.pack(fill=tk.X)

    def _animate_aurora(self):
        """Renders 60fps fluid aurora header AND glowing fluid aura ripple on the file-transfer button."""
        if not self.running:
            return

        self.wave_phase += 0.06
        w = self.aurora_canvas.winfo_width()
        h = self.aurora_canvas.winfo_height()

        if w > 1 and h > 1:
            self.aurora_canvas.delete("all")
            self.aurora_canvas.create_rectangle(0, 0, w, h, fill=BG_COLOR, outline="")

            for i, color in enumerate([PURPLE, CYAN, AMBER]):
                points = [(0, h)]
                for x in range(0, w + 10, 10):
                    sin_val = math.sin((x / (70.0 + i * 20.0)) + self.wave_phase + i)
                    cos_val = math.cos((x / (110.0 + i * 15.0)) - self.wave_phase * 0.7)
                    y = (h * 0.45) + (sin_val * 20.0) + (cos_val * 12.0) + (i * 11.0)
                    points.append((x, y))
                points.append((w, h))
                self.aurora_canvas.create_polygon(
                    points,
                    fill=color,
                    outline="",
                    stipple="gray25" if i == 0 else ("gray50" if i == 1 else "")
                )

            self.aurora_canvas.create_text(
                w // 2, 40,
                text="BeamDrop",
                font=("Segoe UI", 20, "bold"),
                fill=TEXT_PRIMARY
            )
            self.aurora_canvas.create_text(
                w // 2, 66,
                text="Native Windows Peer-to-Peer Continuity",
                font=("Segoe UI", 9),
                fill=CYAN
            )

        # Render Glowing Fluid Aura Ripple on the File-Transfer Button when a peer is discovered
        bw = self.send_aura_canvas.winfo_width()
        bh = self.send_aura_canvas.winfo_height()
        if bw > 10 and bh > 10:
            self.send_aura_canvas.delete("all")
            has_peers = len(self.discovered_peers) > 0

            if has_peers:
                for wave_idx, col in enumerate([CYAN, PURPLE, AMBER]):
                    phase = ((self.wave_phase * 0.35) + wave_idx * 0.33) % 1.0
                    pad_x = 18 - phase * 16
                    pad_y = 14 - phase * 12
                    width_stroke = max(1, int(3 * (1.0 - phase * 0.5)))
                    self.send_aura_canvas.create_rectangle(
                        pad_x, pad_y, bw - pad_x, bh - pad_y,
                        outline=col,
                        width=width_stroke
                    )

            btn_bg = CYAN if has_peers else "#1E3A3A"
            btn_fg = BG_COLOR if has_peers else TEXT_PRIMARY
            self.send_aura_canvas.create_rectangle(
                18, 14, bw - 18, bh - 14,
                fill=btn_bg,
                outline=MINT,
                width=2
            )
            label = (
                f"🚀 BEAM TO {self.discovered_peers[self.selected_peer_ip]['name'].upper()}"
                if (self.selected_peer_ip and self.selected_peer_ip in self.discovered_peers)
                else ("🚀 BEAM TO DISCOVERED PEER" if has_peers else "SEARCHING FOR NEARBY PEERS...")
            )
            self.send_aura_canvas.create_text(
                bw // 2, bh // 2,
                text=label,
                font=("Segoe UI", 11, "bold"),
                fill=btn_fg
            )

        self.root.after(30, self._animate_aurora)

    def _start_networking(self):
        threading.Thread(target=self._run_tcp_server, daemon=True).start()

        self.mdns_service = None
        if MDNSDiscoveryService:
            try:
                self.mdns_service = MDNSDiscoveryService(
                    device_name=self.device_name,
                    device_id=self.device_id,
                    local_ip=self.local_ip,
                    tcp_port=TCP_PORT,
                    on_peer_found=self._on_mdns_peer_found
                )
                self.mdns_service.start()
            except Exception as e:
                print(f"[mDNS] Init warning: {e}")

        threading.Thread(target=self._run_udp_beacon, daemon=True).start()
        threading.Thread(target=self._run_udp_listener, daemon=True).start()
        threading.Thread(target=self._probe_subnet, daemon=True).start()

    def _on_mdns_peer_found(self, name, ip, port, device_type="PHONE"):
        if ip != self.local_ip and not ip.startswith("127."):
            self.discovered_peers[ip] = {
                "name": name,
                "port": port,
                "last_seen": time.time()
            }
            self.root.after(0, self._refresh_peer_list)

    def _run_tcp_server(self):
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            server.bind(("0.0.0.0", TCP_PORT))
            server.listen(5)
            while self.running:
                client, addr = server.accept()
                threading.Thread(target=self._handle_incoming_transfer, args=(client, addr), daemon=True).start()
        except Exception as e:
            print(f"TCP Server error: {e}")
        finally:
            server.close()

    def _read_utf8_line(self, sock, max_bytes=4096):
        buf = bytearray()
        while len(buf) < max_bytes:
            chunk = sock.recv(1)
            if not chunk:
                return buf.decode("utf-8", errors="ignore") if buf else ""
            b = chunk[0]
            if b == 10:
                break
            if b != 13:
                buf.append(b)
        return buf.decode("utf-8", errors="ignore")

    def _handle_incoming_transfer(self, sock, addr):
        try:
            sock.settimeout(40.0)
            sender_ip = addr[0] if addr else ""

            first_line = self._read_utf8_line(sock).strip()
            if not first_line:
                sock.close()
                return

            if first_line.startswith("BEAMDROP_PING"):
                parts = first_line.split("|")
                peer_id = parts[1] if len(parts) > 1 else ""
                peer_name = parts[2] if len(parts) > 2 else f"Android Device ({sender_ip})"
                pong = f"BEAMDROP_PONG|{self.device_id}|{self.device_name}|COMPUTER\n"
                sock.sendall(pong.encode("utf-8"))
                if peer_id != self.device_id and sender_ip and sender_ip != self.local_ip:
                    self.discovered_peers[sender_ip] = {
                        "name": peer_name,
                        "port": TCP_PORT,
                        "last_seen": time.time()
                    }
                    self.root.after(0, self._refresh_peer_list)
                sock.close()
                return

            if first_line.startswith("BEAMDROP_V1|"):
                h_parts = first_line.split("|")
                sender_name = h_parts[1] if len(h_parts) > 1 else "Android Phone"
                file_count = int(h_parts[2]) if (len(h_parts) > 2 and h_parts[2].isdigit()) else 1
            elif first_line == MAGIC_HEADER:
                sender_name = self._read_utf8_line(sock).strip() or "Android Phone"
                count_line = self._read_utf8_line(sock).strip()
                file_count = int(count_line) if count_line.isdigit() else 1
            else:
                sock.close()
                return

            if sender_ip and sender_ip != self.local_ip:
                self.discovered_peers[sender_ip] = {
                    "name": sender_name,
                    "port": TCP_PORT,
                    "last_seen": time.time()
                }
                self.root.after(0, self._refresh_peer_list)

            self.active_cancel_requested = False
            self.root.after(0, self._show_discreet_progress, f"Receiving from {sender_name}...", 0, 0.0)

            for _ in range(file_count):
                meta_line = self._read_utf8_line(sock).strip()
                if not meta_line:
                    break
                parts = meta_line.split("|")
                if len(parts) < 2:
                    break
                if parts[0] == "FILE" and len(parts) >= 3:
                    file_name = os.path.basename(parts[1])
                    file_size = int(parts[2]) if parts[2].isdigit() else 0
                else:
                    file_name = os.path.basename(parts[0])
                    file_size = int(parts[1]) if parts[1].isdigit() else 0

                save_path = self.downloads_dir / file_name
                bytes_received = 0
                start_time = time.time()

                with open(save_path, "wb") as out_f:
                    while bytes_received < file_size:
                        if self.active_cancel_requested:
                            break
                        chunk_size = min(64 * 1024, file_size - bytes_received)
                        chunk = sock.recv(chunk_size)
                        if not chunk:
                            break
                        out_f.write(chunk)
                        bytes_received += len(chunk)

                        pct = int((bytes_received / file_size) * 100) if file_size > 0 else 100
                        speed = (bytes_received / (1024 * 1024)) / max(0.001, time.time() - start_time)
                        self.root.after(0, self._show_discreet_progress, f"↓ {file_name}", pct, speed)

            try:
                sock.sendall(b"OK\n")
            except Exception:
                pass

            # Discreet indicator immediately self-dismisses upon completion
            self.root.after(0, self._hide_discreet_progress)
        except Exception as e:
            print(f"Error handling incoming transfer: {e}")
            self.root.after(0, self._hide_discreet_progress)
        finally:
            sock.close()

    def _run_udp_beacon(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

        while self.running:
            try:
                beacon_msg = f"BEAMDROP_BEACON|{self.device_id}|{self.device_name}|{self.local_ip}|{TCP_PORT}|COMPUTER"
                data = beacon_msg.encode("utf-8")
                s.sendto(data, ("255.255.255.255", UDP_PORT))

                if "." in self.local_ip:
                    prefix = self.local_ip.rsplit(".", 1)[0]
                    s.sendto(data, (f"{prefix}.255", UDP_PORT))

                for peer_ip in list(self.discovered_peers.keys()):
                    try:
                        s.sendto(data, (peer_ip, UDP_PORT))
                    except Exception:
                        pass
            except Exception:
                pass
            time.sleep(2.0)

    def _run_udp_listener(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.bind(("", UDP_PORT))
            while self.running:
                data, addr = s.recvfrom(2048)
                sender_ip = addr[0] if addr else ""
                msg = data.decode("utf-8", errors="ignore").strip()
                parts = msg.split("|")
                if len(parts) >= 6 and parts[0] == "BEAMDROP_BEACON":
                    peer_id = parts[1]
                    peer_name = parts[2]
                    peer_ip = parts[3] if (parts[3] and parts[3] != "127.0.0.1") else sender_ip
                    peer_port = int(parts[4]) if parts[4].isdigit() else TCP_PORT
                    if peer_ip != self.local_ip and peer_id != self.device_id:
                        self.discovered_peers[peer_ip] = {
                            "name": peer_name,
                            "port": peer_port,
                            "last_seen": time.time()
                        }
                        self.root.after(0, self._refresh_peer_list)
                elif len(parts) == 5 and parts[0] == "BEAMDROP_BEACON":
                    peer_name = parts[1]
                    peer_ip = parts[2] if (parts[2] and parts[2] != "127.0.0.1") else sender_ip
                    peer_port = int(parts[3]) if parts[3].isdigit() else TCP_PORT
                    if peer_ip != self.local_ip and peer_name != self.device_name:
                        self.discovered_peers[peer_ip] = {
                            "name": peer_name,
                            "port": peer_port,
                            "last_seen": time.time()
                        }
                        self.root.after(0, self._refresh_peer_list)
        except Exception as e:
            print(f"UDP Listener error: {e}")

    def _refresh_peer_list(self):
        self.peer_listbox.delete(0, tk.END)
        ips = list(self.discovered_peers.keys())
        for idx, ip in enumerate(ips):
            info = self.discovered_peers[ip]
            octet = ip.rsplit(".", 1)[-1] if "." in ip else "100"
            entry = f"📱 {info['name']}  [#{octet}]  ({ip}:{info['port']})"
            self.peer_listbox.insert(tk.END, entry)
            if self.selected_peer_ip == ip or (not self.selected_peer_ip and idx == 0):
                self.peer_listbox.selection_set(idx)
                self.selected_peer_ip = ip

        if not self.discovered_peers:
            self.peer_listbox.insert(tk.END, "Scanning local Wi-Fi for Android & Windows devices...")

    def _on_peer_select(self, event):
        sel = self.peer_listbox.curselection()
        if sel:
            idx = sel[0]
            ips = list(self.discovered_peers.keys())
            if idx < len(ips):
                self.selected_peer_ip = ips[idx]

    def _verify_and_add_peer(self, host_ip):
        if not host_ip or host_ip == self.local_ip or host_ip.startswith("127."):
            return False
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(0.45)
            sock.connect((host_ip, TCP_PORT))
            ping = f"BEAMDROP_PING|{self.device_id}|{self.device_name}|COMPUTER\n"
            sock.sendall(ping.encode("utf-8"))
            resp = self._read_utf8_line(sock).strip()
            sock.close()
            if resp.startswith("BEAMDROP_PONG|"):
                p = resp.split("|")
                peer_id = p[1] if len(p) > 1 else ""
                peer_name = p[2] if len(p) > 2 else f"Android Device ({host_ip})"
                if peer_id != self.device_id:
                    self.discovered_peers[host_ip] = {
                        "name": peer_name,
                        "port": TCP_PORT,
                        "last_seen": time.time()
                    }
                    self.root.after(0, self._refresh_peer_list)
                    return True
        except Exception:
            pass
        return False

    def _add_manual_peer(self):
        raw = self.entry_manual_ip.get().strip().lstrip("#")
        if not raw:
            return
        ip = (self.local_ip.rsplit(".", 1)[0] + "." + raw) if ("." not in raw and "." in self.local_ip) else raw
        if ip and ip != self.local_ip:
            def worker():
                if not self._verify_and_add_peer(ip):
                    self.discovered_peers[ip] = {
                        "name": f"Android Device ({ip})",
                        "port": TCP_PORT,
                        "last_seen": time.time()
                    }
                    self.root.after(0, self._refresh_peer_list)
                self.selected_peer_ip = ip
            threading.Thread(target=worker, daemon=True).start()

    def _rescan_peers(self):
        threading.Thread(target=self._probe_subnet, daemon=True).start()

    def _probe_subnet(self):
        if "." not in self.local_ip:
            return
        prefix = self.local_ip.rsplit(".", 1)[0]
        my_last = int(self.local_ip.rsplit(".", 1)[1])

        threads = []
        for octet in range(1, 255):
            if octet == my_last:
                continue
            t = threading.Thread(target=self._verify_and_add_peer, args=(f"{prefix}.{octet}",), daemon=True)
            threads.append(t)
            t.start()
            if len(threads) >= 32:
                for th in threads:
                    th.join()
                threads = []

        for th in threads:
            th.join()

    def _choose_files(self):
        files = filedialog.askopenfilenames(title="Select Files to Beam")
        if files:
            self.selected_files = list(files)
            names = [os.path.basename(f) for f in self.selected_files]
            total_size_mb = sum(os.path.getsize(f) for f in self.selected_files) / (1024 * 1024)
            self.lbl_selected_files.config(
                text=f"Selected ({len(self.selected_files)}) • {total_size_mb:.2f} MB:\n" +
                     ", ".join(names[:3]) + ("..." if len(names) > 3 else ""),
                fg=MINT
            )

    def _clear_selected_files(self):
        self.selected_files = []
        self.lbl_selected_files.config(
            text="No files staged. Click '+ Choose Files...' to stage files for beaming.",
            fg=TEXT_SEC
        )

    def _cancel_active_transfer(self):
        self.active_cancel_requested = True
        self._hide_discreet_progress()

    def _show_discreet_progress(self, title, pct, speed_mbps):
        if not self.is_transferring:
            self.is_transferring = True
            self.discreet_capsule.pack(fill=tk.X, pady=(4, 0))
        self.lbl_capsule_title.config(text=f"● {title}")
        self.lbl_capsule_pct.config(text=f"{pct}% • {speed_mbps:.1f} MB/s")
        self.progress_bar["value"] = pct

    def _hide_discreet_progress(self):
        self.is_transferring = False
        self.progress_bar["value"] = 0
        self.discreet_capsule.pack_forget()

    def _send_files(self):
        if not self.selected_files:
            self._choose_files()
            if not self.selected_files:
                return

        if not self.selected_peer_ip and self.discovered_peers:
            self.selected_peer_ip = next(iter(self.discovered_peers.keys()))

        if not self.selected_peer_ip:
            messagebox.showwarning("No Peer Selected", "Please select a discovered device or connect via Direct IP.")
            return

        target_ip = self.selected_peer_ip
        files = list(self.selected_files)
        threading.Thread(target=self._transmit_files_worker, args=(target_ip, files), daemon=True).start()

    def _transmit_files_worker(self, target_ip, files):
        try:
            self.active_cancel_requested = False
            self.root.after(0, self._show_discreet_progress, f"Connecting to {target_ip}...", 0, 0.0)
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(20.0)
            sock.connect((target_ip, TCP_PORT))

            total_bytes = sum(os.path.getsize(f) for f in files)
            header = f"{MAGIC_HEADER}|{self.device_name}|{len(files)}|{total_bytes}\n"
            sock.sendall(header.encode("utf-8"))

            sent_total = 0
            start_time = time.time()

            for file_path in files:
                if self.active_cancel_requested:
                    break
                file_name = os.path.basename(file_path)
                file_size = os.path.getsize(file_path)

                meta = f"FILE|{file_name}|{file_size}|application/octet-stream\n"
                sock.sendall(meta.encode("utf-8"))

                bytes_sent = 0
                with open(file_path, "rb") as f:
                    while bytes_sent < file_size:
                        if self.active_cancel_requested:
                            break
                        chunk = f.read(64 * 1024)
                        if not chunk:
                            break
                        sock.sendall(chunk)
                        bytes_sent += len(chunk)
                        sent_total += len(chunk)

                        pct = int((sent_total / total_bytes) * 100) if total_bytes > 0 else 100
                        speed = (sent_total / (1024 * 1024)) / max(0.001, time.time() - start_time)
                        self.root.after(0, self._show_discreet_progress, f"↑ {file_name}", pct, speed)

            sock.close()
            # Immediately clear staged files and self-dismiss the discreet progress indicator
            self.root.after(0, self._clear_selected_files)
            self.root.after(0, self._hide_discreet_progress)
        except Exception as e:
            self.root.after(0, self._hide_discreet_progress)
            messagebox.showerror("Transfer Error", f"Failed to transfer files: {e}")


def main():
    root = tk.Tk()
    app = BeamDropWindowsApp(root)

    def on_closing():
        app.running = False
        if hasattr(app, 'mdns_service') and app.mdns_service:
            try:
                app.mdns_service.stop()
            except Exception:
                pass
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_closing)
    root.mainloop()


if __name__ == "__main__":
    main()
