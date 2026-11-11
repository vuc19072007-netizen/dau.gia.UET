package model.auction;
import model.user.Bidder;

public interface AuctionObserver {
    void onNewBid(Auction auction, Bid bid);

    void onAuctionStatusChanged(Auction auction,AuctionStatus oldStatus,AuctionStatus newStatus);

    void onAuctionEnded(Auction auction, Bidder winner);
}
