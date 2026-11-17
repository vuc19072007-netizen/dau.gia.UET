package app;

import client.ClientSession;
import client.view.NavigationManager;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Ứng dụng client JavaFX của hệ thống đấu giá.
 * <p>
 * Client không giữ dữ liệu nghiệp vụ: mọi thao tác (đăng nhập, đặt giá, tạo phiên...)
 * gửi qua Socket tới server, nên nhiều client trên nhiều máy cùng thấy một trạng thái.
 * <p>
 * Địa chỉ server mặc định localhost:8888, đổi bằng tham số dòng lệnh:
 * --host=192.168.1.10 --port=9000
 * hoặc thuộc tính hệ thống -Dauction.host=... -Dauction.port=...
 */
public class AuctionApp extends Application {
    private ClientSession session;
    private NavigationManager navigation;

    @Override
    public void start(Stage stage) {
        String host = parameter("host", System.getProperty("auction.host", "localhost"));
        int port = Integer.parseInt(parameter("port", System.getProperty("auction.port", "8888")));

        session = new ClientSession(host, port);
        navigation = new NavigationManager(stage, session);

        stage.setTitle("Đấu giá UET");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        navigation.show(NavigationManager.LOGIN);
        stage.show();
    }

    private String parameter(String name, String fallback) {
        if (getParameters() == null) {
            return fallback;
        }
        String value = getParameters().getNamed().get(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public void stop() {
        if (navigation != null) {
            navigation.shutdown();
        }
        if (session != null) {
            session.close();
        }
    }

    public NavigationManager navigation() {
        return navigation;
    }

    public ClientSession session() {
        return session;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
