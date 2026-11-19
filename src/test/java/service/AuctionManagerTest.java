package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import client.network.AuctionClient;
import common.network.NetworkMessage;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import model.auction.Auction;
import model.auction.AuctionObserver;
import model.auction.AuctionStatus;
import model.auction.Bid;
import model.item.Art;
import model.item.Item;
import model.item.ItemStatus;
import model.item.ItemType;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import persistence.DataStore;
import server.network.AuctionServer;

/**
 * Test phần Server 1 trong AuctionManager:
 * đăng ký, quản lý sản phẩm theo chủ sở hữu, danh sách tài khoản (tuần 8),
 * đồng hồ tự mở/đóng phiên và xác định người thắng (tuần 9),
 * reset và lưu/tải dữ liệu (tuần 10).
 * AuctionManager là Singleton nên mỗi test gọi reset() trước và sau khi chạy.
 */
class AuctionManagerTest {

    AuctionManager manager;
    Seller seller;
    Seller otherSeller;
    Bidder bidder;
    Admin admin;

    static BigDecimal money(long value) {
        return BigDecimal.valueOf(value);
    }

    @BeforeEach
    void setup() {
        manager = AuctionManager.getInstance();
        manager.reset();

        seller = (Seller) manager.register(
                "SELLER", "Seller", "0911111111", "password1", "HN");
        otherSeller = (Seller) manager.register(
                "SELLER", "Other", "0922222222", "password1", "HN");
        bidder = (Bidder) manager.register(
                "BIDDER", "Bidder", "0933333333", "password1", "HN");
        bidder.deposit(money(1000));

        admin = new Admin("Admin", "0944444444", "password1", "HN");
        manager.addUser(admin);
    }

    @AfterEach
    void cleanup() {
        manager.reset();
    }

    // Phiên đã tới giờ bắt đầu, còn lâu mới kết thúc.
    Auction openAuction(Item item) {
        return manager.createAuction(item, seller, money(10),
                LocalDateTime.now().minusSeconds(1),
                LocalDateTime.now().plusMinutes(5));
    }

    // ---------------- Đăng ký ----------------

    @Test
    void registerCreatesRightAccountType() {
        assertInstanceOf(Seller.class, seller);
        assertInstanceOf(Bidder.class, bidder);
        assertSame(bidder, manager.login("0933333333", "password1"));
    }

    @Test
    void registerRejectsAdminAndUnknownRole() {
        assertThrows(IllegalArgumentException.class, () -> manager.register(
                "ADMIN", "Hacker", "0955555555", "password1", "HN"));
        assertThrows(IllegalArgumentException.class, () -> manager.register(
                "abc", "Hacker", "0955555555", "password1", "HN"));
        assertThrows(IllegalArgumentException.class, () -> manager.register(
                null, "Hacker", "0955555555", "password1", "HN"));
        // Không tài khoản nào được tạo ra từ ba lần thử trên.
        assertEquals(4, manager.getAllUsers(admin).size());
    }

    @Test
    void registerRejectsDuplicatePhoneAndBadInput() {
        assertThrows(IllegalArgumentException.class, () -> manager.register(
                "BIDDER", "Copy", "0933333333", "password1", "HN"));
        assertThrows(IllegalArgumentException.class, () -> manager.register(
                "BIDDER", "ShortPhone", "12345", "password1", "HN"));
        assertThrows(IllegalArgumentException.class, () -> manager.register(
                "BIDDER", "ShortPass", "0955555555", "123", "HN"));
    }

    // Đăng ký qua mạng phải dùng chung luật với register(): không tự tạo được Admin.
    @Test
    void networkRegisterCannotCreateAdmin() throws Exception {
        AuctionServer server = new AuctionServer(0);
        server.start();
        AuctionClient client = new AuctionClient("localhost", server.getPort());

        try {
            client.connect();

            NetworkMessage adminAttempt = client.register(
                    "Hacker", "0955555555", "password1", "HN", "ADMIN");
            assertFalse(adminAttempt.isSuccess());

            NetworkMessage normal = client.register(
                    "Normal", "0966666666", "password1", "HN", "bidder");
            assertTrue(normal.isSuccess());
            assertInstanceOf(Bidder.class,
                    manager.login("0966666666", "password1"));
        } finally {
            client.disconnect();
            server.stop();
        }

        // Chỉ có đúng một Admin: tài khoản tạo trong setup().
        long admins = manager.getAllUsers(admin).stream()
                .filter(Admin.class::isInstance).count();
        assertEquals(1, admins);
    }

