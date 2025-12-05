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
        // Service runs in background, maybe trigger immediate broadcast if API allowed
    }

    @FXML
    private void connectToPeer() {
        PeerInfo selected = tablePeers.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // Always UDP

            // Send Connection Request
            // DiscoveryService sends: PASSKEY_REQUEST|<me>|<passkey>|<port>
            // We now send a default passkey as the verification is removed.
            // The receiver will just see a request and can Accept/Decline.

            // Send Connection Request
            discoveryService.sendConnectionRequest(selected.getIp());

            // Update UI to indicate waiting
            btnConnect.setDisable(true);
            btnConnect.setText("Waiting...");
        }
    }
}
