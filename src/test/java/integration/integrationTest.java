package integration;

import model.auction.Auction;
import model.auction.AuctionStatus;
import model.item.Art;
import model.user.Bidder;
import model.user.Seller;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử tích hợp: mô phỏng một server đấu giá với nhiều client
 * (bidder) cùng đặt giá trên một phiên.
 * <p>
 * Kịch bản:
 * 1. Tạo một phiên với giá khởi điểm 100, bước giá 10.
 * 2. 10 bidder, mỗi người có 10.000.
 * 3. Mỗi bidder đặt giá tăng dần (100, 110, 120, ...) nhiều lần.
 * 4. Kiểm tra sau khi kết thúc: giá cao nhất đúng, người thắng đúng,
 *    tổng tiền khóa của người thắng khớp, không ai bị trừ tiền sai.
 */
class IntegrationTest {

    static BigDecimal money(long value) {
        return BigDecimal.valueOf(value);
    }

    @Test
    void manyClientsBidConcurrentlyAndWinnerIsCorrect() throws Exception {
        Seller seller = new Seller("Seller", "0912345678", "password1", "HN");
        LocalDateTime start = LocalDateTime.now().minusSeconds(1);
            LocalDateTime end = LocalDateTime.now().plusSeconds(2);
        Auction auction = new Auction(new Art("Bức tranh", money(100)),
                seller, money(10), start, end);
        auction.startAuction();

        // 10 bidder, mỗi người 10.000.
        int clientCount = 10;
        List<Bidder> bidders = new ArrayList<>();
        for (int i = 0; i < clientCount; i++) {
            Bidder bidder = new Bidder("Bidder-" + i,
                    "09" + String.format("%08d", i), "password1", "HN");
            bidder.deposit(money(10000));
            bidders.add(bidder);
        }

        // Mỗi bidder đặt giá tăng dần: 100 + i*10, rồi +100 mỗi vòng.
        ExecutorService pool = Executors.newFixedThreadPool(clientCount);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int round = 0; round < 5; round++) {
                for (int i = 0; i < clientCount; i++) {
                    Bidder bidder = bidders.get(i);
                    long amount = 100 + i * 10 + round * 100;
                    tasks.add(() -> {
                        try {
                            auction.placeBid(bidder, money(amount));
                        } catch (RuntimeException expected) {
                            // Bid thấp hơn giá hiện tại bị từ chối - hợp lệ.
                        }
                        return null;
                    });
                }
            }
            for (Future<Void> result : pool.invokeAll(tasks, 10, TimeUnit.SECONDS)) {
                result.get();
            }
        } finally {
            pool.shutdownNow();
        }

        // Giá cao nhất phải là 100 + 9*10 + 4*100 = 590.
        assertEquals(0, auction.getCurrentPrice().compareTo(money(590)));
        Bidder winner = auction.getHighestBid().getBidder();
        assertNotNull(winner);

        // Người thắng phải là bidder đặt 590 (Bidder-9, vòng cuối).
        assertEquals("Bidder-9", winner.getName());

        // Tiền khóa của người thắng = 590.
        assertEquals(0, winner.getLockedBalance().compareTo(money(590)));

                // Chờ hết giờ rồi kết thúc phiên và thanh toán.
                while (LocalDateTime.now().isBefore(end)) {
                    Thread.sleep(10);
                }
                auction.finishAuction();
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
        auction.payAuction(winner);
        assertEquals(AuctionStatus.PAID, auction.getStatus());

        // Người thắng trả 590, còn 10000 - 590 = 9410.
        assertEquals(0, winner.getBalance().compareTo(money(9410)));
        // Seller nhận 590.
        assertEquals(0, seller.getBalance().compareTo(money(590)));
    }

    @Test
    void concurrentBidsNeverExceedAvailableBalance() throws Exception {
        Seller seller = new Seller("Seller", "0912345678", "password1", "HN");
        LocalDateTime start = LocalDateTime.now().minusSeconds(1);
        LocalDateTime end = start.plusMinutes(5);
        Auction auction = new Auction(new Art("Đồng hồ", money(100)),
                seller, money(10), start, end);
        auction.startAuction();

        // Một bidder có ít tiền (500), nhiều luồng cùng đặt giá cao.
        Bidder poor = new Bidder("Poor", "0987654321", "password1", "HN");
        poor.deposit(money(500));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                long amount = 100 + i * 10;
                tasks.add(() -> {
                    try {
                        auction.placeBid(poor, money(amount));
                    } catch (RuntimeException expected) {
                        // Vượt số dư hoặc giá thấp - hợp lệ.
                    }
                    return null;
                });
            }
            for (Future<Void> result : pool.invokeAll(tasks, 10, TimeUnit.SECONDS)) {
                result.get();
            }
        } finally {
            pool.shutdownNow();
        }

        // Tiền khóa không bao giờ vượt số dư (500).
        assertTrue(poor.getLockedBalance().compareTo(money(500)) <= 0);
        // Số dư khả dụng không âm.
        assertTrue(poor.getAvailableBalance().signum() >= 0);
    }
}
