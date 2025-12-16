package org.file.transfer.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.file.transfer.model.TransferRecord;
import org.file.transfer.service.HistoryService;
import org.file.transfer.service.UserSession;
import org.file.transfer.utils.SettingsManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.stream.Collectors;

public class ReceiverFilesController {

    @FXML
    private TableView<TransferRecord> allTable;
    @FXML
    private TableColumn<TransferRecord, String> colType;
    @FXML
    private TableColumn<TransferRecord, String> colPeer;
    @FXML
    private TableColumn<TransferRecord, String> colFile;
    @FXML
    private TableColumn<TransferRecord, String> colSize;
    @FXML
    private TableColumn<TransferRecord, String> colTime;
    @FXML
    private TableColumn<TransferRecord, String> colStatus;
    @FXML
    private TableColumn<TransferRecord, Void> colAction;

    @FXML
    private TableView<TransferRecord> sentTable;
    @FXML
    private TableColumn<TransferRecord, String> colSentPeer;
    @FXML
    private TableColumn<TransferRecord, String> colSentFile;
    @FXML
    private TableColumn<TransferRecord, String> colSentSize;
    @FXML
    private TableColumn<TransferRecord, String> colSentTime;
    @FXML
    private TableColumn<TransferRecord, String> colSentStatus;
    @FXML
    private TableColumn<TransferRecord, Void> colSentAction;

    @FXML
    private TableView<TransferRecord> receivedTable;
    @FXML
    private TableColumn<TransferRecord, String> colReceivedPeer;
    @FXML
    private TableColumn<TransferRecord, String> colReceivedFile;
    @FXML
    private TableColumn<TransferRecord, String> colReceivedSize;
    @FXML
    private TableColumn<TransferRecord, String> colReceivedTime;
    @FXML
    private TableColumn<TransferRecord, String> colReceivedStatus;
    @FXML
    private TableColumn<TransferRecord, String> colReceivedPath;
    @FXML
    private TableColumn<TransferRecord, Void> colReceivedAction;

    private final ObservableList<TransferRecord> allData = FXCollections.observableArrayList();
    private final ObservableList<TransferRecord> sentData = FXCollections.observableArrayList();
    private final ObservableList<TransferRecord> receivedData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable(allTable, colType, colPeer, colFile, colSize, colTime, colStatus, null, colAction, true);
        setupTable(sentTable, null, colSentPeer, colSentFile, colSentSize, colSentTime, colSentStatus, null,
                colSentAction,
                false);
        setupTable(receivedTable, null, colReceivedPeer, colReceivedFile, colReceivedSize, colReceivedTime,
                colReceivedStatus, colReceivedPath, colReceivedAction, false);

