package server.network;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import client.network.AuctionClient;
import client.network.AuctionEventListener;
import common.network.NetworkMessage;
import common.network.payload.AuctionClosedNotificationPayload;
import common.network.payload.AuctionDTO;
import common.network.payload.BidNotificationPayload;
import common.network.payload.CreateAuctionRequestPayload;
import factory.ItemFactory;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.item.Item;
import model.item.ItemType;
import model.user.Bidder;
import model.user.Seller;
import service.AuctionManager;

class AuctionClientTest {

    private AuctionServer server;
    private int port;
    private Seller seller;
    private Bidder bidder1;
    private Bidder bidder2;
    private Auction auction;

    private AuctionClient client1;
    private AuctionClient client2;

    @BeforeEach
    void setUp() throws Exception {
        AuctionManager manager = AuctionManager.getInstance();

        long suffix = System.currentTimeMillis() % 100000000;
        seller = new Seller("Seller ClientTest", String.format("09%08d", suffix), "password123", "Ha Noi");
        bidder1 = new Bidder("Bidder 1", String.format("08%08d", suffix), "password123", "HCM");
        bidder2 = new Bidder("Bidder 2", String.format("07%08d", suffix), "password123", "Da Nang");

        bidder1.deposit(new BigDecimal("50000000"));
        bidder2.deposit(new BigDecimal("50000000"));

        manager.addUser(seller);
        manager.addUser(bidder1);
        manager.addUser(bidder2);

        Item item = ItemFactory.getFactory(ItemType.ELECTRONICS)
                .createItem("MacBook Pro Test", new BigDecimal("10000000"));
        auction = manager.createAuction(
                item,
                seller,
                new BigDecimal("500000"),
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusHours(1)
        );
        auction.startAuction();

        server = new AuctionServer(0);
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() {
        if (client1 != null) client1.disconnect();
        if (client2 != null) client2.disconnect();
        if (server != null) server.stop();
        AuctionManager.getInstance().reset();
    }

    @Test
    @DisplayName("Kiểm tra đăng ký, đăng nhập, lấy danh sách và đăng xuất qua AuctionClient")
    void testAuthAndGetAuctions() throws Exception {
        client1 = new AuctionClient("localhost", port);
        client1.connect();
        assertTrue(client1.isConnected());

        // Đăng ký tài khoản mới
        String newPhone = String.format("03%08d", System.currentTimeMillis() % 100000000);
        NetworkMessage regRes = client1.register("New User", newPhone, "password123", "Hue", "BIDDER");
        assertTrue(regRes.isSuccess());

        // Đăng nhập bằng tài khoản vừa tạo
        assertTrue(client1.login(newPhone, "password123"));
        assertNotNull(client1.getCurrentUser());

        // Lấy danh sách phiên
        List<AuctionDTO> list = client1.getAuctions();
        assertFalse(list.isEmpty());

        // Đăng xuất
        NetworkMessage logoutRes = client1.logout();
        assertTrue(logoutRes.isSuccess());
        assertNull(client1.getCurrentUser());
    }

    @Test
    @DisplayName("Kiểm tra đặt giá và nhận sự kiện Realtime Broadcast giữa 2 AuctionClient")
    void testPlaceBidAndRealtimeListener() throws Exception {
        CountDownLatch bidLatch = new CountDownLatch(1);
        AtomicReference<BidNotificationPayload> receivedBid = new AtomicReference<>();

        client1 = new AuctionClient("localhost", port);
        client2 = new AuctionClient("localhost", port);
        client1.connect();
        client2.connect();

        // Client 2 đăng ký lắng nghe sự kiện realtime
        client2.addEventListener(new AuctionEventListener() {
            @Override
            public void onNewBid(BidNotificationPayload bidNotification) {
                receivedBid.set(bidNotification);
                bidLatch.countDown();
            }

            @Override
            public void onAuctionClosed(AuctionClosedNotificationPayload closedNotification) {
            }
        });

        assertTrue(client1.login(bidder1.getPhoneNumber(), "password123"));
        assertTrue(client2.login(bidder2.getPhoneNumber(), "password123"));

        BigDecimal bidAmount = new BigDecimal("10500000");
        NetworkMessage bidRes = client1.placeBid(auction.getAuctionId(), bidder1.getId(), bidAmount);
        assertTrue(bidRes.isSuccess());

        assertTrue(bidLatch.await(3, TimeUnit.SECONDS), "Client 2 phải nhận được thông báo giá mới");
        assertEquals(auction.getAuctionId(), receivedBid.get().getAuctionId());
        assertEquals(0, bidAmount.compareTo(receivedBid.get().getNewCurrentPrice()));
    }

    @Test
    @DisplayName("Kiểm tra Seller tạo phiên, hủy phiên và phát sóng đóng phiên")
    void testCreateAndCancelAuction() throws Exception {
        CountDownLatch closeLatch = new CountDownLatch(1);
        AtomicReference<AuctionClosedNotificationPayload> closedPayload = new AtomicReference<>();

        client1 = new AuctionClient("localhost", port);
        client2 = new AuctionClient("localhost", port);
        client1.connect();
        client2.connect();

        client2.addEventListener(new AuctionEventListener() {
            @Override
            public void onNewBid(BidNotificationPayload bidNotification) {
            }

            @Override
            public void onAuctionClosed(AuctionClosedNotificationPayload closedNotification) {
                closedPayload.set(closedNotification);
                closeLatch.countDown();
            }
        });

        assertTrue(client1.login(seller.getPhoneNumber(), "password123"));

        // Tạo phiên mới
        CreateAuctionRequestPayload createReq = new CreateAuctionRequestPayload(
                "Tranh Son Dau Test",
                "ART",
                "Mo ta tranh",
                new BigDecimal("5000000"),
                new BigDecimal("200000"),
                LocalDateTime.now().plusMinutes(1),
                LocalDateTime.now().plusHours(2),
                seller.getId()
        );
        NetworkMessage createRes = client1.createAuction(createReq);
        assertTrue(createRes.isSuccess());
        AuctionDTO createdDto = createRes.getPayloadAs(AuctionDTO.class);
        assertNotNull(createdDto);

        // Hủy phiên mới tạo và kiểm tra broadcast đóng phiên
        NetworkMessage cancelRes = client1.cancelAuction(createdDto.getAuctionId());
        assertTrue(cancelRes.isSuccess());

        assertTrue(closeLatch.await(3, TimeUnit.SECONDS), "Client 2 phải nhận được thông báo hủy phiên");
        assertEquals(createdDto.getAuctionId(), closedPayload.get().getAuctionId());
        assertEquals("CANCELED", closedPayload.get().getStatus());
    }

    @Test
    @DisplayName("Kiểm tra Bidder thanh toán phiên đấu giá đã thắng")
    void testPayAuction() throws Exception {
        AuctionManager.getInstance().startClock();

        // Tạo phiên kéo dài 1.2 giây để kịp connect, login và placeBid
        Item shortItem = ItemFactory.getFactory(ItemType.ART)
                .createItem("Dong Ho Co", new BigDecimal("2000000"));
        Auction shortAuction = AuctionManager.getInstance().createAuction(
                shortItem,
                seller,
                new BigDecimal("100000"),
                LocalDateTime.now().minusSeconds(1),
                LocalDateTime.now().plusNanos(1_200_000_000L)
        );
        shortAuction.startAuction();

        client1 = new AuctionClient("localhost", port);
        client1.connect();
        assertTrue(client1.login(bidder1.getPhoneNumber(), "password123"));

        NetworkMessage bidRes = client1.placeBid(
                shortAuction.getAuctionId(),
                bidder1.getId(),
                new BigDecimal("2100000")
        );
        assertTrue(bidRes.isSuccess());

        // Chờ đồng hồ Server chuyển trạng thái phiên sang FINISHED
        long deadline = System.currentTimeMillis() + 2500;
        while (shortAuction.getStatus() != AuctionStatus.FINISHED
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(AuctionStatus.FINISHED, shortAuction.getStatus());

        NetworkMessage payRes = client1.payAuction(shortAuction.getAuctionId());
        assertTrue(payRes.isSuccess());
    }
}