    // ---------------- Sản phẩm ----------------

    @Test
    void addItemUsesFactoryAndRecordsOwner() {
        Item item = manager.addItem(seller, ItemType.ART, "Tranh", money(100));

        assertInstanceOf(Art.class, item);
        assertTrue(item.isOwnedBy(seller.getId()));
        assertSame(item, manager.getItem(item.getId()));
        assertEquals(List.of(item), manager.getItemsOf(seller.getId()));
        assertTrue(manager.getItemsOf(otherSeller.getId()).isEmpty());
    }

    @Test
    void addItemRejectsUnknownOrBlockedSeller() {
        Seller stranger = new Seller("Stranger", "0977777777", "password1", "HN");
        assertThrows(IllegalArgumentException.class,
                () -> manager.addItem(stranger, ItemType.ART, "Tranh", money(100)));

        seller.setActive(false);
        assertThrows(IllegalStateException.class,
                () -> manager.addItem(seller, ItemType.ART, "Tranh", money(100)));
    }

    @Test
    void ownerCanUpdateAndRemoveItem() {
        Item item = manager.addItem(seller, ItemType.ART, "Tranh", money(100));

        manager.updateItem(seller, item.getId(), " Tranh mới ", money(200), "Mô tả");
        assertEquals("Tranh mới", item.getName());
        assertEquals(0, item.getStartingPrice().compareTo(money(200)));
        assertEquals("Mô tả", item.getDescription());

        manager.removeItem(seller, item.getId());
        assertNull(manager.getItem(item.getId()));
    }

    @Test
    void failedUpdateChangesNothing() {
        Item item = manager.addItem(seller, ItemType.ART, "Tranh", money(100));

        // Tên đúng nhưng giá sai: cả hai trường phải giữ nguyên.
        assertThrows(IllegalArgumentException.class, () -> manager.updateItem(
                seller, item.getId(), "Tên khác", money(0), null));
        assertEquals("Tranh", item.getName());
        assertEquals(0, item.getStartingPrice().compareTo(money(100)));
    }

    @Test
    void onlyOwnerCanUpdateOrRemoveItem() {
        Item item = manager.addItem(seller, ItemType.ART, "Tranh", money(100));

        assertThrows(IllegalArgumentException.class, () -> manager.updateItem(
                otherSeller, item.getId(), "Chiếm", money(1), null));
        assertThrows(IllegalArgumentException.class,
                () -> manager.removeItem(otherSeller, item.getId()));
        // Admin cũng không sửa hộ được: luật là "chỉ chủ sở hữu".
        assertThrows(IllegalArgumentException.class,
                () -> manager.removeItem(admin, item.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> manager.removeItem(null, item.getId()));

        assertEquals("Tranh", item.getName());
        assertNotNull(manager.getItem(item.getId()));
    }

    @Test
    void itemInUnfinishedAuctionCannotBeEditedUntilCanceled() {
        Item item = manager.addItem(seller, ItemType.ART, "Tranh", money(100));
        Auction auction = openAuction(item);

        // Phiên còn OPEN: trạng thái sản phẩm vẫn AVAILABLE nhưng đã bị khóa sửa.
        assertEquals(AuctionStatus.OPEN, auction.getStatus());
        assertThrows(IllegalStateException.class, () -> manager.updateItem(
                seller, item.getId(), "Tên khác", money(200), null));
        assertThrows(IllegalStateException.class,
                () -> manager.removeItem(seller, item.getId()));

        // Hủy phiên thì sản phẩm rảnh, sửa lại được.
        auction.cancelAuction(seller);
        manager.updateItem(seller, item.getId(), "Tên khác", money(200), null);
        assertEquals("Tên khác", item.getName());
    }

    @Test
    void onlyOwnerCanCreateAuctionForItem() {
        Item item = manager.addItem(seller, ItemType.ART, "Tranh", money(100));

        assertThrows(IllegalArgumentException.class, () -> manager.createAuction(
                item, otherSeller, money(10),
                LocalDateTime.now().minusSeconds(1),
                LocalDateTime.now().plusMinutes(5)));
        assertTrue(manager.getAllAuction().isEmpty());

        assertNotNull(openAuction(item));
        // Cùng sản phẩm không lên hai phiên chưa giải quyết cùng lúc.
        assertThrows(IllegalArgumentException.class, () -> openAuction(item));
    }

    @Test
    void auctionAdoptsItemCreatedWithoutManager() {
        // Sản phẩm kiểu cũ: tạo bằng new, chưa có chủ, chưa nằm trong kho.
        Item legacy = new Art("Tranh cũ", money(100));
        openAuction(legacy);

        assertTrue(legacy.isOwnedBy(seller.getId()));
        assertEquals(List.of(legacy), manager.getItemsOf(seller.getId()));
    }

    // ---------------- Tài khoản cho Admin ----------------

    @Test
    void onlyActiveAdminSeesAllUsers() {
        List<User> all = manager.getAllUsers(admin);
        assertEquals(4, all.size());
        // Danh sách trả về là bản sao không sửa được.
        assertThrows(UnsupportedOperationException.class, () -> all.remove(0));

        assertThrows(IllegalArgumentException.class, () -> manager.getAllUsers(seller));
        assertThrows(IllegalArgumentException.class, () -> manager.getAllUsers(null));

        admin.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> manager.getAllUsers(admin));
    }

