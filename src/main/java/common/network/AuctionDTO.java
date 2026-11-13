package common.network.payload;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import model.auction.Auction;
import model.auction.AuctionStatus;
import model.auction.Bid;

/**
 * Data Transfer Object (DTO) biểu diễn một phiên đấu giá để truyền tải an toàn
 * qua mạng mà không phụ thuộc vào các trường concurrency (ReentrantLock) hay danh sách Observer.
 */
public class AuctionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String auctionId;
    private String itemId;
    private String itemName;
    private String itemCategory;
    private String itemDescription;
    private BigDecimal startingPrice;
    private BigDecimal currentPrice;
    private BigDecimal minimumIncrement;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;
    private String sellerId;
    private String sellerName;
    private String highestBidderId;
    private String highestBidderName;
    private int bidCount;

    public AuctionDTO() {
    }

    public static AuctionDTO fromAuction(Auction auction) {
        if (auction == null) return null;

        AuctionDTO dto = new AuctionDTO();
        dto.setAuctionId(auction.getAuctionId());
        if (auction.getItem() != null) {
            dto.setItemId(auction.getItem().getId());
            dto.setItemName(auction.getItem().getName());
            dto.setItemCategory(auction.getItem().getCategory());
            dto.setItemDescription(auction.getItem().getDescription());
            dto.setStartingPrice(auction.getItem().getStartingPrice());
        }
        dto.setCurrentPrice(auction.getCurrentPrice());
        dto.setMinimumIncrement(auction.getMinimumIncrement());
        dto.setStartTime(auction.getStartTime());
        dto.setEndTime(auction.getEndTime());
        dto.setStatus(auction.getStatus());

        if (auction.getSeller() != null) {
            dto.setSellerId(auction.getSeller().getId());
            dto.setSellerName(auction.getSeller().getName());
        }

        Bid highestBid = auction.getHighestBid();
        if (highestBid != null && highestBid.getBidder() != null) {
            dto.setHighestBidderId(highestBid.getBidder().getId());
            dto.setHighestBidderName(highestBid.getBidder().getName());
        }

        if (auction.getBidHistory() != null) {
            dto.setBidCount(auction.getBidHistory().size());
        }

        return dto;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
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

    public String getItemDescription() {
        return itemDescription;
    }

    public void setItemDescription(String itemDescription) {
        this.itemDescription = itemDescription;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
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

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public String getHighestBidderId() {
        return highestBidderId;
    }

    public void setHighestBidderId(String highestBidderId) {
        this.highestBidderId = highestBidderId;
    }

    public String getHighestBidderName() {
        return highestBidderName;
    }

    public void setHighestBidderName(String highestBidderName) {
        this.highestBidderName = highestBidderName;
    }

    public int getBidCount() {
        return bidCount;
    }

    public void setBidCount(int bidCount) {
        this.bidCount = bidCount;
    }

    @Override
    public String toString() {
        return "AuctionDTO{" +
                "auctionId='" + auctionId + '\'' +
                ", itemName='" + itemName + '\'' +
                ", currentPrice=" + currentPrice +
                ", status=" + status +
                '}';
    }
}

