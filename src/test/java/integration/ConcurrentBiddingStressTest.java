package integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import client.network.AuctionClient;
import model.auction.Auction;
import model.item.ItemType;
import model.user.Bidder;
import model.user.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.network.AuctionServer;
import service.AuctionManager;

/**
 * Kiểm tra đồng thời qua mạng: 50 client Socket đăng nhập 50 tài khoản khác nhau
 * rồi cùng lúc đặt giá vào một phiên. Sau khi xong, trạng thái phải nhất quán:
 * - giá hiện tại bằng lượt cao nhất được chấp nhận;
 * - mỗi mức giá chỉ được chấp nhận tối đa một lần (không lost update);
 * - chỉ người dẫn đầu còn bị giữ tiền, đúng bằng giá hiện tại.
 */
class ConcurrentBiddingStressTest {
    private static final int CLIENTS = 50;
    private static final String PASSWORD = "password1";

    private AuctionServer server;
    private Auction auction;
    private final List<Bidder> bidders = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        AuctionManager manager = AuctionManager.getInstance();
        manager.reset();

        Seller seller = new Seller("Seller", "0915000000", PASSWORD, "HN");
        manager.addUser(seller);
        for (int i = 1; i <= CLIENTS; i++) {
            Bidder bidder = new Bidder("Bidder " + i, String.format("0916%06d", i), PASSWORD, "HN");
            bidder.deposit(BigDecimal.valueOf(1_000_000));
            manager.addUser(bidder);
            bidders.add(bidder);
        }

        var item = manager.addItem(seller, ItemType.ART, "Tranh", BigDecimal.valueOf(1000));
        auction = manager.createAuction(item, seller, BigDecimal.valueOf(10),
                LocalDateTime.now().minusSeconds(1), LocalDateTime.now().plusMinutes(5));
        auction.startAuction();

        server = new AuctionServer(0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        AuctionManager.getInstance().reset();
    }

    @Test
    void fiftyClientsBidAtOnceAndStateStaysConsistent() throws Exception {
        List<AuctionClient> clients = new ArrayList<>();
        for (Bidder bidder : bidders) {
            AuctionClient client = new AuctionClient("localhost", server.getPort());
            client.connect();
            assertTrue(client.login(bidder.getPhoneNumber(), PASSWORD));
            clients.add(client);
        }

        ExecutorService pool = Executors.newFixedThreadPool(CLIENTS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        List<Future<?>> results = new ArrayList<>();

        try {
            for (int i = 0; i < CLIENTS; i++) {
                AuctionClient client = clients.get(i);
                // Cứ hai người đặt cùng một mức giá: 1010, 1010, 1020, 1020, ...
                BigDecimal amount = BigDecimal.valueOf(1000 + (i / 2 + 1) * 10L);
                results.add(pool.submit(() -> {
                    start.await();
                    if (client.placeBid(auction.getAuctionId(), null, amount).isSuccess()) {
                        accepted.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            clients.forEach(AuctionClient::disconnect);
        }

        // Lịch sử phải tăng dần, mỗi mức giá tối đa một lần.
        var history = auction.getBidHistory();
        assertEquals(accepted.get(), history.size());
        for (int i = 1; i < history.size(); i++) {
            assertTrue(history.get(i).getAmount().compareTo(history.get(i - 1).getAmount()) > 0,
                    "giá trong lịch sử phải tăng dần");
        }

        BigDecimal finalPrice = auction.getCurrentPrice();
        assertEquals(0, finalPrice.compareTo(history.get(history.size() - 1).getAmount()));

        // Chỉ người dẫn đầu bị giữ tiền, đúng bằng giá hiện tại.
        String leaderId = auction.getHighestBid().getBidder().getId();
        BigDecimal totalLocked = BigDecimal.ZERO;
        for (Bidder bidder : bidders) {
            totalLocked = totalLocked.add(bidder.getLockedBalance());
            if (!bidder.getId().equals(leaderId)) {
                assertEquals(0, bidder.getLockedBalance().signum(), bidder.getName() + " không được bị giữ tiền");
            }
        }
        assertEquals(0, totalLocked.compareTo(finalPrice));
    }
}
