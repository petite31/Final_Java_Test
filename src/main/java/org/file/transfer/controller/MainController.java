package org.file.transfer.controller;

import org.file.transfer.network.TransferManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.LocalDateTime;

public class MainController {

    // === FXML COMPONENTS ===
    @FXML private TextField tfIp;
    @FXML private TextField tfPort;
    @FXML private TextField tfCustomPort;     // Ô nhập port tùy chỉnh (tùy chọn)
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private Label lblSpeed;
    @FXML private Label lblTransferred;
    @FXML private ListView<String> listReceived;
    @FXML private Button btnChooseFile;
    @FXML private Button btnSendFile;
    @FXML private Label lblMyIp;              // Hiển thị IP + Port (ví dụ: 192.168.1.100:49152)

    private TransferManager transferManager;
    private File selectedFile;
    private boolean isPeerOnline = false;

    @FXML
    public void initialize() {
        transferManager = new TransferManager(this);

        // Khởi động với port random
        int listeningPort = transferManager.startListening(0);

        btnSendFile.setDisable(true);
        tfIp.setPromptText("192.168.1.100");
        tfPort.setPromptText("Cổng đối phương");  // ← sửa đúng tên field

        updateStatus("Sẵn sàng • Đang lắng nghe cổng " + listeningPort);
        detectAndShowMyIp();
    }

