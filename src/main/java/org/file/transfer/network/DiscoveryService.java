package org.file.transfer.network;

import org.file.transfer.model.PeerInfo;
import org.file.transfer.service.PasskeyManager;
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
import java.util.function.BiConsumer;

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

    private BiConsumer<String, String> onPasskeyAccepted; // (ip, passkey) -> void
    private TriConsumer<String, String, Runnable> onConnectionRequested; // (senderIp, senderName, acceptCallback) ->
                                                                         // void

    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

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
        if (instance == null)
            throw new IllegalStateException("DiscoveryService not initialized");
        return instance;
    }

    public ObservableList<PeerInfo> getActivePeers() {
        return activePeers;
    }

    public void setOnPasskeyAccepted(BiConsumer<String, String> callback) {
        this.onPasskeyAccepted = callback;
    }

    public void setOnConnectionRequested(TriConsumer<String, String, Runnable> callback) {
        this.onConnectionRequested = callback;
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
            return;
        }

        new Thread(this::listenLoop).start();
        scheduler.scheduleAtFixedRate(this::broadcastPresence, 0, BROADCAST_INTERVAL, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupPeers, 5, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        scheduler.shutdownNow();
    }

    // API to send PASSKEY_REQUEST
    public void sendPasskeyRequest(String targetIp, String passkey) {
        // PASSKEY_REQUEST|<deviceName>|<passkey>|<listeningPort>
        String msg = "PASSKEY_REQUEST|" + deviceName + "|" + passkey + "|" + fileTransferPort;
        sendUdp(msg, targetIp, DISCOVERY_PORT);
    }

    private void broadcastPresence() {
        // FORMAT:
        // DISCOVER_PEER_REQUEST|<deviceName>|<listeningPort>|<mechanism>|<passkey>
        // V3: Mechanism is always UDP.
        String passkey = PasskeyManager.getInstance().isValid() ? PasskeyManager.getInstance().getCurrentPasskey() : "";
        String msg = "DISCOVER_PEER_REQUEST|" + deviceName + "|" + fileTransferPort + "|UDP|" + passkey;
        sendUdp(msg, BROADCAST_Address, DISCOVERY_PORT);
    }

    private void sendUdp(String msg, String ip, int port) {
        try {
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(ip), port);
            if (socket != null && !socket.isClosed())
                socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[2048];
        while (running && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                String senderIp = packet.getAddress().getHostAddress();

                if (isLocalAddress(packet.getAddress()))
                    continue;

                processMessage(message, senderIp, packet.getPort());

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
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void processMessage(String message, String senderIp, int senderDiscoveryPort) {
        String[] parts = message.split("\\|");
        if (parts.length < 2)
            return;

        String type = parts[0];

        if ("DISCOVER_PEER_REQUEST".equals(type) && parts.length >= 3) {
            String peerName = parts[1];
            int peerTransferPort = Integer.parseInt(parts[2]);
            String mech = parts.length > 3 ? parts[3] : "UDP";
            String passkey = parts.length > 4 ? parts[4] : "";

            updatePeer(peerName, senderIp, peerTransferPort, mech, passkey);
            sendResponse(senderIp, senderDiscoveryPort);
        } else if ("DISCOVER_PEER_RESPONSE".equals(type) && parts.length >= 4) {
            String peerName = parts[1];
            int peerTransferPort = Integer.parseInt(parts[3]);
            String mech = parts.length > 4 ? parts[4] : "UDP";
            String passkey = parts.length > 5 ? parts[5] : "";

            updatePeer(peerName, senderIp, peerTransferPort, mech, passkey);
        } else if ("PASSKEY_REQUEST".equals(type) && parts.length >= 4) {
            // PASSKEY_REQUEST|<deviceName>|<passkey>|<listeningPort>
            String requesterName = parts[1];
            String attemptKey = parts[2];
            int requesterTransferPort = Integer.parseInt(parts[3]);

            // V3: Instead of auto-validating, trigger approval callback
            if (onConnectionRequested != null) {
                Runnable acceptAction = () -> {
                    // Validate passkey
                    if (PasskeyManager.getInstance().isValid() &&
                            PasskeyManager.getInstance().getCurrentPasskey().equals(attemptKey)) {

                        System.out.println("[Discovery] User approved connection from " + requesterName);
                        String reply = "PASSKEY_ACCEPT|" + deviceName + "|" + attemptKey;
                        sendUdp(reply, senderIp, senderDiscoveryPort);
                    } else {
                        System.out.println("[Discovery] Passkey mismatch for " + requesterName);
                    }
                };

                // Trigger UI approval
                Platform.runLater(() -> onConnectionRequested.accept(senderIp, requesterName, acceptAction));
            } else {
                System.out.println("[Discovery] No connection request handler set, denying " + requesterName);
            }
        } else if ("PASSKEY_ACCEPT".equals(type) && parts.length >= 3) {
            // PASSKEY_ACCEPT|<deviceName>|<passkey>
            String acceptorName = parts[1];
            String acceptedKey = parts[2];
            System.out.println("[Discovery] Passkey accepted by " + acceptorName);

            if (onPasskeyAccepted != null) {
                Platform.runLater(() -> onPasskeyAccepted.accept(senderIp, acceptedKey));
            }
        }
    }

    private void sendResponse(String targetIp, int targetPort) {
        try {
            String myIp = InetAddress.getLocalHost().getHostAddress();
            String passkey = PasskeyManager.getInstance().isValid() ? PasskeyManager.getInstance().getCurrentPasskey()
                    : "";
            // DISCOVER_PEER_RESPONSE|<deviceName>|<myIp>|<fileTransferPort>|<mechanism>|<passkey>
            String msg = "DISCOVER_PEER_RESPONSE|" + deviceName + "|" + myIp + "|" + fileTransferPort + "|UDP|"
                    + passkey;
            sendUdp(msg, targetIp, targetPort);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePeer(String name, String ip, int port, String mech, String passkey) {
        String key = ip + ":" + port;
        PeerInfo info = peerMap.get(key);

        if (info == null) {
            info = new PeerInfo(name, ip, port);
            info.setMechanism(mech);
            info.setPasskey(passkey);
            peerMap.put(key, info);
            PeerInfo finalInfo = info;
            Platform.runLater(() -> activePeers.add(finalInfo));
        } else {
            info.updateLastSeen();
            info.setPasskey(passkey);
        }
    }

    public void sendConnectionRequest(String ip) {
        // Find the peer info to get the passkey
        String passkey = "";
        for (PeerInfo p : activePeers) {
            if (p.getIp().equals(ip)) {
                passkey = p.getPasskey();
                break;
            }
        }
        sendPasskeyRequest(ip, passkey);
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
