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

import java.io.IOException;

public class RegisterController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button registerButton;
    @FXML
    private Label statusLabel;

    private final AuthService authService = new AuthService();

    @FXML
    private void handleRegister() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String role = "USER";

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please fill in all fields.");
            return;
        }

        StringBuilder message = new StringBuilder();
        if (authService.register(username, password, role, message)) {
            statusLabel.setStyle("-fx-text-fill: green;");
            statusLabel.setText("Registration successful! Redirecting to login...");
        } else {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText(message.toString().isEmpty() ? "Registration failed" : message.toString());
        }
    }

    @FXML
    private void switchToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/file/transfer/view/Login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) registerButton.getScene().getWindow();
            stage.setTitle("FileTransfer P2P - Login");
            stage.setScene(new Scene(root));
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Error loading view.");
        }
    }
}
