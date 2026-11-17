package model.auction;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import exception.AuctionStateException;
import exception.InsufficientFundsException;
import exception.InvalidBidException;
import model.item.Item;
import model.item.ItemStatus;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import model.user.Admin;

/**
 * Một phiên đấu giá: quản lý giá, lịch sử, trạng thái và danh sách Observer.
 * bidLock bảo vệ chuỗi đọc -> kiểm tra -> cập nhật, tránh hai luồng cùng dùng giá cũ.
 * notifyObservers gọi sau khi bỏ bidLock để giao diện/observer chậm không chặn đặt giá.
 * <p>
 * Lỗi nghiệp vụ dùng ngoại lệ chung của nhóm (gói exception):
 * - InvalidBidException: lượt đặt giá không hợp lệ (người đặt, số tiền, bước giá).
 * - AuctionStateException: thao tác sai trạng thái hoặc sai thời gian của phiên.
 * - InsufficientFundsException: thiếu tiền, do ví Bidder ném ra.
 * Ba lớp này kế thừa IllegalArgumentException/IllegalStateException nên code cũ
 * đang bắt hai loại đó vẫn chạy đúng.
 * <p>
 * Serializable để DataStore lưu/tải phiên ra file.
 */
public class Auction implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER =
            Logger.getLogger(Auction.class.getName());

    private final String auctionId;
    // Khác auctionId công khai: khóa này không có getter, chỉ phiên biết.
    private final UUID fundsKey = UUID.randomUUID();
    private final Item item;
    private final Seller seller;

    private BigDecimal currentPrice;
    private final BigDecimal minimumIncrement;

    private final LocalDateTime startTime;
    // Không còn final: Anti-sniping có thể gia hạn giờ kết thúc.
    // Mọi lần đọc/ghi endTime đều nằm trong bidLock.
    private LocalDateTime endTime;

    // ===== Anti-sniping =====
    // Lượt đặt giá rơi vào SNIPING_WINDOW cuối phiên thì phiên được kéo dài thêm
    // EXTENSION. Mục đích: không ai thắng được bằng cách đặt giá ở giây cuối cùng
    // khi người khác không còn kịp phản ứng.
    public static final Duration SNIPING_WINDOW = Duration.ofSeconds(30);
    public static final Duration EXTENSION = Duration.ofSeconds(30);
    // Mặc định tắt; người bán bật khi tạo phiên.
    private boolean antiSnipingEnabled;

    // ===== Auto-bidding =====
    // Hàng đợi ưu tiên: phần tử đứng đầu là cấu hình có giá tối đa cao nhất,
    // bằng nhau thì ai đăng ký trước đứng trước (xem AutoBid.compareTo).
    private final PriorityQueue<AutoBid> autoBids = new PriorityQueue<>();
    // Số thứ tự đăng ký, dùng để phân xử khi hai người cùng giá tối đa.
    private long autoBidSequence;
    // Chặn vòng lặp vô hạn nếu có lỗi logic: tối đa ngần này lượt tự động mỗi lần.
    private static final int MAX_AUTO_BIDS_PER_ROUND = 1000;

    private AuctionStatus status;
    private Bid highestBid;

    private final List<Bid> bidHistory = new ArrayList<>();

    // transient: không ghi observer ra file. Observer là giao diện/kết nối mạng
    // của lần chạy hiện tại, không serialize được và cũng vô nghĩa sau khi khởi
    // động lại. readObject() tạo lại danh sách rỗng.
    private transient List<AuctionObserver> observers =
            new ArrayList<>();

    // Bảo vệ dữ liệu phiên khi nhiều luồng cùng truy cập.
    private final ReentrantLock bidLock = new ReentrantLock();

    public Auction(
            Item item,
            Seller seller,
            BigDecimal minimumIncrement,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        if (item == null || !item.isValid()) {
            throw new IllegalArgumentException(
                    "Sản phẩm không hợp lệ");
        }

        if (seller == null) {
            throw new IllegalArgumentException(
                    "Người bán không được null");
        }

        if (!seller.isActive()) {
            throw new IllegalArgumentException(
                    "Tài khoản người bán đang bị khóa");
        }

        if (minimumIncrement == null
                || minimumIncrement.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Bước giá phải lớn hơn 0");
        }

        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException(
                    "Thời gian không được null");
        }

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        this.auctionId = UUID.randomUUID().toString();
        this.item = item;
        this.seller = seller;
        this.currentPrice = item.getStartingPrice();
        this.minimumIncrement = minimumIncrement;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = AuctionStatus.OPEN;
    }

    // OPEN -> RUNNING.
    public void startAuction() {
        List<AuctionObserver> listeners;

        bidLock.lock();

        try {
            if (status != AuctionStatus.OPEN) {
                throw new AuctionStateException(
                        "Chỉ bắt đầu được phiên OPEN");
            }

            LocalDateTime now = LocalDateTime.now();

            if (now.isBefore(startTime)) {
                throw new AuctionStateException(
                        "Chưa đến thời gian bắt đầu");
            }

            if (!now.isBefore(endTime)) {
                throw new AuctionStateException(
                        "Đã hết thời gian tổ chức phiên");
            }

            item.setStatus(ItemStatus.IN_AUCTION);
            status = AuctionStatus.RUNNING;
            listeners = List.copyOf(observers);

        } finally {
            bidLock.unlock();
        }

        notifyObservers(
                listeners,
                observer -> observer.onAuctionStatusChanged(
                        this,
                        AuctionStatus.OPEN,
                        AuctionStatus.RUNNING));
    }

    /*
     * Đặt giá. Cả chuỗi kiểm tra -> giữ tiền -> ghi nhận nằm trong bidLock nên
     * hai luồng không thể cùng dùng một giá cũ (chống race condition/lost update).
     * Hàm chỉ điều phối; từng bước nằm trong một hàm private bên dưới.
     */
    public boolean placeBid(Bidder bidder, BigDecimal amount) {
        // Một lượt đặt tay có thể kéo theo nhiều lượt đặt tự động (auto-bid).
        // Gom tất cả vào danh sách để thông báo sau khi bỏ khóa.
        List<Bid> newBids = new ArrayList<>();
        List<AuctionObserver> listeners;

        bidLock.lock();

        try {
            validateBidder(bidder);
            validateAuctionAcceptsBids();
            validateAmount(amount);

            newBids.add(recordBid(bidder, amount));
            // Người khác có cấu hình auto-bid sẽ tự trả giá lại (nếu còn hạn mức).
            resolveAutoBids(newBids);

            listeners = List.copyOf(observers);

        } finally {
            bidLock.unlock();
        }

        // Gửi thông báo ngoài vùng khóa, đúng thứ tự các lượt đã xảy ra.
        notifyNewBids(listeners, newBids);

        return true;
    }

    /*
     * Ghi nhận một lượt đặt giá đã qua kiểm tra người đặt/trạng thái/số tiền.
     * Phải được gọi khi đang giữ bidLock.
     * Thứ tự: giữ tiền trước, ghi giá sau. Thiếu tiền thì ném lỗi ngay ở bước
     * giữ tiền, phiên chưa bị đổi gì.
     */
    private Bid recordBid(Bidder bidder, BigDecimal amount) {
        Bid newBid = new Bid(bidder, amount);
        moveReservedFunds(bidder, amount);

        // Chỉ ghi nhận người dẫn đầu sau khi thao tác tiền thành công.
        highestBid = newBid;
        currentPrice = amount;
        bidHistory.add(newBid);

        applyAntiSniping();
        return newBid;
    }

    /*
     * Anti-sniping: nếu lượt đặt giá xảy ra khi phiên còn <= SNIPING_WINDOW thì
     * cộng thêm EXTENSION vào giờ kết thúc. So bằng Duration (đến từng nano giây)
     * chứ không cắt về giây, để lượt đặt ở 0,8 giây cuối vẫn được gia hạn.
     * Gọi trong bidLock nên không chạy xen với đồng hồ đóng phiên.
     */
    private void applyAntiSniping() {
        if (!antiSnipingEnabled) {
            return;
        }

        Duration remaining = Duration.between(LocalDateTime.now(), endTime);

        if (!remaining.isNegative()
                && remaining.compareTo(SNIPING_WINDOW) <= 0) {
            endTime = endTime.plus(EXTENSION);
            LOGGER.info(() -> "Anti-sniping: phiên " + auctionId
                    + " được gia hạn tới " + endTime);
        }
    }

    // Người đặt phải tồn tại, chưa bị khóa và không phải người bán của phiên.
    private void validateBidder(Bidder bidder) {
        if (bidder == null) {
            throw new InvalidBidException(
                    "Người đặt giá không được null");
        }

        if (!bidder.isActive()) {
            throw new InvalidBidException(
                    "Tài khoản người đặt giá đang bị khóa");
        }

        if (seller.getId().equals(bidder.getId())) {
            throw new InvalidBidException(
                    "Không được đặt giá sản phẩm của chính mình");
        }
    }

    // Phiên phải đang RUNNING và còn trong khoảng thời gian nhận giá.
    private void validateAuctionAcceptsBids() {
        if (status != AuctionStatus.RUNNING) {
            throw new AuctionStateException(
                    "Phiên không ở trạng thái RUNNING");
        }

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(startTime)
                || !now.isBefore(endTime)) {
            throw new AuctionStateException(
                    "Ngoài thời gian đặt giá");
        }
    }

    // Số tiền phải dương và đạt tối thiểu giá hiện tại + bước giá.
    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidBidException(
                    "Giá đặt phải lớn hơn 0");
        }

        // Quy tắc của nhóm: áp dụng bước giá từ cả lượt đặt đầu tiên.
        BigDecimal minimumAmount = currentPrice.add(minimumIncrement);

        if (amount.compareTo(minimumAmount) < 0) {
            throw new InvalidBidException(
                    "Giá đặt tối thiểu là " + minimumAmount);
        }
    }

    /*
     * Giữ tiền của người đặt mới và hoàn tiền cho người dẫn đầu cũ.
     * Thiếu tiền thì reserveForAuction() ném InsufficientFundsException trước khi
     * đổi bất kỳ số dư nào, nên phiên và các ví không bị sửa dở dang.
     */
    private void moveReservedFunds(Bidder bidder, BigDecimal amount) {
        Bidder previousLeader =
                highestBid == null ? null : highestBid.getBidder();

        // Một thứ tự khóa chung cho các phiên đổi tiền giữa nhiều bidder.
        // Ví: synchronized từng đối tượng. Phiên: ReentrantLock.
        synchronized (Bidder.class) {
            synchronized (bidder) {
                if (previousLeader != null && previousLeader != bidder
                        && previousLeader.getId().equals(bidder.getId())) {
                    throw new InvalidBidException(
                            "Hãy dùng đúng đối tượng tài khoản đã đăng nhập");
                }
                // reserveForAuction tự tính phần tăng thêm cho chính phiên này.
                bidder.reserveForAuction(fundsKey, amount);
                if (previousLeader != null && previousLeader != bidder) {
                    // Chỉ giải phóng tiền của phiên này; tiền phiên khác giữ nguyên.
                    previousLeader.releaseAuctionFunds(fundsKey);
                }
            }
        }
    }

    // RUNNING -> FINISHED.
    public void finishAuction() {
        Bidder winner;
        List<AuctionObserver> listeners;

        bidLock.lock();

        try {
            if (status != AuctionStatus.RUNNING) {
                throw new AuctionStateException(
                        "Chỉ kết thúc được phiên RUNNING");
            }

            if (LocalDateTime.now().isBefore(endTime)) {
                throw new AuctionStateException(
                        "Chưa đến thời gian kết thúc");
            }

            if (highestBid == null) {
                item.setStatus(ItemStatus.UNSOLD);
            }
            status = AuctionStatus.FINISHED;
            // Phiên đóng thì các cấu hình tự động không còn ý nghĩa.
            autoBids.clear();

            winner = highestBid == null
                    ? null
                    : highestBid.getBidder();

            // Giữ nguyên tiền của người thắng để chờ thanh toán.
            listeners = List.copyOf(observers);

        } finally {
            bidLock.unlock();
        }

        notifyObservers(
                listeners,
                observer -> observer.onAuctionStatusChanged(
                        this,
                        AuctionStatus.RUNNING,
                        AuctionStatus.FINISHED));

        notifyObservers(
                listeners,
                observer -> observer.onAuctionEnded(this, winner));
    }

    // FINISHED -> PAID.
    public void payAuction(Bidder payer) {
        List<AuctionObserver> listeners;

        bidLock.lock();

        try {
            if (status != AuctionStatus.FINISHED) {
                throw new AuctionStateException(
                        "Chỉ thanh toán được phiên FINISHED");
            }

            if (highestBid == null) {
                throw new AuctionStateException(
                        "Phiên không có người thắng");
            }

            Bidder winner = highestBid.getBidder();

            /*
             * Kiểm tra người thanh toán có đúng là người thắng không.
             *
             * Trước đây dùng (payer != winner): toán tử != trên đối tượng chỉ
             * so ĐỊA CHỈ trong bộ nhớ, tức hai biến có trỏ tới cùng một đối
             * tượng hay không. Khi dữ liệu được đọc lại từ file hoặc nhận qua
             * mạng, Java tạo ra đối tượng MỚI có nội dung giống hệt nhưng địa
             * chỉ khác, nên chính người thắng cũng bị từ chối.
             *
             * Bây giờ so theo mã người dùng (getId()): mỗi tài khoản có một mã
             * duy nhất do Entity cấp, nên cùng mã nghĩa là cùng một người.
             * Dùng equals() vì mã là String; == trên String cũng chỉ so địa chỉ.
             * Gọi winner.getId().equals(...) (winner chắc chắn khác null) để
             * không bị NullPointerException.
             */
            if (payer == null
                    || !winner.getId().equals(payer.getId())) {
                throw new IllegalArgumentException(
                        "Chỉ người thắng được thanh toán");
            }

            // Từ đây trở xuống chỉ dùng winner, không dùng payer: winner là đối
            // tượng phiên đang giữ nên trạng thái khóa và số dư của nó mới là
            // dữ liệu thật. payer có thể là bản sao đã cũ.
            if (!winner.isActive()) {
                throw new IllegalStateException(
                        "Tài khoản đang bị khóa");
            }

            BigDecimal payment = highestBid.getAmount();

            /*
             * Giữ thứ tự khóa:
             * phiên -> bidder -> seller.
             *
             * Các phương thức số dư trong Bidder và Seller
             * phải được khai báo synchronized.
             */
            synchronized (winner) {
                synchronized (seller) {
                    // Khóa cả hai ví trong lúc chuyển tiền. Các getter/withdraw
                    // synchronized trên chúng phải chờ thao tác này hoàn tất.
                    winner.payAuctionFunds(fundsKey, payment);
                    seller.deposit(payment);

                    item.setStatus(ItemStatus.SOLD);
                    status = AuctionStatus.PAID;
                }
            }

            listeners = List.copyOf(observers);

        } finally {
            bidLock.unlock();
        }

        notifyObservers(
                listeners,
                observer -> observer.onAuctionStatusChanged(
                        this,
                        AuctionStatus.FINISHED,
                        AuctionStatus.PAID));
    }

    // Overload có kiểm tra quyền cho giao diện: seller sở hữu phiên hoặc admin.
    // Hàm hủy nội bộ chỉ được gọi sau khi kiểm tra quyền của người yêu cầu.
    public void cancelAuction(User actor) {
        /*
         * Ba điều kiện bị từ chối:
         * 1. actor == null: không biết ai yêu cầu.
         * 2. Tài khoản của actor đang bị khóa.
         * 3. actor không phải Admin VÀ cũng không phải người bán của phiên.
         *
         * Điều kiện 3 so theo mã người dùng (getId()) thay vì (actor != seller).
         * Lý do giống payAuction(): != chỉ so địa chỉ đối tượng, nên bản sao
         * của đúng người bán (đọc từ file/nhận qua mạng) sẽ bị từ chối sai.
         * instanceof Admin kiểm tra kiểu thật của đối tượng lúc chạy.
         */
        if (actor == null || !actor.isActive()
                || (!(actor instanceof Admin)
                    && !seller.getId().equals(actor.getId()))) {
            throw new IllegalArgumentException("Chỉ người bán sở hữu phiên hoặc admin được hủy");
        }
        cancelAuction();
    }

    /** Đồng hồ gọi định kỳ. Không giữ khóa khi gửi thông báo Observer. */
    public void updateTime() {
        AuctionStatus oldStatus;
        AuctionStatus newStatus;
        Bidder winner = null;
        List<AuctionObserver> listeners;
        bidLock.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            oldStatus = status;
            // Phiên OPEN bị bỏ lỡ cả khoảng thời gian thì hủy, không cho bắt đầu muộn.
            if (status == AuctionStatus.OPEN && !now.isBefore(endTime)) {
                item.setStatus(ItemStatus.CANCELED);
                status = AuctionStatus.CANCELED;
            // Đến giờ bắt đầu: mở nhận giá.
            } else if (status == AuctionStatus.OPEN && !now.isBefore(startTime)) {
                item.setStatus(ItemStatus.IN_AUCTION);
                status = AuctionStatus.RUNNING;
            // Hết giờ: ngừng nhận giá. Có người thắng thì giữ tiền chờ thanh toán.
            } else if (status == AuctionStatus.RUNNING && !now.isBefore(endTime)) {
                status = AuctionStatus.FINISHED;
                autoBids.clear();
                winner = highestBid == null ? null : highestBid.getBidder();
                if (winner == null) {
                    item.setStatus(ItemStatus.UNSOLD);
                }
            } else {
                return;
            }
            newStatus = status;
            listeners = List.copyOf(observers);
        } finally {
            bidLock.unlock();
        }
        notifyObservers(listeners, o -> o.onAuctionStatusChanged(this, oldStatus, newStatus));
        // Dùng biến cục bộ để callback biết kết quả của lần chuyển trạng thái này.
        if (newStatus == AuctionStatus.FINISHED) {
            Bidder result = winner;
            notifyObservers(listeners, o -> o.onAuctionEnded(this, result));
        }
    }

    // OPEN/RUNNING/FINISHED -> CANCELED.
    private void cancelAuction() {
        AuctionStatus oldStatus;
        List<AuctionObserver> listeners;

        bidLock.lock();

        try {
            if (status == AuctionStatus.PAID
                    || status == AuctionStatus.CANCELED) {
                throw new AuctionStateException(
                        "Không thể hủy phiên đã thanh toán hoặc đã hủy");
            }

            // Phiên không ai đặt đã giải quyết xong; sản phẩm có thể
            // được đăng lại. Không để phiên cũ sửa sản phẩm lần nữa.
            if (status == AuctionStatus.FINISHED && highestBid == null) {
                throw new AuctionStateException(
                        "Phiên đã kết thúc mà không có người đặt giá");
            }

            oldStatus = status;

            if (highestBid != null) {
                // Hoàn khoản tiền phiên đang giữ.
                highestBid.getBidder()
                        .releaseAuctionFunds(fundsKey);
            }

            item.setStatus(ItemStatus.CANCELED);
            status = AuctionStatus.CANCELED;
            autoBids.clear();
            listeners = List.copyOf(observers);

        } finally {
            bidLock.unlock();
        }

        notifyObservers(
                listeners,
                observer -> observer.onAuctionStatusChanged(
                        this,
                        oldStatus,
                        AuctionStatus.CANCELED));
    }

    // ===================== AUTO-BIDDING =====================

    /*
     * Đăng ký (hoặc thay) cấu hình đấu giá tự động cho một người mua.
     * - maxBid: số tiền tối đa người mua chấp nhận trả.
     * - increment: mỗi lần tự trả giá thì cộng thêm bao nhiêu. Nếu nhỏ hơn bước
     *   giá của phiên thì dùng bước giá của phiên.
     * Đăng ký xong, hệ thống chạy ngay một vòng: nếu người này chưa dẫn đầu và
     * hạn mức còn đủ, họ được đặt giá tự động luôn.
     * Trả về true nếu cấu hình còn hiệu lực sau vòng đó (chưa bị loại vì hết
     * hạn mức hoặc thiếu tiền).
     */
    public boolean registerAutoBid(
            Bidder bidder,
            BigDecimal maxBid,
            BigDecimal increment) {

        List<Bid> newBids = new ArrayList<>();
        List<AuctionObserver> listeners;
        boolean stillActive;

        bidLock.lock();

        try {
            validateBidder(bidder);
            validateAuctionAcceptsBids();

            if (maxBid == null || maxBid.signum() <= 0) {
                throw new InvalidBidException(
                        "Giá tối đa phải lớn hơn 0");
            }

            if (increment == null || increment.signum() <= 0) {
                throw new InvalidBidException(
                        "Bước tự động phải lớn hơn 0");
            }

            boolean leading = isLeader(bidder);
            BigDecimal needed = leading
                    ? currentPrice
                    : currentPrice.add(minimumIncrement);

            if (maxBid.compareTo(needed) < 0) {
                throw new InvalidBidException(
                        "Giá tối đa phải từ " + needed + " trở lên");
            }

            // Mỗi người chỉ có một cấu hình cho mỗi phiên: xóa cái cũ (nếu có).
            removeAutoBidOf(bidder.getId());
            autoBids.add(new AutoBid(bidder, maxBid, increment, autoBidSequence++));

            resolveAutoBids(newBids);
            stillActive = findAutoBid(bidder.getId()) != null;
            listeners = List.copyOf(observers);

        } finally {
            bidLock.unlock();
        }

        notifyNewBids(listeners, newBids);
        return stillActive;
    }

    // Hủy cấu hình tự động. Trả về false nếu người này chưa đăng ký.
    public boolean cancelAutoBid(String bidderId) {
        bidLock.lock();

        try {
            return removeAutoBidOf(bidderId);
        } finally {
            bidLock.unlock();
        }
    }

    // Giá tối đa đang cấu hình của một người mua, null nếu không có.
    public BigDecimal getAutoBidMax(String bidderId) {
        bidLock.lock();

        try {
            AutoBid autoBid = findAutoBid(bidderId);
            return autoBid == null ? null : autoBid.maxBid;
        } finally {
            bidLock.unlock();
        }
    }

    /*
     * Vòng xử lý auto-bid, gọi khi đang giữ bidLock. Cách làm giống "đấu giá ủy
     * quyền" (proxy bidding): mỗi người cho hệ thống biết giá tối đa, hệ thống
     * chỉ trả vừa đủ để dẫn đầu.
     *
     * Mỗi bước lấy "ứng viên": cấu hình có giá tối đa cao nhất mà KHÔNG phải của
     * người đang dẫn đầu (thứ tự lấy từ hàng đợi ưu tiên). Sau đó so với cấu hình
     * của người đang dẫn đầu (nếu người đó cũng bật auto-bid):
     *
     * 1. Người dẫn đầu không có auto-bid, hoặc ứng viên có hạn mức CAO HƠN:
     *    ứng viên đặt mức vừa vượt hạn mức của người dẫn đầu
     *    (hạn mức đó + bước của ứng viên), không quá hạn mức của chính mình.
     *    Không có đối thủ tự động thì chỉ đặt giá hiện tại + bước.
     * 2. Ứng viên có hạn mức THẤP HƠN HOẶC BẰNG người dẫn đầu: ứng viên không
     *    thể thắng nên bị loại; người dẫn đầu được nâng giá lên đúng mức cạnh
     *    tranh (bằng hạn mức ứng viên nếu hai hạn mức bằng nhau, ngược lại là
     *    hạn mức ứng viên + bước của người dẫn đầu). Vì vậy khi hai người cùng
     *    hạn mức, ai đăng ký trước (đang dẫn đầu) thắng.
     *
     * Cấu hình hết hạn mức hoặc ví không đủ tiền bị loại khỏi hàng đợi: giá chỉ
     * tăng nên chúng không bao giờ dùng lại được.
     */
    private void resolveAutoBids(List<Bid> newBids) {
        for (int round = 0; round < MAX_AUTO_BIDS_PER_ROUND; round++) {
            AutoBid candidate = nextAutoBidder();

            if (candidate == null) {
                return;
            }

            AutoBid leaderAuto = highestBid == null
                    ? null
                    : findAutoBid(highestBid.getBidder().getId());
            BigDecimal minimumNext = currentPrice.add(minimumIncrement);

            if (leaderAuto == null
                    || candidate.maxBid.compareTo(leaderAuto.maxBid) > 0) {
                // Trường hợp 1: ứng viên vượt lên.
                BigDecimal base = leaderAuto == null ? currentPrice : leaderAuto.maxBid;
                BigDecimal amount = base.add(candidate.step(minimumIncrement))
                        .min(candidate.maxBid)
                        .max(minimumNext);
                placeAutoBid(candidate, amount, newBids);
            } else {
                // Trường hợp 2: người dẫn đầu giữ được vị trí.
                autoBids.remove(candidate);
                BigDecimal target = candidate.maxBid.compareTo(leaderAuto.maxBid) == 0
                        ? leaderAuto.maxBid
                        : candidate.maxBid.add(leaderAuto.step(minimumIncrement))
                                .min(leaderAuto.maxBid);

                if (target.compareTo(minimumNext) >= 0) {
                    placeAutoBid(leaderAuto, target, newBids);
                }
            }
        }
    }

    // Đặt giá thay cho một cấu hình; ví không đủ tiền thì loại cấu hình đó.
    private void placeAutoBid(AutoBid autoBid, BigDecimal amount, List<Bid> newBids) {
        try {
            newBids.add(recordBid(autoBid.bidder, amount));
        } catch (InsufficientFundsException | InvalidBidException error) {
            autoBids.remove(autoBid);
        }
    }

    // Cấu hình được quyền trả giá tiếp theo, hoặc null nếu không còn ai.
    private AutoBid nextAutoBidder() {
        BigDecimal minimumNext = currentPrice.add(minimumIncrement);
        List<AutoBid> ordered = new ArrayList<>(autoBids);
        // Sao chép rồi sắp xếp: duyệt PriorityQueue trực tiếp không theo thứ tự.
        ordered.sort(null);

        for (AutoBid autoBid : ordered) {
            if (isLeader(autoBid.bidder)) {
                continue;
            }

            if (!autoBid.bidder.isActive()
                    || autoBid.maxBid.compareTo(minimumNext) < 0) {
                autoBids.remove(autoBid);
                continue;
            }

            return autoBid;
        }

        return null;
    }

    private boolean isLeader(Bidder bidder) {
        return highestBid != null
                && highestBid.getBidder().getId().equals(bidder.getId());
    }

    private AutoBid findAutoBid(String bidderId) {
        for (AutoBid autoBid : autoBids) {
            if (autoBid.bidder.getId().equals(bidderId)) {
                return autoBid;
            }
        }

        return null;
    }

    private boolean removeAutoBidOf(String bidderId) {
        AutoBid existing = findAutoBid(bidderId);
        return existing != null && autoBids.remove(existing);
    }

    /*
     * Một cấu hình đấu giá tự động.
     * implements Comparable để PriorityQueue tự sắp xếp: giá tối đa cao hơn đứng
     * trước; bằng nhau thì sequence nhỏ hơn (đăng ký sớm hơn) đứng trước.
     * Serializable để lưu cùng phiên ra file.
     */
    private static final class AutoBid
            implements Comparable<AutoBid>, Serializable {
        private static final long serialVersionUID = 1L;

        private final Bidder bidder;
        private final BigDecimal maxBid;
        private final BigDecimal increment;
        private final long sequence;

        private AutoBid(
                Bidder bidder,
                BigDecimal maxBid,
                BigDecimal increment,
                long sequence) {
            this.bidder = bidder;
            this.maxBid = maxBid;
            this.increment = increment;
            this.sequence = sequence;
        }

        // Bước tự động thực tế: không nhỏ hơn bước giá của phiên.
        private BigDecimal step(BigDecimal minimumIncrement) {
            return increment.max(minimumIncrement);
        }

        @Override
        public int compareTo(AutoBid other) {
            int byMax = other.maxBid.compareTo(this.maxBid);
            return byMax != 0 ? byMax : Long.compare(this.sequence, other.sequence);
        }
    }

    // Gửi onNewBid cho từng lượt mới, ngoài vùng khóa.
    private void notifyNewBids(List<AuctionObserver> listeners, List<Bid> newBids) {
        for (Bid bid : newBids) {
            notifyObservers(listeners, observer -> observer.onNewBid(this, bid));
        }
    }

    public void addObserver(AuctionObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException(
                    "Observer không được null");
        }

        bidLock.lock();

        try {
            if (!observers.contains(observer)) {
                observers.add(observer);
            }
        } finally {
            bidLock.unlock();
        }
    }

    public void removeObserver(AuctionObserver observer) {
        bidLock.lock();

        try {
            observers.remove(observer);
        } finally {
            bidLock.unlock();
        }
    }

    /*
     * notification là hành động cần thực hiện với từng observer.
     * Một observer lỗi không ngăn thông báo cho các observer còn lại.
     */
    private void notifyObservers(
            List<AuctionObserver> listeners,
            Consumer<AuctionObserver> notification) {
        // Consumer là hành động nhận một Observer và không trả về giá trị.
        // Lambda truyền vào quyết định gửi bid mới, đổi trạng thái hay kết thúc.
        // accept(observer) thực hiện hành động đó với từng người nhận.

        for (AuctionObserver observer : listeners) {
            try {
                // Callback có thể đến xen kẽ giữa các luồng; giao diện đọc lại
                // trạng thái mới nhất thay vì ghi đè bằng dữ liệu của sự kiện cũ.
                notification.accept(observer);

            } catch (RuntimeException exception) {
                LOGGER.log(
                        Level.WARNING,
                        "Không thể gửi thông báo tới observer",
                        exception);
            }
        }
    }

    /*
     * Hai hàm đặc biệt mà Java Serialization tự gọi khi ghi/đọc đối tượng.
     * writeObject giữ bidLock để file không chứa trạng thái dở dang nếu có
     * người đặt giá đúng lúc đang lưu.
     * readObject tạo lại danh sách observer (trường transient đọc lên là null).
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        bidLock.lock();

        try {
            out.defaultWriteObject();
        } finally {
            bidLock.unlock();
        }
    }

    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        observers = new ArrayList<>();
    }

    public String getAuctionId() {
        return auctionId;
    }

    public Item getItem() {
        return item;
    }

    public Seller getSeller() {
        return seller;
    }

    public BigDecimal getMinimumIncrement() {
        return minimumIncrement;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    // Có khóa vì Anti-sniping có thể đổi endTime trong lúc luồng khác đang đọc.
    public LocalDateTime getEndTime() {
        bidLock.lock();

        try {
            return endTime;
        } finally {
            bidLock.unlock();
        }
    }

    public boolean isAntiSnipingEnabled() {
        bidLock.lock();

        try {
            return antiSnipingEnabled;
        } finally {
            bidLock.unlock();
        }
    }

    public void setAntiSnipingEnabled(boolean enabled) {
        bidLock.lock();

        try {
            antiSnipingEnabled = enabled;
        } finally {
            bidLock.unlock();
        }
    }

    public BigDecimal getCurrentPrice() {
        bidLock.lock();

        try {
            return currentPrice;
        } finally {
            bidLock.unlock();
        }
    }

    public AuctionStatus getStatus() {
        bidLock.lock();

        try {
            return status;
        } finally {
            bidLock.unlock();
        }
    }

    public Bid getHighestBid() {
        bidLock.lock();

        try {
            return highestBid;
        } finally {
            bidLock.unlock();
        }
    }

    public List<Bid> getBidHistory() {
        bidLock.lock();

        try {
            return List.copyOf(bidHistory);
        } finally {
            bidLock.unlock();
        }
    }
}
