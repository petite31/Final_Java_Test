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

        DiscoveryService.getInstance(6969).start();

        showHome();

        try {
            DiscoveryService.getInstance()
                    .setOnConnectionRequested((senderIp, senderName, acceptAction, rejectAction) -> {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Connection Request");
                        alert.setHeaderText("Incoming Connection");
                        alert.setContentText(
                                senderName + " (" + senderIp + ") wants to connect.\nAllow this connection?");

                        alert.showAndWait().ifPresent(response -> {
                            if (response == javafx.scene.control.ButtonType.OK) {
                                acceptAction.run();
                            } else {
                                System.out.println("[UI] User denied connection from " + senderName);
                                rejectAction.run();
                            }
                        });
                    });

            DiscoveryService.getInstance().setOnPasskeyAccepted((ip, passkey) -> {
                showSendWithPeer(ip, passkey);
            });
        } catch (IllegalStateException e) {
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

    public void showSendWithPeer(String ip, String passkey) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/file/transfer/view/SendFiles.fxml"));
            Parent view = loader.load();

            SendFilesController controller = loader.getController();
            controller.setRecipientIp(ip);
            controller.setPasskey(passkey);

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
