package model.auction;

import exception.AuctionStateException;
import exception.InsufficientFundsException;
import exception.InvalidBidException;
import model.item.Art;
import model.user.Bidder;
import model.user.Seller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử các ngoại lệ dùng chung (Người 5) và các quy tắc nghiệp vụ.
 * Các ngoại lệ chuyên biệt phải được ném đúng loại để Server/GUI
 * có thể bắt và hiển thị lý do cho người dùng.
 */
class BusinessRuleTest {

    Seller seller;
    Bidder a, b;
    Auction auction;
    LocalDateTime start, end;

    static BigDecimal money(long value) {
        return BigDecimal.valueOf(value);
    }

    static Bidder bidder(String name) {
        Bidder b = new Bidder(name, "0987654321", "password1", "HN");
        b.deposit(money(1000));
        return b;
    }

    @BeforeEach
    void setup() {
        seller = new Seller("Seller", "0912345678", "password1", "HN");
        a = bidder("A");
        b = bidder("B");
        start = LocalDateTime.now().minusSeconds(1);
        end = start.plusMinutes(5);
        auction = new Auction(new Art("Art", money(100)), seller, money(10), start, end);
    }

    void running() {
        auction.startAuction();
    }

    // ---- InvalidBidException ----

    @Test
    void lowBidThrowsInvalidBidException() {
        running();
        assertThrows(InvalidBidException.class, () -> auction.placeBid(a, money(105)));
    }

    @Test
    void sellerSelfBidThrowsInvalidBidException() {
            // Tạo seller với id cố định để tạo một bidder trùng id.
            Seller fixedSeller = new Seller("SELLER-1", "Seller", "0912345678", "password1", "HN");
            Auction ownAuction = new Auction(new Art("Art", money(100)), fixedSeller,
                    money(10), start, end);
            ownAuction.startAuction();
            Bidder fakeSeller = new Bidder("SELLER-1", "Seller", "0912345678", "password1", "HN");
            fakeSeller.deposit(money(1000));
            assertThrows(InvalidBidException.class, () -> ownAuction.placeBid(fakeSeller, money(110)));
        }

    @Test
    void nullBidderThrowsInvalidBidException() {
        running();
        assertThrows(InvalidBidException.class, () -> auction.placeBid(null, money(110)));
    }

    @Test
    void invalidBidExceptionIsIllegalArgumentException() {
        // Đảm bảo tương thích với code cũ bắt IllegalArgumentException.
        running();
        InvalidBidException ex = assertThrows(InvalidBidException.class,
                () -> auction.placeBid(a, money(105)));
        assertTrue(ex instanceof IllegalArgumentException);
        assertFalse(ex.getMessage().isBlank());
    }

    // ---- AuctionStateException ----

    @Test
    void bidBeforeStartThrowsAuctionStateException() {
        // Phiên chưa startAuction -> trạng thái OPEN.
        assertThrows(AuctionStateException.class, () -> auction.placeBid(a, money(110)));
    }

    @Test
    void bidAfterCancelThrowsAuctionStateException() {
        running();
        auction.cancelAuction(seller);
        assertThrows(AuctionStateException.class, () -> auction.placeBid(a, money(110)));
    }

    @Test
    void earlyFinishThrowsAuctionStateException() {
        running();
        assertThrows(AuctionStateException.class, auction::finishAuction);
    }

    @Test
    void auctionStateExceptionIsIllegalStateException() {
        assertThrows(AuctionStateException.class, () -> auction.placeBid(a, money(110)));
        AuctionStateException ex = assertThrows(AuctionStateException.class,
                () -> auction.placeBid(a, money(110)));
        assertTrue(ex instanceof IllegalStateException);
    }

    // ---- InsufficientFundsException ----

    @Test
    void bidBeyondBalanceThrowsInsufficientFundsException() {
        running();
        // a có 1000, đặt 2000 vượt số dư.
        assertThrows(InsufficientFundsException.class, () -> auction.placeBid(a, money(2000)));
    }

    @Test
    void withdrawBeyondAvailableThrowsInsufficientFundsException() {
        a.deposit(money(100));
        assertThrows(InsufficientFundsException.class, () -> a.withdraw(money(2000)));
    }

    @Test
    void insufficientFundsExceptionIsIllegalArgumentException() {
        running();
        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> auction.placeBid(a, money(2000)));
        assertTrue(ex instanceof IllegalArgumentException);
    }

    // ---- Kịch bản nghiệp vụ bổ sung ----

    @Test
    void validBidUpdatesPriceAndLeader() {
        running();
        assertTrue(auction.placeBid(a, money(110)));
        assertEquals(0, auction.getCurrentPrice().compareTo(money(110)));
        assertSame(a, auction.getHighestBid().getBidder());
    }

    @Test
    void secondBidderMustExceedCurrentPlusIncrement() {
        running();
        auction.placeBid(a, money(110));
        // b phải đặt >= 120 (110 + 10).
        assertThrows(InvalidBidException.class, () -> auction.placeBid(b, money(115)));
        assertTrue(auction.placeBid(b, money(120)));
        assertSame(b, auction.getHighestBid().getBidder());
    }

    @Test
    void paymentTwiceIsRejected() throws InterruptedException {
        LocalDateTime nearEnd = LocalDateTime.now().plusSeconds(1);
        Auction timed = new Auction(new Art("Timed", money(100)), seller, money(10),
                LocalDateTime.now().minusSeconds(1), nearEnd);
        timed.startAuction();
        timed.placeBid(a, money(110));
        while (LocalDateTime.now().isBefore(nearEnd)) {
            Thread.sleep(10);
        }
        timed.finishAuction();
        timed.payAuction(a);
        // Thanh toán lần hai phải bị từ chối.
        assertThrows(AuctionStateException.class, () -> timed.payAuction(a));
        assertEquals(AuctionStatus.PAID, timed.getStatus());
    }
}