package client.network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import common.network.MessageType;
import common.network.NetworkMessage;
import common.network.payload.*;
import model.user.User;

/**
 * Socket Client kết nối với AuctionServer.
 * Quản lý luồng gửi yêu cầu đồng bộ/bất đồng bộ và nhận sự kiện phát sóng Realtime từ Server.
 *
 * Áp dụng kiến trúc đa luồng:
 * - 1 Luồng nền (Listener Thread) liên tục đọc các gói tin NetworkMessage từ ObjectInputStream.
 * - Nếu là gói Response (LOGIN_RESPONSE, ACTION_SUCCESS, ACTION_FAILED): hoàn thành CompletableFuture
 *   của request đang chờ tương ứng.
 * - Nếu là gói Broadcast (NEW_BID_NOTIFICATION, AUCTION_CLOSED_NOTIFICATION): kích hoạt callback
 *   cho danh sách AuctionEventListener (Observer Pattern phía Client).
 */
public class AuctionClient {
    private static final Logger LOGGER = Logger.getLogger(AuctionClient.class.getName());

    private final String host;
    private final int port;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private volatile boolean connected = false;
    private final Object writeLock = new Object();

    private Thread listenerThread;
    private final List<AuctionEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Queue<CompletableFuture<NetworkMessage>> pendingResponseQueue = new ConcurrentLinkedQueue<>();

    private User currentUser;

    public AuctionClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Mở kết nối Socket tới Server và bắt đầu luồng lắng nghe.
     */
    public synchronized void connect() throws IOException {
        if (connected) return;

        socket = new Socket(host, port);

        // Khởi tạo ObjectOutputStream trước, flush rồi mới tạo ObjectInputStream
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());

        connected = true;

        listenerThread = new Thread(this::listenForServerMessages, "client-network-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        LOGGER.info(() -> "Đã kết nối thành công tới AuctionServer tại " + host + ":" + port);
    }

    /**
     * Vòng lặp đọc các gói tin nhận từ Server.
     */
    private void listenForServerMessages() {
        while (connected && !socket.isClosed()) {
            try {
                Object received = in.readObject();

                if (received instanceof NetworkMessage message) {
                    if (message.getType() != null && message.getType().isBroadcast()) {
                        handleBroadcast(message);
                    } else {
                        // Là gói Response trả lời cho một Request gửi trước đó
                        CompletableFuture<NetworkMessage> future = pendingResponseQueue.poll();
                        if (future != null) {
                            future.complete(message);
                        } else {
                            LOGGER.info("Nhận được Response không có future chờ: " + message);
                        }
                    }
                }
            } catch (EOFException | SocketException e) {
                if (connected) {
                    LOGGER.info("Mất kết nối tới Server.");
                }
                break;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Lỗi đọc gói tin từ Server: " + e.getMessage(), e);
            }
        }
        disconnect();
    }

    private void handleBroadcast(NetworkMessage message) {
        MessageType type = message.getType();
        Object payload = message.getPayload();

        for (AuctionEventListener listener : listeners) {
            try {
                if (type == MessageType.NEW_BID_NOTIFICATION && payload instanceof BidNotificationPayload bidPayload) {
                    listener.onNewBid(bidPayload);
                } else if (type == MessageType.AUCTION_CLOSED_NOTIFICATION
                        && payload instanceof AuctionClosedNotificationPayload closedPayload) {
                    listener.onAuctionClosed(closedPayload);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Lỗi xử lý sự kiện broadcast trong listener", e);
            }
        }
    }

    /**
     * Gửi yêu cầu lên Server và đợi phản hồi đồng bộ trong thời gian giới hạn.
     */
    public NetworkMessage sendRequest(NetworkMessage request, long timeoutSeconds) throws Exception {
        if (!connected || socket.isClosed()) {
            throw new IOException("Client chưa kết nối tới Server");
        }

        CompletableFuture<NetworkMessage> future = new CompletableFuture<>();

        synchronized (writeLock) {
            pendingResponseQueue.add(future);
            out.writeObject(request);
            out.flush();
            out.reset();
        }

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingResponseQueue.remove(future);
            throw e;
        }
    }