    // ============================= CHỌN FILE =============================
    @FXML
    private void chooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file để gửi");
        selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            long size = selectedFile.length();
            btnChooseFile.setText("Đổi file");
            btnSendFile.setText("Gửi: " + selectedFile.getName());
            btnSendFile.setDisable(false);
            updateStatus("Đã chọn: " + selectedFile.getName() + " (" + formatBytes(size) + ")");
        }
    }

    // ============================= KIỂM TRA KẾT NỐI =============================
    @FXML
    private void testConnection() {
        String ip = tfIp.getText().trim();
        if (!ip.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
            showAlert("IP không hợp lệ! (ví dụ: 192.168.1.100)");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(tfPort.getText());
        } catch (Exception e) {
            showAlert("Port không hợp lệ!");
            return;
        }

        updateStatus("Đang kiểm tra kết nối tới " + ip + ":" + port + "...");
        isPeerOnline = false;
        btnSendFile.setDisable(true);

        new Thread(() -> {
            System.out.println("[DEBUG] === KIỂM TRA KẾT NỐI tới " + ip + ":" + port + " ===");
            boolean ok = transferManager.testConnection(ip, port);

            Platform.runLater(() -> {
                if (ok) {
                    isPeerOnline = true;
                    updateStatus("ONLINE – Sẵn sàng gửi file!");
                    lblStatus.setStyle("-fx-text-fill: #00ff00;");
                    btnSendFile.setDisable(selectedFile == null);
                } else {
                    isPeerOnline = false;
                    updateStatus("OFFLINE hoặc chặn port!");
                    lblStatus.setStyle("-fx-text-fill: #ff5555;");
                }
            });
        }).start();
    }

    // ============================= GỬI FILE =============================
    @FXML
    private void sendFile() {
        if (selectedFile == null) {
            showAlert("Chưa chọn file để gửi!");
            return;
        }

        String ip = tfIp.getText().trim();
        int port;
        try {
            port = Integer.parseInt(tfPort.getText());
        } catch (Exception e) {
            showAlert("Port không hợp lệ!");
            return;
        }

        btnSendFile.setDisable(true);
        btnChooseFile.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        updateStatus("Đang gửi " + selectedFile.getName() + " tới " + ip + ":" + port + "...");

        System.out.println("[DEBUG] === GỬI FILE: " + selectedFile.getName() + " (" + formatBytes(selectedFile.length()) + ") ===");

        new Thread(() -> transferManager.sendFile(selectedFile, ip, port)).start();
    }

    @FXML
    private void startReceiving() {
        int port = transferManager.getListeningPort();
        updateStatus("Đã sẵn sàng nhận file • Cổng: " + port);
        showAlert("Bạn đã sẵn sàng nhận file!\nIP của bạn: " + getBestLocalIp() + "\nCổng: " + port);
    }

    // ============================= ĐỔI PORT (TÙY CHỌN) =============================
    @FXML
    private void applyCustomPort() {
        transferManager.stopListening();
        updateStatus("Đang khởi động lại với cổng mới...");

        String text = tfCustomPort.getText().trim();
        int newPort;

        if (text.isEmpty()) {
            newPort = 0; // random
            updateStatus("Đang dùng port random...");
        } else {
            try {
                newPort = Integer.parseInt(text);
                if (newPort < 1024 || newPort > 65535) throw new Exception();
            } catch (Exception e) {
                showAlert("Port phải từ 1024 đến 65535!");
                transferManager.startListening(0);
                return;
            }
        }

        int actualPort = transferManager.startListening(newPort);
        tfPort.setText(String.valueOf(actualPort));
        updateStatus("Đã chuyển sang cổng " + actualPort);
    }

    // ============================= CALLBACK TỪ TRANSFERMANAGER =============================
    public void updateProgress(double percent, long sent, long total, double speedKB) {
        Platform.runLater(() -> {
            progressBar.setProgress(percent);
            lblTransferred.setText(formatBytes(sent) + " / " + formatBytes(total));
            lblSpeed.setText(String.format("%,.2f KB/s", speedKB));
        });
    }

    public void updateStatus(String status) {
        Platform.runLater(() -> lblStatus.setText(status));
    }

    public void addReceivedFile(String info) {
        Platform.runLater(() -> listReceived.getItems().add(0,
                "Nhận được " + info + " — " + LocalDateTime.now().withNano(0)));
    }

    public void transferComplete() {
        Platform.runLater(() -> {
            progressBar.setVisible(false);
            progressBar.setProgress(0);
            lblSpeed.setText("0 KB/s");
            lblTransferred.setText("0 / 0 bytes");
            btnSendFile.setDisable(!selectedFileExists());
            btnChooseFile.setDisable(false);
            updateStatus("Hoàn thành truyền file!");
        });
    }

    public void transferFailed(String reason) {
        Platform.runLater(() -> {
            showAlert("Gửi file thất bại!\nLý do: " + reason);
            btnSendFile.setDisable(false);
            btnChooseFile.setDisable(false);
            updateStatus("Gửi thất bại: " + reason);
        });
    }

    // ============================= HIỂN THỊ IP + PORT =============================
    public void setListeningPort(int port) {
        Platform.runLater(() -> {
            String ip = getBestLocalIp();
            lblMyIp.setText(ip + ":" + port);
            tfPort.setText(String.valueOf(port));
        });
    }

    private void detectAndShowMyIp() {
        Platform.runLater(() -> lblMyIp.setText("Đang lấy IP..."));
        new Thread(() -> {
            String ip = getBestLocalIp();
            Platform.runLater(() -> {
                if (!ip.equals("127.0.0.1") && !ip.equals("Không xác định")) {
                    lblMyIp.setText(ip + ":?");
                    lblMyIp.setStyle("-fx-text-fill: #00ffaa; -fx-font-weight: bold;");
                } else {
                    lblMyIp.setText("localhost");
                    lblMyIp.setStyle("-fx-text-fill: #ff6b6b;");
                }
            });
        }).start();
    }

    private String getBestLocalIp() {
        try {
            var en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                var nif = en.nextElement();
                if (nif.isLoopback() || !nif.isUp()) continue;
                var addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    var addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "Không xác định";
        }
    }

    // ============================= UTILS =============================
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%,.2f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%,.2f MB", bytes / (1024.0 * 1024));
        return String.format("%,.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private boolean selectedFileExists() {
        return selectedFile != null && selectedFile.exists();
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
            a.setHeaderText(null);
            a.setTitle("Thông báo");
            a.show();
        });
    }
}