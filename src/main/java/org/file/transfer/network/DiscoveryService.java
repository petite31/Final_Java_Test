package org.file.transfer.network;

import org.file.transfer.model.PeerInfo;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.net.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscoveryService {
    private static final int DISCOVERY_PORT = 8888;
    private static final String BROADCAST_Address = "255.255.255.255";
    private static final int BROADCAST_INTERVAL = 2; // seconds
    private static final int PEER_TIMEOUT = 5000; // 5 seconds

    private DatagramSocket socket;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private boolean running = false;
    private final String deviceName;
    private final int fileTransferPort; // Port 6969 usually

    private final ObservableList<PeerInfo> activePeers = FXCollections.observableArrayList();
    private final Map<String, PeerInfo> peerMap = new ConcurrentHashMap<>();

    private static DiscoveryService instance;

    private DiscoveryService(int transferPort) {
        this.fileTransferPort = transferPort;
        this.deviceName = System.getProperty("user.name", "Unknown-User");
    }

    public static synchronized DiscoveryService getInstance(int transferPort) {
        if (instance == null) {
            instance = new DiscoveryService(transferPort);
        }
        return instance;
    }

    public static synchronized DiscoveryService getInstance() {
        // Fallback if accessed before init, though ideally should be init first
        if (instance == null)
            throw new IllegalStateException("DiscoveryService not initialized");
        return instance;
    }

    public ObservableList<PeerInfo> getActivePeers() {
        return activePeers;
    }

    public void start() {
        if (running)
            return;
        running = true;

        try {
            socket = new DatagramSocket(DISCOVERY_PORT);
            socket.setBroadcast(true);
            System.out.println("[Discovery] Listening on port " + DISCOVERY_PORT);
        } catch (SocketException e) {
            System.err.println("[Discovery] Failed to bind port " + DISCOVERY_PORT + ": " + e.getMessage());
            // Fallback or handle error? For now just return, maybe UI should show error
            // Logic might continue if we can't listen but can broadcast? No, usually
            // symmetric.
            return;
        }

        // Start Listening Thread
        new Thread(this::listenLoop).start();

        scheduler.scheduleAtFixedRate(this::broadcastPresence, 0, BROADCAST_INTERVAL, TimeUnit.SECONDS);

        // Start Cleanup Task (remove timed out peers)
        scheduler.scheduleAtFixedRate(this::cleanupPeers, 5, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        scheduler.shutdownNow();
    }

    private void broadcastPresence() {
        try {
            // FORMAT: DISCOVER_PEER_REQUEST|<deviceName>|<listeningPort>
            String msg = "DISCOVER_PEER_REQUEST|" + deviceName + "|" + fileTransferPort;
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(BROADCAST_Address),
                    DISCOVERY_PORT);
            if (socket != null && !socket.isClosed())
                socket.send(packet);
        } catch (Exception e) {
            System.err.println("[Discovery] Broadcast failed: " + e.getMessage());
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[1024];
        while (running && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                String senderIp = packet.getAddress().getHostAddress();

                // Ignore self
                if (isLocalAddress(packet.getAddress()))
                    continue;

                processMessage(message, senderIp, packet.getPort()); // Port here is source port of UDP packet, not the
                                                                     // transfer port

            } catch (SocketException e) {
                if (running)
                    System.err.println("[Discovery] Socket error: " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isLocalAddress(InetAddress addr) {
        try {
            // Simple check if it's one of local interfaces
            // This is naive. Better to check if senderIp == localIp
            // For P2P on same machine, we might want to allow 127.0.0.1 if testing?
            // User said "LAN Discovery", usually implies different machines.
            // But for testing on localhost, we might want to allow it IF the port is
            // different?
            // But Discovery uses fixed port 8888.
            // Same machine running 2 instances cannot bind 8888 twice.
            // So actually, to test this strictly on one machine, we'd need different
            // discovery ports or multicast.
            // However, requirements say "Listen on a dedicated discovery UDP port (e.g.,
            // 8888)".
            // This implies one instance per machine is the standard deployment.
            // I will assume standard LAN deployment.

            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void processMessage(String message, String senderIp, int senderDiscoveryPort) {
        String[] parts = message.split("\\|");
        if (parts.length < 3)
            return;

        String type = parts[0];
        String peerName = parts[1];

        // Handle Request
        if ("DISCOVER_PEER_REQUEST".equals(type)) {
            int peerTransferPort = Integer.parseInt(parts[2]);
            updatePeer(peerName, senderIp, peerTransferPort);

            // Respond
            sendResponse(senderIp, senderDiscoveryPort);
        }
        // Handle Response
        else if ("DISCOVER_PEER_RESPONSE".equals(type)) {
            // FORMAT: DISCOVER_PEER_RESPONSE|<deviceName>|<peerIP>|<port>
            // Wait, the sender IP is already known from packet. The payload IP might be
            // redundant or useful if behind NAT?
            // Logic says: Respond with DISCOVER_PEER_RESPONSE|<deviceName>|<peerIP>|<port>
            // The <peerIP> in response is likely 'MY IP'.
            if (parts.length >= 4) {
                int peerTransferPort = Integer.parseInt(parts[3]);
                updatePeer(peerName, senderIp, peerTransferPort);
            }
        }
    }

    private void sendResponse(String targetIp, int targetPort) {
        try {
            // FORMAT: DISCOVER_PEER_RESPONSE|<deviceName>|<peerIP>|<port>
            // We can just use our local IP or leave it empty if receiver can detect it.
            // Let's try to detect our IP.
            String myIp = InetAddress.getLocalHost().getHostAddress();
            String msg = "DISCOVER_PEER_RESPONSE|" + deviceName + "|" + myIp + "|" + fileTransferPort;
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(targetIp), targetPort);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePeer(String name, String ip, int port) {
        String key = ip + ":" + port;
        PeerInfo info = peerMap.get(key);

        if (info == null) {
            info = new PeerInfo(name, ip, port);
            peerMap.put(key, info);
            PeerInfo finalInfo = info;
            Platform.runLater(() -> activePeers.add(finalInfo));
        } else {
            info.updateLastSeen();
        }
    }

    private void cleanupPeers() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PeerInfo>> it = peerMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PeerInfo> entry = it.next();
            if (now - entry.getValue().getLastSeen() > PEER_TIMEOUT) {
                PeerInfo removed = entry.getValue();
                it.remove();
                Platform.runLater(() -> activePeers.remove(removed));
            }
        }
    }
}
