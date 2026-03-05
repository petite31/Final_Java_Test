package org.file.transfer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.file.transfer.network.TransferManager;
import org.file.transfer.service.TransferService;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        TransferService.getInstance().initialize(new TransferManager());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/file/transfer/view/Login.fxml"));
        Parent root = loader.load();

        primaryStage.setTitle("File Transfer");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}