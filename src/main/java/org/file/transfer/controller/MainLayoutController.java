package org.file.transfer.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
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
        showHome();
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
    private void showLanDiscovery() {
        loadView("LanDiscovery.fxml", null);
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
            // If data needed passed, check instance of controller
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
