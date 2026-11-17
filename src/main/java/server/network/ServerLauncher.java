package server.network;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

import model.auction.Auction;
import model.item.Art;
import model.item.Electronics;
import model.item.Item;
import model.item.ItemType;
import model.item.Vehicle;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import persistence.DataStore;
import service.AuctionManager;

/**
 * Điểm khởi chạy (Entry Point) của Socket Server cho hệ thống Đấu giá Trực tuyến.
 * <p>
 * Thứ tự khởi động:
 * 1. Tải dữ liệu đã lưu (file auction-data.ser). Lần chạy đầu chưa có file thì
 *    nạp dữ liệu mẫu.
 * 2. Mở Socket Server.
 * 3. Khi tắt (Ctrl + C), lưu toàn bộ dữ liệu ra file để lần sau chạy tiếp.
 * <p>
 * Cách chạy: mvn compile exec:java   (cổng mặc định 8888)
 * hoặc:      mvn compile exec:java -Dexec.args="9000"
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
        System.out.println("   ONLINE AUCTION SYSTEM - SOCKET SERVER         ");
        System.out.println("=================================================");

        DataStore store = new DataStore();
        loadOrSeed(store);

        AuctionServer server = new AuctionServer(port);
        try {
            server.start();
            System.out.println("Server đang chạy trên cổng " + server.getPort() + ". Nhấn Ctrl + C để dừng.");
            printDemoAccounts();

            // Đăng ký Shutdown Hook để đóng server và lưu dữ liệu khi tắt ứng dụng
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nĐang tắt Server an toàn...");
                server.stop();
                AuctionManager.getInstance().stopClock();
                save(store);
                System.out.println("Server đã tắt thành công.");
            }));

            // Giữ luồng chính chạy
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Không thể khởi động Server: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Không thể khởi động Server", e);
        }
    }

    /**
     * Tải dữ liệu cũ nếu có, không thì nạp dữ liệu mẫu.
     * File hỏng (ví dụ lớp đã đổi cấu trúc) thì báo lỗi và dùng dữ liệu mẫu.
     */
    static void loadOrSeed(DataStore store) {
        try {
            if (AuctionManager.getInstance().loadFrom(store)) {
                System.out.println("Đã tải dữ liệu từ " + DataStore.DEFAULT_FILE);
                return;
            }
        } catch (IllegalStateException error) {
            LOGGER.log(Level.WARNING, "Không đọc được file dữ liệu, dùng dữ liệu mẫu", error);
        }
        seedInitialData();
    }

    private static void save(DataStore store) {
        try {
            AuctionManager.getInstance().saveTo(store);
            System.out.println("Đã lưu dữ liệu vào " + DataStore.DEFAULT_FILE);
        } catch (IllegalStateException error) {
            LOGGER.log(Level.WARNING, "Không lưu được dữ liệu", error);
        }
    }

    private static void printDemoAccounts() {
        System.out.println("Tài khoản mẫu (số điện thoại / mật khẩu):");
        System.out.println("  ADMIN   0900000000 / admin123");
        System.out.println("  SELLER  0912345678 / password1   (Cong ty Dau gia Ha Noi)");
        System.out.println("  SELLER  0912000111 / password1   (Phong tranh Hoi An)");
        System.out.println("  BIDDER  0987654321 / password1   (Nguyen Van A, 50 triệu)");
        System.out.println("  BIDDER  0977112233 / password1   (Tran Thi B, 100 triệu)");
    }

    /**
     * Nạp dữ liệu mẫu ban đầu để thử nghiệm ngay: 1 admin, 2 người bán,
     * 2 người mua có sẵn tiền, 3 phiên đang chạy và 1 phiên sắp mở.
     */
    public static void seedInitialData() {
        AuctionManager manager = AuctionManager.getInstance();

        // Admin không đăng ký được qua giao diện (register() chặn ADMIN), nên tạo sẵn.
        Admin admin = new Admin("Quan tri vien", "0900000000", "admin123", "Ha Noi");
        Seller seller = new Seller("Cong ty Dau gia Ha Noi", "0912345678", "password1", "Ha Noi");
        Seller gallery = new Seller("Phong tranh Hoi An", "0912000111", "password1", "Quang Nam");
        Bidder bidder1 = new Bidder("Nguyen Van A", "0987654321", "password1", "TP Ho Chi Minh");
        Bidder bidder2 = new Bidder("Tran Thi B", "0977112233", "password1", "Da Nang");
        bidder1.deposit(new BigDecimal("50000000"));
        bidder2.deposit(new BigDecimal("100000000"));

        for (User user : new User[] {admin, seller, gallery, bidder1, bidder2}) {
            if (manager.getUser(user.getId()) == null) {
                try {
                    manager.addUser(user);
                } catch (IllegalArgumentException ignored) {
                    // Số điện thoại đã có: bỏ qua tài khoản mẫu này.
                }
            }
        }

        if (!manager.getAllAuction().isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        Electronics phone = (Electronics) manager.addItem(
                seller, ItemType.ELECTRONICS, "iPhone 16 Pro Max 256GB", new BigDecimal("30000000"));
        phone.setDescription("Máy mới 100% chính hãng VN/A");
        phone.setBrand("Apple");
        phone.setModel("A3296");
        Auction phoneAuction = manager.createAuction(
                phone, seller, new BigDecimal("500000"), now.minusMinutes(10), now.plusHours(2));
        phoneAuction.startAuction();

        Art painting = (Art) manager.addItem(
                gallery, ItemType.ART, "Tranh sơn dầu Phố Cổ Hà Nội", new BigDecimal("15000000"));
        painting.setDescription("Tác phẩm nguyên bản có chữ ký họa sĩ");
        painting.setArtist("Bùi Xuân Phái");
        painting.setYearPublished(1980);
        Auction artAuction = manager.createAuction(
                painting, gallery, new BigDecimal("1000000"), now.minusMinutes(5), now.plusHours(1));
        artAuction.setAntiSnipingEnabled(true);
        artAuction.startAuction();

        Vehicle bike = (Vehicle) manager.addItem(
                seller, ItemType.VEHICLE, "Honda Super Cub 1985", new BigDecimal("20000000"));
        bike.setDescription("Xe zin, giấy tờ đầy đủ");
        bike.setManufacturer("Honda");
        bike.setYearMade(1985);
        // Phiên ngắn 5 phút, bật chống bid phút chót để demo.
        Auction bikeAuction = manager.createAuction(
                bike, seller, new BigDecimal("200000"), now.minusSeconds(5), now.plusMinutes(5));
        bikeAuction.setAntiSnipingEnabled(true);
        bikeAuction.startAuction();

        // Phiên sắp mở sau 2 phút: đồng hồ server tự chuyển sang RUNNING.
        Item watch = manager.addItem(
                seller, ItemType.ELECTRONICS, "Đồng hồ Casio G-Shock", new BigDecimal("2000000"));
        manager.createAuction(watch, seller, new BigDecimal("100000"), now.plusMinutes(2), now.plusMinutes(30));

        // Một sản phẩm còn trong kho, chưa lên phiên, để demo màn hình Seller.
        manager.addItem(seller, ItemType.ART, "Bình gốm Bát Tràng", new BigDecimal("3000000"));

        LOGGER.info("Đã nạp dữ liệu mẫu.");
    }
}
