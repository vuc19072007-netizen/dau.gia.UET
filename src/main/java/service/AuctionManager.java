package service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import model.auction.Auction;
import model.auction.AuctionStatus;
import model.item.Item;
import model.user.Seller;
import model.user.User;

public final class AuctionManager {

    // Một đối tượng dùng chung được tạo khi lớp khởi tạo.
    private static final AuctionManager instance =
            new AuctionManager();

    private final List<Auction> auctions = new ArrayList<>();
    private final List<User> users = new ArrayList<>();
    // Bộ lập lịch chạy một luồng nền, dùng chung cho mọi phiên.
    // Việc thay đổi từng phiên vẫn được bảo vệ bằng bidLock của chính phiên.
    private ScheduledExecutorService clock;

    // synchronized khóa manager, ngăn hai nơi cùng tạo hai đồng hồ.
    public synchronized void startClock() {
        if (clock != null && !clock.isShutdown()) return;
        clock = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "auction-clock");
            // Daemon không giữ JVM sống nếu ứng dụng đã đóng mọi luồng chính.
            thread.setDaemon(true);
            return thread;
        });
        // this::updateAllAuctions tương đương () -> updateAllAuctions().
        // Chạy ngay lần đầu; mỗi lần xong nghỉ 100 ms rồi chạy tiếp.
        // Độ trễ cập nhật trạng thái có thể lớn hơn 100 ms nếu hệ thống đang bận.
        clock.scheduleWithFixedDelay(this::updateAllAuctions, 0, 100, TimeUnit.MILLISECONDS);
    }

    public void updateAllAuctions() {
        // Chụp danh sách rồi bỏ khóa manager trước khi gọi Auction/Observer.
        for (Auction auction : getAllAuction()) {
            try { auction.updateTime(); }
            catch (RuntimeException error) {
                // Một phiên lỗi không được làm dừng lịch cập nhật các phiên còn lại.
                Logger.getLogger(AuctionManager.class.getName())
                        .log(Level.WARNING, "Không cập nhật được phiên", error);
            }
        }
    }

    // Gọi khi đóng JavaFX để dừng tác vụ nền. Có thể gọi nhiều lần an toàn.
    public synchronized void stopClock() {
        if (clock != null) {
            clock.shutdownNow();
            clock = null;
        }
    }

    // Ngăn các lớp khác gọi new AuctionManager().
    private AuctionManager() {
    }

    // Singleton chỉ đảm bảo có một manager; an toàn đa luồng của dữ liệu
    // được cung cấp riêng bởi synchronized/ReentrantLock, không tự có từ Singleton.
    public static AuctionManager getInstance() {
        return instance;
    }

    // Tạo đối tượng phiên và đăng ký vào manager.
    public Auction createAuction(
            Item item,
            Seller seller,
            BigDecimal minimumIncrement,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        Auction auction = new Auction(
                item,
                seller,
                minimumIncrement,
                startTime,
                endTime);

        addAuction(auction);
        return auction;
    }

    public synchronized void addAuction(Auction auction) {
        if (auction == null) {
            throw new IllegalArgumentException(
                    "Phiên đấu giá không được null");
        }

        if (getAuction(auction.getAuctionId()) != null) {
            throw new IllegalArgumentException(
                    "Mã phiên đấu giá đã tồn tại");
        }

        /*
         * Kiểm tra sản phẩm đã xuất hiện trong phiên khác chưa.
         * Theo quy tắc hiện tại, chỉ đăng lại khi:
         * - Phiên cũ đã hủy; hoặc
         * - Phiên cũ kết thúc mà không có người đặt giá.
         */
        for (Auction existing : auctions) {
            boolean sameItem = existing.getItem().getId()
                    .equals(auction.getItem().getId());

            if (!sameItem) {
                continue;
            }

            AuctionStatus existingStatus = existing.getStatus();

            boolean canRelist =
                    existingStatus == AuctionStatus.CANCELED
                    || (existingStatus == AuctionStatus.FINISHED
                        && existing.getHighestBid() == null);

            if (!canRelist) {
                throw new IllegalArgumentException(
                        "Sản phẩm đang có phiên chưa giải quyết "
                        + "hoặc đã bán");
            }
        }

        auctions.add(auction);
    }

    public synchronized Auction getAuction(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Mã phiên không được để trống");
        }

        for (Auction auction : auctions) {
            if (auctionId.equals(auction.getAuctionId())) {
                return auction;
            }
        }

        return null;
    }

    public synchronized List<Auction> getAllAuction() {
        // Trả bản sao không cho thêm/xóa phần tử.
        return List.copyOf(auctions);
    }

    public synchronized void addUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Người dùng không được null");
        }

        if (getUser(user.getId()) != null) {
            throw new IllegalArgumentException(
                    "Mã người dùng đã tồn tại");
        }

        for (User existing : users) {
            if (existing.getPhoneNumber()
                    .equals(user.getPhoneNumber())) {
                throw new IllegalArgumentException(
                        "Số điện thoại đã được sử dụng");
            }
        }

        users.add(user);
    }

    public synchronized User getUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException(
                    "Mã người dùng không được để trống");
        }

        for (User user : users) {
            if (userId.equals(user.getId())) {
                return user;
            }
        }

        return null;
    }

    public synchronized User login(
            String phoneNumber,
            String password) {

        if (phoneNumber == null || password == null) {
            throw new IllegalArgumentException(
                    "Vui lòng nhập số điện thoại và mật khẩu");
        }

        String phone = phoneNumber.trim();

        for (User user : users) {
            boolean correctPhone =
                    user.getPhoneNumber().equals(phone);

            boolean correctPassword =
                    user.getPassword().equals(password);

            if (correctPhone && correctPassword) {
                if (!user.isActive()) {
                    throw new IllegalStateException(
                            "Tài khoản đang bị khóa");
                }

                // Trả đúng đối tượng đang được manager quản lý.
                return user;
            }
        }

        throw new IllegalArgumentException(
                "Số điện thoại hoặc mật khẩu không đúng");
    }
}