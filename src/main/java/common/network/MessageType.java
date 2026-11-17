package common.network;

/**
 * Định nghĩa danh sách các loại thông điệp (MessageType) trao đổi qua giao thức mạng
 * giữa Client và Server trong hệ thống Đấu giá Trực tuyến.
 *
 * Phân loại theo 3 nhóm nghiệp vụ:
 * 1. Nhóm Request: Client gửi yêu cầu lên Server.
 * 2. Nhóm Response: Server phản hồi kết quả riêng cho Client tương ứng.
 * 3. Nhóm Broadcast: Server phát thông báo theo thời gian thực (Realtime) cho toàn bộ Client đang kết nối.
 */
public enum MessageType {

    // ==========================================
    // 1. NHÓM REQUEST (Client gửi lên Server)
    // ==========================================

    /**
     * Yêu cầu đăng nhập.
     * Payload: LoginRequestPayload (phoneNumber, password)
     */
    LOGIN_REQUEST(Category.REQUEST),

    /**
     * Yêu cầu đăng xuất phiên làm việc hiện tại.
     * Payload: null
     */
    LOGOUT_REQUEST(Category.REQUEST),

    /**
     * Yêu cầu đăng ký tài khoản mới.
     * Payload: RegisterRequestPayload (name, phoneNumber, password, address, role)
     */
    REGISTER_REQUEST(Category.REQUEST),

    /**
     * Yêu cầu lấy danh sách các phiên đấu giá.
     * Payload: null
     */
    GET_AUCTIONS_REQUEST(Category.REQUEST),

    /**
     * Yêu cầu tạo phiên đấu giá mới (dành cho Seller/Admin).
     * Payload: CreateAuctionRequestPayload
     */
    CREATE_AUCTION_REQUEST(Category.REQUEST),

    /**
     * Yêu cầu đặt giá (Bid) vào một phiên đấu giá.
     * Payload: PlaceBidRequestPayload (auctionId, bidderId, amount)
     */
    PLACE_BID_REQUEST(Category.REQUEST),

    /**
     * Yêu cầu thanh toán phiên đấu giá đã thắng (dành cho Bidder thắng cuộc).
     * Payload: String auctionId
     */
    PAY_AUCTION_REQUEST(Category.REQUEST),

    /**
     * Yêu cầu hủy phiên đấu giá và hoàn tiền (dành cho Seller sở hữu phiên hoặc Admin).
     * Payload: String auctionId
     */
    CANCEL_AUCTION_REQUEST(Category.REQUEST),

    /**
     * Lấy chi tiết một phiên kèm lịch sử đặt giá (màn hình đấu giá trực tiếp).
     * Payload: String auctionId. Phản hồi: AuctionDTO đầy đủ.
     */
    GET_AUCTION_DETAIL_REQUEST(Category.REQUEST),

    /**
     * Lấy lại thông tin tài khoản đang đăng nhập (số dư mới nhất).
     * Payload: null. Phản hồi: UserDTO.
     */
    GET_PROFILE_REQUEST(Category.REQUEST),

    /**
     * Nạp tiền vào ví (Bidder). Payload: BigDecimal. Phản hồi: UserDTO.
     */
    DEPOSIT_REQUEST(Category.REQUEST),

    /**
     * Bật hoặc thay cấu hình đấu giá tự động (Bidder).
     * Payload: AutoBidRequestPayload.
     */
    SET_AUTO_BID_REQUEST(Category.REQUEST),

    /**
     * Tắt đấu giá tự động trong một phiên. Payload: String auctionId.
     */
    CANCEL_AUTO_BID_REQUEST(Category.REQUEST),

    /**
     * Danh sách sản phẩm của Seller đang đăng nhập. Phản hồi: List&lt;ItemDTO&gt;.
     */
    GET_MY_ITEMS_REQUEST(Category.REQUEST),

    /**
     * Thêm sản phẩm (Seller). Payload: ItemRequestPayload. Phản hồi: ItemDTO.
     */
    ADD_ITEM_REQUEST(Category.REQUEST),

    /**
     * Sửa sản phẩm (Seller, chỉ chủ sở hữu). Payload: ItemRequestPayload.
     */
    UPDATE_ITEM_REQUEST(Category.REQUEST),

    /**
     * Xóa sản phẩm (Seller, chỉ chủ sở hữu). Payload: String itemId.
     */
    DELETE_ITEM_REQUEST(Category.REQUEST),

    /**
     * Danh sách mọi tài khoản (Admin). Phản hồi: List&lt;UserDTO&gt;.
     */
    GET_USERS_REQUEST(Category.REQUEST),

    /**
     * Khóa tài khoản (Admin). Payload: String userId.
     */
    BAN_USER_REQUEST(Category.REQUEST),

    /**
     * Mở khóa tài khoản (Admin). Payload: String userId.
     */
    UNBAN_USER_REQUEST(Category.REQUEST),

    // ==========================================
    // 2. NHÓM RESPONSE (Server trả riêng cho 1 Client)
    // ==========================================

    /**
     * Phản hồi kết quả đăng nhập cho Client.
     * Status: "SUCCESS" / "FAILED"
     * Payload: Thông tin người dùng đăng nhập (User) nếu thành công
     */
    LOGIN_RESPONSE(Category.RESPONSE),

    /**
     * Phản hồi thực hiện hành động thành công (tạo phiên, đặt giá, thanh toán, hủy phiên, đăng ký,...).
     * Status: "SUCCESS"
     * Payload: Dữ liệu kết quả tương ứng (Object)
     */
    ACTION_SUCCESS(Category.RESPONSE),

    /**
     * Phản hồi thực hiện hành động thất bại.
     * Status: "FAILED"
     * ErrorMessage: Chi tiết lý do thất bại
     */
    ACTION_FAILED(Category.RESPONSE),

    // ==========================================
    // 3. NHÓM BROADCAST (Server đẩy cho toàn bộ Client)
    // ==========================================

    /**
     * Thông báo thời gian thực khi có người đặt giá mới thành công trong phiên.
     * Payload: BidNotificationPayload
     */
    NEW_BID_NOTIFICATION(Category.BROADCAST),

    /**
     * Thông báo thời gian thực khi một phiên đấu giá kết thúc hoặc bị đóng/hủy.
     * Payload: AuctionClosedNotificationPayload
     */
    AUCTION_CLOSED_NOTIFICATION(Category.BROADCAST),

    /**
     * Thông báo một phiên vừa được tạo hoặc vừa đổi trạng thái
     * (OPEN -> RUNNING -> FINISHED -> PAID/CANCELED), để danh sách phiên trên
     * mọi client cập nhật ngay. Payload: AuctionDTO.
     */
    AUCTION_UPDATED_NOTIFICATION(Category.BROADCAST);

    private enum Category {
        REQUEST,
        RESPONSE,
        BROADCAST
    }

    private final Category category;

    MessageType(Category category) {
        this.category = category;
    }

    /**
     * Kiểm tra xem thông điệp có thuộc nhóm Request hay không.
     */
    public boolean isRequest() {
        return this.category == Category.REQUEST;
    }

    /**
     * Kiểm tra xem thông điệp có thuộc nhóm Response hay không.
     */
    public boolean isResponse() {
        return this.category == Category.RESPONSE;
    }

    /**
     * Kiểm tra xem thông điệp có thuộc nhóm Broadcast hay không.
     */
    public boolean isBroadcast() {
        return this.category == Category.BROADCAST;
    }
}