package org.file.transfer.network;

import org.file.transfer.controller.MainController;

import java.io.File;
import java.net.*;

public class TransferManager {
    private final MainController controller;
    private FileSender fileSender;
    private FileReceiver fileReceiver;
    private boolean isRunning = false;

    public TransferManager(MainController controller) {
        this.controller = controller;
    }

    /**
     * Trả về cổng đang lắng nghe (dùng để hiển thị hoặc thông báo cho người khác)
     */
    public int getListeningPort() {
        if (fileReceiver != null) {
            return fileReceiver.getPort();
        }
        return -1; // chưa khởi động
    }


    /**
     * Khởi động lắng nghe
     * @param requestedPort 0 = random, > 0 = cố định port
     * @return cổng thực tế đang dùng
     */
    public synchronized int startListening(int requestedPort) {
        if (isRunning) {
            System.out.println("[DEBUG] Đã đang lắng nghe, bỏ qua startListening()");
            return fileReceiver != null ? fileReceiver.getPort() : -1;
        }

        try {
            fileSender = new FileSender(this);
            fileReceiver = new FileReceiver(this, requestedPort); // truyền port vào đây
            isRunning = true;

            int actualPort = fileReceiver.getPort();
            System.out.println("[DEBUG] TransferManager: Đã khởi động thành công trên cổng " + actualPort);
            controller.setListeningPort(actualPort); // cập nhật giao diện
            return actualPort;

        } catch (Exception e) {
            System.out.println("[DEBUG] Lỗi khởi động TransferManager: " + e.getMessage());
            e.printStackTrace();
            controller.transferFailed("Không thể khởi động mạng: " + e.getMessage());
            return -1;
        }
    }

    /**
     * DỪNG HOÀN TOÀN – GIẢI PHÓNG CỔNG (QUAN TRỌNG KHI ĐỔI PORT)
     */
    public synchronized void stopListening() {
        System.out.println("[DEBUG] TransferManager: Đang dừng toàn bộ UDP...");

        if (fileReceiver != null) {
            fileReceiver.close();
            fileReceiver = null;
        }
        if (fileSender != null) {
            fileSender.close();
            fileSender = null;
        }
        isRunning = false;
        System.out.println("[DEBUG] Đã dừng lắng nghe thành công!");
    }

    // Gửi file
    public void sendFile(File file, String ip, int port) {
        if (!isRunning || fileSender == null) {
            controller.transferFailed("Chưa khởi động mạng!");
            return;
        }
        System.out.println("[DEBUG] TransferManager: Bắt đầu gửi file tới " + ip + ":" + port);
        fileSender.sendFile(file, ip, port);
    }

    // Test kết nối
    public boolean testConnection(String ip, int port) {
        return fileSender != null && fileSender.testConnection(ip, port);
    }

    // Getter để FileReceiver/FileSender gọi lại Controller
    public MainController getController() {
        return controller;
    }
}