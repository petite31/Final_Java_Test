package org.file.transfer.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.file.transfer.persistence.BlockFileManager;

import java.util.BitSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FileReceiveStatusController {

    @FXML
    private ListView<String> listTransfers;
    @FXML
    private Label lblOverallStatus;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @FXML
    public void initialize() {
        listTransfers.setCellFactory(param -> new TransferCell());
        scheduler.scheduleAtFixedRate(this::refreshStatus, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void refreshStatus() {
        Platform.runLater(() -> {
            java.util.Set<String> activeFiles = BlockFileManager.getInstance().getActiveFiles();

            // Add new
            for (String f : activeFiles) {
                if (!listTransfers.getItems().contains(f)) {
                    listTransfers.getItems().add(f);
                }
            }

            // Force redraw of cells
            listTransfers.refresh();

            lblOverallStatus.setText("Active Transfers: " + activeFiles.size());
        });
    }

    private static class TransferCell extends ListCell<String> {
        private final VBox root = new VBox(5);
        private final Label lblName = new Label();
        private final ProgressBar progressBar = new ProgressBar();
        private final Label lblStats = new Label();

        public TransferCell() {
            root.getChildren().addAll(lblName, progressBar, lblStats);
            progressBar.setMaxWidth(Double.MAX_VALUE);
        }

        @Override
        protected void updateItem(String fileName, boolean empty) {
            super.updateItem(fileName, empty);
            if (empty || fileName == null) {
                setGraphic(null);
            } else {
                lblName.setText(fileName);

                BlockFileManager.TransferState state = BlockFileManager.getInstance().getState(fileName);

                int received = state.receivedBlocks.cardinality();
                int total = state.totalBlocks;

                if (total > 0) {
                    double progress = (double) received / total;
                    progressBar.setProgress(progress);
                    lblStats.setText(String.format("Progress: %.1f%% (%d/%d blocks)", progress * 100, received, total));
                } else {
                    progressBar.setProgress(-1);
                    lblStats.setText("Waiting for metadata...");
                }

                setGraphic(root);
            }
        }
    }
}
