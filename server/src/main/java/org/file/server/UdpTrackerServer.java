package org.file.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UdpTrackerServer {
    private static final int PORT = 8889; // Same as client's DiscoveryService port or a specific tracker port

    public void start() {
        try (DatagramSocket serverSocket = new DatagramSocket(PORT)) {
            System.out.println("UDP Tracker Server started on port " + PORT);
            byte[] receiveData = new byte[1024];

            while (!serverSocket.isClosed()) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);

                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                String clientIp = receivePacket.getAddress().getHostAddress();
                int clientPort = receivePacket.getPort();

                // Keep-alive or Register Packet from Client
                // e.g Format: "REGISTER|Username|LocalIP|LocalPort"
                // e.g Format: "HEARTBEAT|Username"

                String[] parts = message.split("\\|");
                if (parts.length >= 2) {
                    String command = parts[0];
                    String username = parts[1];

                    if ("REGISTER".equals(command) && parts.length >= 4) {
                        String localIp = parts[2];
                        int localPort = Integer.parseInt(parts[3]);

                        // Save NAT mapping
                        SessionManager.updateUdpEndpoint(username, clientIp, clientPort, localIp, localPort);

                        // Send ACK
                        String ack = "REGISTER_ACK";
                        byte[] sendData = ack.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                                receivePacket.getAddress(), receivePacket.getPort());
                        serverSocket.send(sendPacket);

                    } else if ("HEARTBEAT".equals(command)) {
                        SessionManager.ClientInfo info = SessionManager.getClient(username);
                        if (info != null) {
                            info.updateSeen();
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
