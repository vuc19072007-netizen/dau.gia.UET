package persistence;

import model.auction.Auction;
import model.auction.AuctionStatus;
import model.item.Art;
import model.user.Bidder;
import model.user.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử lưu/tải dữ liệu bằng Serialization.
 * Mục tiêu: sau khi khởi động lại, giá dẫn đầu và khoản giữ trong ví vẫn khớp.
 */
class DataStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanup() {
        // Không cần dọn vì @TempDir tự xóa.
    }

    private DataStore newStore(String name) {
        return new DataStore(tempDir.resolve(name).toString());
    }

    @Test
    void saveAndLoadRestoresUsersAndAuctions() {
        DataStore store = newStore("state.ser");

        Seller seller = new Seller("Seller", "0912345678", "password1", "HN");
        Bidder bidder = new Bidder("Bidder", "0987654321", "password1", "HN");
        bidder.deposit(new BigDecimal("1000"));

        LocalDateTime start = LocalDateTime.now().minusSeconds(1);
        LocalDateTime end = start.plusMinutes(5);
        Auction auction = new Auction(new Art("Art", new BigDecimal("100")),
                seller, new BigDecimal("10"), start, end);
        auction.startAuction();
        auction.placeBid(bidder, new BigDecimal("110"));

        store.save(List.of(seller, bidder), List.of(auction));

        DataStore.SystemState loaded = store.load();
        assertNotNull(loaded);
        assertEquals(2, loaded.getUsers().size());
        assertEquals(1, loaded.getAuctions().size());

        Auction restored = loaded.getAuctions().get(0);
        // Giá dẫn đầu vẫn khớp.
        assertEquals(0, restored.getCurrentPrice().compareTo(new BigDecimal("110")));
        assertEquals("Bidder", restored.getHighestBid().getBidder().getName());
        // Lịch sử bid vẫn còn.
        assertEquals(1, restored.getBidHistory().size());
    }

    @Test
    void loadReturnsNullWhenFileMissing() {
        DataStore store = newStore("missing.ser");
        assertNull(store.load());
        assertFalse(store.exists());
    }

    @Test
    void lockedFundsPersistAcrossRestart() {
        DataStore store = newStore("funds.ser");

        Seller seller = new Seller("Seller", "0912345678", "password1", "HN");
        Bidder bidder = new Bidder("Bidder", "0987654321", "password1", "HN");
        bidder.deposit(new BigDecimal("1000"));

        LocalDateTime start = LocalDateTime.now().minusSeconds(1);
        LocalDateTime end = start.plusMinutes(5);
        Auction auction = new Auction(new Art("Art", new BigDecimal("100")),
                seller, new BigDecimal("10"), start, end);
        auction.startAuction();
        auction.placeBid(bidder, new BigDecimal("110"));

        store.save(List.of(seller, bidder), List.of(auction));

        DataStore.SystemState loaded = store.load();
        Bidder restoredBidder = (Bidder) loaded.getUsers().stream()
                .filter(u -> u.getId().equals(bidder.getId()))
                .findFirst().orElseThrow();
        // Khoản giữ trong ví vẫn khớp (110).
        assertEquals(0, restoredBidder.getLockedBalance().compareTo(new BigDecimal("110")));
        // Số dư khả dụng = 1000 - 110 = 890.
        assertEquals(0, restoredBidder.getAvailableBalance().compareTo(new BigDecimal("890")));
    }

    @Test
    void finishedAuctionStatusPersists() throws InterruptedException {
        DataStore store = newStore("finished.ser");

        Seller seller = new Seller("Seller", "0912345678", "password1", "HN");
        Bidder bidder = new Bidder("Bidder", "0987654321", "password1", "HN");
        bidder.deposit(new BigDecimal("1000"));

        LocalDateTime start = LocalDateTime.now().minusSeconds(1);
        LocalDateTime end = LocalDateTime.now().plusSeconds(1);
        Auction auction = new Auction(new Art("Art", new BigDecimal("100")),
                seller, new BigDecimal("10"), start, end);
        auction.startAuction();
        auction.placeBid(bidder, new BigDecimal("110"));
        while (LocalDateTime.now().isBefore(end)) {
            Thread.sleep(10);
        }
        auction.finishAuction();

        store.save(List.of(seller, bidder), List.of(auction));

        DataStore.SystemState loaded = store.load();
        Auction restored = loaded.getAuctions().get(0);
        assertEquals(AuctionStatus.FINISHED, restored.getStatus());
        assertEquals("Bidder", restored.getHighestBid().getBidder().getName());
    }
}
