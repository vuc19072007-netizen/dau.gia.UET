package server.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import common.network.NetworkMessage;
import model.auction.Auction;
import service.AuctionManager;

/**
 * Lớp điều phối mạng Socket Server chính trong hệ thống Đấu giá Trực tuyến.
 * Quản lý ServerSocket, Thread Pool kết nối các Client, lưu danh sách ClientHandler hoạt động
 * và phân phối thông điệp phát sóng (Broadcast) theo thời gian thực (Realtime).
 */
public class AuctionServer {
    private static final Logger LOGGER = Logger.getLogger(AuctionServer.class.getName());

    public static final int DEFAULT_PORT = 8888;

    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService clientThreadPool;
    private final List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
    private final AuctionNetworkBroadcaster broadcaster;

    private volatile boolean running = false;
    private Thread acceptorThread;

    public AuctionServer() {
        this(DEFAULT_PORT);
    }

    public AuctionServer(int port) {
        this.port = port;
        // Dùng CachedThreadPool để tự động mở rộng theo số lượng Client kết nối
        // và tái sử dụng các Thread đã giải phóng.
        this.clientThreadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("client-handler-" + t.threadId());
            t.setDaemon(true);
            return t;
        });
        this.broadcaster = new AuctionNetworkBroadcaster(this);
    }

    /**
     * Khởi động Socket Server, mở ServerSocket và bắt đầu chấp nhận kết nối.
     */
    public synchronized void start() throws IOException {
        if (running) {
            LOGGER.warning("AuctionServer đã đang chạy!");
            return;
        }

        // Đảm bảo đồng hồ cập nhật phiên của AuctionManager đã được bật
        AuctionManager.getInstance().startClock();

        // Gắn Broadcaster vào tất cả các phiên hiện có để lắng nghe sự kiện
        for (Auction auction : AuctionManager.getInstance().getAllAuction()) {
            attachAuction(auction);
        }

        serverSocket = new ServerSocket(port);
        running = true;

        LOGGER.info(() -> "AuctionServer đã khởi động thành công trên cổng: " + port);

        // Luồng chấp nhận kết nối từ Client (Acceptor Loop)
        acceptorThread = new Thread(this::listenForClients, "server-acceptor");
        acceptorThread.setDaemon(true);
        acceptorThread.start();
    }

    private void listenForClients() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info(() -> "Client mới kết nối từ: " + clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(this, clientSocket);
                registerClient(handler);
                clientThreadPool.submit(handler);

            } catch (SocketException e) {
                if (!running) {
                    LOGGER.info("ServerSocket đã đóng khi dừng server.");
                    break;
                }
                LOGGER.log(Level.WARNING, "Lỗi Socket khi chấp nhận Client: " + e.getMessage(), e);
            } catch (IOException e) {
                if (running) {
                    LOGGER.log(Level.SEVERE, "Lỗi I/O khi chấp nhận kết nối Client", e);
                }
            }
        }
    }

    /**
     * Dừng Socket Server, đóng tất cả các kết nối Client và giải phóng Thread Pool.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        LOGGER.info("Đang dừng AuctionServer...");
        running = false;

        // Đóng ServerSocket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Lỗi khi đóng ServerSocket", e);
            }
        }

        // Đóng toàn bộ các ClientHandler đang hoạt động
        for (ClientHandler client : connectedClients) {
            client.close();
        }
        connectedClients.clear();

        // Tắt Thread Pool
        clientThreadPool.shutdown();
        try {
            if (!clientThreadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                clientThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            clientThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("AuctionServer đã dừng hoàn toàn.");
    }

    /**
     * Phát sóng thông điệp (Broadcast) tới toàn bộ Client đang kết nối.
     *
     * @param message Gói tin NetworkMessage cần gửi
     */
    public void broadcast(NetworkMessage message) {
        broadcast(message, null);
    }

    /**
     * Phát sóng thông điệp tới toàn bộ Client ngoại trừ một Client cụ thể (ví dụ Client vừa gửi bid).
     *
     * @param message Gói tin NetworkMessage cần gửi
     * @param excludeClient ClientHandler cần loại trừ (hoặc null nếu gửi cho tất cả)
     */
    public void broadcast(NetworkMessage message, ClientHandler excludeClient) {
        if (message == null) return;

        for (ClientHandler client : connectedClients) {
            if (excludeClient != null && client.equals(excludeClient)) {
                continue;
            }
            try {
                client.sendMessage(message);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Không thể gửi broadcast tới client: " + client, e);
            }
        }
    }

    /**
     * Đăng ký một ClientHandler vào danh sách kết nối.
     */
    public void registerClient(ClientHandler client) {
        if (client != null && !connectedClients.contains(client)) {
            connectedClients.add(client);
            LOGGER.info(() -> "Đã đăng ký Client. Tổng số Client đang kết nối: " + connectedClients.size());
        }
    }

    /**
     * Hủy đăng ký ClientHandler khi ngắt kết nối.
     */
    public void unregisterClient(ClientHandler client) {
        if (client != null) {
            connectedClients.remove(client);
            LOGGER.info(() -> "Đã hủy đăng ký Client. Tổng số Client còn lại: " + connectedClients.size());
        }
    }

    /**
     * Gắn bộ quan sát Realtime Broadcaster vào một phiên đấu giá.
     */
    public void attachAuction(Auction auction) {
        if (auction != null && broadcaster != null) {
            auction.addObserver(broadcaster);
        }
    }

    public int getPort() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            return serverSocket.getLocalPort();
        }
        return port;
    }

    public boolean isRunning() {
        return running;
    }

    public int getConnectedClientCount() {
        return connectedClients.size();
    }

    public List<ClientHandler> getConnectedClients() {
        return List.copyOf(connectedClients);
    }

    public AuctionNetworkBroadcaster getBroadcaster() {
        return broadcaster;
    }
}
