package org.file.transfer;


import java.net.*;
import java.util.Scanner;

public class UdpPing {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try {
            // Ask user input
            System.out.print("Nhập IP cần ping UDP: ");
            String targetIp = scanner.nextLine();

            System.out.print("Nhập Port cần ping: ");
            int targetPort = Integer.parseInt(scanner.nextLine());

            System.out.print("Nhập Port local để nhận phản hồi (VD: 1511): ");
            int listenPort = Integer.parseInt(scanner.nextLine());

            // Start listener thread
            startListener(listenPort);

            // Create socket for sending
            DatagramSocket socket = new DatagramSocket();

            InetAddress targetAddr = InetAddress.getByName(targetIp);

            byte[] data = "PING-UDP".getBytes();

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    targetAddr,
                    targetPort
            );

            System.out.println("\nGửi UDP PING tới " + targetIp + ":" + targetPort);
            socket.send(packet);

            System.out.println("Đã gửi. Chờ phản hồi...\n");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Listener
    private static void startListener(int port) {
        new Thread(() -> {
            try {
                DatagramSocket serverSocket = new DatagramSocket(port);
                System.out.println("\n[LISTENING] Đang lắng nghe UDP trên port " + port);

                byte[] buffer = new byte[1024];

                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                    serverSocket.receive(receivePacket);

                    String msg = new String(receivePacket.getData(), 0, receivePacket.getLength());

                    System.out.println("[RECEIVED] Từ " +
                            receivePacket.getAddress().getHostAddress() +
                            ":" + receivePacket.getPort() +
                            " → \"" + msg + "\"");
                }

            } catch (Exception e) {
                System.err.println("Listener error: " + e.getMessage());
            }
        }).start();
    }
}
