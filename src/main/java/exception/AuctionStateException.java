package exception;

/**
 * Ngoại lệ dùng chung khi thao tác không hợp lệ với trạng thái hiện tại
 * của phiên đấu giá (ví dụ: đặt giá khi phiên chưa mở, kết thúc phiên
 * chưa tới giờ, thanh toán phiên chưa kết thúc...).
 */
public class AuctionStateException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public AuctionStateException(String message) {
        super(message);
    }

    public AuctionStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
