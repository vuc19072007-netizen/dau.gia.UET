package client;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import client.network.AuctionClient;
import common.network.payload.UserDTO;
import javafx.application.Platform;

/**
 * Trạng thái của một lần chạy client: kết nối tới server và người đang đăng nhập.
 * Các controller dùng chung một ClientSession (NavigationManager truyền vào).
 * <p>
 * Mọi yêu cầu mạng chạy trên MỘT luồng nền riêng ({@link #run}):
 * - Không chặn luồng giao diện (JavaFX Application Thread), cửa sổ không bị đơ.
 * - Một luồng duy nhất nên các yêu cầu đi lên server đúng thứ tự gửi.
 * Kết quả được đưa về luồng giao diện bằng Platform.runLater().
 */
public class ClientSession {
    private final String host;
    private final int port;
    private AuctionClient client;

    private final ExecutorService network = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "client-requests");
        thread.setDaemon(true);
        return thread;
    });

    public ClientSession(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Chạy work trên luồng nền. Thành công thì gọi onSuccess, lỗi thì gọi onError,
     * cả hai đều trên luồng giao diện nên được phép sửa control.
     */
    public <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        network.submit(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception error) {
                Platform.runLater(() -> onError.accept(error));
            }
        });
    }

    /**
     * Kết nối tới server nếu chưa kết nối. Gọi trên luồng nền.
     * Mỗi lần kết nối lại tạo AuctionClient mới (client cũ đã đóng socket).
     */
    public synchronized AuctionClient connect() throws Exception {
        if (client == null || !client.isConnected()) {
            AuctionClient fresh = new AuctionClient(host, port);
            fresh.connect();
            client = fresh;
        }
        return client;
    }

    public synchronized AuctionClient client() {
        return client;
    }

    public UserDTO user() {
        AuctionClient current = client();
        return current == null ? null : current.getCurrentUser();
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    // Đóng kết nối khi tắt ứng dụng.
    public synchronized void close() {
        if (client != null) {
            client.setOnDisconnected(null);
            client.disconnect();
        }
        network.shutdownNow();
    }
}
