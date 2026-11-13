package server.network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import common.network.MessageType;
import common.network.NetworkMessage;
import common.network.payload.*;
import factory.ItemFactory;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemType;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import service.AuctionManager;

/**
 * Xử lý kết nối, giao tiếp hai chiều và phiên làm việc (Session) của từng Client riêng biệt.
 * Chạy trên một luồng do Thread Pool của AuctionServer phân bổ.
 *
 * Nguyên tắc kỹ thuật mạng & luồng đối tượng:
 * 1. Khởi tạo luồng: Luôn tạo ObjectOutputStream và flush() TRƯỚC khi tạo ObjectInputStream
 *    để tránh deadlock truyền nhận header của luồng đối tượng.
 * 2. Tránh object caching: Sau mỗi lần out.writeObject() và out.flush(), phải gọi out.reset()
 *    để JVM xóa bộ đệm đối tượng cũ, đảm bảo Client luôn nhận được trạng thái mới nhất.
 * 3. An toàn luồng (Thread-safety): Mọi thao tác ghi vào ObjectOutputStream được bảo vệ
 *    bằng writeLock đồng bộ (synchronized) để tránh phân mảnh gói tin khi Server broadcast
 *    cùng lúc với phản hồi kết quả của luồng này.
 */
public class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final AuctionServer server;
    private final Socket socket;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    private volatile boolean connected = true;
    private final Object writeLock = new Object();

    // Tài khoản đã xác thực của Client trong phiên kết nối này
    private User authenticatedUser;

    public ClientHandler(AuctionServer server, Socket socket) {
        if (server == null || socket == null) {
            throw new IllegalArgumentException("AuctionServer và Socket không được null");
        }
        this.server = server;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // Bước 1: Khởi tạo luồng tuần tự theo chuẩn Java Object Serialization
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush(); // Bắt buộc flush để đẩy header sang Client trước khi Client tạo InputStream
            in = new ObjectInputStream(socket.getInputStream());

            LOGGER.info(() -> "Khởi tạo thành công luồng Object I/O cho client: " + socket.getRemoteSocketAddress());

            // Bước 2: Vòng lặp nhận và xử lý thông điệp từ Client
            while (connected && !socket.isClosed()) {
                try {
                    Object received = in.readObject();

                    if (received instanceof NetworkMessage message) {
                        LOGGER.info(() -> String.format(
                                "[RECEIVE REQUEST] From %s: %s",
                                socket.getRemoteSocketAddress(),
                                message
                        ));
                        handleMessage(message);
                    } else if (received != null) {
                        LOGGER.warning("Nhận được đối tượng không phải NetworkMessage: " + received.getClass().getName());
                        sendMessage(NetworkMessage.createErrorResponse(
                                MessageType.ACTION_FAILED,
                                "Đối tượng gửi lên không hợp lệ, yêu cầu NetworkMessage"
                        ));
                    }
                } catch (EOFException | SocketException e) {
                    LOGGER.info(() -> "Client đã ngắt kết nối: " + socket.getRemoteSocketAddress());
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.log(Level.WARNING, "Không tìm thấy lớp đối tượng nhận từ Client", e);
                }
            }

        } catch (IOException e) {
            if (connected) {
                LOGGER.log(Level.WARNING, "Lỗi luồng I/O với client: " + socket.getRemoteSocketAddress(), e);
            }
        } finally {
            close();
        }
    }

    /**
     * Điều hướng và xử lý thông điệp (NetworkMessage) nhận từ Client.
     */
    public void handleMessage(NetworkMessage message) {
        if (message == null || message.getType() == null) {
            sendMessage(NetworkMessage.createErrorResponse(MessageType.ACTION_FAILED, "Thông điệp rỗng hoặc thiếu Type"));
            return;
        }

        MessageType type = message.getType();

        try {
            switch (type) {
                case LOGIN_REQUEST -> handleLogin(message);
                case LOGOUT_REQUEST -> handleLogout();
                case REGISTER_REQUEST -> handleRegister(message);
                case GET_AUCTIONS_REQUEST -> handleGetAuctions();
                case CREATE_AUCTION_REQUEST -> handleCreateAuction(message);
                case PLACE_BID_REQUEST -> handlePlaceBid(message);
                case PAY_AUCTION_REQUEST -> handlePayAuction(message);
                case CANCEL_AUCTION_REQUEST -> handleCancelAuction(message);
                default -> {
                    LOGGER.warning("Không hỗ trợ xử lý MessageType: " + type);
                    sendMessage(NetworkMessage.createErrorResponse(
                            MessageType.ACTION_FAILED,
                            "Loại thông điệp không được hỗ trợ từ Client: " + type
                    ));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi xử lý thông điệp " + type + ": " + e.getMessage(), e);
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Lỗi xử lý yêu cầu: " + e.getMessage()
            ));
        }
    }

    // ==========================================
    // Các hàm xử lý nghiệp vụ theo từng MessageType
    // ==========================================

    private void handleLogin(NetworkMessage message) {
        String phone = null;
        String password = null;

        Object payload = message.getPayload();
        if (payload instanceof LoginRequestPayload loginPayload) {
            phone = loginPayload.getPhoneNumber();
            password = loginPayload.getPassword();
        } else if (payload instanceof String[] creds && creds.length >= 2) {
            phone = creds[0];
            password = creds[1];
        }

        if (phone == null || password == null) {
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.LOGIN_RESPONSE,
                    "Payload đăng nhập không hợp lệ, yêu cầu LoginRequestPayload hoặc [phone, password]"
            ));
            return;
        }

        try {
            User user = AuctionManager.getInstance().login(phone, password);
            this.authenticatedUser = user;
            LOGGER.info(() -> "Đăng nhập thành công cho người dùng: " + user.getName() + " (" + user.getRole() + ")");
            sendMessage(NetworkMessage.createSuccessResponse(MessageType.LOGIN_RESPONSE, user));
        } catch (Exception e) {
            LOGGER.warning("Đăng nhập thất bại (" + phone + "): " + e.getMessage());
            sendMessage(NetworkMessage.createErrorResponse(MessageType.LOGIN_RESPONSE, e.getMessage()));
        }
    }

    private void handleLogout() {
        if (this.authenticatedUser != null) {
            String userName = this.authenticatedUser.getName();
            this.authenticatedUser = null;
            LOGGER.info(() -> "Người dùng đã đăng xuất: " + userName);
        }
        sendMessage(NetworkMessage.createSuccessResponse(MessageType.ACTION_SUCCESS, "Đăng xuất thành công"));
    }

    private void handleRegister(NetworkMessage message) {
        Object payload = message.getPayload();
        if (!(payload instanceof RegisterRequestPayload regPayload)) {
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Payload đăng ký không hợp lệ, yêu cầu RegisterRequestPayload"
            ));
            return;
        }

        try {
            String role = regPayload.getRole() != null ? regPayload.getRole().toUpperCase() : "BIDDER";
            User newUser;

            switch (role) {
                case "SELLER" -> newUser = new Seller(
                        regPayload.getName(),
                        regPayload.getPhoneNumber(),
                        regPayload.getPassword(),
                        regPayload.getAddress()
                );
                case "ADMIN" -> newUser = new Admin(
                        regPayload.getName(),
                        regPayload.getPhoneNumber(),
                        regPayload.getPassword(),
                        regPayload.getAddress()
                );
                default -> newUser = new Bidder(
                        regPayload.getName(),
                        regPayload.getPhoneNumber(),
                        regPayload.getPassword(),
                        regPayload.getAddress()
                );
            }

            AuctionManager.getInstance().addUser(newUser);
            LOGGER.info(() -> "Đăng ký người dùng mới thành công: " + newUser.getName() + " (" + role + ")");
            sendMessage(NetworkMessage.createSuccessResponse(
                    MessageType.ACTION_SUCCESS,
                    "Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay bây giờ."
            ));
        } catch (Exception e) {
            LOGGER.warning(() -> "Đăng ký thất bại: " + e.getMessage());
            sendMessage(NetworkMessage.createErrorResponse(MessageType.ACTION_FAILED, e.getMessage()));
        }
    }

    private void handleGetAuctions() {
        try {
            List<Auction> allAuctions = AuctionManager.getInstance().getAllAuction();
            List<AuctionDTO> dtos = new ArrayList<>();

            for (Auction auction : allAuctions) {
                dtos.add(AuctionDTO.fromAuction(auction));
            }

            sendMessage(NetworkMessage.createSuccessResponse(MessageType.ACTION_SUCCESS, dtos));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Không thể lấy danh sách phiên đấu giá", e);
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Lỗi lấy danh sách phiên: " + e.getMessage()
            ));
        }
    }

    private void handleCreateAuction(NetworkMessage message) {
        if (authenticatedUser == null) {
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Vui lòng đăng nhập trước khi tạo phiên đấu giá"
            ));
            return;
        }

        if (!(authenticatedUser instanceof Seller) && !(authenticatedUser instanceof Admin)) {
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Chỉ người bán (Seller) hoặc Quản trị viên (Admin) mới có quyền tạo phiên đấu giá"
            ));
            return;
        }

        Object payload = message.getPayload();
        if (!(payload instanceof CreateAuctionRequestPayload createPayload)) {
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Payload tạo phiên không hợp lệ, yêu cầu CreateAuctionRequestPayload"
            ));
            return;
        }

        try {
            String categoryStr = createPayload.getItemCategory() != null
                    ? createPayload.getItemCategory().toUpperCase()
                    : "ELECTRONICS";
            ItemType itemType;
            try {
                itemType = ItemType.valueOf(categoryStr);
            } catch (IllegalArgumentException e) {
                itemType = ItemType.ELECTRONICS;
            }

            Item item = ItemFactory.getFactory(itemType).createItem(
                    createPayload.getItemName(),
                    createPayload.getStartingPrice()
            );

            if (createPayload.getDescription() != null) {
                item.setDescription(createPayload.getDescription());
            }

            Seller seller = (authenticatedUser instanceof Seller s)
                    ? s
                    : new Seller(authenticatedUser.getName(), authenticatedUser.getPhoneNumber(),
                    authenticatedUser.getPassword(), authenticatedUser.getAddress());

            Auction newAuction = AuctionManager.getInstance().createAuction(
                    item,
                    seller,
                    createPayload.getMinimumIncrement(),
                    createPayload.getStartTime(),
                    createPayload.getEndTime()
            );

            // Gắn Observer phát sóng Realtime vào phiên mới tạo
            server.attachAuction(newAuction);

            LOGGER.info(() -> "Tạo phiên đấu giá mới thành công: " + newAuction.getAuctionId());
            sendMessage(NetworkMessage.createSuccessResponse(
                    MessageType.ACTION_SUCCESS,
                    AuctionDTO.fromAuction(newAuction)
            ));

        } catch (Exception e) {
            LOGGER.warning(() -> "Tạo phiên đấu giá thất bại: " + e.getMessage());
            sendMessage(NetworkMessage.createErrorResponse(MessageType.ACTION_FAILED, e.getMessage()));
        }
    }

    private void handlePlaceBid(NetworkMessage message) {
        if (authenticatedUser == null) {
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Vui lòng đăng nhập trước khi đặt giá"
            ));
            return;
        }

        if (!(authenticatedUser instanceof Bidder bidder)) {
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Chỉ tài khoản người mua (Bidder) mới có thể đặt giá"
            ));
            return;
        }

        Object payload = message.getPayload();
        if (!(payload instanceof PlaceBidRequestPayload bidPayload)) {
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Payload đặt giá không hợp lệ, yêu cầu PlaceBidRequestPayload"
            ));
            return;
        }

        try {
            Auction auction = AuctionManager.getInstance().getAuction(bidPayload.getAuctionId());
            if (auction == null) {
                sendMessage(NetworkMessage.createErrorResponse(
                        MessageType.ACTION_FAILED,
                        "Không tìm thấy phiên đấu giá có mã: " + bidPayload.getAuctionId()
                ));
                return;
            }

            auction.placeBid(bidder, bidPayload.getAmount());

            LOGGER.info(() -> String.format(
                    "Đặt giá thành công: %s đặt %s vào phiên %s",
                    bidder.getName(),
                    bidPayload.getAmount(),
                    auction.getAuctionId()
            ));

            sendMessage(NetworkMessage.createSuccessResponse(
                    MessageType.ACTION_SUCCESS,
                    "Đặt giá thành công! Bạn đang dẫn đầu phiên đấu giá."
            ));

        } catch (Exception e) {
            LOGGER.warning(() -> "Đặt giá thất bại: " + e.getMessage());
            sendMessage(NetworkMessage.createErrorResponse(MessageType.ACTION_FAILED, e.getMessage()));
        }
    }

    private void handlePayAuction(NetworkMessage message) {
        if (authenticatedUser == null) {
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Vui lòng đăng nhập trước khi thanh toán"
            ));
            return;
        }

        if (!(authenticatedUser instanceof Bidder bidder)) {
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Chỉ tài khoản người mua (Bidder) mới có thể thanh toán phiên đấu giá"
            ));
            return;
        }

        try {
            String auctionId = message.getPayloadAs(String.class);
            if (auctionId == null || auctionId.isBlank()) {
                sendMessage(NetworkMessage.createErrorResponse(
                        MessageType.ACTION_FAILED,
                        "Mã phiên đấu giá thanh toán không được để trống"
                ));
                return;
            }

            Auction auction = AuctionManager.getInstance().getAuction(auctionId);
            if (auction == null) {
                sendMessage(NetworkMessage.createErrorResponse(
                        MessageType.ACTION_FAILED,
                        "Không tìm thấy phiên đấu giá có mã: " + auctionId
                ));
                return;
            }

            auction.payAuction(bidder);

            LOGGER.info(() -> String.format(
                    "Thanh toán thành công: %s đã thanh toán phiên %s",
                    bidder.getName(),
                    auctionId
            ));

            sendMessage(NetworkMessage.createSuccessResponse(
                    MessageType.ACTION_SUCCESS,
                    "Thanh toán phiên đấu giá thành công!"
            ));

        } catch (Exception e) {
            LOGGER.warning(() -> "Thanh toán phiên thất bại: " + e.getMessage());
            sendMessage(NetworkMessage.createErrorResponse(MessageType.ACTION_FAILED, e.getMessage()));
        }
    }

    private void handleCancelAuction(NetworkMessage message) {
        if (authenticatedUser == null) {
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Vui lòng đăng nhập trước khi hủy phiên đấu giá"
            ));
            return;
        }

        if (!(authenticatedUser instanceof Seller) && !(authenticatedUser instanceof Admin)) {
            sendMessage(NetworkMessage.createErrorResponse(
                    MessageType.ACTION_FAILED,
                    "Chỉ người bán (Seller) hoặc Quản trị viên (Admin) mới có quyền hủy phiên"
            ));
            return;
        }

        try {
            String auctionId = message.getPayloadAs(String.class);
            if (auctionId == null || auctionId.isBlank()) {
                sendMessage(NetworkMessage.createErrorResponse(
                        MessageType.ACTION_FAILED,
                        "Mã phiên đấu giá cần hủy không được để trống"
                ));
                return;
            }

            Auction auction = AuctionManager.getInstance().getAuction(auctionId);
            if (auction == null) {
                sendMessage(NetworkMessage.createErrorResponse(
                        MessageType.ACTION_FAILED,
                        "Không tìm thấy phiên đấu giá có mã: " + auctionId
                ));
                return;
            }

            auction.cancelAuction(authenticatedUser);

            LOGGER.info(() -> String.format(
                    "Hủy phiên thành công: %s đã hủy phiên %s",
                    authenticatedUser.getName(),
                    auctionId
            ));

            sendMessage(NetworkMessage.createSuccessResponse(
                    MessageType.ACTION_SUCCESS,
                    "Đã hủy phiên đấu giá và hoàn tiền cho người đặt giá (nếu có)."
            ));

        } catch (Exception e) {
            LOGGER.warning(() -> "Hủy phiên thất bại: " + e.getMessage());
            sendMessage(NetworkMessage.createErrorResponse(MessageType.ACTION_FAILED, e.getMessage()));
        }
    }

    // ==========================================
    // Quản lý luồng gửi gói tin & Đóng kết nối
    // ==========================================

    /**
     * Gửi an toàn một gói tin NetworkMessage tới Client này.
     * Sử dụng writeLock và reset() để đảm bảo không bị deadlock hay dùng lại cache object cũ.
     */
    public void sendMessage(NetworkMessage message) {
        if (!connected || socket.isClosed()) {
            return;
        }

        synchronized (writeLock) {
            if (out == null) {
                return;
            }
            try {
                out.writeObject(message);
                out.flush();
                out.reset();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Không thể gửi tin nhắn tới client: " + socket.getRemoteSocketAddress(), e);
                close();
            }
        }
    }

    /**
     * Đóng kết nối và giải phóng tài nguyên.
     */
    public void close() {
        if (!connected) return;
        connected = false;

        server.unregisterClient(this);

        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}

        try {
            if (out != null) out.close();
        } catch (IOException ignored) {}

        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        LOGGER.info(() -> "Đã đóng kết nối với Client: " + socket.getRemoteSocketAddress());
    }

    public User getAuthenticatedUser() {
        return authenticatedUser;
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isConnected() {
        return connected && !socket.isClosed();
    }
}