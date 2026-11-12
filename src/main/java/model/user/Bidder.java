package model.user;

import java.math.BigDecimal;
import exception.InsufficientFundsException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Người đấu giá: User cung cấp thông tin tài khoản, Payable cung cấp thao tác ví,
 * Biddable cung cấp khả năng giữ tiền và thanh toán.
 * Phương thức synchronized khóa trên chính đối tượng bidder: tại một thời điểm,
 * một luồng được xử lý số dư của người này. Gọi lại hàm synchronized trên cùng
 * đối tượng từ luồng đang giữ khóa là hợp lệ (khóa có tính reentrant).
 * Thiếu tiền thì ném InsufficientFundsException (kế thừa IllegalArgumentException).
 */
public class Bidder extends User implements Payable, Biddable {
    private BigDecimal balance;
    private BigDecimal lockedBalance; // số tiền cọc để đấu giá
    private BigDecimal maxBidLimit;   // mức giá tối đa tự động
    private boolean autoBidEnabled = false;
    // Chi tiết khoản giữ của từng phiên; vẫn giữ tổng lockedBalance theo sơ đồ.
    // UUID là khóa bí mật do Auction nắm giữ; giá trị là tiền đang giữ cho phiên.
    // Map khác tổng lockedBalance: biết chính xác khoản nào được hoàn/thanh toán.
    private final Map<UUID, BigDecimal> auctionFunds = new HashMap<>();

    // Tổng tiền khóa gồm: tiền thủ công + tiền của tất cả các phiên.
    // Hàm private này chỉ được gọi khi phương thức synchronized đã giữ khóa ví.
    private BigDecimal manualLockedBalance() {
        BigDecimal auctionTotal = BigDecimal.ZERO;
        for (BigDecimal amount : auctionFunds.values()) auctionTotal = auctionTotal.add(amount);
        return lockedBalance.subtract(auctionTotal);
    }

    // key được phiên giữ kín. total là tổng giá đặt, không phải phần tăng thêm.
    public synchronized void reserveForAuction(UUID key, BigDecimal total) {
        if (key == null || total == null || total.signum() <= 0)
            throw new IllegalArgumentException("Khoản giữ không hợp lệ");
        // Chưa từng giữ tiền cho phiên thì old = 0.
        BigDecimal old = auctionFunds.getOrDefault(key, BigDecimal.ZERO);
        // Ví dụ đã giữ 100, đặt lên 150 thì chỉ cần thêm 50.
        // BigDecimal không sửa chính nó; add/subtract trả về một giá trị mới.
        BigDecimal extra = total.subtract(old);
        if (extra.signum() < 0) throw new IllegalArgumentException("Không được giảm giá giữ");
        if (extra.compareTo(getAvailableBalance()) > 0)
            throw new InsufficientFundsException("Số dư khả dụng không đủ");
        auctionFunds.put(key, total);
        lockedBalance = lockedBalance.add(extra);
        markUpdated();
    }

    // Hoàn tiền phiên: giảm tiền khóa, không cộng balance vì lúc giữ chưa trừ balance.
    // Nếu khoản đã được xóa thì không làm gì, tránh hoàn cùng khoản hai lần.
    public synchronized void releaseAuctionFunds(UUID key) {
        BigDecimal amount = auctionFunds.remove(key);
        if (amount != null) {
            lockedBalance = lockedBalance.subtract(amount);
            markUpdated();
        }
    }

    // Thanh toán đúng khoản giữ của phiên, đồng thời trừ tổng tiền và tiền khóa.
    // Xóa khoản giữ để không thể thanh toán cùng khoản đó thêm lần nữa.
    public synchronized void payAuctionFunds(UUID key, BigDecimal amount) {
        BigDecimal reserved = auctionFunds.get(key);
        if (amount == null || amount.signum() <= 0 || reserved == null || reserved.compareTo(amount) != 0)
            throw new IllegalStateException("Tiền thanh toán không khớp khoản giữ của phiên");
        balance = balance.subtract(amount);
        lockedBalance = lockedBalance.subtract(reserved);
        auctionFunds.remove(key);
        markUpdated();
    }

    public Bidder(String id, String name, String phoneNumber, String password, String address) {
        super(id, name, phoneNumber, password, address, "BIDDER");
        this.balance = BigDecimal.ZERO;
        this.lockedBalance = BigDecimal.ZERO;
        this.maxBidLimit = BigDecimal.ZERO;
    }