    // ---------------- Đồng hồ (tuần 9) ----------------

    @Test
    void clockStartsAndFinishesAuctionAndReportsWinner() throws Exception {
        Item item = manager.addItem(seller, ItemType.ART, "Tranh", money(100));
        LocalDateTime end = LocalDateTime.now().plusSeconds(1);
        Auction auction = manager.createAuction(item, seller, money(10),
                LocalDateTime.now().minusSeconds(1), end);

        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch ended = new CountDownLatch(1);
        AtomicReference<Bidder> reportedWinner = new AtomicReference<>();
        AtomicInteger endedCalls = new AtomicInteger();

        auction.addObserver(new AuctionObserver() {
            @Override public void onNewBid(Auction a, Bid b) { }
            @Override public void onAuctionStatusChanged(
                    Auction a, AuctionStatus oldStatus, AuctionStatus newStatus) {
                if (newStatus == AuctionStatus.RUNNING) {
                    running.countDown();
                }
            }
            @Override public void onAuctionEnded(Auction a, Bidder winner) {
                reportedWinner.set(winner);
                endedCalls.incrementAndGet();
                ended.countDown();
            }
        });

        // Không ai gọi startAuction()/finishAuction(): chỉ có đồng hồ làm việc.
        manager.startClock();

        assertTrue(running.await(2, TimeUnit.SECONDS));
        assertEquals(ItemStatus.IN_AUCTION, item.getStatus());
        auction.placeBid(bidder, money(150));

        assertTrue(ended.await(5, TimeUnit.SECONDS));
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
        assertSame(bidder, reportedWinner.get());
        // Tiền người thắng vẫn bị giữ, chờ thanh toán.
        assertEquals(0, bidder.getLockedBalance().compareTo(money(150)));

        // Đồng hồ vẫn chạy tiếp nhưng không báo kết thúc lần thứ hai.
        Thread.sleep(350);
        assertEquals(1, endedCalls.get());
    }

    @Test
    void clockFinishesAuctionWithoutBidsAsUnsold() throws Exception {
        Item item = manager.addItem(seller, ItemType.ART, "Tranh", money(100));
        Auction auction = manager.createAuction(item, seller, money(10),
                LocalDateTime.now().minusSeconds(1),
                LocalDateTime.now().plusNanos(400_000_000L));

        CountDownLatch ended = new CountDownLatch(1);
        AtomicReference<Bidder> reportedWinner = new AtomicReference<>(bidder);
        auction.addObserver(new AuctionObserver() {
            @Override public void onNewBid(Auction a, Bid b) { }
            @Override public void onAuctionStatusChanged(
                    Auction a, AuctionStatus oldStatus, AuctionStatus newStatus) { }
            @Override public void onAuctionEnded(Auction a, Bidder winner) {
                reportedWinner.set(winner);
                ended.countDown();
            }
        });

        manager.startClock();

        assertTrue(ended.await(5, TimeUnit.SECONDS));
        assertNull(reportedWinner.get());
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
        assertEquals(ItemStatus.UNSOLD, item.getStatus());
    }

