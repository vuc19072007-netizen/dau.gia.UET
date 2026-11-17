package client.controller;

import client.view.NavigationManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Màn hình đăng ký. Kiểm tra nhanh ở client (thiếu dữ liệu, mật khẩu nhập lại sai);
 * luật đầy đủ (10 số, 8–15 ký tự, trùng số điện thoại) do server kiểm tra.
 */
public class RegisterController extends BaseController {
    private static final String BIDDER = "Người mua (BIDDER)";
    private static final String SELLER = "Người bán (SELLER)";

    @FXML
    private TextField nameField;
    @FXML
    private TextField phoneField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmField;
    @FXML
    private TextField addressField;
    @FXML
    private ComboBox<String> roleBox;
    @FXML
    private Button registerButton;
    @FXML
    private Label messageLabel;

    @FXML
    private void initialize() {
        roleBox.setItems(FXCollections.observableArrayList(BIDDER, SELLER));
        roleBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void register() {
        String name = text(nameField);
        String phone = text(phoneField);
        String password = passwordField.getText();
        String address = text(addressField);

        if (name.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            showError(messageLabel, "Vui lòng nhập họ tên, số điện thoại và mật khẩu");
            return;
        }
        if (!password.equals(confirmField.getText())) {
            showError(messageLabel, "Mật khẩu nhập lại không khớp");
            return;
        }

        String role = SELLER.equals(roleBox.getValue()) ? "SELLER" : "BIDDER";
        registerButton.setDisable(true);

        session.run(() -> session.connect().register(name, phone, password, address, role), response -> {
            registerButton.setDisable(false);
            if (response.isSuccess()) {
                navigation.show(NavigationManager.LOGIN, "Đăng ký thành công. Hãy đăng nhập.");
            } else {
                showError(messageLabel, response.getErrorMessage());
            }
        }, error -> {
            registerButton.setDisable(false);
            showError(messageLabel, describe(error));
        });
    }

    private static String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    @FXML
    private void backToLogin() {
        navigation.show(NavigationManager.LOGIN);
    }
}
