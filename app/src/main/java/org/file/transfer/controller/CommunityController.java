package org.file.transfer.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.file.transfer.model.MarketFile;

public class CommunityController {
    @FXML private TableView<MarketFile> tableMarket;
    @FXML private TableColumn<MarketFile, String> colFileName;
    @FXML private TableColumn<MarketFile, String> colFileSize;
    @FXML private TableColumn<MarketFile, String> colSeller;
    @FXML private TableColumn<MarketFile, String> colPrice;
    @FXML private TableColumn<MarketFile, Void> colAction;

    @FXML private TextField txtSearch;

    @FXML
    public void initialize() {
        setupTableColumns();
        loadMarketData();
    }

    private void setupTableColumns() {
        // Logic render nút Buy/Download/Delete sẽ code ở đây
    }

    @FXML
    private void handleSearch() {
        String query = txtSearch.getText();
        System.out.println("Searching for: " + query);
    }

    @FXML
    private void handleSellFile() {
        // 1. Mở cửa sổ chọn file
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Choose file to sell");
        java.io.File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            // 2. Hỏi người dùng nhập giá tiền
            TextInputDialog dialog = new TextInputDialog("0");
            dialog.setTitle("Set the Price");
            dialog.setHeaderText("Sell: " + selectedFile.getName());
            dialog.setContentText("Price (VNĐ):");

            dialog.showAndWait().ifPresent(priceStr -> {
                try {
                    long price = Long.parseLong(priceStr);
                    if (price < 0) throw new NumberFormatException();

                    // 3. Tiến hành Upload (Chạy trên luồng riêng để không đơ UI)
                    uploadMarketFile(selectedFile, price);
                } catch (NumberFormatException e) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "The price is invalid. Please enter a positive integer.");
                    alert.showAndWait();
                }
            });
        }
    }

    private void uploadMarketFile(java.io.File file, long price) {
        // Lấy ID của user đang đăng nhập (Bạn cần lấy từ UserSession/AuthService hiện tại của bạn)
        int currentUserId = org.file.transfer.service.UserSession.getInstance().getUserId();

        // ĐỊA CHỈ IP SERVER. (Đổi thành IP Server của bạn nếu chạy khác máy)
        String serverIp = "127.0.0.1";

        new Thread(() -> {
            try (java.net.Socket socket = new java.net.Socket(serverIp, 8892);
                 java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream());
                 java.io.FileInputStream fis = new java.io.FileInputStream(file)) {

                // 1. Gửi Metadata
                dos.writeUTF("UPLOAD_SELL");
                dos.writeUTF(file.getName());
                dos.writeLong(file.length());
                dos.writeInt(currentUserId);
                dos.writeLong(price);
                dos.flush();

                // 2. Gửi Data Bytes của file
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                dos.flush();

                // Báo cáo thành công lên UI
                javafx.application.Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Sell successful");
                    alert.showAndWait();
                    loadMarketData(); // Tải lại bảng để thấy file mới
                });

            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Something wrong!");
                    alert.showAndWait();
                });
            }
        }).start();
    }

    private void loadMarketData() {
        // Sẽ gọi database để lấy danh sách file cộng đồng
    }

    @FXML
    private void handleBack() {
        // Gọi instance của MainLayout để quay về Home
        if (MainLayoutController.getInstance() != null) {
            MainLayoutController.getInstance().showHome();
        }
    }

}