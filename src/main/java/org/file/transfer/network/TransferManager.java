package org.file.transfer.network;

import org.file.transfer.controller.MainController;

import java.io.File;
import java.net.*;
import java.util.Random;

public class TransferManager {
    public static final int FIXED_PORT = 6969;

    private final MainController controller;
    private FileSender fileSender;
    private FileReceiver fileReceiver;
    private boolean isRunning = false;
    private String myPasskey;

    public TransferManager(MainController controller) {
        this.controller = controller;
        this.myPasskey = generatePasskey();
    }

    private String generatePasskey() {
        return String.format("%06d", new Random().nextInt(1000000));
    }

    public String getMyPasskey() {
        return myPasskey;
    }

    /**
     * Trả về cổng đang lắng nghe
     */
    public int getListeningPort() {
        return FIXED_PORT;
    }

    /**
     * Khởi động lắng nghe trên cổng cố định 6969
     */
    public synchronized int startListening() {
        if (isRunning) {
            System.out.println("[DEBUG] Đã đang lắng nghe, bỏ qua startListening()");
            return FIXED_PORT;
        }

        try {
            fileSender = new FileSender(this);
            fileReceiver = new FileReceiver(this, FIXED_PORT, myPasskey); // Pass passkey to receiver
            isRunning = true;

            System.out.println("[DEBUG] TransferManager: Đã khởi động thành công trên cổng " + FIXED_PORT);
            controller.setListeningPort(FIXED_PORT); // cập nhật giao diện
            return FIXED_PORT;

        } catch (Exception e) {
            System.out.println("[DEBUG] Lỗi khởi động TransferManager: " + e.getMessage());
            e.printStackTrace();
            controller.transferFailed("Không thể khởi động mạng (Port 6969 có thể đang bận): " + e.getMessage());
            return -1;
        }
    }

    /**
     * DỪNG HOÀN TOÀN
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

    // Authenticate and Send
    public void authenticateAndSend(File file, String ip, String passkey) {
        if (!isRunning || fileSender == null) {
            controller.transferFailed("Chưa khởi động mạng!");
            return;
        }

        new Thread(() -> {
            controller.updateStatus("Đang xác thực với " + ip + "...");
            boolean authSuccess = fileSender.performHandshake(ip, FIXED_PORT, passkey);

            if (authSuccess) {
                controller.updateStatus("Xác thực thành công! Bắt đầu gửi...");
                sendFile(file, ip, FIXED_PORT);
            } else {
                controller.transferFailed("Xác thực thất bại! Sai Passkey hoặc đối phương từ chối.");
            }
        }).start();
    }

    // Gửi file (Private, called after auth)
    private void sendFile(File file, String ip, int port) {
        System.out.println("[DEBUG] TransferManager: Bắt đầu gửi file tới " + ip + ":" + port);
        fileSender.sendFile(file, ip, port);
    }

    // Test kết nối (Optional now, but good to keep)
    public boolean testConnection(String ip, int port) {
        return fileSender != null && fileSender.testConnection(ip, port);
    }

    // Getter để FileReceiver/FileSender gọi lại Controller
    public MainController getController() {
        return controller;
    }
}