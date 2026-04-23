package org.file.transfer.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import org.file.transfer.model.PeerInfo;
import org.file.transfer.network.DiscoveryService;
import org.file.transfer.service.PasskeyManager;

public class NearbyDevicesController {

    @FXML
    private TableView<PeerInfo> tablePeers;
    @FXML
    private TableColumn<PeerInfo, String> colName;
    @FXML
    private TableColumn<PeerInfo, String> colIp;
    @FXML
    private TableColumn<PeerInfo, Number> colPort;
    @FXML
    private TableColumn<PeerInfo, String> colMech;
    @FXML
    private TableColumn<PeerInfo, String> colStatus;
    @FXML
    private Button btnConnect;

    private DiscoveryService discoveryService;

    @FXML
    public void initialize() {
        discoveryService = DiscoveryService.getInstance();

        colName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        colIp.setCellValueFactory(cellData -> cellData.getValue().ipProperty());
        colPort.setCellValueFactory(cellData -> cellData.getValue().portProperty());
        colMech.setCellValueFactory(cellData -> cellData.getValue().mechanismProperty());
        colStatus.setCellValueFactory(
                cellData -> javafx.beans.binding.Bindings.createStringBinding(cellData.getValue()::getStatus, cellData.getValue().nameProperty())); // Lắng nghe thay đổi

        tablePeers.setItems(discoveryService.getActivePeers());

        // Chỉ bật nút Connect nếu thiết bị đang Online
        tablePeers.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean isOnline = newVal != null && "Online".equals(newVal.getStatus());
            btnConnect.setDisable(!isOnline);
            btnConnect.setText("Connect");
        });

        tablePeers.setRowFactory(tv -> {
            TableRow<PeerInfo> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                // Nhấn đúp để kết nối
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    PeerInfo selected = row.getItem();
                    if ("Online".equals(selected.getStatus())) {
                        connectToPeer();
                    }
                }
            });
            return row;
        });

        // Lắng nghe sự kiện bị từ chối kết nối để reset nút
        discoveryService.setOnConnectionRefused(() -> {
            btnConnect.setText("Connect");
            btnConnect.setDisable(false);
        });
    }

    @FXML
    private void connectToPeer() {
        PeerInfo selected = tablePeers.getSelectionModel().getSelectedItem();
        if (selected != null && "Online".equals(selected.getStatus())) {
            discoveryService.sendConnectionRequest(selected.getIp());
            btnConnect.setDisable(true);
            btnConnect.setText("Waiting...");

            // Tùy chọn: Đặt Timeout 10 giây nếu bên kia không phản hồi
            new Thread(() -> {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ignored) {}

                javafx.application.Platform.runLater(() -> {
                    if ("Waiting...".equals(btnConnect.getText())) {
                        btnConnect.setText("Connect");
                        btnConnect.setDisable(false);
                        discoveryService.setOnConnectionRefused(null); // Tránh lỗi
                    }
                });
            }).start();
        }
    }

    @FXML
    private void refreshPeers() {
    }

}
