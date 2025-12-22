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
    }
}
