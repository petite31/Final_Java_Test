package org.file.transfer.controller;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.stage.DirectoryChooser;
import org.file.transfer.transport.TransportManager;
import org.file.transfer.utils.SettingsManager;

import java.io.File;

public class SettingsController {

    @FXML
    private Label lblSavePath;
    @FXML
    private RadioButton rbLan;
    @FXML
    private RadioButton rbExternal;

    @FXML
    private CheckBox cbUdp;
    @FXML
    private CheckBox cbBluetooth;
    @FXML
    private CheckBox cbWifiDirect;

    @FXML
    public void initialize() {
        lblSavePath.setText(SettingsManager.getInstance().getDownloadDirectory());
        if (SettingsManager.getInstance().isAllowExternal()) {
            rbExternal.setSelected(true);
        } else {
            rbLan.setSelected(true);
        }

        rbLan.selectedProperty().addListener((obs, old, isLan) -> {
            SettingsManager.getInstance().setAllowExternal(!isLan);
        });

        // Transport Toggles
        if (cbUdp != null) {
            cbUdp.setSelected(TransportManager.getInstance().isUdpEnabled());
            cbUdp.selectedProperty().addListener((o, old, val) -> {
                TransportManager.getInstance().setUseUdp(val);
            });
        }

        if (cbBluetooth != null) {
            cbBluetooth.setSelected(TransportManager.getInstance().isBluetoothEnabled());
            cbBluetooth.selectedProperty().addListener((o, old, val) -> {
                TransportManager.getInstance().setUseBluetooth(val);
            });
        }
    }

    @FXML
    private void changeSaveLocation() {
        DirectoryChooser dc = new DirectoryChooser();
        File f = dc.showDialog(null);
        if (f != null) {
            SettingsManager.getInstance().setDownloadDirectory(f.getAbsolutePath());
            lblSavePath.setText(f.getAbsolutePath());
        }
    }

    @FXML
    private void testNetwork() {
        // Implement ping test logic here
    }
}
