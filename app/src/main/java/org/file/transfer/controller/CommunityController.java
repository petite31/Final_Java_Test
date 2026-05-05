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

    private javafx.collections.ObservableList<MarketFile> marketFileList = javafx.collections.FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        tableMarket.setItems(marketFileList);
        loadMarketData();
    }

    private void setupTableColumns() {
        colFileName.setCellValueFactory(cellData -> cellData.getValue().fileNameProperty());
        colFileSize.setCellValueFactory(cellData -> {
            long size = cellData.getValue().getFileSize();
            return new javafx.beans.property.SimpleStringProperty(size / 1024 + " KB");
        });

        colSeller.setCellValueFactory(cellData -> cellData.getValue().sellerNameProperty());
        colPrice.setCellValueFactory(cellData -> {
            long price = cellData.getValue().getPrice();
            return new javafx.beans.property.SimpleStringProperty(price == 0 ? "Free" : price + " VNĐ");
        });

        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnDownload = new Button("Download");
            private final Button btnDelete = new Button("Delete");
            private final Button btnBuy = new Button("Buy");
            private final javafx.scene.layout.HBox pane = new javafx.scene.layout.HBox(8);

            {
                btnDownload.getStyleClass().add("button-action");
                btnDownload.setStyle("-fx-text-fill: #007a82; -fx-font-weight: bold;");
                btnDownload.setMinWidth(Button.USE_PREF_SIZE);
                btnDownload.setOnAction(event -> {
                    MarketFile file = getTableView().getItems().get(getIndex());
                    handleDownload(file);
                });

                btnDelete.getStyleClass().add("button-action");
                btnDelete.setStyle("-fx-text-fill: #e74c3c;");
                btnDelete.setMinWidth(Button.USE_PREF_SIZE);
                btnDelete.setOnAction(event -> {
                    MarketFile file = getTableView().getItems().get(getIndex());
                    handleDelete(file);
                });

                btnBuy.getStyleClass().add("button-action");
                btnBuy.setStyle("-fx-text-fill: #2196F3; -fx-font-weight: bold;");
                btnBuy.setMinWidth(Button.USE_PREF_SIZE);
                btnBuy.setOnAction(event -> {
                    MarketFile file = getTableView().getItems().get(getIndex());
                    handleBuy(file);
                });

                pane.setAlignment(javafx.geometry.Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    MarketFile file = getTableView().getItems().get(getIndex());
                    int currentUserId = org.file.transfer.service.UserSession.getInstance().getUserId();

                    pane.getChildren().clear();

                    if (file.getSellerId() == currentUserId) {
                        pane.getChildren().addAll(btnDownload, btnDelete);
                    } else if (file.isBought() || file.getPrice() == 0) {
                        pane.getChildren().add(btnDownload);
                    } else {
                        pane.getChildren().add(btnBuy);
                    }
                    setGraphic(pane);
                }
            }
        });
    }

    private void handleBuy(MarketFile file) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Do you want to buy " + file.getFileName() + " for " + file.getPrice() + " VNĐ?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                int currentUserId = org.file.transfer.service.UserSession.getInstance().getUserId();
                new Thread(() -> {
                    try (java.net.Socket socket = new java.net.Socket("192.168.1.4", 8892);
                         java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream());
                         java.io.DataInputStream dis = new java.io.DataInputStream(socket.getInputStream())) {

                        dos.writeUTF("BUY_MARKET_FILE");
                        dos.writeInt(currentUserId);
                        dos.writeInt(file.getId());
                        dos.flush();

                        boolean success = dis.readBoolean();
                        javafx.application.Platform.runLater(() -> {
                            if (success) {
                                new Alert(Alert.AlertType.INFORMATION, "Purchased successfully!").show();
                                loadMarketData();
                            } else {
                                new Alert(Alert.AlertType.ERROR, "Purchase failed!").show();
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        javafx.application.Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Network error").show());
                    }
                }).start();
            }
        });
    }

    private void handleDownload(MarketFile file) {
        javafx.stage.DirectoryChooser dirChooser = new javafx.stage.DirectoryChooser();
        dirChooser.setTitle("Select Save Directory");
        java.io.File saveDir = dirChooser.showDialog(null);
        if (saveDir == null) return;

        int currentUserId = org.file.transfer.service.UserSession.getInstance().getUserId();
        new Thread(() -> {
            try (java.net.Socket socket = new java.net.Socket("192.168.1.4", 8892);
                 java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream());
                 java.io.DataInputStream dis = new java.io.DataInputStream(socket.getInputStream())) {

                dos.writeUTF("DOWNLOAD_MARKET_FILE");
                dos.writeInt(file.getId());
                dos.writeInt(currentUserId);
                dos.flush();

                boolean allowDownload = dis.readBoolean();
                if (!allowDownload) {
                    String msg = dis.readUTF();
                    javafx.application.Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).show());
                    return;
                }

                long fileSize = dis.readLong();
                java.io.File downloadedFile = new java.io.File(saveDir, file.getFileName());
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(downloadedFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    while (totalRead < fileSize && (bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                }
                javafx.application.Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, "Downloaded to " + downloadedFile.getAbsolutePath()).show());

            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Download error").show());
            }
        }).start();
    }

    private void handleDelete(MarketFile file) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + file.getFileName() + "?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                int currentUserId = org.file.transfer.service.UserSession.getInstance().getUserId();
                new Thread(() -> {
                    try (java.net.Socket socket = new java.net.Socket("192.168.1.4", 8892);
                         java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream());
                         java.io.DataInputStream dis = new java.io.DataInputStream(socket.getInputStream())) {

                        dos.writeUTF("DELETE_MARKET_FILE");
                        dos.writeInt(file.getId());
                        dos.writeInt(currentUserId);
                        dos.flush();

                        boolean success = dis.readBoolean();
                        javafx.application.Platform.runLater(() -> {
                            if (success) {
                                new Alert(Alert.AlertType.INFORMATION, "Deleted successfully!").show();
                                loadMarketData();
                            } else {
                                new Alert(Alert.AlertType.ERROR, "Delete failed!").show();
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        javafx.application.Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Network error").show());
                    }
                }).start();
            }
        });
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
        String serverIp = "192.168.1.4";

        new Thread(() -> {
            try (java.net.Socket socket = new java.net.Socket(serverIp, 8892);
                 java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream());
                 java.io.DataInputStream dis = new java.io.DataInputStream(socket.getInputStream());
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

                // Đọc phản hồi từ server trước khi báo thành công
                boolean success = dis.readBoolean();

                javafx.application.Platform.runLater(() -> {
                    if (success) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Sell successful");
                        alert.showAndWait();
                        loadMarketData(); // Tải lại bảng để thấy file mới
                    } else {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to sell file. Server error or Database issue.");
                        alert.showAndWait();
                    }
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
        int currentUserId = org.file.transfer.service.UserSession.getInstance().getUserId();
        String serverIp = "192.168.1.4";

        new Thread(() -> {
            try (java.net.Socket socket = new java.net.Socket(serverIp, 8892);
                 java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream());
                 java.io.DataInputStream dis = new java.io.DataInputStream(socket.getInputStream())) {

                dos.writeUTF("GET_MARKET_DATA");
                dos.writeInt(currentUserId);
                dos.flush();

                int count = dis.readInt();
                java.util.List<MarketFile> files = new java.util.ArrayList<>();

                for (int i = 0; i < count; i++) {
                    int id = dis.readInt();
                    String fileName = dis.readUTF();
                    long fileSize = dis.readLong();
                    String filePath = dis.readUTF();
                    int sellerId = dis.readInt();
                    String sellerName = dis.readUTF();
                    long price = dis.readLong();
                    String uploadDate = dis.readUTF();
                    boolean isBought = dis.readBoolean();

                    files.add(new MarketFile(id, fileName, fileSize, filePath, sellerId, sellerName, price, uploadDate, isBought));
                }

                javafx.application.Platform.runLater(() -> {
                    marketFileList.setAll(files);
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    private void handleBack() {
        // Gọi instance của MainLayout để quay về Home
        if (MainLayoutController.getInstance() != null) {
            MainLayoutController.getInstance().showHome();
        }
    }

}