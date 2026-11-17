package client.controller;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import client.ClientSession;
import client.network.AuctionClient;
import client.view.NavigationManager;
import common.network.NetworkMessage;
import common.network.payload.UserDTO;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;

/**
 * Lớp cha của mọi controller (phần "C" trong MVC).
 * Gom các việc lặp lại: giữ NavigationManager và ClientSession, chạy yêu cầu mạng
 * trên luồng nền, hiển thị thông báo.
 * <p>
 * Controller chỉ đọc dữ liệu từ View, gọi server qua AuctionClient và đổ kết quả
 * ngược lên View. Luật nghiệp vụ (giá hợp lệ, đủ tiền...) nằm ở server.
 */
public abstract class BaseController {
    protected NavigationManager navigation;
    protected ClientSession session;

    /** NavigationManager gọi ngay sau khi tạo controller. */
    public final void init(NavigationManager navigation, ClientSession session) {
        this.navigation = navigation;
        this.session = session;
    }

    /** Gọi khi màn hình được hiển thị. parameter có thể null. */
    public void onShow(Object parameter) {
    }

    /** Gọi trước khi rời màn hình: gỡ listener, dừng đồng hồ... */
    public void onHide() {
    }

    protected AuctionClient client() {
        return session.client();
    }

    protected UserDTO user() {
        return session.user();
    }

    /** Chạy yêu cầu mạng trên luồng nền; lỗi thì hiện lên label (nếu có). */
    protected <T> void run(Callable<T> work, Consumer<T> onSuccess, Label errorLabel) {
        session.run(work, onSuccess, error -> showError(errorLabel, describe(error)));
    }

    /**
     * Gửi một yêu cầu trả về NetworkMessage. Server báo thành công thì gọi
     * onSuccess với payload; thất bại thì hiện câu báo lỗi của server.
     */
    protected void request(Callable<NetworkMessage> work, Consumer<Object> onSuccess, Label messageLabel) {
        run(work, response -> {
            if (response.isSuccess()) {
                onSuccess.accept(response.getPayload());
            } else {
                showError(messageLabel, response.getErrorMessage());
            }
        }, messageLabel);
    }

    protected static String describe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (root instanceof java.net.ConnectException) {
            return "Không kết nối được tới server. Hãy chạy server trước.";
        }
        if (root instanceof java.util.concurrent.TimeoutException) {
            return "Server không phản hồi.";
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    protected static void showError(Label label, String message) {
        if (label == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            alert.setHeaderText(null);
            alert.show();
            return;
        }
        label.getStyleClass().removeAll("message-success");
        if (!label.getStyleClass().contains("message-error")) {
            label.getStyleClass().add("message-error");
        }
        label.setText(message);
    }

    protected static void showSuccess(Label label, String message) {
        label.getStyleClass().removeAll("message-error");
        if (!label.getStyleClass().contains("message-success")) {
            label.getStyleClass().add("message-success");
        }
        label.setText(message);
    }

    /** Hộp thoại xác nhận. Trả về true nếu người dùng chọn OK. */
    protected static boolean confirm(String question) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
}
