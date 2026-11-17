# Đấu giá UET — Hệ thống đấu giá trực tuyến

Bài tập lớn môn Lập trình nâng cao (UET, HK II 2025–2026). Hệ thống đấu giá trực tuyến theo kiến trúc **Client–Server**, viết bằng **Java 26 + JavaFX 27**, giao diện theo **MVC** (FXML + Controller), giao tiếp qua **Java Socket** và lưu dữ liệu bằng **Java Serialization**.

## Mục lục

1. [Chức năng](#chức-năng)
2. [Cài đặt và chạy](#cài-đặt-và-chạy)
3. [Tài khoản mẫu](#tài-khoản-mẫu)
4. [Kiến trúc](#kiến-trúc)
5. [Thiết kế OOP và Design Patterns](#thiết-kế-oop-và-design-patterns)
6. [Xử lý đồng thời](#xử-lý-đồng-thời)
7. [Giao thức mạng](#giao-thức-mạng)
8. [Kiểm thử, Checkstyle và CI](#kiểm-thử-checkstyle-và-ci)
9. [Cấu trúc thư mục](#cấu-trúc-thư-mục)

## Chức năng

### Bắt buộc

| Chức năng | Mô tả |
|---|---|
| Quản lý người dùng | Đăng ký (Bidder/Seller), đăng nhập, đăng xuất. Admin xem danh sách tài khoản, khóa và mở khóa. Tài khoản bị khóa không đăng nhập, đặt giá hay tạo phiên được. |
| Quản lý sản phẩm (CRUD) | Seller thêm, sửa, xóa sản phẩm của **chính mình** (Điện tử, Nghệ thuật, Phương tiện). Không sửa/xóa được sản phẩm đang đấu giá hoặc đã bán. |
| Tham gia đấu giá | Giá đặt phải ≥ giá hiện tại + bước giá và không vượt số dư khả dụng. Tiền được giữ (lock) cho người dẫn đầu, hoàn lại ngay khi bị vượt giá. |
| Cập nhật realtime | Giá mới, lịch sử và trạng thái phiên được server đẩy tới mọi client đang mở, không cần tải lại. |
| Kết thúc phiên | Đồng hồ server tự mở phiên khi tới giờ và tự đóng khi hết giờ, xác định người thắng. Người thắng thanh toán; chặn thanh toán hai lần. Seller/Admin hủy phiên thì tiền đang giữ được hoàn. |
| Ví | Bidder nạp tiền; xem số dư khả dụng và số tiền đang giữ. |
| Xử lý lỗi | Ngoại lệ riêng `InvalidBidException`, `AuctionStateException`, `InsufficientFundsException`; mọi lỗi được gửi về client kèm lý do để hiển thị. |

### Nâng cao

| Chức năng | Mô tả |
|---|---|
| Auto-Bidding | Bidder đặt giá tối đa và bước tự động. Các cấu hình lưu trong `PriorityQueue`; hệ thống tự trả giá thay người dùng theo kiểu đấu giá ủy quyền: người có hạn mức cao hơn nhảy lên vừa vượt hạn mức đối thủ, bằng nhau thì ai đăng ký trước thắng. |
| Anti-sniping | Seller bật khi tạo phiên. Lượt đặt giá trong 30 giây cuối gia hạn phiên thêm 30 giây; đồng hồ đếm ngược trên mọi client cập nhật theo giờ mới. |
| Biểu đồ giá | Màn hình đấu giá trực tiếp có `LineChart` vẽ diễn biến giá, thêm điểm mới theo thời gian thực. |

## Cài đặt và chạy

### Yêu cầu

- **JDK 26** (ví dụ Eclipse Temurin 26).
- **Maven 3.9+**.
- JavaFX được Maven tự tải, không cần cài riêng.

Kiểm tra phiên bản:

```bash
java -version
```

```bash
mvn -version
```

### 1. Chạy server

```bash
mvn compile exec:java
```

Server mở cổng **8888**. Lần chạy đầu nạp dữ liệu mẫu; khi tắt bằng `Ctrl + C`, dữ liệu được lưu vào `auction-data.ser` và tự tải lại ở lần chạy sau. Muốn bắt đầu lại từ dữ liệu mẫu thì xóa file này.

Đổi cổng:

```bash
mvn compile exec:java -Dexec.args="9000"
```

### 2. Chạy client

Mở terminal khác (mỗi terminal là một client; mở nhiều cái để thử đấu giá realtime):

```bash
mvn javafx:run
```

Kết nối tới server ở máy khác hoặc cổng khác:

```bash
mvn javafx:run -Djavafx.args="--host=192.168.1.10 --port=9000"
```

### Chạy trong IntelliJ IDEA

- Server: chạy `server.network.ServerLauncher`.
- Client: chạy `app.Launcher` (không chạy thẳng `AuctionApp`, vì khi đó Java báo *JavaFX runtime components are missing*). Hoặc dùng cửa sổ Maven: `Plugins → javafx → javafx:run`.

## Tài khoản mẫu

| Vai trò | Số điện thoại | Mật khẩu | Ghi chú |
|---|---|---|---|
| Admin | `0900000000` | `admin123` | Không đăng ký được qua giao diện |
| Seller | `0912345678` | `password1` | Công ty Đấu giá Hà Nội |
| Seller | `0912000111` | `password1` | Phòng tranh Hội An |
| Bidder | `0987654321` | `password1` | Ví 50.000.000 đ |
| Bidder | `0977112233` | `password1` | Ví 100.000.000 đ |

Dữ liệu mẫu có 3 phiên đang chạy (một phiên ngắn 5 phút bật Anti-sniping), 1 phiên tự mở sau 2 phút và 1 sản phẩm còn trong kho.

### Kịch bản demo gợi ý

1. Chạy server, mở 2 client.
2. Client 1 đăng nhập Bidder A, client 2 đăng nhập Bidder B, cùng mở phiên "Honda Super Cub".
3. A đặt giá: B thấy giá, lịch sử và biểu đồ cập nhật ngay. B đặt thấp hơn mức tối thiểu: thấy lý do bị từ chối, giá hai bên giữ nguyên.
4. B bật đấu giá tự động với giá tối đa cao hơn; A đặt tiếp và thấy B tự trả giá lại.
5. Đặt giá trong 30 giây cuối: giờ kết thúc được gia hạn trên cả hai máy.
6. Hết giờ: người thắng thấy nút Thanh toán.
7. Đăng nhập Seller: thêm sản phẩm, tạo phiên; các client đang mở danh sách thấy phiên mới.
8. Đăng nhập Admin: khóa một Bidder, thử đăng nhập lại bằng tài khoản đó.

## Kiến trúc

```
┌──────────────── Client (JavaFX) ────────────────┐        ┌──────────────────── Server ────────────────────┐
│ View: FXML + CSS (resources/view)               │        │ AuctionServer: ServerSocket + Thread Pool       │
│ Controller: client.controller.*                 │ Socket │ ClientHandler: 1 luồng / client, kiểm tra quyền │
│ ClientSession: luồng nền gửi yêu cầu            │◄──────►│ AuctionNetworkBroadcaster: Observer → broadcast │
│ AuctionClient: gửi/nhận NetworkMessage          │ Object │ AuctionManager (Singleton): người dùng, sản     │
│ AuctionEventListener: nhận sự kiện realtime     │ Stream │   phẩm, phiên, đồng hồ đóng/mở phiên            │
└─────────────────────────────────────────────────┘        │ Model: User, Item, Auction, Bid                 │
                                                           │ DataStore: lưu/tải bằng Serialization           │
                                                           └─────────────────────────────────────────────────┘
```

- **Model** (`model.*`): luật nghiệp vụ. Chỉ server có model thật; client nhận bản sao gọn (DTO) qua mạng.
- **View** (`resources/view/*.fxml`, `styles.css`): bố cục, không chứa logic.
- **Controller** (`client.controller.*`): đọc dữ liệu từ View, gửi yêu cầu, hiển thị kết quả. `NavigationManager` nạp FXML và tiêm `ClientSession` vào controller.
- **Lưu trữ** (`persistence.DataStore`): ghi người dùng, sản phẩm và phiên vào **một** file để quan hệ giữa các đối tượng (ví dụ tiền đang giữ trong ví của người dẫn đầu) còn nguyên sau khi khởi động lại.

## Thiết kế OOP và Design Patterns

### Cây kế thừa

- `Entity` (abstract, `Serializable`): mã, thời gian tạo/cập nhật.
  - `User` (abstract) → `Bidder`, `Seller`, `Admin`.
  - `Item` (abstract, `getCategory()` trừu tượng) → `Electronics`, `Art`, `Vehicle`.
  - `Bid`.
- Interface: `Payable` (ví: `Bidder`, `Seller`), `Biddable` (giữ tiền: `Bidder`), `AuctionObserver`.

### Design Patterns

| Pattern | Ở đâu | Dùng để làm gì |
|---|---|---|
| Singleton | `service.AuctionManager` | Cả server dùng chung một nơi giữ dữ liệu. Constructor `private`, `getInstance()`. |
| Factory Method | `factory.ItemFactory` + `ArtFactory`, `ElectronicsFactory`, `VehicleFactory` | Tạo đúng lớp sản phẩm con theo `ItemType` mà nơi gọi không cần biết lớp cụ thể. |
| Observer | `AuctionObserver` (server), `AuctionEventListener` (client) | `Auction` báo giá mới/đổi trạng thái; `AuctionNetworkBroadcaster` chuyển thành tin nhắn Socket; client báo cho màn hình đang mở. |
| MVC | `client.controller`, `resources/view`, `model` | Tách giao diện, điều khiển và nghiệp vụ. |
| DTO | `common.network.payload.*DTO` | Gửi dữ liệu gọn qua mạng, không gửi mật khẩu hay khóa đồng bộ. |

## Xử lý đồng thời

- **Mỗi phiên một `ReentrantLock`** (`Auction.bidLock`): chuỗi *kiểm tra giá → giữ tiền → ghi giá mới* nằm trọn trong khóa, nên hai người đặt cùng một mức giá chỉ một lượt được nhận (chống lost update).
- **Ví dùng `synchronized`** trên từng đối tượng `Bidder`/`Seller`. Đổi tiền giữa hai ví giữ thêm một khóa chung để tránh deadlock.
- **Thông báo Observer gửi sau khi mở khóa**, để một client mạng chậm không chặn người khác đặt giá.
- **Server**: mỗi client một luồng từ `ExecutorService`; ghi ra socket có khóa riêng để phản hồi và broadcast không đan xen.
- **Client**: mọi yêu cầu chạy trên một luồng nền; kết quả và sự kiện realtime được đưa về luồng giao diện bằng `Platform.runLater()`.

Kiểm chứng bằng test: hai luồng cùng đặt một mức giá (lặp 20 lần), 30 lượt đặt song song của một người, và **50 client Socket đặt giá cùng lúc** (`ConcurrentBiddingStressTest`).

## Giao thức mạng

Mọi gói tin là một `NetworkMessage` (loại, payload, trạng thái, lỗi) gửi bằng `ObjectOutputStream`. Ba nhóm:

- **Request** (client → server): `LOGIN`, `REGISTER`, `LOGOUT`, `GET_AUCTIONS`, `GET_AUCTION_DETAIL`, `PLACE_BID`, `SET_AUTO_BID`, `CANCEL_AUTO_BID`, `PAY_AUCTION`, `CANCEL_AUCTION`, `CREATE_AUCTION`, `GET_MY_ITEMS`, `ADD_ITEM`, `UPDATE_ITEM`, `DELETE_ITEM`, `GET_PROFILE`, `DEPOSIT`, `GET_USERS`, `BAN_USER`, `UNBAN_USER`.
- **Response** (server → client gửi yêu cầu): `LOGIN_RESPONSE`, `ACTION_SUCCESS`, `ACTION_FAILED` (kèm lý do).
- **Broadcast** (server → mọi client): `NEW_BID_NOTIFICATION` (kèm giờ kết thúc mới), `AUCTION_UPDATED_NOTIFICATION`, `AUCTION_CLOSED_NOTIFICATION`.

Server luôn xác định người dùng từ phiên đăng nhập trên kết nối, không tin mã người dùng do client gửi lên.

## Kiểm thử, Checkstyle và CI

Chạy toàn bộ: biên dịch, Checkstyle, test và đo độ bao phủ:

```bash
mvn verify
```

Trên máy không có màn hình (Linux server, CI), test giao diện JavaFX cần màn hình ảo:

```bash
xvfb-run -a mvn verify
```

- **JUnit 5**: hơn 140 test gồm nghiệp vụ đấu giá, auto-bid, anti-sniping, đồng thời, giao thức mạng với server thật, lưu/tải dữ liệu và test giao diện đầu-cuối (`AuctionAppTest` bấm qua các màn hình như người dùng).
- **JaCoCo**: báo cáo tại `target/site/jacoco/index.html`; build báo lỗi nếu độ bao phủ dòng dưới **60%** (hiện khoảng 72%).
- **Checkstyle** (`checkstyle.xml`): chạy ở pha `validate`, vi phạm quy ước làm hỏng build.
- **GitHub Actions** (`.github/workflows/ci.yml`): tự chạy `mvn verify` với JDK 26 trên màn hình ảo mỗi lần push hoặc mở Pull Request vào `main`/`dev`.

## Cấu trúc thư mục

```
src/main/java
├── app/                    AuctionApp (client JavaFX), Launcher
├── client/
│   ├── ClientSession       kết nối + luồng nền cho yêu cầu
│   ├── controller/         controller của từng màn hình
│   ├── network/            AuctionClient, AuctionEventListener
│   ├── util/Formats        định dạng tiền, thời gian
│   └── view/NavigationManager
├── common/network/         NetworkMessage, MessageType, payload/ (DTO)
├── exception/              ngoại lệ nghiệp vụ dùng chung
├── factory/                ItemFactory và các factory con
├── model/                  Entity, auction/, item/, user/
├── persistence/            DataStore (Serialization)
├── server/network/         AuctionServer, ClientHandler, Broadcaster, ServerLauncher
└── service/                AuctionManager (Singleton)
src/main/resources/view     *.fxml, styles.css
src/test/java               JUnit 5
```
