package org.file.transfer.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.file.transfer.service.TransferService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.file.transfer.service.TransferHistoryService;

public class SendFilesController {

    @FXML
    private VBox dropZone;
    @FXML
    private Button btnChoose;
    @FXML
    private Label lblSelectedFile;
    @FXML
    private TextField tfIp;
    @FXML
    private PasswordField tfPasskey;
    @FXML
    private Button btnSend;
    @FXML
    private Label lblStatus;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label lblSpeed;
    @FXML
    private Label lblTime;

    private List<File> selectedFiles = new ArrayList<>();

    @FXML
    public void initialize() {
        setupDragAndDrop();
    }

    private void setupDragAndDrop() {
        dropZone.setOnDragOver(event -> {
            if (event.getGestureSource() != dropZone && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        dropZone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                selectedFiles = new ArrayList<>(db.getFiles());
                updateFileSelection();
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    @FXML
    private void chooseFile() {
        FileChooser fileChooser = new FileChooser();
        List<File> files = fileChooser.showOpenMultipleDialog(null);

        if (files != null && !files.isEmpty()) {
            selectedFiles = new ArrayList<>(files);
            updateFileSelection();
        }
    }

    private void updateFileSelection() {
        if (selectedFiles.isEmpty()) {
            lblSelectedFile.setText("No file selected");
        } else if (selectedFiles.size() == 1) {
            lblSelectedFile.setText(selectedFiles.get(0).getName());
        } else {
            lblSelectedFile.setText(selectedFiles.size() + " files selected");
        }
        lblStatus.setText("Ready to send");
        progressBar.setProgress(0);
        lblSpeed.setText("0 MB/s");
        lblTime.setText("--:--");
    }

    @FXML
    private void sendFile() {
        if (selectedFiles == null || selectedFiles.isEmpty())
            return;

        String inputTarget = tfIp.getText().trim();
        String passkey = tfPasskey.getText();

        if (inputTarget.isEmpty() || passkey.isEmpty()) {
            lblStatus.setText("Invalid Input");
            return;
        }

        String ip = inputTarget;
        if (!inputTarget.matches(".*\\d+\\..*") && !inputTarget.contains(":")) {
            org.file.transfer.model.PeerInfo targetPeer = null;
            try {
                for (org.file.transfer.model.PeerInfo p : org.file.transfer.network.DiscoveryService.getInstance()
                        .getActivePeers()) {
                    if (p.getName().equalsIgnoreCase(inputTarget)) {
                        targetPeer = p;
                        break;
                    }
                }
            } catch (Exception e) {
            }

            if (targetPeer != null) {
                ip = targetPeer.getIp();
            } else {
                lblStatus.setText("User '" + inputTarget + "' not found.");
                return;
            }
        }

        List<File> filesToSend = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();

        for (File f : selectedFiles) {
            if (TransferHistoryService.getInstance().isDuplicate(ip, f)) {
                duplicates.add(f.getName());
            } else {
                filesToSend.add(f);
            }
        }

        if (!duplicates.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Duplicate Warning");
            alert.setHeaderText("Duplicate files detected");
            alert.setContentText("The following files have already been sent to " + ip + ":\n"
                    + String.join(", ", duplicates)
                    + "\n\nDo you want to send them again?");

            ButtonType btnYes = new ButtonType("Send All");
            ButtonType btnNo = new ButtonType("Skip Duplicates");
            ButtonType btnCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(btnYes, btnNo, btnCancel);

            var result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == btnYes) {
                    filesToSend = new ArrayList<>(selectedFiles);
                } else if (result.get() == btnNo) {
                    if (filesToSend.isEmpty()) {
                        lblStatus.setText("Cancelled (All duplicates)");
                        return;
                    }
                } else {
                    lblStatus.setText("Cancelled");
                    return;
                }
            } else {
                return;
            }
        } else {
            filesToSend = new ArrayList<>(selectedFiles);
        }

        lblStatus.setText("Sending " + filesToSend.size() + " files...");
        final List<File> finalFiles = filesToSend;
        final String finalIp = ip;

        new Thread(() -> {
            TransferService.getInstance().getTransferManager().authenticateAndSendFiles(finalFiles, finalIp, passkey,
                    this);
        }).start();
    }

    public void updateProgress(double percent, double speedMB, long remainingSeconds) {
        Platform.runLater(() -> {
            progressBar.setProgress(percent);
            lblSpeed.setText(String.format("%.2f MB/s", speedMB));
            lblTime.setText(formatTime(remainingSeconds));
        });
    }

    public void onTransferComplete() {
        Platform.runLater(() -> {
            lblStatus.setText("Completed");
            progressBar.setProgress(1.0);
            lblTime.setText("Done");
        });
    }

    public void onTransferFrozen() {
        Platform.runLater(() -> {
            lblStatus.setText("Frozen (Retrying...)");
        });
    }

    private String formatTime(long seconds) {
        if (seconds < 60)
            return seconds + "s";
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    public void setRecipientIp(String ip) {
        if (tfIp != null) {
            tfIp.setText(ip);
        }
    }

    public void setPasskey(String passkey) {
        if (tfPasskey != null) {
            tfPasskey.setText(passkey);
        }
    }

    public void onError(String message) {
        Platform.runLater(() -> {
            lblStatus.setText("Error: " + message);
        });
    }
}