    @Test
    void auctionThatMissedItsWholeWindowIsCanceled() throws Exception {
        Item item = manager.addItem(seller, ItemType.ART, "Tranh", money(100));
        LocalDateTime end = LocalDateTime.now().plusNanos(200_000_000L);
        Auction auction = manager.createAuction(item, seller, money(10),
                LocalDateTime.now().minusSeconds(1), end);

        // Giả lập server tắt suốt khung giờ: không cập nhật cho tới khi đã hết giờ.
        while (LocalDateTime.now().isBefore(end)) {
            Thread.sleep(10);
        }
        manager.updateAllAuctions();

        assertEquals(AuctionStatus.CANCELED, auction.getStatus());
        assertEquals(ItemStatus.CANCELED, item.getStatus());
    }

    @Test
    void startClockTwiceAndStopClockTwiceAreSafe() {
        manager.startClock();
        manager.startClock();
        manager.stopClock();
        manager.stopClock();
    }

    // ---------------- reset, lưu và tải (tuần 10) ----------------

    @Test
    void resetClearsEverything() {
        Item item = manager.addItem(seller, ItemType.ART, "Tranh", money(100));
        Auction auction = openAuction(item);
        manager.startClock();

        manager.reset();

        assertNull(manager.getUser(seller.getId()));
        assertNull(manager.getItem(item.getId()));
        assertNull(manager.getAuction(auction.getAuctionId()));
        assertTrue(manager.getAllAuction().isEmpty());
        // Số điện thoại cũ dùng lại được vì dữ liệu cũ đã bị xóa.
        assertNotNull(manager.register(
                "SELLER", "Seller", "0911111111", "password1", "HN"));
    }

    @Test
    void saveThenLoadRestoresUsersAuctionsItemsAndFunds(@TempDir Path dir) {
        DataStore store = new DataStore(dir.resolve("state.ser").toString());
        Item item = manager.addItem(seller, ItemType.ART, "Tranh", money(100));
        Auction auction = openAuction(item);
        auction.startAuction();
        auction.placeBid(bidder, money(150));

        manager.saveTo(store);
        manager.reset();
        assertTrue(manager.loadFrom(store));

        // Đăng nhập lại được bằng tài khoản đã lưu.
        Bidder loadedBidder =
                (Bidder) manager.login("0933333333", "password1");
        assertEquals(bidder.getId(), loadedBidder.getId());
        assertEquals(0, loadedBidder.getLockedBalance().compareTo(money(150)));
        assertEquals(0, loadedBidder.getAvailableBalance().compareTo(money(850)));

        Auction loaded = manager.getAuction(auction.getAuctionId());
        assertEquals(AuctionStatus.RUNNING, loaded.getStatus());
        assertEquals(0, loaded.getCurrentPrice().compareTo(money(150)));
        // Người dẫn đầu trong phiên và tài khoản trong danh sách là CÙNG một đối tượng,
        // nên hoàn tiền/thanh toán sau khi tải vẫn trừ đúng ví.
        assertSame(loadedBidder, loaded.getHighestBid().getBidder());

        // Kho sản phẩm được dựng lại từ phiên.
        assertEquals(1, manager.getItemsOf(seller.getId()).size());
        assertEquals("Tranh", manager.getItem(item.getId()).getName());

        // Phiên tải lên vẫn dùng tiếp được: người khác vượt giá thì người cũ được hoàn.
        Bidder second = (Bidder) manager.register(
                "BIDDER", "Second", "0988888888", "password1", "HN");
        second.deposit(money(1000));
        loaded.placeBid(second, money(200));
        assertEquals(0, loadedBidder.getLockedBalance().signum());
    }

    @Test
    void loadFromMissingFileKeepsCurrentData(@TempDir Path dir) {
        DataStore store = new DataStore(dir.resolve("missing.ser").toString());

        assertFalse(manager.loadFrom(store));
        assertNotNull(manager.getUser(seller.getId()));
        assertThrows(IllegalArgumentException.class, () -> manager.saveTo(null));
        assertThrows(IllegalArgumentException.class, () -> manager.loadFrom(null));
    }
}
