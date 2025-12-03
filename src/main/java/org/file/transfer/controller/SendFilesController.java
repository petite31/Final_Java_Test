package org.file.transfer.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.file.transfer.service.TransferService;

import java.io.File;

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

    private File selectedFile;

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
                selectedFile = db.getFiles().get(0);
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
        selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            updateFileSelection();
        }
    }

    private void updateFileSelection() {
        lblSelectedFile.setText(selectedFile.getName());
        lblStatus.setText("Ready to send");
        progressBar.setProgress(0);
        lblSpeed.setText("0 KB/s");
        lblTime.setText("--:--");
        // Reset UI state if needed
    }

    @FXML
    private void sendFile() {
        if (selectedFile == null)
            return;

        String ip = tfIp.getText();
        String passkey = tfPasskey.getText();

        // Basic validation
        if (ip.isEmpty() || passkey.isEmpty()) {
            lblStatus.setText("Invalid Input");
            return;
        }

        lblStatus.setText("Sending...");

        // Use a thread to send so we don't block UI
        new Thread(() -> {
            // We need to access TransferManager via Service
            // Note: We need to pass a callback to update UI
            // For this refactor, we might need to update TransferManager to accept a
            // listener
            // Or we can pass 'this' if we make SendFilesController implement an interface

            // TEMPORARY: We need to bridge the old TransferManager to this new controller
            // Ideally, TransferManager should take a 'TransferListener' interface

            TransferService.getInstance().getTransferManager().authenticateAndSend(selectedFile, ip, passkey, this);
        }).start();
    }

    // Callbacks called by TransferManager/FileSender
    public void updateProgress(double percent, double speedKB, long remainingSeconds) {
        Platform.runLater(() -> {
            progressBar.setProgress(percent);
            lblSpeed.setText(String.format("%.2f KB/s", speedKB));
            lblTime.setText(formatTime(remainingSeconds));
        });
    }

    public void onTransferComplete() {
        Platform.runLater(() -> {
            lblStatus.setText("Completed");
            progressBar.setProgress(1.0);
        });
    }

    public void onTransferFrozen() {
        Platform.runLater(() -> {
            // Do NOT show error. Just freeze.
            // Maybe update status text to indicate waiting, or just leave as is per
            // requirement
            lblStatus.setText("Sending..."); // Keep it looking active or just "..."
        });
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}
