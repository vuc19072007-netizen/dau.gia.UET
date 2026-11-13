package common.network.payload;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Payload cho PLACE_BID_REQUEST (Client gửi yêu cầu đặt giá trong phiên).
 */
public class PlaceBidRequestPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private String auctionId;
    private String bidderId;
    private BigDecimal amount;

    public PlaceBidRequestPayload() {
    }

    public PlaceBidRequestPayload(String auctionId, String bidderId, BigDecimal amount) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public void setBidderId(String bidderId) {
        this.bidderId = bidderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "PlaceBidRequestPayload{" +
                "auctionId='" + auctionId + '\'' +
                ", bidderId='" + bidderId + '\'' +
                ", amount=" + amount +
                '}';
    }
}

