package org.file.transfer.controller;

import org.file.transfer.network.IP_Port_Managment;
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

import static org.file.transfer.network.IP_Port_Managment.getBestLocalIp;

public class MainController {

    // === FXML COMPONENTS ===
    @FXML
    private TextField tfIp;
    @FXML
    private PasswordField tfPasskey; // Changed from Port to Passkey
    @FXML
    private Label lblMyPasskey; // New label for my passkey
    @FXML
    private Label lblMyPort; // Kept for compatibility but might be unused in new FXML
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label lblStatus;
    @FXML
    private Label lblSpeed;
    @FXML
    private Label lblTransferred;
    @FXML
    private ListView<String> listReceived;
    @FXML
    private Button btnChooseFile;
    @FXML
    private Button btnSendFile;
    @FXML
    private Label lblMyIp;

    private TransferManager transferManager;
    private File selectedFile;
    private boolean isPeerOnline = false;

    @FXML
    public void initialize() {
        transferManager = new TransferManager(this);

        // Khởi động với port cố định 6969
        int listeningPort = transferManager.startListening();

        btnSendFile.setDisable(true);
        tfIp.setPromptText("192.168.1.x");

        // Hiển thị Passkey của mình
        lblMyPasskey.setText(transferManager.getMyPasskey());

        updateStatus("Ready • Listening on Port " + listeningPort);
        detectAndShowMyIp();
    }

    // ============================= CHỌN FILE =============================
    @FXML
    private void chooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose File to Send");
        selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            long size = selectedFile.length();
            btnChooseFile.setText("Change File");
            btnSendFile.setText("Send: " + selectedFile.getName());
            btnSendFile.setDisable(false);
            updateStatus("Selected: " + selectedFile.getName() + " (" + formatBytes(size) + ")");
        }
    }

    // ============================= GỬI FILE (CÓ AUTH)
    // =============================
    @FXML
    private void sendFile() {
        if (selectedFile == null) {
            showAlert("Please choose a file first!");
            return;
        }

        String ip = tfIp.getText().trim();
        String passkey = tfPasskey.getText().trim();

        if (!ip.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
            showAlert("Invalid IP Address!");
            return;
        }

        if (passkey.length() != 6 || !passkey.matches("\\d+")) {
            showAlert("Passkey must be a 6-digit number!");
            return;
        }

        btnSendFile.setDisable(true);
        btnChooseFile.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);

        // Gọi hàm xác thực và gửi
        transferManager.authenticateAndSend(selectedFile, ip, passkey);
    }

    // Removed startReceiving as it's auto-started now, but keeping method if FXML
    // calls it (though removed from FXML)
    // If FXML still has it, we can keep empty or remove. In new FXML I removed the
    // button.

    // ============================= CALLBACK TỪ TRANSFERMANAGER
    // =============================
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
                "Received " + info + " — " + LocalDateTime.now().withNano(0)));
    }

    public void transferComplete() {
        Platform.runLater(() -> {
            progressBar.setVisible(false);
            progressBar.setProgress(0);
            lblSpeed.setText("0 KB/s");
            lblTransferred.setText("0 / 0 bytes");
            btnSendFile.setDisable(!selectedFileExists());
            btnChooseFile.setDisable(false);
            updateStatus("Transfer Completed Successfully!");
            showAlert("File sent successfully!");
        });
    }

    public void transferFailed(String reason) {
        Platform.runLater(() -> {
            showAlert("Transfer Failed!\nReason: " + reason);
            btnSendFile.setDisable(false);
            btnChooseFile.setDisable(false);
            updateStatus("Failed: " + reason);
        });
    }

    // ============================= HIỂN THỊ IP + PORT
    // =============================
    public void setListeningIp(String ip) {
        Platform.runLater(() -> lblMyIp.setText(ip));
    }

    public void setListeningPort(int port) {
        // Port is fixed, maybe update label if exists, or ignore
        if (lblMyPort != null) {
            Platform.runLater(() -> lblMyPort.setText(String.valueOf(port)));
        }
    }

    private void detectAndShowMyIp() {
        Platform.runLater(() -> lblMyIp.setText("Detecting..."));
        new Thread(() -> {
            String ip = getBestLocalIp();
            Platform.runLater(() -> {
                if (!ip.equals("127.0.0.1") && !ip.equals("Không xác định")) {
                    lblMyIp.setText(ip);
                    lblMyIp.setStyle("-fx-text-fill: #1E88E5; -fx-font-weight: bold;");
                } else {
                    lblMyIp.setText("localhost");
                    lblMyIp.setStyle("-fx-text-fill: #C62828;");
                }
            });
        }).start();
    }

    // ============================= UTILS =============================
    private String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%,.2f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024)
            return String.format("%,.2f MB", bytes / (1024.0 * 1024));
        return String.format("%,.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private boolean selectedFileExists() {
        return selectedFile != null && selectedFile.exists();
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
            a.setHeaderText(null);
            a.setTitle("Notification");
            a.show();
        });
    }
}