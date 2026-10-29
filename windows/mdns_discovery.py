"""
Cross-Platform mDNS (Multicast DNS) Peer Discovery Service for BeamDrop.
Operates on standard mDNS multicast address 224.0.0.251:5353 for service type _beamdrop._tcp.local.
Enables instant, zero-configuration peer discovery between Windows and Android BeamDrop devices.
"""

import socket
import struct
import threading
import time
import platform

MDNS_GROUP = "224.0.0.251"
MDNS_PORT = 5353
SERVICE_TYPE = "_beamdrop._tcp.local."

try:
    from zeroconf import Zeroconf, ServiceInfo, ServiceBrowser
    HAS_ZEROCONF = True
except ImportError:
    HAS_ZEROCONF = False

class MDNSDiscoveryService:
    def __init__(self, device_name, device_id, local_ip, tcp_port, on_peer_found, on_peer_lost=None):
        self.device_name = device_name
        self.device_id = device_id
        self.local_ip = local_ip
        self.tcp_port = tcp_port
        self.on_peer_found = on_peer_found
        self.on_peer_lost = on_peer_lost

        self.running = False
        self.zeroconf = None
        self.service_info = None
        self.browser = None

        self._fallback_socket = None
        self._fallback_threads = []

    def start(self):
        self.running = True
        if HAS_ZEROCONF:
            self._start_zeroconf()
        else:
            print("[mDNS] python-zeroconf not installed. Using pure-Python mDNS socket engine.")
            self._start_pure_socket_mdns()

    def stop(self):
        self.running = False
        if self.zeroconf:
            try:
                if self.service_info:
                    self.zeroconf.unregister_service(self.service_info)
                if self.browser:
                    self.browser.cancel()
                self.zeroconf.close()
            except Exception:
                pass
            self.zeroconf = None

        if self._fallback_socket:
            try:
                self._fallback_socket.close()
            except Exception:
                pass

    # ==========================================
    # Implementation A: zeroconf (Standard RFC 6762)
    # ==========================================
    def _start_zeroconf(self):
        try:
            self.zeroconf = Zeroconf()
            clean_name = self.device_name.replace(" ", "_")
            service_name = f"BeamDrop-{clean_name}-{self.device_id}.{SERVICE_TYPE}"

            desc = {
                "deviceName": self.device_name,
                "deviceType": "COMPUTER",
                "id": self.device_id,
                "port": str(self.tcp_port)
            }

            self.service_info = ServiceInfo(
                SERVICE_TYPE,
                service_name,
                addresses=[socket.inet_aton(self.local_ip)],
                port=self.tcp_port,
                properties=desc,
                server=f"{socket.gethostname()}.local."
            )

            self.zeroconf.register_service(self.service_info)
            print(f"[mDNS] Registered service: {service_name}")

            # Start browsing for other BeamDrop devices (Android, etc.)
            class Listener:
                def __init__(self, outer):
                    self.outer = outer

                def add_service(self, zc, type_, name):
                    info = zc.get_service_info(type_, name)
                    if info and info.addresses:
                        ip = socket.inet_ntoa(info.addresses[0])
                        if ip != self.outer.local_ip:
                            clean = name.replace(f".{SERVICE_TYPE}", "").removeprefix("BeamDrop-").replace("_", " ")
                            self.outer.on_peer_found(name=clean, ip=ip, port=info.port, device_type="PHONE")

                def update_service(self, zc, type_, name):
                    self.add_service(zc, type_, name)

                def remove_service(self, zc, type_, name):
                    pass

            self.browser = ServiceBrowser(self.zeroconf, SERVICE_TYPE, Listener(self))
        except Exception as e:
            print(f"[mDNS] zeroconf error: {e}, falling back to socket engine")
            self._start_pure_socket_mdns()

    # ==========================================
    # Implementation B: Pure-Socket Multicast Engine
    # ==========================================
    def _start_pure_socket_mdns(self):
        """Standard library fallback listening on 224.0.0.251:5353."""
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

            # Bind to mDNS port
            if platform.system() == "Windows":
                sock.bind(("", MDNS_PORT))
            else:
                sock.bind((MDNS_GROUP, MDNS_PORT))

            # Join mDNS multicast group
            mreq = struct.pack("4sl", socket.inet_aton(MDNS_GROUP), socket.INADDR_ANY)
            sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)
            self._fallback_socket = sock

            t_recv = threading.Thread(target=self._pure_socket_listen, daemon=True)
            t_send = threading.Thread(target=self._pure_socket_announce_loop, daemon=True)
            self._fallback_threads = [t_recv, t_send]
            t_recv.start()
            t_send.start()
        except Exception as e:
            print(f"[mDNS] Socket bind warning: {e}. UDP fallback beacon will maintain discovery.")

    def _pure_socket_listen(self):
        while self.running and self._fallback_socket:
            try:
                data, addr = self._fallback_socket.recvfrom(4096)
                ip = addr[0]
                if ip == self.local_ip or ip.startswith("127."):
                    continue

                # Search payload for BeamDrop identifier
                text = data.decode("utf-8", errors="ignore")
                if "BeamDrop" in text or "_beamdrop._tcp" in text:
                    peer_name = "Android Peer"
                    if "BeamDrop-" in text:
                        part = text.split("BeamDrop-")[1].split(".")[0].replace("_", " ")
                        if part:
                            peer_name = part

                    self.on_peer_found(name=peer_name, ip=ip, port=self.tcp_port, device_type="PHONE")
            except Exception:
                if not self.running:
                    break

    def _pure_socket_announce_loop(self):
        """Periodically announces BeamDrop service on multicast group 224.0.0.251:5353."""
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 255)

        clean_name = self.device_name.replace(" ", "_")
        announcement = f"BeamDrop-Windows_PC-{self.device_id}._beamdrop._tcp.local.|{self.local_ip}|{self.tcp_port}|COMPUTER"
        data = announcement.encode("utf-8")

        while self.running:
            try:
                sock.sendto(data, (MDNS_GROUP, MDNS_PORT))
            except Exception:
                pass
            time.sleep(3.0)
        sock.close()
