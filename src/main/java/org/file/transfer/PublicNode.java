package org.file.transfer;

import java.net.*;
import java.util.Arrays;
import java.security.SecureRandom;

public class PublicNode {

    // Cấu hình STUN Server của Google
    private static final String STUN_HOST = "stun.l.google.com";
    private static final int STUN_PORT = 19302;
    private static final int MAGIC_COOKIE = 0x2112A442;

    public static void main(String[] args) {
        DatagramSocket socket = null;
        try {
            // 1. TẠO SOCKET
            socket = new DatagramSocket();
            socket.setReuseAddress(true);
            System.out.println("=== CLIENT ĐANG KHỞI ĐỘNG ===");
            System.out.println("Local Port: " + socket.getLocalPort());

            // 2. GỬI YÊU CẦU STUN
            InetAddress stunAddr = InetAddress.getByName(STUN_HOST);
            byte[] txId = new byte[12];
            new SecureRandom().nextBytes(txId);

            // Tạo gói tin STUN request chuẩn
            byte[] request = new byte[20];
            request[0] = 0; request[1] = 1; // Binding Request
            request[2] = 0; request[3] = 0; // Length = 0
            request[4] = (byte)(MAGIC_COOKIE >> 24);
            request[5] = (byte)(MAGIC_COOKIE >> 16);
            request[6] = (byte)(MAGIC_COOKIE >> 8);
            request[7] = (byte)(MAGIC_COOKIE);
            System.arraycopy(txId, 0, request, 8, 12);

            DatagramPacket sendPacket = new DatagramPacket(request, request.length, stunAddr, STUN_PORT);
            socket.send(sendPacket);

            // 3. NHẬN PHẢN HỒI VÀ GIẢI MÃ
            byte[] buf = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
            socket.setSoTimeout(3000); // Timeout 3s

            socket.receive(receivePacket);
            String[] publicInfo = parseStunResponse(receivePacket.getData(), receivePacket.getLength());

            if (publicInfo != null) {
                System.out.println("\n------------------------------------------------");
                System.out.println(">>> OK! ĐỊA CHỈ PUBLIC CỦA BẠN LÀ:");
                System.out.println(">>> IP:   " + publicInfo[0]);  // Sẽ ra dạng chuẩn x.x.x.x
                System.out.println(">>> PORT: " + publicInfo[1]);
                System.out.println("------------------------------------------------\n");

                // 4. CHUYỂN SANG CHẾ ĐỘ LẮNG NGHE (SERVER)
                socket.setSoTimeout(0); // Bỏ timeout để lắng nghe mãi mãi
                System.out.println(">>> Đang chờ tin nhắn từ mạng internet...");

                while (true) {
                    Arrays.fill(buf, (byte)0); // Xóa bộ đệm cũ
                    DatagramPacket incoming = new DatagramPacket(buf, buf.length);
                    socket.receive(incoming);

                    // Lọc bỏ gói tin rác từ STUN server nếu nó gửi lại
                    if (incoming.getAddress().getHostAddress().equals(stunAddr.getHostAddress())) continue;

                    String msg = new String(incoming.getData(), 0, incoming.getLength(), "UTF-8");
                    System.out.println("[NHẬN ĐƯỢC] Từ " + incoming.getAddress().getHostAddress() + ":" + incoming.getPort());
                    System.out.println("Nội dung: " + msg);

                    // Gửi xác nhận lại cho người gửi
                    String reply = "Server đã nhận: " + msg;
                    byte[] replyData = reply.getBytes("UTF-8");
                    socket.send(new DatagramPacket(replyData, replyData.length, incoming.getAddress(), incoming.getPort()));
                }
            } else {
                System.out.println("Lỗi: Không tìm thấy địa chỉ trong phản hồi STUN.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }

    // --- HÀM GIẢI MÃ (ĐÃ FIX LỖI BITWISE) ---
    private static String[] parseStunResponse(byte[] data, int length) {
        if (length < 20) return null;
        int msgLen = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        int offset = 20;
        int end = 20 + msgLen;

        while (offset + 4 <= end) {
            int type = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            int len = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);

            // Tìm attribute 0x0020 (XOR-MAPPED-ADDRESS)
            if (type == 0x0020) {
                // Giải mã Port
                int port = (((data[offset + 6] & 0xFF) << 8) | (data[offset + 7] & 0xFF)) ^ (MAGIC_COOKIE >> 16);

                // Giải mã IP (FIXED: Thêm & 0xFF vào Magic Cookie để chỉ lấy đúng byte cần thiết)
                int b1 = (data[offset + 8] & 0xFF) ^ ((MAGIC_COOKIE >> 24) & 0xFF);
                int b2 = (data[offset + 9] & 0xFF) ^ ((MAGIC_COOKIE >> 16) & 0xFF);
                int b3 = (data[offset + 10] & 0xFF) ^ ((MAGIC_COOKIE >> 8) & 0xFF);
                int b4 = (data[offset + 11] & 0xFF) ^ (MAGIC_COOKIE & 0xFF);

                String ip = b1 + "." + b2 + "." + b3 + "." + b4;
                return new String[]{ip, String.valueOf(port)};
            }
            offset += 4 + len;
        }
        return null;
    }
}