        refresh();
    }

    private void setupTable(TableView<TransferRecord> table,
            TableColumn<TransferRecord, String> typeCol,
            TableColumn<TransferRecord, String> peerCol,
            TableColumn<TransferRecord, String> fileCol,
            TableColumn<TransferRecord, String> sizeCol,
            TableColumn<TransferRecord, String> timeCol,
            TableColumn<TransferRecord, String> statusCol,
            TableColumn<TransferRecord, String> pathCol,
            TableColumn<TransferRecord, Void> actionCol,
            boolean showType) {

        String myName = UserSession.getInstance().getUsername();
        if (myName == null)
            myName = "Unknown";
        final String currentUsername = myName;

        if (showType && typeCol != null) {
            typeCol.setCellValueFactory(cell -> {
                boolean isSender = cell.getValue().getSender().equals(currentUsername);
                return new SimpleStringProperty(isSender ? "Sent" : "Received");
            });
        }

        peerCol.setCellValueFactory(cell -> {
            boolean isSender = cell.getValue().getSender().equals(currentUsername);
            return new SimpleStringProperty(isSender ? cell.getValue().getReceiver() : cell.getValue().getSender());
        });

        fileCol.setCellValueFactory(cell -> cell.getValue().fileNameProperty());
        fileCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(item);
                    // Check file existence in Download Directory
                    // Note: This logic assumes received file is in download dir.
                    // For sent file, we check existence too, although source might have moved.
                    // The requirement stresses "file đang tồn tại trong thư mục đã chọn" (exists in
                    // selected dir).
                    File f = new File(SettingsManager.getInstance().getDownloadDirectory(), item);
                    if (f.exists()) {
                        setTextFill(Color.GREEN);
                        setTooltip(new Tooltip("File exists locally"));
                    } else {
                        setTextFill(Color.RED);
                        setTooltip(new Tooltip("File missing"));
                    }
                }
            }
        });

        sizeCol.setCellValueFactory(cell -> new SimpleStringProperty(formatSize(cell.getValue().getSize())));

        timeCol.setCellValueFactory(cell -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return new SimpleStringProperty(sdf.format(cell.getValue().getTimestamp()));
        });

        statusCol.setCellValueFactory(cell -> cell.getValue().statusProperty());
        // Color status logic if needed (e.g. Success vs Failed)

        if (pathCol != null) {
            pathCol.setCellValueFactory(cell -> cell.getValue().filePathProperty());
        }

        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button btnOpen = new Button("Open");
            private final Button btnDelete = new Button("Delete");
            private final HBox pane = new HBox(5, btnOpen, btnDelete);

            {
                // Style Open Button
                btnOpen.getStyleClass().add("button-icon");
                btnOpen.setStyle("-fx-text-fill: #007a82; -fx-font-size: 12px; -fx-font-weight: bold;");
                btnOpen.setOnAction(event -> {
                    TransferRecord record = getTableView().getItems().get(getIndex());
                    handleOpen(record);
                });

                // Style Delete Button
                btnDelete.getStyleClass().add("button-icon");
                btnDelete.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
                btnDelete.setOnAction(event -> {
                    TransferRecord record = getTableView().getItems().get(getIndex());
                    handleDelete(record);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    // Check if file exists to enable/disable Open
                    TransferRecord record = getTableView().getItems().get(getIndex());
                    if (record != null) {
                        File f;
                        // Use stored path if available, else fallback
                        String storedPath = record.getFilePath();
                        if (storedPath != null && !storedPath.isEmpty()) {
                            f = new File(storedPath);
                        } else {
                            f = new File(SettingsManager.getInstance().getDownloadDirectory(), record.getFileName());
                        }
                        btnOpen.setDisable(!f.exists());
                    }
                    setGraphic(pane);
                }
            }
        });

        if (table == allTable)
            table.setItems(allData);
        else if (table == sentTable)
            table.setItems(sentData);
        else if (table == receivedTable)
            table.setItems(receivedData);
    }

    @FXML
    private void refresh() {
        String username = UserSession.getInstance().getUsername();
        if (username == null)
            return;

        // Run fetch on background thread
        new Thread(() -> {
            var history = HistoryService.getInstance().getHistory(username);

            javafx.application.Platform.runLater(() -> {
                allData.setAll(history);
                sentData.setAll(history.stream()
                        .filter(r -> r.getSender().equals(username))
                        .collect(Collectors.toList()));
                receivedData.setAll(history.stream()
                        .filter(r -> r.getReceiver().equals(username))
                        .collect(Collectors.toList()));
            });
        }).start();
    }

    private void handleOpen(TransferRecord record) {
        try {
            File file;
            String storedPath = record.getFilePath();
            if (storedPath != null && !storedPath.isEmpty()) {
                file = new File(storedPath);
            } else {
                file = new File(SettingsManager.getInstance().getDownloadDirectory(), record.getFileName());
            }

            if (file.exists()) {
                java.awt.Desktop.getDesktop().open(file);
            } else {
                new Alert(Alert.AlertType.ERROR, "File not found: " + file.getAbsolutePath()).show();
            }
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Could not open file: " + e.getMessage()).show();
        }
    }

    private void handleDelete(TransferRecord record) {
        String username = UserSession.getInstance().getUsername();
        if (HistoryService.getInstance().deleteHistory(record.getId(), username)) {
            refresh();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to delete history item.");
            alert.show();
        }
    }

    @FXML
    private void handleDeleteAll() {
        String username = UserSession.getInstance().getUsername();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete all history?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                if (HistoryService.getInstance().deleteAllHistory(username)) {
                    refresh();
                } else {
                    new Alert(Alert.AlertType.ERROR, "Failed to delete all history.").show();
                }
            }
        });
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
