package client.controller;

import client.network.AuctionClient;
import client.view.NavigationManager;
import common.network.NetworkMessage;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Màn hình đăng nhập: kết nối server (nếu chưa), gửi số điện thoại và mật khẩu.
 * Thành công thì sang danh sách phiên; thất bại thì hiện lý do server trả về.
 */
public class LoginController extends BaseController {
    @FXML
    private TextField phoneField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Label messageLabel;
    @FXML
    private Label serverLabel;

    @Override
    public void onShow(Object parameter) {
        serverLabel.setText("Server: " + session.host() + ":" + session.port());
        if (parameter instanceof String notice) {
            showSuccess(messageLabel, notice);
        }
    }

    @FXML
    private void login() {
        String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (phone.isEmpty() || password.isEmpty()) {
            showError(messageLabel, "Vui lòng nhập số điện thoại và mật khẩu");
            return;
        }

        loginButton.setDisable(true);
        messageLabel.setText("Đang đăng nhập...");

        session.run(() -> {
            AuctionClient client = session.connect();
            client.setOnDisconnected(this::onConnectionLost);
            return client.loginRequest(phone, password);
        }, this::onLoginResult, error -> {
            loginButton.setDisable(false);
            showError(messageLabel, describe(error));
        });
    }

    private void onLoginResult(NetworkMessage response) {
        loginButton.setDisable(false);
        if (response.isSuccess()) {
            navigation.show(NavigationManager.AUCTION_LIST);
        } else {
            showError(messageLabel, response.getErrorMessage());
        }
    }

    /*
     * Gọi từ luồng mạng khi mất kết nối. Chuyển về luồng giao diện, quay lại màn hình
     * đăng nhập (trừ khi đang ở đó) và báo cho người dùng.
     */
    private void onConnectionLost() {
        Platform.runLater(() -> {
            if (navigation.currentController() instanceof LoginController) {
                return;
            }
            navigation.show(NavigationManager.LOGIN, null);
            if (navigation.currentController() instanceof LoginController login) {
                showError(login.messageLabel, "Mất kết nối tới server. Vui lòng đăng nhập lại.");
            }
        });
    }

    @FXML
    private void openRegister() {
        navigation.show(NavigationManager.REGISTER);
    }
}
