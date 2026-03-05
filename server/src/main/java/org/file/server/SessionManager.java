package org.file.server;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    public static class ClientInfo {
        public String username;
        // From TCP Signaling Server connection
        public String localTcpIp;
        public int localTcpPort;

        // Registered from UDP Tracker
        public String publicUdpIp;
        public int publicUdpPort;

        // Local network IP / Port reported by the client for LAN UDP Punching
        public String localUdpIp;
        public int localUdpPort;

        public long lastSeen;

        public ClientInfo(String username) {
            this.username = username;
            this.lastSeen = System.currentTimeMillis();
        }

        public void updateSeen() {
            this.lastSeen = System.currentTimeMillis();
        }
    }

    // Map Username -> ClientInfo
    private static final Map<String, ClientInfo> onlineClients = new ConcurrentHashMap<>();

    // Map publicUdpIp:publicUdpPort -> Username (Fast lookup for UDP Tracker)
    private static final Map<String, String> udpEndpointToUser = new ConcurrentHashMap<>();

    public static void addClient(String username, String tcpIp, int tcpPort) {
        ClientInfo info = onlineClients.computeIfAbsent(username, k -> new ClientInfo(username));
        info.localTcpIp = tcpIp;
        info.localTcpPort = tcpPort;
        info.updateSeen();
        System.out.println("Client logged in: " + username + " from TCP " + tcpIp);
    }

    public static void removeClient(String username) {
        ClientInfo info = onlineClients.remove(username);
        if (info != null && info.publicUdpIp != null) {
            udpEndpointToUser.remove(info.publicUdpIp + ":" + info.publicUdpPort);
        }
        System.out.println("Client removed: " + username);
    }

    public static ClientInfo getClient(String username) {
        return onlineClients.get(username);
    }

    public static Collection<ClientInfo> getAllClients() {
        return onlineClients.values();
    }

    public static void updateUdpEndpoint(String username, String publicIp, int publicPort, String localIp,
            int localPort) {
        ClientInfo info = onlineClients.get(username);
        if (info != null) {
            info.publicUdpIp = publicIp;
            info.publicUdpPort = publicPort;
            info.localUdpIp = localIp;
            info.localUdpPort = localPort;
            info.updateSeen();

            udpEndpointToUser.put(publicIp + ":" + publicPort, username);
            System.out.println("Mapped UDP for " + username + " -> Public " + publicIp + ":" + publicPort + " / Local "
                    + localIp + ":" + localPort);
        }
    }

    public static String getUsernameByUdpEndpoint(String ip, int port) {
        return udpEndpointToUser.get(ip + ":" + port);
    }
}
