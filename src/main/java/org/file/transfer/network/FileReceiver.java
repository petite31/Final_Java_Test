package org.file.transfer.network;

import javafx.application.Platform;
import org.file.transfer.controller.MainController;
import org.file.transfer.model.FilePacket;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FileReceiver {
    private final MainController controller;
    private final DatagramSocket socket;
    private final FileSender fileSender;
    private final String myPasskey;
    private final Set<String> authenticatedIps = Collections.synchronizedSet(new HashSet<>());

    public FileReceiver(TransferManager manager, int requestedPort, String passkey) throws SocketException {
        this.controller = manager.getController();
        this.myPasskey = passkey;

        // Tạo socket với port cố định (6969)
        this.socket = new DatagramSocket(requestedPort);

        int actualPort = socket.getLocalPort(); // Lấy port thật

        // 🔥 LẤY IP THẬT CỦA MÁY
        String localIp;
        try {
            localIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            localIp = "Unknown";
        }

        System.out.println("[DEBUG] FileReceiver: Bind tại " + localIp + ":" + actualPort + " | Passkey: " + myPasskey);

        // Gửi lên giao diện
        controller.setListeningPort(actualPort);
        controller.setListeningIp(localIp);
        controller.setListeningPort(actualPort);
        controller.updateStatus("Đang lắng nghe tại " + localIp + ":" + actualPort + "...");

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
                    String senderIp = packet.getAddress().getHostAddress();

                    // Xử lý gói tin nhỏ (PING hoặc AUTH)
                    if (packet.getLength() < 200) {
                        String msg = new String(packet.getData(), 0, packet.getLength());

                        // 1. PING
                        if (msg.startsWith("PING_FROM_P2P_APP")) {
                            String pong = "PONG_FROM_P2P_APP_OK";
                            DatagramPacket reply = new DatagramPacket(
                                    pong.getBytes(), pong.length(),
                                    packet.getAddress(), packet.getPort());
                            socket.send(reply);
                            continue;
                        }

                        // 2. AUTH REQUEST: "AUTH_REQUEST:<PASSKEY>"
                        if (msg.startsWith("AUTH_REQUEST:")) {
                            String receivedPasskey = msg.split(":")[1];
                            String responseMsg;
                            if (this.myPasskey.equals(receivedPasskey)) {
                                authenticatedIps.add(senderIp);
                                responseMsg = "AUTH_RESPONSE:OK";
                                System.out.println("[DEBUG] Auth SUCCESS for " + senderIp);
                                controller.updateStatus("Đã xác thực kết nối từ " + senderIp);
                            } else {
                                responseMsg = "AUTH_RESPONSE:FAIL";
                                System.out.println("[DEBUG] Auth FAILED for " + senderIp + " (Wrong Passkey: "
                                        + receivedPasskey + ")");
                            }
                            DatagramPacket reply = new DatagramPacket(
                                    responseMsg.getBytes(), responseMsg.length(),
                                    packet.getAddress(), packet.getPort());
                            socket.send(reply);
                            continue;
                        }
                    }

                    // 3. FILE PACKET (Chỉ nhận nếu đã xác thực)
                    if (!authenticatedIps.contains(senderIp)) {
                        System.out.println("[WARN] Từ chối gói tin từ IP chưa xác thực: " + senderIp);
                        continue;
                    }

                    // Xử lý file packet
                    ObjectInputStream ois = new ObjectInputStream(
                            new ByteArrayInputStream(packet.getData(), 0, packet.getLength()));
                    FilePacket fp = (FilePacket) ois.readObject();

                    fileSender.sendAck(fp.packetId(), packet.getAddress(), packet.getPort());
                    handleFilePacket(fp, packet.getAddress());

                } catch (SocketException e) {
                    if (socket.isClosed())
                        break;
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