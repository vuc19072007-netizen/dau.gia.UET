package exception;

/**
 * Ngoại lệ dùng chung khi một lượt đặt giá không hợp lệ.
 * <p>
 * Được dùng bởi {@code Auction.placeBid} khi giá không đủ bước,
 * người đặt không hợp lệ, hoặc vi phạm quy tắc đấu giá.
 * Server/GUI bắt ngoại lệ này để hiển thị lý do cho người dùng.
 */
public class InvalidBidException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public InvalidBidException(String message) {
        super(message);
    }

    public InvalidBidException(String message, Throwable cause) {
        super(message, cause);
    }
}