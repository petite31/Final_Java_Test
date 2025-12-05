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
    public void initialize() {
        refreshIp();
        lblPasskey.setText(TransferService.getInstance().getMyPasskey());
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
        // In a real app, call service to regenerate.
        // For now, we just refresh the display assuming service might have changed it
        // or we implement it later.
        TransferService.getInstance().regeneratePasskey();
        lblPasskey.setText(TransferService.getInstance().getMyPasskey());
    }
}
