package server.network;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import common.network.MessageType;
import common.network.NetworkMessage;
import common.network.payload.AuctionDTO;
import common.network.payload.BidNotificationPayload;
import common.network.payload.LoginRequestPayload;
import common.network.payload.PlaceBidRequestPayload;
import factory.ItemFactory;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemType;
import model.user.Bidder;
import model.user.Seller;
import service.AuctionManager;

class AuctionServerTest {

    private AuctionServer server;
    private int port;
    private Seller testSeller;
    private Bidder testBidder;
    private Auction testAuction;

    @BeforeEach
    void setUp() throws Exception {
        AuctionManager manager = AuctionManager.getInstance();

        // Chuẩn bị tài khoản test với số điện thoại duy nhất theo timestamp
        long suffix = System.currentTimeMillis() % 100000000;
        String sellerPhone = String.format("09%08d", suffix);
        String bidderPhone = String.format("08%08d", suffix);

        testSeller = new Seller("Seller Test", sellerPhone, "password123", "Ha Noi");
        manager.addUser(testSeller);

        testBidder = new Bidder("Bidder Test", bidderPhone, "password123", "HCM");
        testBidder.deposit(new BigDecimal("100000000")); // Nạp 100 triệu
        manager.addUser(testBidder);

        // Tạo 1 phiên đấu giá đang RUNNING
        Item item = ItemFactory.getFactory(ItemType.ELECTRONICS).createItem(
                "Laptop Test " + suffix,
                new BigDecimal("20000000")
        );
        testAuction = manager.createAuction(
                item,
                testSeller,
                new BigDecimal("1000000"),
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusHours(1)
        );
        testAuction.startAuction();

        // Khởi động server trên cổng ngẫu nhiên (port 0)
        server = new AuctionServer(0);
        server.start();
        port = server.getPort();
        assertTrue(port > 0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        AuctionManager.getInstance().reset();
    }

    @Test
    @DisplayName("Kiểm tra kết nối Client Socket và gửi nhận thông điệp LOGIN_REQUEST")
    void testClientLoginSuccess() throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            // Chuẩn bị luồng I/O theo đúng quy tắc ObjectOutputStream trước, flush rồi mới tạo ObjectInputStream
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // Gửi gói tin LOGIN_REQUEST
            LoginRequestPayload payload = new LoginRequestPayload(
                    testBidder.getPhoneNumber(),
                    "password123"
            );
            NetworkMessage loginReq = NetworkMessage.createRequest(MessageType.LOGIN_REQUEST, payload);
            out.writeObject(loginReq);
            out.flush();

            // Đọc phản hồi từ server
            Object received = in.readObject();
            assertTrue(received instanceof NetworkMessage);
            NetworkMessage response = (NetworkMessage) received;

            assertEquals(MessageType.LOGIN_RESPONSE, response.getType());
            assertEquals(NetworkMessage.STATUS_SUCCESS, response.getStatus());
            assertTrue(response.isSuccess());
        }
    }

    @Test
    @DisplayName("Kiểm tra yêu cầu lấy danh sách phiên GET_AUCTIONS_REQUEST trả về List<AuctionDTO>")
    void testGetAuctionsRequest() throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            NetworkMessage req = NetworkMessage.createRequest(MessageType.GET_AUCTIONS_REQUEST, null);
            out.writeObject(req);
            out.flush();

            Object received = in.readObject();
            assertTrue(received instanceof NetworkMessage);
            NetworkMessage response = (NetworkMessage) received;

            assertEquals(MessageType.ACTION_SUCCESS, response.getType());
            assertTrue(response.isSuccess());
            assertTrue(response.getPayload() instanceof List<?>);

            @SuppressWarnings("unchecked")
            List<AuctionDTO> auctions = (List<AuctionDTO>) response.getPayload();
            assertFalse(auctions.isEmpty());
            boolean found = auctions.stream().anyMatch(a -> a.getAuctionId().equals(testAuction.getAuctionId()));
            assertTrue(found);
        }
    }

    @Test
    @DisplayName("Kiểm tra cơ chế Realtime Broadcast khi có lượt đặt giá mới (NEW_BID_NOTIFICATION)")
    void testRealtimeBroadcastOnNewBid() throws Exception {
        CountDownLatch broadcastReceivedLatch = new CountDownLatch(1);
        AtomicReference<NetworkMessage> receivedBroadcast = new AtomicReference<>();

        // Client 1: đóng vai trò Observer/Lắng nghe realtime
        Socket client1 = new Socket("localhost", port);
        ObjectOutputStream out1 = new ObjectOutputStream(client1.getOutputStream());
        out1.flush();
        ObjectInputStream in1 = new ObjectInputStream(client1.getInputStream());

        Thread listenerThread = new Thread(() -> {
            try {
                while (!client1.isClosed()) {
                    Object obj = in1.readObject();
                    if (obj instanceof NetworkMessage msg && msg.getType() == MessageType.NEW_BID_NOTIFICATION) {
                        receivedBroadcast.set(msg);
                        broadcastReceivedLatch.countDown();
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();

        // Client 2: Đăng nhập và thực hiện đặt giá vào phiên testAuction
        try (Socket client2 = new Socket("localhost", port)) {
            ObjectOutputStream out2 = new ObjectOutputStream(client2.getOutputStream());
            out2.flush();
            ObjectInputStream in2 = new ObjectInputStream(client2.getInputStream());

            // 1. Đăng nhập
            NetworkMessage loginReq = NetworkMessage.createRequest(
                    MessageType.LOGIN_REQUEST,
                    new LoginRequestPayload(testBidder.getPhoneNumber(), "password123")
            );
            out2.writeObject(loginReq);
            out2.flush();
            NetworkMessage loginRes = (NetworkMessage) in2.readObject();
            assertTrue(loginRes.isSuccess());

            // 2. Đặt giá
            BigDecimal bidAmount = testAuction.getCurrentPrice().add(testAuction.getMinimumIncrement());
            NetworkMessage bidReq = NetworkMessage.createRequest(
                    MessageType.PLACE_BID_REQUEST,
                    new PlaceBidRequestPayload(testAuction.getAuctionId(), testBidder.getId(), bidAmount)
            );
            out2.writeObject(bidReq);
            out2.flush();

            // Nhận kết quả đặt giá thành công cho Client 2 (bỏ qua các gói broadcast nếu có)
            NetworkMessage bidRes;
            do {
                bidRes = (NetworkMessage) in2.readObject();
            } while (bidRes != null && bidRes.getType().isBroadcast());
            assertNotNull(bidRes);
            assertTrue(bidRes.isSuccess());
        }

        // Kiểm tra Client 1 đã nhận được Broadcast NEW_BID_NOTIFICATION trong vòng 3 giây
        boolean completed = broadcastReceivedLatch.await(3, TimeUnit.SECONDS);
        assertTrue(completed, "Client 1 phải nhận được gói tin NEW_BID_NOTIFICATION phát sóng từ Server");

        NetworkMessage broadcastMsg = receivedBroadcast.get();
        assertNotNull(broadcastMsg);
        assertEquals(MessageType.NEW_BID_NOTIFICATION, broadcastMsg.getType());
        assertTrue(broadcastMsg.getPayload() instanceof BidNotificationPayload);

        BidNotificationPayload bidPayload = (BidNotificationPayload) broadcastMsg.getPayload();
        assertEquals(testAuction.getAuctionId(), bidPayload.getAuctionId());
        assertEquals(testBidder.getName(), bidPayload.getBidderName());

        // Đóng client 1
        client1.close();
    }
}
