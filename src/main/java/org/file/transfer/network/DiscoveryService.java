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
    private static final int DISCOVERY_PORT = 8889;
    private static final String BROADCAST_Address = "255.255.255.255";
    private static final int BROADCAST_INTERVAL = 2;
    private static final int PEER_TIMEOUT = 5000;

    private DatagramSocket socket;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private boolean running = false;
    private String deviceName;
    private final int fileTransferPort;

    private BiConsumer<String, String> onPasskeyAccepted;
    private QuadConsumer<String, String, Runnable, Runnable> onConnectionRequested;

    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    @FunctionalInterface
    public interface QuadConsumer<T, U, V, W> {
        void accept(T t, U u, V v, W w);
    }

    private final ObservableList<PeerInfo> activePeers = FXCollections.observableArrayList();
    private final Map<String, PeerInfo> peerMap = new ConcurrentHashMap<>();

    private static DiscoveryService instance;

    private DiscoveryService(int transferPort) {
        this.fileTransferPort = transferPort;
        this.deviceName = System.getProperty("user.name", "Unknown-User");

        try {
            if (org.file.transfer.service.UserSession.getInstance().isLoggedIn()) {
                this.deviceName = org.file.transfer.service.UserSession.getInstance().getUsername();
            } else {
                String saved = org.file.transfer.util.AccountManager.loadUsername();
                if (saved != null)
                    this.deviceName = saved;
            }
        } catch (Exception e) {
        }
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

    public void setOnConnectionRequested(QuadConsumer<String, String, Runnable, Runnable> callback) {
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

    public void sendPasskeyRequest(String targetIp, String passkey) {
        String msg = "PASSKEY_REQUEST|" + deviceName + "|" + passkey + "|" + fileTransferPort;
        sendUdp(msg, targetIp, DISCOVERY_PORT);
    }

    private void broadcastPresence() {
        if (org.file.transfer.service.UserSession.getInstance().isLoggedIn()) {
            this.deviceName = org.file.transfer.service.UserSession.getInstance().getUsername();
        }

        String passkey = org.file.transfer.service.TransferService.getInstance().getMyPasskey();
        if (passkey == null)
            passkey = "DEFAULT";
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
            String requesterName = parts[1];
            String attemptKey = parts[2];
            int requesterTransferPort = Integer.parseInt(parts[3]);

            if (onConnectionRequested != null) {
                Runnable acceptAction = () -> {
                    System.out.println("[Discovery] User approved connection from " + requesterName);
                    String myKey = org.file.transfer.service.TransferService.getInstance().getMyPasskey();
                    if (myKey == null)
                        myKey = "DEFAULT";

                    String reply = "PASSKEY_ACCEPT|" + deviceName + "|" + myKey;
                    sendUdp(reply, senderIp, senderDiscoveryPort);
                };

                Runnable rejectAction = () -> {
                    System.out.println("[Discovery] User refused connection from " + requesterName);
                    String reply = "CONNECTION_REFUSED|" + deviceName;
                    sendUdp(reply, senderIp, senderDiscoveryPort);
                };

                Platform.runLater(
                        () -> onConnectionRequested.accept(senderIp, requesterName, acceptAction, rejectAction));
            } else {
                System.out.println("[Discovery] No connection request handler set, denying " + requesterName);
            }
        } else if ("PASSKEY_ACCEPT".equals(type) && parts.length >= 3) {
            String acceptorName = parts[1];
            String acceptedKey = parts[2];
            System.out.println("[Discovery] Passkey accepted by " + acceptorName);
            System.out.println("[Discovery] Accepted by " + acceptedKey);

            if (onPasskeyAccepted != null) {
                Platform.runLater(() -> onPasskeyAccepted.accept(senderIp, acceptedKey));
            }
        } else if ("CONNECTION_REFUSED".equals(type) && parts.length >= 2) {
            String refuser = parts[1];
            Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Connection Refused");
                alert.setHeaderText(null);
                alert.setContentText(refuser + " refused this connection, please try again.");
                alert.showAndWait();
            });
        }
    }

    private void sendResponse(String targetIp, int targetPort) {
        try {
            String myIp = IP_Port_Managment.getBestLocalIp();
            String passkey = org.file.transfer.service.TransferService.getInstance().getMyPasskey();
            if (passkey == null)
                passkey = "DEFAULT";
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
        String passkey = "";
        for (PeerInfo p : activePeers) {
            if (p.getIp().equals(ip)) {
                passkey = p.getPasskey();
                break;
            }
        }
        sendPasskeyRequest(ip, passkey);
    }

    public String getPeerName(String ip) {
        for (PeerInfo p : activePeers) {
            if (p.getIp().equals(ip)) {
                return p.getName();
            }
        }
        return ip;
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