    public NetworkMessage sendRequest(NetworkMessage request) throws Exception {
        return sendRequest(request, 5);
    }

    // ==========================================
    // Các phương thức nghiệp vụ tiện lợi
    // ==========================================

    /**
     * Đăng nhập vào hệ thống.
     */
    public boolean login(String phoneNumber, String password) throws Exception {
        LoginRequestPayload payload = new LoginRequestPayload(phoneNumber, password);
        NetworkMessage req = NetworkMessage.createRequest(MessageType.LOGIN_REQUEST, payload);
        NetworkMessage res = sendRequest(req);

        if (res.isSuccess()) {
            if (res.getPayload() instanceof User user) {
                this.currentUser = user;
            }
            return true;
        }
        return false;
    }

    /**
     * Đăng xuất khỏi phiên làm việc hiện tại.
     */
    public NetworkMessage logout() throws Exception {
        NetworkMessage req = NetworkMessage.createRequest(MessageType.LOGOUT_REQUEST, null);
        NetworkMessage res = sendRequest(req);
        if (res.isSuccess()) {
            this.currentUser = null;
        }
        return res;
    }

    /**
     * Đăng ký tài khoản mới.
     */
    public NetworkMessage register(String name, String phone, String password, String address, String role)
            throws Exception {
        RegisterRequestPayload payload = new RegisterRequestPayload(name, phone, password, address, role);
        NetworkMessage req = NetworkMessage.createRequest(MessageType.REGISTER_REQUEST, payload);
        return sendRequest(req);
    }

    /**
     * Lấy danh sách các phiên đấu giá từ Server.
     */
    @SuppressWarnings("unchecked")
    public List<AuctionDTO> getAuctions() throws Exception {
        NetworkMessage req = NetworkMessage.createRequest(MessageType.GET_AUCTIONS_REQUEST, null);
        NetworkMessage res = sendRequest(req);

        if (res.isSuccess() && res.getPayload() instanceof List<?>) {
            return (List<AuctionDTO>) res.getPayload();
        }
        return List.of();
    }

    /**
     * Đặt giá vào một phiên đấu giá.
     */
    public NetworkMessage placeBid(String auctionId, String bidderId, BigDecimal amount) throws Exception {
        PlaceBidRequestPayload payload = new PlaceBidRequestPayload(auctionId, bidderId, amount);
        NetworkMessage req = NetworkMessage.createRequest(MessageType.PLACE_BID_REQUEST, payload);
        return sendRequest(req);
    }

    /**
     * Tạo một phiên đấu giá mới (Seller/Admin).
     */
    public NetworkMessage createAuction(CreateAuctionRequestPayload payload) throws Exception {
        NetworkMessage req = NetworkMessage.createRequest(MessageType.CREATE_AUCTION_REQUEST, payload);
        return sendRequest(req);
    }

    /**
     * Thanh toán phiên đấu giá đã thắng (dành cho Bidder).
     */
    public NetworkMessage payAuction(String auctionId) throws Exception {
        NetworkMessage req = NetworkMessage.createRequest(MessageType.PAY_AUCTION_REQUEST, auctionId);
        return sendRequest(req);
    }

    /**
     * Hủy phiên đấu giá và hoàn tiền (dành cho Seller/Admin).
     */
    public NetworkMessage cancelAuction(String auctionId) throws Exception {
        NetworkMessage req = NetworkMessage.createRequest(MessageType.CANCEL_AUCTION_REQUEST, auctionId);
        return sendRequest(req);
    }

    // ==========================================
    // Quản lý Listener và Đóng kết nối
    // ==========================================

    public void addEventListener(AuctionEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeEventListener(AuctionEventListener listener) {
        listeners.remove(listener);
    }

    public synchronized void disconnect() {
        if (!connected) return;
        connected = false;

        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}

        try {
            if (out != null) out.close();
        } catch (IOException ignored) {}

        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        CompletableFuture<NetworkMessage> future;
        while ((future = pendingResponseQueue.poll()) != null) {
            future.completeExceptionally(new IOException("Kết nối đã bị đóng"));
        }

        LOGGER.info("AuctionClient đã ngắt kết nối an toàn.");
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public User getCurrentUser() {
        return currentUser;
    }
}
