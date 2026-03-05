package org.file.transfer.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.file.transfer.service.TransferService;
import static org.file.transfer.network.IP_Port_Managment.getBestLocalIp;

import java.net.InetAddress;

public class HomeController {

    @FXML
    private Label lblPasskey;
    @FXML
    private Label lblIp;
    @FXML
    private Label lblHostname;
    @FXML
    private Label lblUsername;

    @FXML
    public void initialize() {
        refreshIp();
        lblPasskey.setText(TransferService.getInstance().getMyPasskey());

        String username = org.file.transfer.service.UserSession.getInstance().getUsername();
        if (username == null || username.isEmpty()) {
            username = System.getProperty("user.name", "Unknown");
        }
        lblUsername.setText(username);
    }

    @FXML
    private void refreshIp() {
        lblIp.setText("Detecting...");

        new Thread(() -> {
            String ip = getBestLocalIp();
            String hostname = "Unknown";
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
            }

            String finalHostname = hostname;
            Platform.runLater(() -> {
                lblIp.setText(ip);
                lblHostname.setText(finalHostname);
            });
        }).start();
    }

    @FXML
    private void generateNewPasskey() {
        TransferService.getInstance().regeneratePasskey();
        lblPasskey.setText(TransferService.getInstance().getMyPasskey());
    }
}
