package client.network;

import common.network.payload.AuctionClosedNotificationPayload;
import common.network.payload.BidNotificationPayload;

/**
 * Interface lắng nghe các sự kiện phát sóng theo thời gian thực (Realtime Broadcast)
 * từ Server tới Client qua kết nối Socket.
 */
public interface AuctionEventListener {

    /**
     * Kích hoạt khi có người đặt giá mới trong bất kỳ phiên đấu giá nào.
     */
    void onNewBid(BidNotificationPayload bidNotification);

    /**
     * Kích hoạt khi một phiên đấu giá kết thúc hoặc bị đóng/hủy.
     */
    void onAuctionClosed(AuctionClosedNotificationPayload closedNotification);
}

