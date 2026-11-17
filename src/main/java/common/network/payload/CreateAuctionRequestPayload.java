package common.network.payload;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload cho CREATE_AUCTION_REQUEST (Client gửi lên Server để tạo phiên đấu giá mới).
 */
public class CreateAuctionRequestPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private String itemName;
    private String itemCategory; // "ART", "ELECTRONICS", "VEHICLE"
    private String description;
    private BigDecimal startingPrice;
    private BigDecimal minimumIncrement;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String sellerId;
    // Tạo phiên cho sản phẩm có sẵn trong kho của Seller (đã thêm bằng ADD_ITEM).
    // null thì server tạo sản phẩm mới từ itemName/itemCategory/startingPrice.
    private String itemId;
    // Bật chống bid phút chót cho phiên này.
    private boolean antiSnipingEnabled;

    public CreateAuctionRequestPayload() {
    }

    public CreateAuctionRequestPayload(
            String itemName,
            String itemCategory,
            String description,
            BigDecimal startingPrice,
            BigDecimal minimumIncrement,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String sellerId) {
        this.itemName = itemName;
        this.itemCategory = itemCategory;
        this.description = description;
        this.startingPrice = startingPrice;
        this.minimumIncrement = minimumIncrement;
        this.startTime = startTime;
        this.endTime = endTime;
        this.sellerId = sellerId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public boolean isAntiSnipingEnabled() {
        return antiSnipingEnabled;
    }

    public void setAntiSnipingEnabled(boolean antiSnipingEnabled) {
        this.antiSnipingEnabled = antiSnipingEnabled;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getItemCategory() {
        return itemCategory;
    }

    public void setItemCategory(String itemCategory) {
        this.itemCategory = itemCategory;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }

    public BigDecimal getMinimumIncrement() {
        return minimumIncrement;
    }

    public void setMinimumIncrement(BigDecimal minimumIncrement) {
        this.minimumIncrement = minimumIncrement;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    @Override
    public String toString() {
        return "CreateAuctionRequestPayload{" +
                "itemName='" + itemName + '\'' +
                ", itemCategory='" + itemCategory + '\'' +
                ", startingPrice=" + startingPrice +
                ", minimumIncrement=" + minimumIncrement +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", sellerId='" + sellerId + '\'' +
                '}';
    }
}

