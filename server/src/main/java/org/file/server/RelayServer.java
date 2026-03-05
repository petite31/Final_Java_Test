package org.file.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class RelayServer {
    private static final int RELAY_PORT = 8890;

    public void start() {
        try (DatagramSocket socket = new DatagramSocket(RELAY_PORT)) {
            System.out.println("UDP Relay Server started on port " + RELAY_PORT);
            byte[] buffer = new byte[8192]; // Match or exceed max UDP payload of client

            while (!socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Very basic implementation:
                // Expect first segment of packet to be
                // "RELAY|<TargetPublicIP>|<TargetPublicPort>|Payload"

                // Note: For a real production app, you might use a Relay Token rather than
                // trusting the IP.
                // e.g "RELAY|token|Data..." -> Lookup token to find recipient IP:Port

                String message = new String(packet.getData(), 0, packet.getLength());
                String[] parts = message.split("\\|", 4);

                if ("RELAY".equals(parts[0]) && parts.length == 4) {
                    String targetIp = parts[1];
                    int targetPort = Integer.parseInt(parts[2]);
                    byte[] payload = parts[3].getBytes();

                    DatagramPacket forwardPacket = new DatagramPacket(payload, payload.length,
                            java.net.InetAddress.getByName(targetIp), targetPort);
                    socket.send(forwardPacket);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
