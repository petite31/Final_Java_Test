package org.file.transfer.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.file.transfer.service.AuthService;
import org.file.transfer.service.UserSession;
import org.file.transfer.util.AccountManager;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Label statusLabel;

    private final AuthService authService = new AuthService();

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter username and password.");
            return;
        }

        StringBuilder message = new StringBuilder();
        if (authService.login(username, password, message)) {
            // Update Session
            UserSession.getInstance().login(username, "USER");
            // Save to XML
            AccountManager.saveUsername(username);

            statusLabel.setStyle("-fx-text-fill: green;");
            statusLabel.setText("Login successful!");
            loadView("/org/file/transfer/view/MainLayout.fxml", "FileTransfer P2P - " + username);
        } else {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText(message.toString().isEmpty() ? "Login failed" : message.toString());
        }
    }

    @FXML
    private void switchToRegister() {
        loadView("/org/file/transfer/view/Register.fxml", "FileTransfer P2P - Register");
    }

    private void loadView(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Error loading view.");
        }
    }
}
