package exception;

/**
 * Ngoại lệ dùng chung khi số dư không đủ để thực hiện giao dịch
 * (đặt cọc, thanh toán, rút tiền...).
 */
public class InsufficientFundsException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(String message) {
        super(message);
    }

    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }
}