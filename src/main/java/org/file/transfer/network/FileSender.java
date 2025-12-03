package org.file.transfer.network;

import org.file.transfer.controller.SendFilesController;
import org.file.transfer.model.FilePacket;

import java.io.*;
import java.net.*;

public class FileSender {
    private final TransferManager manager;
    private final DatagramSocket socket;

    public FileSender(TransferManager manager) throws SocketException {
        this.manager = manager;
        this.socket = new DatagramSocket();
        System.out.println("[DEBUG] FileSender: Khởi tạo thành công");
    }

    // === TEST KẾT NỐI ===
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

    // === HANDSHAKE (XÁC THỰC) ===
    public boolean performHandshake(String ip, int port, String passkey) {
        try {
            socket.setSoTimeout(3000); // 3s timeout
            String authMsg = "AUTH_REQUEST:" + passkey;
            InetAddress address = InetAddress.getByName(ip);

            DatagramPacket packet = new DatagramPacket(
                    authMsg.getBytes(), authMsg.length(), address, port);
            socket.send(packet);
            System.out.println("[DEBUG] Gửi yêu cầu xác thực tới " + ip + ":" + port);

            byte[] buf = new byte[256];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);

            String reply = new String(resp.getData(), 0, resp.getLength());
            return reply.equals("AUTH_RESPONSE:OK");

        } catch (Exception e) {
            System.out.println("[DEBUG] Handshake failed: " + e.getMessage());
            return false;
        }
    }

    // === GỬI FILE HOÀN CHỈNH (CÓ ACK + RETRY + TIẾN ĐỘ) ===
    public void sendFile(File file, String targetIp, int targetPort, SendFilesController callback) {
        try {
            InetAddress address = InetAddress.getByName(targetIp);
            long fileSize = file.length();
            int totalPackets = (int) Math.ceil(fileSize / 60000.0); // 60KB/packet
            int packetId = 0;
            long startTime = System.currentTimeMillis();
            long lastUpdate = startTime;
            long sentBytes = 0;

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[60000];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] data = bytesRead == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);

                    FilePacket packet = new FilePacket(
                            packetId,
                            data,
                            file.getName(),
                            fileSize,
                            totalPackets,
                            packetId == totalPackets - 1);

                    boolean ackReceived = false;
                    int retry = 0;

                    while (!ackReceived) {
                        if (retry >= 5) {
                            // FREEZE LOGIC: Stop trying, but don't report failure.
                            // Just exit the loop silently. The UI will remain at current progress.
                            System.out.println("[DEBUG] Max retries reached. Freezing transfer.");
                            if (callback != null)
                                callback.onTransferFrozen();
                            return;
                        }

                        // Gửi packet
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(baos);
                        oos.writeObject(packet);
                        oos.close();

                        DatagramPacket dp = new DatagramPacket(
                                baos.toByteArray(), baos.size(), address, targetPort);
                        socket.send(dp);

                        // Đợi ACK trong 1s
                        socket.setSoTimeout(1000);
                        try {
                            byte[] ackBuf = new byte[64];
                            DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                            socket.receive(ackPacket);
                            String ackMsg = new String(ackPacket.getData(), 0, ackPacket.getLength());
                            if (ackMsg.startsWith("ACK_" + packetId)) {
                                ackReceived = true;
                            }
                        } catch (SocketTimeoutException e) {
                            retry++;
                            System.out.println("[DEBUG] Timeout gói " + packetId + ", thử lại...");
                        }
                    }

                    sentBytes += bytesRead;
                    packetId++;

                    // Cập nhật tiến độ mỗi 200ms
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 200 || packet.isLast()) {
                        double percent = (double) sentBytes / fileSize;
                        double speedKB = sentBytes / ((now - startTime) / 1000.0) / 1024.0;
                        long remainingSeconds = speedKB > 0 ? (long) ((fileSize - sentBytes) / 1024.0 / speedKB) : 0;

                        if (callback != null) {
                            callback.updateProgress(percent, speedKB, remainingSeconds);
                        }
                        lastUpdate = now;
                    }
                }
            }

            if (callback != null)
                callback.onTransferComplete();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === GỬI ACK ===
    public void sendAck(int packetId, InetAddress address, int port) {
        try {
            String ackMsg = "ACK_" + packetId;
            DatagramPacket packet = new DatagramPacket(
                    ackMsg.getBytes(), ackMsg.length(), address, port);
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