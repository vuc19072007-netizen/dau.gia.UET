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
