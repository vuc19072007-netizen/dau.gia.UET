package common.network.payload;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import model.auction.Bid;

/**
 * Một dòng trong lịch sử đặt giá, gửi qua mạng.
 */
public class BidDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String bidderId;
    private final String bidderName;
    private final BigDecimal amount;
    private final LocalDateTime time;

    public BidDTO(String bidderId, String bidderName, BigDecimal amount, LocalDateTime time) {
        this.bidderId = bidderId;
        this.bidderName = bidderName;
        this.amount = amount;
        this.time = time;
    }

    public static BidDTO fromBid(Bid bid) {
        return new BidDTO(
                bid.getBidder().getId(),
                bid.getBidder().getName(),
                bid.getAmount(),
                bid.getCreatedAt());
    }

    public String getBidderId() {
        return bidderId;
    }

    public String getBidderName() {
        return bidderName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getTime() {
        return time;
    }
}
