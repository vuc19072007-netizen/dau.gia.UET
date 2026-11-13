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
import factory.ItemFactory;
import model.item.Item;
import model.item.ItemStatus;
import model.item.ItemType;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;

public final class AuctionManager {

    // Một đối tượng dùng chung được tạo khi lớp khởi tạo.
    private static final AuctionManager instance =
            new AuctionManager();

    private final List<Auction> auctions = new ArrayList<>();
    private final List<User> users = new ArrayList<>();
    // Kho sản phẩm của tất cả người bán. Mỗi Item tự nhớ mã chủ sở hữu (ownerId).
    private final List<Item> items = new ArrayList<>();
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

        // Kiểm tra quyền TRƯỚC khi tạo phiên: người bán chỉ được đưa sản phẩm
        // của chính mình lên đấu giá.
        checkCanAuction(item, seller);

        Auction auction = new Auction(
                item,
                seller,
                minimumIncrement,
                startTime,
                endTime);

        addAuction(auction);
        // Chỉ ghi nhận chủ sở hữu sau khi phiên đã tạo và đăng ký thành công.
        adoptItem(item, seller);
        return auction;
    }

    /*
     * Sản phẩm đã có chủ thì chỉ đúng chủ đó được tạo phiên.
     * Sản phẩm CHƯA có chủ (tạo bằng new Electronics(...) theo kiểu cũ, không qua
     * addItem()) thì vẫn cho qua để code cũ tiếp tục chạy; adoptItem() sẽ gán chủ.
     * item/seller null được bỏ qua ở đây vì constructor của Auction đã báo lỗi đó.
     */
    private void checkCanAuction(Item item, Seller seller) {
        if (item == null || seller == null) {
            return;
        }

        if (item.getOwnerId() != null && !item.isOwnedBy(seller.getId())) {
            throw new IllegalArgumentException(
                    "Chỉ chủ sở hữu được tạo phiên cho sản phẩm này");
        }
    }

    // Đưa sản phẩm kiểu cũ vào kho và gán chủ, để getItemsOf() của người bán
    // nhìn thấy nó. Sản phẩm đã ở trong kho thì không thêm lần nữa.
    private synchronized void adoptItem(Item item, Seller seller) {
        if (item.getOwnerId() == null) {
            item.setOwnerId(seller.getId());
        }

        if (findItem(item.getId()) == null) {
            items.add(item);
        }
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
        if (hasUnresolvedAuction(auction.getItem().getId())) {
            throw new IllegalArgumentException(
                    "Sản phẩm đang có phiên chưa giải quyết "
                    + "hoặc đã bán");
        }

        auctions.add(auction);
    }

    /*
     * true nếu sản phẩm còn dính tới một phiên chưa giải quyết xong hoặc đã bán.
     * Một phiên cũ được coi là "đã giải quyết, sản phẩm rảnh" khi:
     * - Phiên đã hủy (CANCELED); hoặc
     * - Phiên kết thúc (FINISHED) mà không ai đặt giá.
     * Các trường hợp còn lại (OPEN, RUNNING, FINISHED có người thắng, PAID)
     * đều coi là sản phẩm đang bận.
     * Tách thành hàm riêng vì ba nơi cùng cần luật này: đăng phiên mới,
     * sửa sản phẩm và xóa sản phẩm.
     */
    private boolean hasUnresolvedAuction(String itemId) {
        for (Auction existing : auctions) {
            if (!existing.getItem().getId().equals(itemId)) {
                continue;
            }

            AuctionStatus existingStatus = existing.getStatus();

            boolean resolved =
                    existingStatus == AuctionStatus.CANCELED
                    || (existingStatus == AuctionStatus.FINISHED
                        && existing.getHighestBid() == null);

            if (!resolved) {
                return true;
            }
        }

        return false;
    }

    // ===================== QUẢN LÝ SẢN PHẨM (màn hình Seller) =====================

    /*
     * Thêm sản phẩm mới cho một người bán.
     * - Người bán phải là tài khoản đã đăng ký trong hệ thống và chưa bị khóa.
     * - ItemFactory.getFactory(type) chọn đúng nhà máy (Art/Electronics/Vehicle);
     *   createItem() tạo đúng lớp con. Manager không cần biết lớp con cụ thể:
     *   đây là chỗ dùng mẫu Factory Method.
     * - Tên rỗng, giá <= 0 hay type null bị các lớp Item/ItemFactory từ chối
     *   bằng IllegalArgumentException.
     * Trả về sản phẩm vừa tạo để server gửi lại cho giao diện.
     */
    public synchronized Item addItem(
            Seller seller,
            ItemType type,
            String name,
            BigDecimal startingPrice) {

        if (seller == null || getUser(seller.getId()) == null) {
            throw new IllegalArgumentException(
                    "Người bán chưa đăng ký trong hệ thống");
        }

        if (!seller.isActive()) {
            throw new IllegalStateException(
                    "Tài khoản đang bị khóa");
        }

        Item item = ItemFactory.getFactory(type)
                .createItem(name, startingPrice);

        item.setOwnerId(seller.getId());
        items.add(item);
        return item;
    }

    // Tìm sản phẩm theo mã; không có thì trả null (giống getAuction/getUser).
    public synchronized Item getItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException(
                    "Mã sản phẩm không được để trống");
        }

        return findItem(itemId);
    }

    // Vòng lặp tìm kiếm dùng chung cho các hàm đã giữ khóa manager.
    private Item findItem(String itemId) {
        for (Item item : items) {
            if (item.getId().equals(itemId)) {
                return item;
            }
        }

        return null;
    }

    // Danh sách sản phẩm của một người bán, để màn hình Seller hiển thị.
    public synchronized List<Item> getItemsOf(String sellerId) {
        List<Item> result = new ArrayList<>();

        for (Item item : items) {
            if (item.isOwnedBy(sellerId)) {
                result.add(item);
            }
        }

        // Trả bản sao không sửa được, giống getAllAuction().
        return List.copyOf(result);
    }

    /*
     * Sửa tên, giá khởi điểm và mô tả của sản phẩm.
     * actor là người đang đăng nhập gửi yêu cầu (Server 2 lấy từ kết nối).
     */
    public synchronized void updateItem(
            User actor,
            String itemId,
            String name,
            BigDecimal startingPrice,
            String description) {

        Item item = requireEditableItem(actor, itemId);
        item.updateDetails(name, startingPrice, description);
    }

    // Xóa sản phẩm khỏi kho. Điều kiện giống hệt sửa sản phẩm.
    public synchronized void removeItem(User actor, String itemId) {
        Item item = requireEditableItem(actor, itemId);
        items.remove(item);
    }

    /*
     * Các kiểm tra chung trước khi sửa hoặc xóa. Qua hết thì trả về sản phẩm.
     * 1. Có người yêu cầu và tài khoản chưa bị khóa.
     * 2. Sản phẩm tồn tại.
     * 3. Người yêu cầu đúng là chủ sở hữu ("Seller chỉ sửa sản phẩm của mình").
     * 4. Sản phẩm không dính tới phiên nào chưa xong và chưa bán. Lý do: Auction
     *    chép giá khởi điểm lúc tạo phiên; nếu cho sửa giá khi phiên đang mở thì
     *    giá trong sản phẩm và giá trong phiên sẽ lệch nhau.
     * Kiểm tra cả hasUnresolvedAuction() lẫn trạng thái, vì phiên còn OPEN (chưa
     * tới giờ) thì trạng thái sản phẩm vẫn là AVAILABLE.
     */
    private Item requireEditableItem(User actor, String itemId) {
        if (actor == null) {
            throw new IllegalArgumentException(
                    "Thiếu thông tin người yêu cầu");
        }

        if (!actor.isActive()) {
            throw new IllegalStateException(
                    "Tài khoản đang bị khóa");
        }

        Item item = getItem(itemId);

        if (item == null) {
            throw new IllegalArgumentException(
                    "Không tìm thấy sản phẩm");
        }

        if (!item.isOwnedBy(actor.getId())) {
            throw new IllegalArgumentException(
                    "Chỉ chủ sở hữu được sửa hoặc xóa sản phẩm");
        }

        boolean busy = hasUnresolvedAuction(item.getId())
                || item.getStatus() == ItemStatus.IN_AUCTION
                || item.getStatus() == ItemStatus.SOLD;

        if (busy) {
            throw new IllegalStateException(
                    "Sản phẩm đang trong phiên đấu giá hoặc đã bán");
        }

        return item;
    }

    // ===================== TÀI KHOẢN (đăng ký, màn hình Admin) =====================

    /*
     * Đăng ký tài khoản từ dữ liệu thô của form đăng ký.
     * - role chỉ nhận "BIDDER" hoặc "SELLER". Không cho tự đăng ký "ADMIN":
     *   nếu cho thì ai cũng tự cấp quyền quản trị cho mình được.
     * - Kiểm tra tên, số điện thoại 10 số, mật khẩu 8-15 ký tự nằm sẵn trong
     *   constructor của User, nên ở đây không viết lại.
     * - addUser() kiểm tra trùng số điện thoại. Hàm này synchronized nên hai
     *   người đăng ký cùng một số cùng lúc thì chỉ một người thành công.
     * Trả về tài khoản vừa tạo.
     */
    public synchronized User register(
            String role,
            String name,
            String phoneNumber,
            String password,
            String address) {

        if (role == null) {
            throw new IllegalArgumentException(
                    "Vui lòng chọn vai trò");
        }

        User user;

        // switch trên String: so nội dung chuỗi (giống equals), phân biệt hoa thường.
        switch (role) {
            case "BIDDER":
                user = new Bidder(name, phoneNumber, password, address);
                break;
            case "SELLER":
                user = new Seller(name, phoneNumber, password, address);
                break;
            default:
                throw new IllegalArgumentException(
                        "Chỉ đăng ký được vai trò BIDDER hoặc SELLER");
        }

        addUser(user);
        return user;
    }

    /*
     * Danh sách mọi tài khoản cho màn hình Admin (xem, khóa, mở khóa).
     * Chỉ Admin đang hoạt động được xem, vì danh sách chứa thông tin của mọi người.
     * Trả bản sao không thêm/xóa được; việc khóa/mở khóa vẫn đi qua
     * Admin.banUser()/unBanUser() trên từng tài khoản.
     */
    public synchronized List<User> getAllUsers(User actor) {
        if (!(actor instanceof Admin) || !actor.isActive()) {
            throw new IllegalArgumentException(
                    "Chỉ admin được xem danh sách tài khoản");
        }

        return List.copyOf(users);
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