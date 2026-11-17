package common.network.payload;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Yêu cầu bật đấu giá tự động: tự trả giá thêm increment mỗi lần bị vượt,
 * cho tới khi chạm maxBid.
 */
public class AutoBidRequestPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String auctionId;
    private final BigDecimal maxBid;
    private final BigDecimal increment;

    public AutoBidRequestPayload(String auctionId, BigDecimal maxBid, BigDecimal increment) {
        this.auctionId = auctionId;
        this.maxBid = maxBid;
        this.increment = increment;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public BigDecimal getMaxBid() {
        return maxBid;
    }

    public BigDecimal getIncrement() {
        return increment;
    }
}
