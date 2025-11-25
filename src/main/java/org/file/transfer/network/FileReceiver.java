package org.file.transfer.network;

import org.file.transfer.controller.MainController;
import org.file.transfer.model.FilePacket;

import java.io.*;
import java.net.*;
import java.nio.file.*;

public class FileReceiver {
    private final MainController controller;
    private final DatagramSocket socket;
    private final FileSender fileSender;

    public FileReceiver(TransferManager manager, int requestedPort) throws SocketException {
        this.manager = manager;
        this.controller = manager.getController();

        // Tạo socket với port random hoặc cố định
        this.socket = requestedPort == 0
                ? new DatagramSocket(0)
                : new DatagramSocket(requestedPort);

        int actualPort = socket.getLocalPort();   // ← LẤY CỔNG THẬT Ở ĐÂY

        System.out.println("[DEBUG] FileReceiver: Đã bind vào cổng " + actualPort);

        // GỌI NGAY ĐỂ HIỂN THỊ LÊN GIAO DIỆN
        controller.setListeningPort(actualPort);
        controller.updateStatus("Đang lắng nghe trên cổng " + actualPort + "...");

        this.fileSender = new FileSender(manager);
        startReceivingLoop();
    }

    // PHƯƠNG THỨC BẮT BUỘC PHẢI CÓ
    public int getPort() {
        return socket.getLocalPort();
    }

    // PHƯƠNG THỨC BẮT BUỘC PHẢI CÓ
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            System.out.println("[DEBUG] FileReceiver: Đã đóng cổng " + getPort());
        }
    }

    private void startReceivingLoop() {
        new Thread(() -> {
            byte[] buffer = new byte[70000];
            while (!socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    // Xử lý PING
                    if (packet.getLength() < 200) {
                        String msg = new String(packet.getData(), 0, packet.getLength());
                        if (msg.startsWith("PING_FROM_P2P_APP")) {
                            String pong = "PONG_FROM_P2P_APP_OK";
                            DatagramPacket reply = new DatagramPacket(
                                    pong.getBytes(), pong.length(),
                                    packet.getAddress(), packet.getPort()
                            );
                            socket.send(reply);
                            continue;
                        }
                    }

                    // Xử lý file packet
                    ObjectInputStream ois = new ObjectInputStream(
                            new ByteArrayInputStream(packet.getData(), 0, packet.getLength())
                    );
                    FilePacket fp = (FilePacket) ois.readObject();

                    fileSender.sendAck(fp.packetId(), packet.getAddress(), packet.getPort());
                    handleFilePacket(fp, packet.getAddress());

                } catch (SocketException e) {
                    if (socket.isClosed()) break;
                } catch (Exception e) {
                    System.out.println("[DEBUG] Lỗi nhận packet: " + e.getMessage());
                }
            }
        }).start();
    }

    private void handleFilePacket(FilePacket fp, InetAddress fromIp) {
        // Code xử lý nhận file (giữ nguyên như cũ)
        // ... (lưu vào Received)
        controller.addReceivedFile(fp.fileName() + " từ " + fromIp.getHostAddress());
        controller.updateStatus("Nhận xong: " + fp.fileName());
    }
}