    public Bidder(String name, String phoneNumber, String password, String address) {
        super(name, phoneNumber, password, address, "BIDDER");
        this.balance = BigDecimal.ZERO;
        this.lockedBalance = BigDecimal.ZERO;
        this.maxBidLimit = BigDecimal.ZERO;
    }

    // Nạp tiền
    @Override
    public synchronized void deposit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0");
        }
        this.balance = this.balance.add(amount);
        markUpdated();
    }

    // API cũ chỉ hoàn khoản giữ thủ công.
    // Tiền phiên khi bị vượt giá được Auction hoàn bằng releaseAuctionFunds().
    public synchronized void restoreFunds(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền hoàn phải lớn hơn 0!");
        }

        if (amount.compareTo(manualLockedBalance()) > 0) {
            throw new IllegalArgumentException("Số tiền hoàn vượt quá số tiền đang khóa!");
        }

        this.lockedBalance = this.lockedBalance.subtract(amount);
        markUpdated();
    }

    // Lưu cấu hình bật/tắt và hạn mức; chưa phải thuật toán tự động đặt giá.
    public synchronized void enableAutoBid(BigDecimal maxLimit) {
        if (!canAfford(maxLimit)) {
            throw new InsufficientFundsException("Số dư khả dụng không đủ");
        }
        this.maxBidLimit = maxLimit;
        this.autoBidEnabled = true;
        markUpdated();
    }

    // Tắt đấu giá tự động
    public synchronized void disableAutoBid() {
        this.autoBidEnabled = false;
        this.maxBidLimit = BigDecimal.ZERO;
        markUpdated();
    }

    // Lấy tổng số dư
    @Override
    public synchronized BigDecimal getBalance() {
        return balance;
    }

    // Lấy số dư khả dụng
    @Override
    public synchronized BigDecimal getAvailableBalance() {
        return balance.subtract(lockedBalance);
    }

    // API cũ thanh toán tiền giữ thủ công; tiền thắng phiên đi qua payAuctionFunds().
    @Override
    public synchronized void confirmPayment(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền thanh toán phải lớn hơn 0!");
        }
        if (amount.compareTo(manualLockedBalance()) > 0) {
            throw new IllegalArgumentException("Số tiền thanh toán vượt quá số tiền đang khóa!");
        }
        if (amount.compareTo(this.balance) > 0) {
            throw new InsufficientFundsException("Số dư không đủ để thanh toán!");
        }
        //trừ số tiền khóa của phiên đấu giá thắng
        this.balance = this.balance.subtract(amount);
        this.lockedBalance = this.lockedBalance.subtract(amount);
        markUpdated();
    }

    // Kiểm tra khả năng tài chính (còn tiền khả dụng hay không)
    @Override
    public synchronized boolean canAfford() {
        return getAvailableBalance().compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public synchronized boolean canAfford(BigDecimal amount) {
        return amount != null
                && amount.compareTo(BigDecimal.ZERO) > 0
                && amount.compareTo(getAvailableBalance()) <= 0;
    }

    // Giữ tiền thủ công: amount là số tiền giữ thêm.
    // Auction sử dụng reserveForAuction() để không trộn tiền giữa các phiên.
    @Override
    public synchronized void lockFunds(BigDecimal amount) {
        if (!canAfford(amount)) {
            throw new InsufficientFundsException("Số dư không đủ để khóa");
        }
        this.lockedBalance = this.lockedBalance.add(amount);
        markUpdated();
    }

    // Chỉ mở tiền giữ thủ công, không được mở khoản giữ của phiên
    @Override
    public synchronized BigDecimal unlockFunds() {
        BigDecimal unlocked = manualLockedBalance();
        this.lockedBalance = this.lockedBalance.subtract(unlocked);
        markUpdated();
        return unlocked;
    }

    // Rút tiền
    @Override
    public synchronized void withdraw(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền rút phải lớn hơn 0!");
        }

        if (amount.compareTo(getAvailableBalance()) > 0) {
            throw new InsufficientFundsException("Số dư khả dụng không đủ để rút tiền!");
        }

        this.balance = this.balance.subtract(amount);
        markUpdated();
    }

    public synchronized BigDecimal getLockedBalance() {
        return lockedBalance;
    }

    public synchronized BigDecimal getMaxBidLimit() {
        return maxBidLimit;
    }

    public synchronized boolean isAutoBidEnabled() {
        return autoBidEnabled;
    }
}