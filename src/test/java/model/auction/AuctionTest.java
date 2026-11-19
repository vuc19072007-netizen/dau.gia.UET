package model.auction;

import model.item.*;
import model.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class AuctionTest {
    Seller seller;
    Bidder a, b;
    Auction auction;
    LocalDateTime start, end;
    static BigDecimal money(long value) { return BigDecimal.valueOf(value); }
    static Bidder bidder(String name) {
        Bidder b = new Bidder(name, "0987654321", "password1", "HN"); b.deposit(money(1000)); return b;
    }
    @BeforeEach void setup() {
        seller = new Seller("Seller", "0912345678", "password1", "HN");
        a = bidder("A"); b = bidder("B");
        start = LocalDateTime.now().minusSeconds(1); end = start.plusMinutes(5);
        auction = new Auction(new Art("Art", money(100)), seller, money(10), start, end);
    }
    void running() { auction.startAuction(); }
    void runningWithNearEnd() {
        end = LocalDateTime.now().plusSeconds(1);
        auction = new Auction(new Art("Art", money(100)), seller, money(10), start, end);
        running();
    }
    void awaitEnd() throws InterruptedException {
        while (LocalDateTime.now().isBefore(end)) Thread.sleep(10);
    }
    void finish() throws InterruptedException { awaitEnd(); auction.finishAuction(); }
    void eq(BigDecimal value, long expected) { assertEquals(0, value.compareTo(money(expected))); }

    @Test void acceptsStartingPricePlusIncrement() {
        running(); assertTrue(auction.placeBid(a, money(110)));
        eq(auction.getCurrentPrice(), 110); eq(a.getLockedBalance(), 110);
        assertSame(a, auction.getHighestBid().getBidder()); assertEquals(1, auction.getBidHistory().size());
    }
    static Stream<Arguments> invalidAmounts() {
        return Stream.of(Arguments.of((BigDecimal)null), Arguments.of(money(-1)),
                Arguments.of(money(0)), Arguments.of(money(99)), Arguments.of(money(100)));
    }
    @ParameterizedTest @MethodSource("invalidAmounts")
    void invalidBidLeavesStateUnchanged(BigDecimal amount) {
        running(); assertThrows(IllegalArgumentException.class, () -> auction.placeBid(a, amount));
        eq(a.getLockedBalance(), 0); eq(auction.getCurrentPrice(), 100);
        assertTrue(auction.getBidHistory().isEmpty());
    }
    @Test void rejectsBelowMinimumIncrement() {
        running(); auction.placeBid(a, money(110));
        assertThrows(IllegalArgumentException.class, () -> auction.placeBid(b, money(115)));
        eq(b.getLockedBalance(), 0); eq(auction.getCurrentPrice(), 110);
    }
    @Test void rejectsInsufficientFundsWithoutReservation() {
        running(); assertThrows(IllegalArgumentException.class, () -> auction.placeBid(a, money(2000)));
        eq(a.getLockedBalance(), 0);
    }
    @Test void rejectsBlockedBidder() {
        running(); a.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> auction.placeBid(a, money(110)));
    }
    @Test void rejectsNullBidder() {
        running(); assertThrows(IllegalArgumentException.class, () -> auction.placeBid(null, money(110)));
    }
    @Test void rejectsBidBeforeStart() {
        assertThrows(IllegalStateException.class, () -> auction.placeBid(a, money(110)));
    }
    @Test void rejectsBidAtOrAfterEndTime() throws InterruptedException {
        runningWithNearEnd(); awaitEnd();
        assertThrows(IllegalStateException.class, () -> auction.placeBid(a, money(110)));
    }
    @Test void rejectsBidAfterCancel() {
        running(); auction.cancelAuction(seller);
        assertThrows(IllegalStateException.class, () -> auction.placeBid(a, money(110)));
    }
    @Test void refusesEarlyFinish() {
        running(); assertThrows(IllegalStateException.class, auction::finishAuction);
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
    }
    @Test void finishesAndKeepsWinnerMoneyReserved() throws InterruptedException {
        runningWithNearEnd(); auction.placeBid(a, money(110)); auction.placeBid(b, money(150)); finish();
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
        assertSame(b, auction.getHighestBid().getBidder()); eq(a.getLockedBalance(), 0); eq(b.getLockedBalance(), 150);
    }
    @Test void finishesWithoutBidsAsUnsold() throws InterruptedException {
        runningWithNearEnd(); finish(); assertNull(auction.getHighestBid());
        assertEquals(ItemStatus.UNSOLD, auction.getItem().getStatus());
    }
    @Test void ownRaiseLocksOnlyDifference() {
        running(); auction.placeBid(a, money(110)); auction.placeBid(a, money(150)); eq(a.getLockedBalance(), 150);
    }
    @Test void manualUnlockCannotStealAuctionReservation() {
        running(); auction.placeBid(a, money(110)); eq(a.unlockFunds(), 0);
        assertThrows(IllegalArgumentException.class, () -> a.restoreFunds(money(100)));
        auction.placeBid(b, money(150)); eq(a.getLockedBalance(), 0); eq(b.getLockedBalance(), 150);
    }
    @Test void cancelRefundsOnlyItsOwnReservation() {
        running(); auction.placeBid(a, money(110));
        Auction other = new Auction(new Art("Other", money(100)), seller, money(10), start, end);
        other.startAuction(); other.placeBid(a, money(200)); auction.cancelAuction(seller);
        eq(a.getLockedBalance(), 200); other.cancelAuction(seller); eq(a.getLockedBalance(), 0);
    }
    @Test void paymentTransfersMoneyExactlyOnce() throws InterruptedException {
        runningWithNearEnd(); auction.placeBid(a, money(110)); finish();
        assertThrows(IllegalArgumentException.class, () -> auction.payAuction(b));
        auction.payAuction(a); eq(a.getBalance(), 890); eq(seller.getBalance(), 110); eq(a.getLockedBalance(), 0);
        assertEquals(AuctionStatus.PAID, auction.getStatus()); assertEquals(ItemStatus.SOLD, auction.getItem().getStatus());
        assertThrows(IllegalStateException.class, () -> auction.payAuction(a));
    }
    // Ba test dưới đây kiểm tra việc so sánh người dùng theo mã (getId())
    // trong payAuction() và cancelAuction().

    // Bản sao cùng mã với người thắng phải thanh toán được.
    @Test void winnerCopyWithSameIdCanPay() throws InterruptedException {
        // Phiên sắp hết giờ, a đặt 110 rồi chờ phiên kết thúc: a là người thắng.
        runningWithNearEnd(); auction.placeBid(a, money(110)); finish();
        // Tạo đối tượng MỚI nhưng truyền lại đúng mã của a (constructor có id).
        // Giống tình huống server nhận lại người dùng qua mạng hoặc đọc từ file.
        // Bản sao này chưa nạp tiền nên số dư của nó bằng 0.
        Bidder copy = new Bidder(a.getId(), "A", "0987654321", "password1", "HN");
        // Không ném lỗi nghĩa là phiên đã nhận ra copy chính là người thắng.
        auction.payAuction(copy);
        // Tiền phải trừ trên a (đối tượng phiên đang giữ): 1000 - 110 = 890.
        // Bản sao không bị đụng tới, người bán nhận đủ 110.
        eq(a.getBalance(), 890); eq(copy.getBalance(), 0); eq(seller.getBalance(), 110);
        assertEquals(AuctionStatus.PAID, auction.getStatus());
    }

    // Bản sao cùng mã với người bán phải hủy được phiên.
    @Test void sellerCopyWithSameIdCanCancel() {
        // a đặt 110 nên đang bị giữ 110 cho phiên này.
        running(); auction.placeBid(a, money(110));
        // Đối tượng mới mang đúng mã của người bán sở hữu phiên.
        Seller copy = new Seller(seller.getId(), "Seller", "0912345678", "password1", "HN");
        auction.cancelAuction(copy);
        // Phiên chuyển sang CANCELED và tiền đang giữ của a được hoàn lại.
        assertEquals(AuctionStatus.CANCELED, auction.getStatus()); eq(a.getLockedBalance(), 0);
    }

    // Người bán khác (mã khác) không được hủy phiên của người khác.
    @Test void otherSellerCannotCancel() {
        running();
        // Không truyền id nên Entity tự sinh mã mới, khác mã của seller.
        Seller other = new Seller("Other", "0911111111", "password1", "HN");
        // assertThrows: test chỉ đạt khi lời gọi ném đúng loại ngoại lệ này.
        assertThrows(IllegalArgumentException.class, () -> auction.cancelAuction(other));
        // Bị từ chối thì trạng thái phiên phải giữ nguyên.
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
    }

    // Ca bàn giao cho Server 2: hai người đặt CÙNG một mức giá hợp lệ cùng lúc.
    // Chỉ một lượt được nhận; tiền giữ phải khớp với đúng người dẫn đầu.
    // @RepeatedTest chạy lại 20 lần vì lỗi đa luồng không phải lần nào cũng lộ ra.
    @RepeatedTest(20) void twoBiddersSamePriceOnlyOneAccepted() throws Exception {
        running();
        // Hai luồng, mỗi luồng đóng vai một người đặt giá.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        // Cổng xuất phát: cả hai luồng chờ ở await(), countDown() mở cổng cùng lúc
        // để hai lượt đặt giá chạm nhau sát nhất có thể.
        CountDownLatch startGate = new CountDownLatch(1);
        // Đếm số lượt thành công và số lượt bị từ chối (AtomicInteger an toàn đa luồng).
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try {
            List<Future<?>> results = new ArrayList<>();
            for (Bidder bidder : List.of(a, b)) {
                results.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        // 110 = giá khởi điểm 100 + bước giá 10: hợp lệ với cả hai.
                        auction.placeBid(bidder, money(110));
                        accepted.incrementAndGet();
                    } catch (IllegalArgumentException tooLow) {
                        // Người đến sau: giá hiện tại đã là 110 nên cần ít nhất 120.
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            startGate.countDown();
            // get() chờ luồng xong; nếu luồng ném lỗi lạ thì test thất bại tại đây.
            for (Future<?> result : results) result.get(5, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }

        assertEquals(1, accepted.get());
        assertEquals(1, rejected.get());
        // Giá và lịch sử chỉ ghi nhận đúng một lượt.
        eq(auction.getCurrentPrice(), 110);
        assertEquals(1, auction.getBidHistory().size());
        // Người dẫn đầu bị giữ đúng 110, người còn lại không bị giữ đồng nào.
        Bidder leader = auction.getHighestBid().getBidder();
        Bidder other = leader == a ? b : a;
        eq(leader.getLockedBalance(), 110);
        eq(other.getLockedBalance(), 0);
    }

    // Người thắng còn hoạt động thì đồng hồ không được tự hủy phiên.
    @Test void activeWinnerAuctionStaysFinished() throws InterruptedException {
        runningWithNearEnd(); auction.placeBid(a, money(110));
        awaitEnd(); auction.updateTime(); auction.updateTime();
        assertEquals(AuctionStatus.FINISHED, auction.getStatus()); eq(a.getLockedBalance(), 110);
    }

    @Test void timeUpdateOpensAndClosesAuction() throws InterruptedException {
        end = LocalDateTime.now().plusSeconds(1);
        Auction timed = new Auction(new Art("Timed", money(100)), seller, money(10), start, end);
        timed.updateTime(); assertEquals(AuctionStatus.RUNNING, timed.getStatus());
        awaitEnd(); timed.updateTime(); assertEquals(AuctionStatus.FINISHED, timed.getStatus());
    }
    @Test void observersReceiveAcceptedBidsAndCanUnsubscribe() {
        AtomicInteger count = new AtomicInteger();
        AuctionObserver observer = new AuctionObserver() {
            public void onNewBid(Auction a, Bid b) { count.incrementAndGet(); }
            public void onAuctionStatusChanged(Auction a, AuctionStatus o, AuctionStatus n) {}
            public void onAuctionEnded(Auction a, Bidder w) {}
        };
        auction.addObserver(observer); running(); auction.placeBid(a, money(110));
        auction.removeObserver(observer); auction.placeBid(a, money(120)); assertEquals(1, count.get());
    }
    @Test void concurrentBidsKeepHighestPriceAndMatchingReservation() throws Exception {
        running(); ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> work = new ArrayList<>();
            for (int i=0;i<30;i++) {
                BigDecimal amount = money(100+i*10);
                work.add(() -> { try { auction.placeBid(a, amount); } catch (IllegalArgumentException expected) {} return null; });
            }
            for (Future<Void> result : pool.invokeAll(work, 5, TimeUnit.SECONDS)) result.get();
            eq(auction.getCurrentPrice(), 390); eq(a.getLockedBalance(), 390);
        } finally { pool.shutdownNow(); }
    }
}
