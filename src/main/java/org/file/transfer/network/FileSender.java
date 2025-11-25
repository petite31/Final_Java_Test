package org.file.transfer.network;

import org.file.transfer.controller.MainController;
import org.file.transfer.model.FilePacket;

import java.io.*;
import java.net.*;

public class FileSender {
    private final TransferManager manager;
    private final MainController controller;
    private final DatagramSocket socket;

    // NHẬN TransferManager → LẤY CONTROLLER TỪ ĐÓ
    public FileSender(TransferManager manager) throws SocketException {
        this.manager = manager;
        this.controller = manager.getController();
        this.socket = new DatagramSocket(); // socket riêng để gửi
        System.out.println("[DEBUG] FileSender: Khởi tạo thành công");
    }

    public boolean testConnection(String ip, int port) {
        try (DatagramSocket temp = new DatagramSocket()) {
            temp.setSoTimeout(3000);
            String ping = "PING_FROM_P2P_APP_" + System.currentTimeMillis();
            DatagramPacket packet = new DatagramPacket(ping.getBytes(), ping.length(),
                    InetAddress.getByName(ip), port);
            temp.send(packet);

            byte[] buf = new byte[256];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            temp.receive(resp);
            String reply = new String(resp.getData(), 0, resp.getLength());
            return reply.startsWith("PONG_FROM_P2P_APP");
        } catch (Exception e) {
            System.out.println("[DEBUG] Test connection failed: " + e.getMessage());
            return false;
        }
    }

    public void sendFile(File file, String ip, int port) {
        // Code gửi file của bạn (giữ nguyên)
        controller.updateStatus("Đang gửi " + file.getName() + "...");
        // ... phần còn lại
    }

    public void sendAck(int packetId, InetAddress address, int port) {
        try {
            byte[] ack = ("ACK_" + packetId).getBytes();
            DatagramPacket packet = new DatagramPacket(ack, ack.length, address, port);
            socket.send(packet);
        } catch (Exception e) {
            System.out.println("[DEBUG] Gửi ACK lỗi: " + e.getMessage());
        }
    }

    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}