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

            // Send PASSKEY_REQUEST
            // DiscoveryService sends: PASSKEY_REQUEST|<me>|<passkey>|<port>
            // Peer should display it or auto-accept if they have matching logic (or user
            // types it)
            // Wait, requirements say: "Auto-fill IP + passkey on peer acceptance"
            // So we send REQUEST. Logic:
            // 1. Sender click "Connect"
            // 2. Sender generates passkey (if not exist) -> Shows to user "Tell peer your
            // passkey: XXXXX"
            // 3. Sends PASSKEY_REQUEST to Peer with this key.
            // 4. Peer receives REQUEST. Peer checks if they have matching key input?
            // Wait, "PASSKEY_ACCEPT ... automatically extract sender IP ... auto-fill"
            // This implies the User actions are:
            // User A (Receiver): Opens app. Passkey is generated/displayed? Requirements
            // says "Display it... Broadcast...".
            // User B (Sender): Sees Peer A. Connects.
            // Scenario 1: B types A's passkey?
            // Scenario 2: "Auto-fill on peer acceptance".
            // Let's interpret Requirement: "PASSKEY_REQUEST... PASSKEY_ACCEPT...
            // auto-fill".
            // This sounds like:
            // A sends Request with *some* key? Or A request "I want to connect".
            // Actually, usually:
            // Receiver (A) has Key "123456".
            // Sender (B) must know "123456".
            // If B sends "PASSKEY_REQUEST|B|123456", A says "Matches! ACCEPT".
            // B gets "ACCEPT". B auto-fills IP of A and "123456" into Send Form and starts.
            // So B needs to Input the key first? OR B Generates key and A accepts?
            // Req: "Generate... Display... Broadcast the passkey...".
            // Ah! "Broadcast the passkey with a discovery packet".
            // So A broadcasts "I am A, Key: 123456".
            // B sees A. B clicks Connect. B already knows Key from discovery??
            // That would be insecure (broadcasting connection key).
            // "Broadcast the passkey" -> This seems insecure IF it's the auth key.
            // Maybe "Broadcast presence...".
            // Re-read: "Broadcast the passkey with a discovery packet when created."
            // Ok, if the requirement explicitly says broadcast it, then it's meant for easy
            // local discovery/auth?
            // "PASSKEY_REQUEST|<device>|<passkey>".

            // Let's assume the flow:
            // 1. Device A generates Key. Broadcasts it (or implicitly uses it).
            // 2. Device B connects. Sends "PASSKEY_REQUEST|B|KeyOfA".
            // 3. A validates. Sends "PASSKEY_ACCEPT".
            // 4. B gets ACCEPT. B starts transfer.

            // BUT, if discovery packet doesn't have key (my impl didn't add it yet), B
            // doesn't know it.
            // "Broadcast the passkey with a discovery packet".
            // I missed that in Discovery Update!

            // Correction: I need to update DiscoveryService to include passkey in
            // "DISCOVER_PEER_REQUEST".
            // Then PeerInfo will have it.
            // Then B can just use it.

            // Security note: Broadcasting passkey on LAN means anyone on LAN can connect.
            // It protects against outside-LAN, but not inside.
            // This is "easy pairing".

            // So:
            // 1. DiscoveryService broadcasts key.
            // 2. PeerInfo includes key.
            // 3. Connect -> Send PASSKEY_REQUEST (for handshake/verification).
            // 4. Receive ACCEPT.
            // 5. Navigate to Send.

            // Wait, I didn't update DiscoveryService to broadcast passkey. I should fix
            // that next.
            // For now, let's assume we implement the flow assuming PeerInfo has it or we
            // prompt user (Fallback).
            // But Req says "auto-fill", implying no prompt.
            // So I MUST Broadcast it.

            // Send Connection Request
            discoveryService.sendConnectionRequest(selected.getIp());

            // Update UI to indicate waiting
            btnConnect.setDisable(true);
            btnConnect.setText("Waiting...");
        }
    }
}
