package model.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import exception.InvalidBidException;
import model.item.Art;
import model.user.Bidder;
import model.user.Seller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test cho hai chức năng nâng cao của Auction: Auto-bidding và Anti-sniping.
 */
class AdvancedFeaturesTest {
    private Seller seller;
    private Bidder alice;
    private Bidder bob;
    private Auction auction;

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value);
    }

    private static void assertMoney(long expected, BigDecimal actual) {
        assertEquals(0, actual.compareTo(money(expected)),
                "mong đợi " + expected + " nhưng là " + actual);
    }

    private static Bidder bidder(String name, String phone, long balance) {
        Bidder bidder = new Bidder(name, phone, "password1", "HN");
        bidder.deposit(money(balance));
        return bidder;
    }

    // Phiên RUNNING: giá khởi điểm 100, bước giá 10, còn 5 phút.
    private Auction runningAuction(Duration remaining) {
        Auction created = new Auction(
                new Art("Tranh", money(100)),
                seller,
                money(10),
                LocalDateTime.now().minusSeconds(1),
                LocalDateTime.now().plus(remaining));
        created.startAuction();
        return created;
    }

    @BeforeEach
    void setUp() {
        seller = new Seller("Seller", "0912000001", "password1", "HN");
        alice = bidder("Alice", "0912000002", 10_000);
        bob = bidder("Bob", "0912000003", 10_000);
        auction = runningAuction(Duration.ofMinutes(5));
    }

    // ===================== AUTO-BIDDING =====================

    @Test
    void registeringAutoBidPlacesOpeningBidImmediately() {
        assertTrue(auction.registerAutoBid(alice, money(500), money(10)));

        // Chưa ai đặt, Alice tự đặt mức tối thiểu 100 + 10.
        assertMoney(110, auction.getCurrentPrice());
        assertSame(alice, auction.getHighestBid().getBidder());
        assertMoney(110, alice.getLockedBalance());
    }

    @Test
    void autoBidAnswersManualBidUntilLimit() {
        auction.registerAutoBid(alice, money(200), money(10));

        // Bob đặt 150 tay; Alice tự trả lại 160.
        auction.placeBid(bob, money(150));
        assertSame(alice, auction.getHighestBid().getBidder());
        assertMoney(160, auction.getCurrentPrice());
        assertMoney(0, bob.getLockedBalance());

        // Bob đặt 200; Alice không thể vượt hạn mức 200 nên dừng.
        auction.placeBid(bob, money(200));
        assertSame(bob, auction.getHighestBid().getBidder());
        assertMoney(200, auction.getCurrentPrice());
        assertMoney(0, alice.getLockedBalance());
        assertNull(auction.getAutoBidMax(alice.getId()), "hết hạn mức thì cấu hình bị loại");
    }

    @Test
    void higherMaximumWinsAutoBidWar() {
        auction.registerAutoBid(alice, money(300), money(10));
        auction.registerAutoBid(bob, money(500), money(10));

        // Bob có hạn mức cao hơn nên nhảy thẳng lên mức vừa vượt hạn mức
        // của Alice: 300 + 10.
        assertSame(bob, auction.getHighestBid().getBidder());
        assertMoney(310, auction.getCurrentPrice());
        assertMoney(0, alice.getLockedBalance());
        assertMoney(310, bob.getLockedBalance());
    }

    @Test
    void equalMaximumFavoursEarlierRegistration() {
        auction.registerAutoBid(alice, money(300), money(10));
        auction.registerAutoBid(bob, money(300), money(10));

        // Bob không thể vượt 300 khi Alice (đăng ký trước) đã giữ 300.
        assertSame(alice, auction.getHighestBid().getBidder());
        assertMoney(300, auction.getCurrentPrice());
    }

    @Test
    void autoBidIncrementBelowMinimumUsesAuctionStep() {
        auction.registerAutoBid(alice, money(500), money(1));
        auction.placeBid(bob, money(150));

        // Bước 1 nhỏ hơn bước giá 10 của phiên nên Alice trả 160.
        assertMoney(160, auction.getCurrentPrice());
    }

    @Test
    void autoBidWithInsufficientFundsIsDropped() {
        Bidder poor = bidder("Poor", "0912000004", 120);
        assertFalse(auction.registerAutoBid(poor, money(1000), money(50)),
                "đặt 150 cần nhiều hơn số dư 120 nên cấu hình bị loại ngay");
        assertNull(auction.getHighestBid());
        assertNull(auction.getAutoBidMax(poor.getId()));
    }

    @Test
    void autoBidRejectsInvalidSettings() {
        assertThrows(InvalidBidException.class,
                () -> auction.registerAutoBid(alice, money(105), money(10)),
                "hạn mức nhỏ hơn mức tối thiểu tiếp theo");
        assertThrows(InvalidBidException.class,
                () -> auction.registerAutoBid(alice, money(500), money(0)));
        assertThrows(InvalidBidException.class,
                () -> auction.registerAutoBid(alice, null, money(10)));
    }

    @Test
    void cancelAutoBidStopsAutomaticAnswers() {
        auction.registerAutoBid(alice, money(500), money(10));
        assertTrue(auction.cancelAutoBid(alice.getId()));
        assertFalse(auction.cancelAutoBid(alice.getId()));

        auction.placeBid(bob, money(200));
        assertSame(bob, auction.getHighestBid().getBidder());
    }

    @Test
    void reRegisteringReplacesPreviousLimit() {
        auction.registerAutoBid(alice, money(200), money(10));
        auction.registerAutoBid(alice, money(400), money(10));
        assertMoney(400, auction.getAutoBidMax(alice.getId()));
    }

    @Test
    void autoBidsNotifyObserversForEveryBid() {
        List<BigDecimal> seen = new ArrayList<>();
        auction.addObserver(new AuctionObserver() {
            @Override
            public void onNewBid(Auction a, Bid bid) {
                seen.add(bid.getAmount());
            }

            @Override
            public void onAuctionStatusChanged(Auction a, AuctionStatus o, AuctionStatus n) {
            }

            @Override
            public void onAuctionEnded(Auction a, Bidder winner) {
            }
        });

        auction.registerAutoBid(alice, money(130), money(10));
        auction.placeBid(bob, money(120));

        // 110 (Alice tự đặt), 120 (Bob), 130 (Alice trả lại).
        assertEquals(3, seen.size());
        assertMoney(130, seen.get(2));
    }

    @Test
    void autoBidSurvivesSerialization() throws Exception {
        auction.registerAutoBid(alice, money(500), money(10));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(auction);
        }
        Auction copy;
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (Auction) in.readObject();
        }

        assertMoney(500, copy.getAutoBidMax(alice.getId()));
    }

    // ===================== ANTI-SNIPING =====================

    @Test
    void lateBidExtendsEndTimeWhenEnabled() {
        Auction closing = runningAuction(Duration.ofSeconds(10));
        closing.setAntiSnipingEnabled(true);
        LocalDateTime before = closing.getEndTime();

        closing.placeBid(alice, money(110));

        assertEquals(before.plus(Auction.EXTENSION), closing.getEndTime());
    }

    @Test
    void lateBidDoesNotExtendWhenDisabled() {
        Auction closing = runningAuction(Duration.ofSeconds(10));
        LocalDateTime before = closing.getEndTime();

        closing.placeBid(alice, money(110));

        assertEquals(before, closing.getEndTime());
    }

    @Test
    void earlyBidDoesNotExtend() {
        auction.setAntiSnipingEnabled(true);
        LocalDateTime before = auction.getEndTime();

        auction.placeBid(alice, money(110));

        assertEquals(before, auction.getEndTime());
    }

    @Test
    void extendedAuctionDoesNotCloseAtOriginalTime() throws InterruptedException {
        Auction closing = runningAuction(Duration.ofMillis(800));
        closing.setAntiSnipingEnabled(true);
        LocalDateTime originalEnd = closing.getEndTime();

        closing.placeBid(alice, money(110));

        while (LocalDateTime.now().isBefore(originalEnd.plus(Duration.ofMillis(100)))) {
            Thread.sleep(20);
        }
        closing.updateTime();
        assertEquals(AuctionStatus.RUNNING, closing.getStatus(),
                "đã được gia hạn nên chưa đóng ở giờ cũ");
        closing.placeBid(bob, money(120));
        assertSame(bob, closing.getHighestBid().getBidder());
    }
}
