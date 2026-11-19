package common.network.payload;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload cho NEW_BID_NOTIFICATION (Server broadcast đến toàn bộ Client khi có lượt đặt giá mới).
 */
public class BidNotificationPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private String auctionId;
    private String bidId;
    private String bidderId;
    private String bidderName;
    private BigDecimal amount;
    private BigDecimal newCurrentPrice;
    private LocalDateTime timestamp;

    public BidNotificationPayload() {
    }

    public BidNotificationPayload(
            String auctionId,
            String bidId,
            String bidderId,
            String bidderName,
            BigDecimal amount,
            BigDecimal newCurrentPrice,
            LocalDateTime timestamp) {
        this.auctionId = auctionId;
        this.bidId = bidId;
        this.bidderId = bidderId;
        this.bidderName = bidderName;
        this.amount = amount;
        this.newCurrentPrice = newCurrentPrice;
        this.timestamp = timestamp;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public String getBidId() {
        return bidId;
    }

    public void setBidId(String bidId) {
        this.bidId = bidId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public void setBidderId(String bidderId) {
        this.bidderId = bidderId;
    }

    public String getBidderName() {
        return bidderName;
    }

    public void setBidderName(String bidderName) {
        this.bidderName = bidderName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getNewCurrentPrice() {
        return newCurrentPrice;
    }

    public void setNewCurrentPrice(BigDecimal newCurrentPrice) {
        this.newCurrentPrice = newCurrentPrice;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "BidNotificationPayload{" +
                "auctionId='" + auctionId + '\'' +
                ", bidderName='" + bidderName + '\'' +
                ", amount=" + amount +
                ", newCurrentPrice=" + newCurrentPrice +
                ", timestamp=" + timestamp +
                '}';
    }
}

