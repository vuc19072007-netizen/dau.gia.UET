package client.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import model.auction.AuctionStatus;

/**
 * Định dạng tiền, thời gian và trạng thái để hiển thị trên giao diện.
 */
public final class Formats {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Formats() {
    }

    /** 1500000 -> "1.500.000 đ". */
    public static String money(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symbols).format(amount) + " đ";
    }

    /** Đọc số tiền người dùng gõ, cho phép dấu chấm/phẩy/khoảng trắng phân cách. */
    public static BigDecimal parseMoney(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập số tiền");
        }
        String digits = text.replace(".", "").replace(",", "").replace(" ", "")
                .replace("đ", "").trim();
        try {
            BigDecimal value = new BigDecimal(digits);
            if (value.signum() <= 0) {
                throw new IllegalArgumentException("Số tiền phải lớn hơn 0");
            }
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Số tiền không hợp lệ: " + text);
        }
    }

    public static String dateTime(LocalDateTime time) {
        return time == null ? "-" : DATE_TIME.format(time);
    }

    public static String time(LocalDateTime time) {
        return time == null ? "-" : TIME.format(time);
    }

    /** Thời gian còn lại dạng "hh:mm:ss"; hết giờ thì "00:00:00". */
    public static String countdown(LocalDateTime end) {
        if (end == null) {
            return "-";
        }
        Duration left = Duration.between(LocalDateTime.now(), end);
        if (left.isNegative()) {
            return "00:00:00";
        }
        long seconds = left.getSeconds();
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    public static String status(AuctionStatus status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case OPEN -> "Sắp mở";
            case RUNNING -> "Đang diễn ra";
            case FINISHED -> "Đã kết thúc";
            case PAID -> "Đã thanh toán";
            case CANCELED -> "Đã hủy";
        };
    }

    public static String role(String role) {
        if (role == null) {
            return "-";
        }
        return switch (role) {
            case "BIDDER" -> "Người mua";
            case "SELLER" -> "Người bán";
            case "ADMIN" -> "Quản trị";
            default -> role;
        };
    }
}
