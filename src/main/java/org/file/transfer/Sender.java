package org.file.transfer;

import java.net.*;
import java.util.Scanner;

public class Sender {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        DatagramSocket socket = null;

        try {
            socket = new DatagramSocket();
            System.out.println("--- CHƯƠNG TRÌNH GỬI TIN ---");

            // 1. Nhập thông tin đích
            System.out.print("Nhập Public IP đích: ");
            String targetIp = scanner.nextLine().trim();

            System.out.print("Nhập Public Port đích: ");
            int targetPort = Integer.parseInt(scanner.nextLine().trim());

            InetAddress address = InetAddress.getByName(targetIp);

            while (true) {
                // 2. Nhập và gửi
                System.out.print("\nNhập tin nhắn (hoặc 'exit'): ");
                String msg = scanner.nextLine();
                if ("exit".equalsIgnoreCase(msg)) break;

                byte[] data = msg.getBytes("UTF-8");
                DatagramPacket packet = new DatagramPacket(data, data.length, address, targetPort);

                socket.send(packet);
                System.out.println(">> Đã gửi tới " + targetIp + ":" + targetPort);

                // 3. Chờ phản hồi nhanh (tùy chọn)
                try {
                    socket.setSoTimeout(2000);
                    byte[] buf = new byte[1024];
                    DatagramPacket response = new DatagramPacket(buf, buf.length);
                    socket.receive(response);
                    System.out.println("<< Phản hồi: " + new String(response.getData(), 0, response.getLength()));
                } catch (SocketTimeoutException e) {
                    // Không có phản hồi cũng không sao (UDP mà)
                }
            }

        } catch (Exception e) {
            System.out.println("Lỗi: " + e.getMessage());
        } finally {
            if (socket != null) socket.close();
            scanner.close();
        }
    }
}