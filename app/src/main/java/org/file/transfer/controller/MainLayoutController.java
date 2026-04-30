package org.file.transfer.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import org.file.transfer.network.DiscoveryService;

import java.io.IOException;

public class MainLayoutController {

    @FXML
    private BorderPane mainLayout;
    @FXML
    private BorderPane contentArea;

    private static MainLayoutController instance;

    public static MainLayoutController getInstance() {
        return instance;
    }

    @FXML
    public void initialize() {
        instance = this;

        DiscoveryService.getInstance(6969).start();

        showHome();

        try {
            DiscoveryService.getInstance()
                    .setOnConnectionRequested((senderIp, senderName, acceptAction, rejectAction) -> {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Yêu cầu kết nối (Nearby Share)");
                        alert.setHeaderText("Có thiết bị muốn kết nối với bạn!");
                        alert.setContentText("Thiết bị: " + senderName + "\nIP: " + senderIp +
                                "\n\nBạn có muốn chấp nhận kết nối và chia sẻ Passkey của mình không?");

                        // Dùng if-else truyền thống, không dùng .ifPresent(...) nữa
                        java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
                        if (result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK) {
                            acceptAction.run(); // Sẽ gửi PASSKEY_ACCEPT về lại
                        } else {
                            rejectAction.run(); // Sẽ gửi CONNECTION_REFUSED
                        }
                    });

            DiscoveryService.getInstance().setOnPasskeyAccepted((ip, passkey) -> {
                // Tự động chuyển sang màn hình SendFiles và điền thông tin
                showSendWithPeer(ip, passkey);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void showHome() {
        loadView("Home.fxml", null);
    }

    @FXML
    private void showSend() {
        loadView("SendFiles.fxml", null);
    }

    @FXML
    private void showReceive() {
        loadView("ReceiverFiles.fxml", null);
    }

    @FXML
    private void showNearbyDevices() {
        loadView("nearby_devices.fxml", null);
    }

    @FXML
    private void showSettings() {
        loadView("Settings.fxml", null);
    }

    @FXML
    private void showCommunity(){ loadView("Community.fxml", null);}

    public void showSendWithPeer(String ip, String passkey) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/file/transfer/view/SendFiles.fxml"));
            Parent view = loader.load();

            SendFilesController controller = loader.getController();
            controller.setRecipientIp(ip);
            controller.setPasskey(passkey);

            contentArea.setCenter(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadView(String fxml, Object data) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/file/transfer/view/" + fxml));
            Parent view = loader.load();
            contentArea.setCenter(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
