package model.auction;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import exception.AuctionStateException;
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
    private final LocalDateTime endTime;

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
        Bid newBid;
        List<AuctionObserver> listeners;

        bidLock.lock();

        try {
            validateBidder(bidder);
            validateAuctionAcceptsBids();
            validateAmount(amount);

            newBid = new Bid(bidder, amount);
            moveReservedFunds(bidder, amount);

            // Chỉ ghi nhận người dẫn đầu sau khi thao tác tiền thành công.
            highestBid = newBid;
            currentPrice = amount;
            bidHistory.add(newBid);

            listeners = List.copyOf(observers);

        } finally {
            bidLock.unlock();
        }

        // Gửi thông báo ngoài vùng khóa.
        notifyObservers(
                listeners,
                observer -> observer.onNewBid(this, newBid));

        return true;
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
                winner = highestBid == null ? null : highestBid.getBidder();
                if (winner == null) item.setStatus(ItemStatus.UNSOLD);
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

    public LocalDateTime getEndTime() {
        return endTime;
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
