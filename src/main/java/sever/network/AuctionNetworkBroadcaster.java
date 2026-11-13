package server.network;

import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

import common.network.MessageType;
import common.network.NetworkMessage;
import common.network.payload.AuctionClosedNotificationPayload;
import common.network.payload.BidNotificationPayload;
import model.auction.Auction;
import model.auction.AuctionObserver;
import model.auction.AuctionStatus;
import model.auction.Bid;
import model.user.Bidder;

/**
 * Cầu nối giữa Observer Pattern của tầng Model và giao thức mạng Realtime (Socket Server).
 * Khi phiên đấu giá có sự kiện (đặt giá mới, đổi trạng thái, kết thúc), Broadcaster sẽ
 * đóng gói dữ liệu thành NetworkMessage tương ứng và yêu cầu AuctionServer phát sóng (Broadcast)
 * tới toàn bộ Client đang kết nối.
 */
public class AuctionNetworkBroadcaster implements AuctionObserver {
    private static final Logger LOGGER = Logger.getLogger(AuctionNetworkBroadcaster.class.getName());

    private final AuctionServer server;

    public AuctionNetworkBroadcaster(AuctionServer server) {
        if (server == null) {
            throw new IllegalArgumentException("AuctionServer không được null");
        }
        this.server = server;
    }

    /**
     * Nhận sự kiện có lượt đặt giá mới thành công từ phiên đấu giá.
     * Tạo thông điệp NEW_BID_NOTIFICATION và phát sóng cho toàn bộ Client.
     */
    @Override
    public void onNewBid(Auction auction, Bid bid) {
        if (auction == null || bid == null) return;

        try {
            BidNotificationPayload payload = new BidNotificationPayload(
                    auction.getAuctionId(),
                    bid.getId(),
                    bid.getBidder() != null ? bid.getBidder().getId() : null,
                    bid.getBidder() != null ? bid.getBidder().getName() : "Ẩn danh",
                    bid.getAmount(),
                    auction.getCurrentPrice(),
                    bid.getCreatedAt() != null ? bid.getCreatedAt() : LocalDateTime.now()
            );

            NetworkMessage message = NetworkMessage.createBroadcast(
                    MessageType.NEW_BID_NOTIFICATION,
                    payload
            );

            LOGGER.info(() -> String.format(
                    "[REALTIME BROADCAST] New bid on auction %s: %s by %s",
                    auction.getAuctionId(),
                    bid.getAmount(),
                    payload.getBidderName()
            ));

            server.broadcast(message);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi broadcast sự kiện đặt giá mới", e);
        }
    }

    /**
     * Nhận sự kiện thay đổi trạng thái phiên đấu giá (OPEN -> RUNNING -> FINISHED/CANCELED).
     */
    @Override
    public void onAuctionStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {
        if (auction == null) return;

        LOGGER.info(() -> String.format(
                "[AUCTION STATUS CHANGED] Auction %s: %s -> %s",
                auction.getAuctionId(),
                oldStatus,
                newStatus
        ));

        // Nếu phiên kết thúc hoặc bị hủy, thông báo đóng phiên tới toàn bộ Client
        if (newStatus == AuctionStatus.CANCELED) {
            broadcastAuctionClosed(auction, newStatus.name());
        }
    }

    /**
     * Nhận sự kiện phiên đấu giá kết thúc thành công với người thắng cuộc.
     */
    @Override
    public void onAuctionEnded(Auction auction, Bidder winner) {
        if (auction == null) return;

        LOGGER.info(() -> String.format(
                "[AUCTION ENDED] Auction %s ended. Winner: %s",
                auction.getAuctionId(),
                winner != null ? winner.getName() : "Không có người thắng"
        ));

        broadcastAuctionClosed(auction, AuctionStatus.FINISHED.name());
    }

    private void broadcastAuctionClosed(Auction auction, String statusName) {
        try {
            Bid highestBid = auction.getHighestBid();
            String winnerId = (highestBid != null && highestBid.getBidder() != null)
                    ? highestBid.getBidder().getId()
                    : null;
            String winnerName = (highestBid != null && highestBid.getBidder() != null)
                    ? highestBid.getBidder().getName()
                    : "Không có người thắng";

            AuctionClosedNotificationPayload payload = new AuctionClosedNotificationPayload(
                    auction.getAuctionId(),
                    winnerId,
                    winnerName,
                    auction.getCurrentPrice(),
                    LocalDateTime.now(),
                    statusName
            );

            NetworkMessage message = NetworkMessage.createBroadcast(
                    MessageType.AUCTION_CLOSED_NOTIFICATION,
                    payload
            );

            LOGGER.info(() -> String.format(
                    "[REALTIME BROADCAST] Auction closed: %s, Final price: %s, Winner: %s",
                    auction.getAuctionId(),
                    payload.getFinalPrice(),
                    winnerName
            ));

            server.broadcast(message);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi broadcast sự kiện đóng phiên", e);
        }
    }
}
