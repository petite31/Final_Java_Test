package org.file.transfer.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import org.file.transfer.network.DiscoveryService;

import java.io.IOException;

public class MainLayoutController {

    @FXML
    private BorderPane mainLayout;
    @FXML
    private BorderPane contentArea;

    private static MainLayoutController instance;

    public static MainLayoutController getInstance() {
        return instance;
    }

    @FXML
    public void initialize() {
        instance = this;

        // Initialize DiscoveryService early
        // Typically port 6969 for file transfer
        DiscoveryService.getInstance(6969).start();

        showHome();

        // Auto-navigate on Passkey reception
        try {
            DiscoveryService.getInstance().setOnPasskeyAccepted((ip, passkey) -> {
                showSendWithPeer(ip);
                // We should also pre-fill passkey in the Send controller.
                // But showSendWithPeer only takes IP.
                // The SendFilesController will need to be updated or we need a way to pass
                // passkey.
            });
        } catch (IllegalStateException e) {
            // Service not started yet, ignore
        }
    }

    @FXML
    private void showHome() {
        loadView("Home.fxml", null);
    }

    @FXML
    private void showSend() {
        loadView("SendFiles.fxml", null);
    }

    @FXML
    private void showReceive() {
        loadView("ReceiverFiles.fxml", null);
    }

    @FXML
    private void showNearbyDevices() {
        loadView("nearby_devices.fxml", null);
    }

    @FXML
    private void showSettings() {
        loadView("Settings.fxml", null);
    }

    public void showSendWithPeer(String ip) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/file/transfer/view/SendFiles.fxml"));
            Parent view = loader.load();

            SendFilesController controller = loader.getController();
            controller.setRecipientIp(ip);
            // TODO: controller.setPasskey(passkey);

            contentArea.setCenter(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadView(String fxml, Object data) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/file/transfer/view/" + fxml));
            Parent view = loader.load();
            contentArea.setCenter(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
