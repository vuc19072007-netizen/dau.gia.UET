package server.network;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.logging.Logger;

import factory.ItemFactory;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemType;
import model.user.Bidder;
import model.user.Seller;
import service.AuctionManager;

/**
 * Điểm khởi chạy (Entry Point) của Socket Server cho hệ thống Đấu giá Trực tuyến.
 * Khởi tạo dữ liệu mẫu (Seed Data) và mở Socket Server lắng nghe các Client kết nối.
 */
public class ServerLauncher {
    private static final Logger LOGGER = Logger.getLogger(ServerLauncher.class.getName());

    public static void main(String[] args) {
        int port = AuctionServer.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                LOGGER.warning("Cổng truyền vào không hợp lệ, sử dụng cổng mặc định: " + port);
            }
        }

        System.out.println("=================================================");
        System.out.println("   ONLINE AUCTION SYSTEM - SOCKET SERVER 2       ");
        System.out.println("=================================================");

        // Khởi tạo dữ liệu mẫu
        seedInitialData();

        // Khởi chạy server
        AuctionServer server = new AuctionServer(port);
        try {
            server.start();
            System.out.println("Server đang chạy trên cổng " + port + ". Nhấn Ctrl + C để dừng.");

            // Đăng ký Shutdown Hook để đóng server một cách an toàn khi tắt ứng dụng
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nĐang tắt Server an toàn...");
                server.stop();
                AuctionManager.getInstance().stopClock();
                System.out.println("Server đã tắt thành công.");
            }));

            // Giữ luồng chính chạy
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Không thể khởi động Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Nạp dữ liệu mẫu ban đầu để thử nghiệm kết nối mạng ngay lập tức.
     */
    public static void seedInitialData() {
        AuctionManager manager = AuctionManager.getInstance();

        // Tài khoản Người bán mẫu
        Seller seller = new Seller(
                "Cong ty Dau gia Ha Noi",
                "0912345678",
                "password123",
                "Ha Noi"
        );
        try {
            manager.addUser(seller);
        } catch (IllegalArgumentException ignored) {}

        // Tài khoản Người mua mẫu có sẵn số dư trong ví
        Bidder bidder1 = new Bidder(
                "Nguyen Van A",
                "0987654321",
                "password1",
                "TP Ho Chi Minh"
        );
        bidder1.deposit(new BigDecimal("50000000")); // Nạp 50 triệu
        try {
            manager.addUser(bidder1);
        } catch (IllegalArgumentException ignored) {}

        Bidder bidder2 = new Bidder(
                "Tran Thi B",
                "0977112233",
                "password2",
                "Da Nang"
        );
        bidder2.deposit(new BigDecimal("100000000")); // Nạp 100 triệu
        try {
            manager.addUser(bidder2);
        } catch (IllegalArgumentException ignored) {}

        // Tạo một số sản phẩm và phiên đấu giá mẫu đang RUNNING
        if (manager.getAllAuction().isEmpty()) {
            // 1. Điện thoại iPhone 16 Pro Max (Đang RUNNING)
            Item phone = ItemFactory.getFactory(ItemType.ELECTRONICS).createItem(
                    "iPhone 16 Pro Max 256GB Titan",
                    new BigDecimal("30000000")
            );
            phone.setDescription("Máy mới 100% chính hãng VN/A");

            Auction auctionPhone = manager.createAuction(
                    phone,
                    seller,
                    new BigDecimal("500000"), // Bước giá 500k
                    LocalDateTime.now().minusMinutes(10), // Bắt đầu 10 phút trước
                    LocalDateTime.now().plusHours(2)      // Kết thúc sau 2 tiếng
            );
            auctionPhone.startAuction();

            // 2. Tranh sơn dầu cổ điển (Đang RUNNING)
            Item art = ItemFactory.getFactory(ItemType.ART).createItem(
                    "Tranh son dau Pho Co Ha Noi 1980",
                    new BigDecimal("15000000")
            );
            art.setDescription("Tác phẩm nguyên bản có chữ ký họa sĩ");

            Auction auctionArt = manager.createAuction(
                    art,
                    seller,
                    new BigDecimal("1000000"), // Bước giá 1 triệu
                    LocalDateTime.now().minusMinutes(5),
                    LocalDateTime.now().plusHours(1)
            );
            auctionArt.startAuction();

            LOGGER.info("Đã nạp thành công dữ liệu mẫu (Seller, 2 Bidders, 2 Running Auctions).");
        }
    }
}
