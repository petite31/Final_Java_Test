package org.file.transfer.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import org.file.transfer.utils.SettingsManager;

import java.io.File;

public class ReceiverFilesController {

    @FXML
    private ListView<String> listView;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private TilePane tilePane;
    @FXML
    private ToggleButton btnList;
    @FXML
    private ToggleButton btnGrid3;
    @FXML
    private ToggleButton btnGrid4;

    @FXML
    public void initialize() {
        refresh();
    }

    @FXML
    private void viewList() {
        listView.setVisible(true);
        scrollPane.setVisible(false);
    }

    @FXML
    private void viewGrid3() {
        listView.setVisible(false);
        scrollPane.setVisible(true);
        tilePane.setPrefColumns(3);
        refresh();
    }

    @FXML
    private void viewGrid4() {
        listView.setVisible(false);
        scrollPane.setVisible(true);
        tilePane.setPrefColumns(4);
        refresh();
    }

    @FXML
    private void refresh() {
        File dir = new File(SettingsManager.getInstance().getDownloadDirectory());
        File[] files = dir.listFiles();

        if (files == null)
            return;

        // List View
        listView.getItems().clear();
        listView.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    File f = new File(SettingsManager.getInstance().getDownloadDirectory(), item);
                    setGraphic(createListRow(f));
                }
            }
        });

        for (File f : files) {
            if (f.isFile())
                listView.getItems().add(f.getName());
        }

        // Grid View
        tilePane.getChildren().clear();
        for (File f : files) {
            if (f.isFile())
                tilePane.getChildren().add(createGridCard(f));
        }
    }

    private HBox createListRow(File file) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("📄");
        icon.setStyle("-fx-font-size: 20px;");

        VBox info = new VBox(2);
        Label name = new Label(file.getName());
        name.setStyle("-fx-font-weight: bold;");
        Label meta = new Label(formatSize(file.length()) + " • Method: P2P");
        meta.setStyle("-fx-text-fill: #A3AED0; -fx-font-size: 11px;");
        info.getChildren().addAll(name, meta);

        HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);

        Button btnView = new Button("View");
        btnView.getStyleClass().add("button-action");
        btnView.setOnAction(e -> previewFile(file));

        Button btnOpen = new Button("Open");
        btnOpen.getStyleClass().add("button-action");
        btnOpen.setOnAction(e -> openFile(file));

        row.getChildren().addAll(icon, info, btnView, btnOpen);
        return row;
    }

    private VBox createGridCard(File file) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(150, 180);

        Label icon = new Label("📄");
        icon.setStyle("-fx-font-size: 40px;");

        Label name = new Label(file.getName());
        name.setWrapText(true);
        name.setAlignment(Pos.CENTER);

        HBox actions = new HBox(5);
        actions.setAlignment(Pos.CENTER);
        Button btnView = new Button("👁");
        btnView.setOnAction(e -> previewFile(file));
        Button btnOpen = new Button("📂");
        btnOpen.setOnAction(e -> openFile(file));
        actions.getChildren().addAll(btnView, btnOpen);

        card.getChildren().addAll(icon, name, actions);
        return card;
    }

    private void previewFile(File file) {
        // Simple preview dialog
        Dialog<Void> d = new Dialog<>();
        d.setTitle("Preview " + file.getName());
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.show();
    }

    private void openFile(File file) {
        try {
            java.awt.Desktop.getDesktop().open(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void openFolder() {
        try {
            java.awt.Desktop.getDesktop().open(new File(SettingsManager.getInstance().getDownloadDirectory()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatSize(long bytes) {
        return bytes / 1024 + " KB";
    }
}
