package common.network.payload;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload cho AUCTION_CLOSED_NOTIFICATION (Server broadcast khi phiên đấu giá kết thúc hoặc đóng).
 */
public class AuctionClosedNotificationPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private String auctionId;
    private String winnerId;
    private String winnerName;
    private BigDecimal finalPrice;
    private LocalDateTime closedAt;
    private String status; // "FINISHED", "CANCELED", "PAID"

    public AuctionClosedNotificationPayload() {
    }

    public AuctionClosedNotificationPayload(
            String auctionId,
            String winnerId,
            String winnerName,
            BigDecimal finalPrice,
            LocalDateTime closedAt,
            String status) {
        this.auctionId = auctionId;
        this.winnerId = winnerId;
        this.winnerName = winnerName;
        this.finalPrice = finalPrice;
        this.closedAt = closedAt;
        this.status = status;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(String winnerId) {
        this.winnerId = winnerId;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public void setWinnerName(String winnerName) {
        this.winnerName = winnerName;
    }

    public BigDecimal getFinalPrice() {
        return finalPrice;
    }

    public void setFinalPrice(BigDecimal finalPrice) {
        this.finalPrice = finalPrice;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "AuctionClosedNotificationPayload{" +
                "auctionId='" + auctionId + '\'' +
                ", winnerName='" + winnerName + '\'' +
                ", finalPrice=" + finalPrice +
                ", closedAt=" + closedAt +
                ", status='" + status + '\'' +
                '}';
    }
}

