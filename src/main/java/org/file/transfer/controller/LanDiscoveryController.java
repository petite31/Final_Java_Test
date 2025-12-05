package org.file.transfer.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import org.file.transfer.model.PeerInfo;
import org.file.transfer.network.DiscoveryService;

public class LanDiscoveryController {

    @FXML
    private TableView<PeerInfo> tablePeers;
    @FXML
    private TableColumn<PeerInfo, String> colName;
    @FXML
    private TableColumn<PeerInfo, String> colIp;
    @FXML
    private TableColumn<PeerInfo, Number> colPort;
    @FXML
    private TableColumn<PeerInfo, String> colStatus;
    @FXML
    private Button btnConnect;

    private DiscoveryService discoveryService;

    @FXML
    public void initialize() {
        discoveryService = DiscoveryService.getInstance(6969);
        discoveryService.start();

        colName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        colIp.setCellValueFactory(cellData -> cellData.getValue().ipProperty());
        colPort.setCellValueFactory(
                cellData -> javafx.beans.binding.Bindings.createIntegerBinding(() -> cellData.getValue().getPort()));
        colStatus.setCellValueFactory(
                cellData -> javafx.beans.binding.Bindings.createStringBinding(cellData.getValue()::getStatus));

        tablePeers.setItems(discoveryService.getActivePeers());

        // Update button state
        tablePeers.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            btnConnect.setDisable(newVal == null);
        });

        // Double click to connect
        tablePeers.setRowFactory(tv -> {
            TableRow<PeerInfo> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    connectToPeer();
                }
            });
            return row;
        });
    }

    @FXML
    private void refreshPeers() {
        // No-op for now, service runs in background
    }

    @FXML
    private void connectToPeer() {
        PeerInfo selected = tablePeers.getSelectionModel().getSelectedItem();
        if (selected != null) {
            String passkey = org.file.transfer.service.PasskeyManager.getInstance().getCurrentPasskey();
            MainLayoutController.getInstance().showSendWithPeer(selected.getIp(), passkey);
        }
    }
}
