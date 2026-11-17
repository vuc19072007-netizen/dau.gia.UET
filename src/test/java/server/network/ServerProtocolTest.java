package server.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import client.network.AuctionClient;
import client.network.AuctionEventListener;
import common.network.NetworkMessage;
import common.network.payload.AuctionClosedNotificationPayload;
import common.network.payload.AuctionDTO;
import common.network.payload.BidNotificationPayload;
import common.network.payload.CreateAuctionRequestPayload;
import common.network.payload.ItemDTO;
import common.network.payload.ItemRequestPayload;
import common.network.payload.UserDTO;
import model.auction.Auction;
import model.item.ItemStatus;
import model.item.ItemType;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.AuctionManager;

/**
 * Test các loại thông điệp mở rộng: ví, sản phẩm, quản trị, chi tiết phiên,
 * đấu giá tự động và thông báo realtime. Chạy qua server và client thật.
 */
class ServerProtocolTest {
    private static final String PASSWORD = "password1";

    private AuctionServer server;
    private Seller seller;
    private Bidder bidder;
    private Bidder rival;
    private Admin admin;
    private AuctionClient client;
    private AuctionClient watcher;

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value);
    }

    @BeforeEach
    void setUp() throws Exception {
        AuctionManager manager = AuctionManager.getInstance();
        manager.reset();

        seller = new Seller("Seller", "0911000001", PASSWORD, "HN");
        bidder = new Bidder("Bidder", "0911000002", PASSWORD, "HN");
        rival = new Bidder("Rival", "0911000003", PASSWORD, "HN");
        admin = new Admin("Admin", "0911000004", PASSWORD, "HN");
        bidder.deposit(money(1_000_000));
        rival.deposit(money(1_000_000));
        manager.addUser(seller);
        manager.addUser(bidder);
        manager.addUser(rival);
        manager.addUser(admin);

        server = new AuctionServer(0);
        server.start();
        client = connect();
        watcher = connect();
    }

    @AfterEach
    void tearDown() {
        client.disconnect();
        watcher.disconnect();
        server.stop();
        AuctionManager.getInstance().reset();
    }

    private AuctionClient connect() throws Exception {
        AuctionClient created = new AuctionClient("localhost", server.getPort());
        created.connect();
        return created;
    }

    private void login(AuctionClient target, String phone) throws Exception {
        assertTrue(target.login(phone, PASSWORD), "đăng nhập " + phone);
    }

    private Auction runningAuction(String name) {
        AuctionManager manager = AuctionManager.getInstance();
        var item = manager.addItem(seller, ItemType.ART, name, money(1000));
        Auction auction = manager.createAuction(item, seller, money(100),
                LocalDateTime.now().minusSeconds(1), LocalDateTime.now().plusMinutes(10));
        auction.startAuction();
        server.attachAuction(auction);
        return auction;
    }

    // ===================== Tài khoản & ví =====================

    @Test
    void loginReturnsUserWithoutPassword() throws Exception {
        NetworkMessage res = client.loginRequest(bidder.getPhoneNumber(), PASSWORD);
        assertTrue(res.isSuccess());
        UserDTO user = (UserDTO) res.getPayload();
        assertEquals(bidder.getId(), user.getId());
        assertTrue(user.isBidder());
        assertEquals(0, user.getBalance().compareTo(money(1_000_000)));
    }

    @Test
    void wrongPasswordReturnsReason() throws Exception {
        NetworkMessage res = client.loginRequest(bidder.getPhoneNumber(), "saimatkhau");
        assertFalse(res.isSuccess());
        assertNotNull(res.getErrorMessage());
        assertNull(client.getCurrentUser());
    }

    @Test
    void depositUpdatesBalance() throws Exception {
        login(client, bidder.getPhoneNumber());
        NetworkMessage res = client.deposit(money(500));
        assertTrue(res.isSuccess());
        assertEquals(0, client.getCurrentUser().getBalance().compareTo(money(1_000_500)));
        assertFalse(client.deposit(money(-1)).isSuccess());
    }

    @Test
    void sellerCannotDeposit() throws Exception {
        login(client, seller.getPhoneNumber());
        assertFalse(client.deposit(money(500)).isSuccess());
    }

    @Test
    void requestsRequireLogin() throws Exception {
        assertFalse(client.deposit(money(500)).isSuccess());
        assertFalse(client.refreshProfile().isSuccess());
        assertThrows(IllegalStateException.class, () -> client.getMyItems());
    }

    // ===================== Sản phẩm (Seller) =====================

    @Test
    void sellerManagesOwnItems() throws Exception {
        login(client, seller.getPhoneNumber());

        NetworkMessage added = client.addItem(
                new ItemRequestPayload(null, "VEHICLE", "Xe đạp", "Còn mới", money(500)));
        assertTrue(added.isSuccess(), added.getErrorMessage());
        ItemDTO item = (ItemDTO) added.getPayload();
        assertEquals("Vehicle", item.getCategory());
        assertEquals(seller.getId(), item.getOwnerId());

        NetworkMessage updated = client.updateItem(
                new ItemRequestPayload(item.getId(), null, "Xe đạp thể thao", "Mới", money(700)));
        assertTrue(updated.isSuccess(), updated.getErrorMessage());
        assertEquals("Xe đạp thể thao", ((ItemDTO) updated.getPayload()).getName());

        List<ItemDTO> mine = client.getMyItems();
        assertEquals(1, mine.size());

        assertTrue(client.deleteItem(item.getId()).isSuccess());
        assertTrue(client.getMyItems().isEmpty());
    }

    @Test
    void invalidCategoryIsRejected() throws Exception {
        login(client, seller.getPhoneNumber());
        NetworkMessage res = client.addItem(
                new ItemRequestPayload(null, "FOOD", "Bánh", null, money(10)));
        assertFalse(res.isSuccess());
        assertTrue(res.getErrorMessage().contains("Loại sản phẩm"));
    }

    @Test
    void bidderCannotManageItems() throws Exception {
        login(client, bidder.getPhoneNumber());
        assertFalse(client.addItem(
                new ItemRequestPayload(null, "ART", "Tranh", null, money(10))).isSuccess());
    }

    @Test
    void otherSellerCannotEditItem() throws Exception {
        var item = AuctionManager.getInstance().addItem(seller, ItemType.ART, "Tranh", money(100));
        Seller other = (Seller) AuctionManager.getInstance()
                .register("SELLER", "Other", "0911000099", PASSWORD, "HN");
        login(client, other.getPhoneNumber());
        assertFalse(client.updateItem(
                new ItemRequestPayload(item.getId(), null, "Đổi tên", null, money(5))).isSuccess());
        assertFalse(client.deleteItem(item.getId()).isSuccess());
    }

    // ===================== Tạo phiên =====================

    @Test
    void createAuctionFromExistingItemBroadcastsUpdate() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<AuctionDTO> update = new AtomicReference<>();
        watcher.addEventListener(new AuctionEventListener() {
            @Override
            public void onNewBid(BidNotificationPayload bid) {
            }

            @Override
            public void onAuctionClosed(AuctionClosedNotificationPayload closed) {
            }

            @Override
            public void onAuctionUpdated(AuctionDTO auction) {
                update.set(auction);
                received.countDown();
            }
        });

        login(client, seller.getPhoneNumber());
        ItemDTO item = (ItemDTO) client.addItem(
                new ItemRequestPayload(null, "ART", "Tranh", null, money(1000))).getPayload();

        CreateAuctionRequestPayload request = new CreateAuctionRequestPayload();
        request.setItemId(item.getId());
        request.setMinimumIncrement(money(50));
        request.setStartTime(LocalDateTime.now());
        request.setEndTime(LocalDateTime.now().plusMinutes(5));
        request.setAntiSnipingEnabled(true);

        NetworkMessage res = client.createAuction(request);
        assertTrue(res.isSuccess(), res.getErrorMessage());
        AuctionDTO created = (AuctionDTO) res.getPayload();
        assertEquals(item.getId(), created.getItemId());
        assertTrue(created.isAntiSnipingEnabled());

        assertTrue(received.await(3, TimeUnit.SECONDS), "watcher phải nhận AUCTION_UPDATED");
        assertEquals(created.getAuctionId(), update.get().getAuctionId());
    }

    @Test
    void failedAuctionCreationDoesNotLeaveItemBehind() throws Exception {
        login(client, seller.getPhoneNumber());
        CreateAuctionRequestPayload request = new CreateAuctionRequestPayload(
                "Tranh", "ART", null, money(1000), money(50),
                LocalDateTime.now().plusMinutes(5), LocalDateTime.now(), null);

        NetworkMessage res = client.createAuction(request);
        assertFalse(res.isSuccess(), "giờ kết thúc trước giờ bắt đầu");
        assertTrue(client.getMyItems().isEmpty());
    }

    @Test
    void adminCannotCreateAuction() throws Exception {
        login(client, admin.getPhoneNumber());
        CreateAuctionRequestPayload request = new CreateAuctionRequestPayload(
                "Tranh", "ART", null, money(1000), money(50),
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(5), null);
        assertFalse(client.createAuction(request).isSuccess());
    }

    // ===================== Chi tiết phiên & auto-bid =====================

    @Test
    void auctionDetailContainsBidHistoryAndAutoBid() throws Exception {
        Auction auction = runningAuction("Tranh chi tiết");
        login(client, bidder.getPhoneNumber());

        assertTrue(client.placeBid(auction.getAuctionId(), null, money(1100)).isSuccess());
        NetworkMessage auto = client.setAutoBid(auction.getAuctionId(), money(5000), money(100));
        assertTrue(auto.isSuccess(), auto.getErrorMessage());

        AuctionDTO detail = (AuctionDTO) client.getAuctionDetail(auction.getAuctionId()).getPayload();
        assertEquals(1, detail.getBidHistory().size());
        assertEquals(0, detail.getMyAutoBidMax().compareTo(money(5000)));
        assertEquals(0, detail.getMinimumNextBid().compareTo(money(1200)));

        assertTrue(client.cancelAutoBid(auction.getAuctionId()).isSuccess());
        assertFalse(client.cancelAutoBid(auction.getAuctionId()).isSuccess());
    }

    @Test
    void unknownAuctionIsReported() throws Exception {
        NetworkMessage res = client.getAuctionDetail("khong-co");
        assertFalse(res.isSuccess());
    }

    @Test
    void bidNotificationCarriesExtendedEndTime() throws Exception {
        AuctionManager manager = AuctionManager.getInstance();
        var item = manager.addItem(seller, ItemType.ART, "Tranh", money(1000));
        Auction closing = manager.createAuction(item, seller, money(100),
                LocalDateTime.now().minusSeconds(1), LocalDateTime.now().plusSeconds(10));
        closing.setAntiSnipingEnabled(true);
        closing.startAuction();
        server.attachAuction(closing);
        LocalDateTime originalEnd = closing.getEndTime();

        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<BidNotificationPayload> bid = new AtomicReference<>();
        watcher.addEventListener(new AuctionEventListener() {
            @Override
            public void onNewBid(BidNotificationPayload notification) {
                bid.set(notification);
                received.countDown();
            }

            @Override
            public void onAuctionClosed(AuctionClosedNotificationPayload closed) {
            }
        });

        login(client, bidder.getPhoneNumber());
        assertTrue(client.placeBid(closing.getAuctionId(), null, money(1100)).isSuccess());

        assertTrue(received.await(3, TimeUnit.SECONDS));
        assertTrue(bid.get().getEndTime().isAfter(originalEnd), "giờ kết thúc phải được gia hạn");
    }

    // ===================== Quản trị (Admin) =====================

    @Test
    void adminListsAndBansUsers() throws Exception {
        login(client, admin.getPhoneNumber());
        List<UserDTO> users = client.getUsers();
        assertEquals(4, users.size());

        NetworkMessage banned = client.banUser(bidder.getId());
        assertTrue(banned.isSuccess(), banned.getErrorMessage());
        assertFalse(bidder.isActive());
        assertFalse(watcher.login(bidder.getPhoneNumber(), PASSWORD), "tài khoản bị khóa không đăng nhập được");

        assertTrue(client.unbanUser(bidder.getId()).isSuccess());
        assertTrue(bidder.isActive());
        assertFalse(client.banUser(admin.getId()).isSuccess(), "không khóa được admin");
        assertFalse(client.banUser("khong-co").isSuccess());
    }

    @Test
    void nonAdminCannotListUsers() throws Exception {
        login(client, bidder.getPhoneNumber());
        assertThrows(IllegalStateException.class, () -> client.getUsers());
        assertFalse(client.banUser(rival.getId()).isSuccess());
    }

    @Test
    void itemStatusFollowsAuction() throws Exception {
        Auction auction = runningAuction("Tranh trạng thái");
        login(client, seller.getPhoneNumber());
        ItemDTO item = client.getMyItems().stream()
                .filter(i -> i.getId().equals(auction.getItem().getId()))
                .findFirst().orElseThrow();
        assertEquals(ItemStatus.IN_AUCTION, item.getStatus());
    }
}
