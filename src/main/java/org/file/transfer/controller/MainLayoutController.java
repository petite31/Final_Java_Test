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

    @FXML
    public void initialize() {
        showHome();
    }

    @FXML
    private void showHome() {
        loadView("Home.fxml");
    }

    @FXML
    private void showSend() {
        loadView("SendFiles.fxml");
    }

    @FXML
    private void showReceive() {
        loadView("ReceiverFiles.fxml");
    }

    @FXML
    private void showSettings() {
        loadView("Settings.fxml");
    }

    private void loadView(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/file/transfer/view/" + fxml));
            Parent view = loader.load();
            contentArea.setCenter(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
