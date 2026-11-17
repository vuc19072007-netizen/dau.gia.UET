package client.view;

import java.io.IOException;
import java.net.URL;

import client.ClientSession;
import client.controller.BaseController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Chuyển màn hình cho toàn bộ client (phần "V" và cách nối View với Controller).
 * <p>
 * Mỗi màn hình là một file FXML trong resources/view. Khi chuyển màn hình:
 * 1. FXMLLoader đọc file FXML, tạo các control và controller ghi trong fx:controller.
 * 2. controllerFactory tạo controller rồi "tiêm" NavigationManager và ClientSession
 *    vào, nên controller không phải tự tìm hai đối tượng này.
 * 3. Controller của màn hình cũ được gọi onHide() để gỡ listener mạng.
 * 4. Controller mới được gọi onShow(parameter), ví dụ mã phiên cần mở.
 */
public class NavigationManager {
    public static final String LOGIN = "login.fxml";
    public static final String REGISTER = "register.fxml";
    public static final String AUCTION_LIST = "auction_list.fxml";
    public static final String LIVE_AUCTION = "live_auction.fxml";
    public static final String SELLER_DASHBOARD = "seller_dashboard.fxml";
    public static final String ADMIN = "admin.fxml";

    private static final String STYLESHEET = "/view/styles.css";

    private final Stage stage;
    private final ClientSession session;
    private BaseController current;

    public NavigationManager(Stage stage, ClientSession session) {
        this.stage = stage;
        this.session = session;
    }

    /** Mở màn hình không cần tham số. */
    public void show(String fxml) {
        show(fxml, null);
    }

    /** Mở màn hình và truyền tham số cho onShow (ví dụ mã phiên). */
    public void show(String fxml, Object parameter) {
        URL location = NavigationManager.class.getResource("/view/" + fxml);

        if (location == null) {
            throw new IllegalArgumentException("Không tìm thấy màn hình: " + fxml);
        }

        FXMLLoader loader = new FXMLLoader(location);
        loader.setControllerFactory(this::createController);

        Parent root;
        try {
            root = loader.load();
        } catch (IOException error) {
            throw new IllegalStateException("Không tải được màn hình " + fxml, error);
        }

        if (current != null) {
            current.onHide();
        }
        current = loader.getController();

        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root);
            scene.getStylesheets().add(NavigationManager.class.getResource(STYLESHEET).toExternalForm());
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }

        current.onShow(parameter);
    }

    // Tạo controller bằng constructor không tham số rồi tiêm phụ thuộc.
    private Object createController(Class<?> type) {
        try {
            Object controller = type.getDeclaredConstructor().newInstance();
            if (controller instanceof BaseController base) {
                base.init(this, session);
            }
            return controller;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Không tạo được controller " + type.getName(), error);
        }
    }

    public Stage stage() {
        return stage;
    }

    public BaseController currentController() {
        return current;
    }

    /** Gọi khi đóng cửa sổ. */
    public void shutdown() {
        if (current != null) {
            current.onHide();
        }
    }
}
