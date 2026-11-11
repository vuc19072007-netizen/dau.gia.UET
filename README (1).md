# Online Auction System

Mã nguồn nghiệp vụ nằm trong `src/main/java`. Mở `pom.xml` bằng IntelliJ, chọn JDK 26 rồi chạy `mvn compile` để biên dịch.

- `model` chứa `Entity`, tài khoản, ví, sản phẩm và phiên đấu giá.
- `factory` tạo các loại sản phẩm. Giá và số dư dùng `BigDecimal`.
- `service.AuctionManager` tạo, tra cứu và quản lý thời gian của phiên.
- `model.auction.AuctionObserver` nhận sự kiện đặt giá, đổi trạng thái và kết thúc.

Giá đặt tối thiểu bằng giá hiện tại cộng bước giá, kể cả lượt đầu.
`AuctionManager` hiện giữ danh sách phiên và người dùng trong bộ nhớ.

## Giao diện trên dev

Chạy `mvn javafx:run`. Người mua mẫu: `0987654321` / `password1`.
Chọn phiên điện thoại và đặt `1100000` để thử lượt đầu.
`AuctionApp` dựng giao diện bằng Java. Hai file FXML vẫn nằm trong `src/main/resources/view`.
Chạy `mvn test` để kiểm tra.
