package app;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import model.auction.AuctionObserver;
import model.auction.Bid;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import model.auction.Auction;
import model.auction.AuctionStatus;
import model.item.Electronics;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import service.AuctionManager;

public class AuctionApp extends Application {
    private final AuctionManager manager =
            AuctionManager.getInstance();

    private Stage stage;
    // Mỗi màn hình danh sách có một observer; logout phải gỡ nó khỏi các phiên.
    private AuctionObserver screenObserver;
    private final List<Auction> observedAuctions = new ArrayList<>();
    // Mã phiên bản màn hình, chỉ đọc/ghi trên JavaFX Application Thread.
    // Callback đã nằm trong hàng đợi sẽ bị bỏ qua nếu người dùng đổi màn hình.
    private long screenVersion;

    // Tránh một lần đăng nhập mới lại nhận thêm một bản thông báo trùng.
    private void detachObservers() {
        screenVersion++;
        if (screenObserver != null) {
            for (Auction auction : observedAuctions) auction.removeObserver(screenObserver);
        }
        observedAuctions.clear();
        screenObserver = null;
    }

    @Override
    public void stop() {
        detachObservers();
        manager.stopClock();
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        // Dữ liệu mẫu để chạy thử giao diện.
        seedData();
        // Đồng hồ chạy nền độc lập với nút "Cập nhật danh sách".
        manager.startClock();

        stage.setTitle("Hệ thống đấu giá");
        showLogin();
        stage.show();
    }

    private void seedData() {
        // Đây là dữ liệu giả lập để học giao diện, không đọc từ cơ sở dữ liệu.
        if (!manager.getAllAuction().isEmpty()) return;
        Seller seller = new Seller(
                "Người bán mẫu",
                "0912345678",
                "password1",
                "Hà Nội");

        Bidder bidder = new Bidder(
                "Người mua mẫu",
                "0987654321",
                "password1",
                "Hà Nội");

        bidder.deposit(new BigDecimal("10000000"));

        manager.addUser(seller);
        manager.addUser(bidder);

        Electronics item = new Electronics(
                "Điện thoại mẫu",
                new BigDecimal("1000000"));

        Auction auction = manager.createAuction(
                item,
                seller,
                new BigDecimal("100000"),
                LocalDateTime.now().minusSeconds(1),
                LocalDateTime.now().plusMinutes(5));

        auction.startAuction();
    }

    private void showLogin() {
        detachObservers();
        TextField phoneField = new TextField();
        phoneField.setPromptText("Số điện thoại");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Mật khẩu");

        Label message = new Label();
        Button loginButton = new Button("Đăng nhập");

        loginButton.setOnAction(event -> {
            try {
                User user = manager.login(
                        phoneField.getText(),
                        passwordField.getText());

                showAuctionList(user);

            } catch (IllegalArgumentException
                     | IllegalStateException exception) {
                message.setText(exception.getMessage());
            }
        });

        VBox root = new VBox(
                12,
                new Label("ĐĂNG NHẬP"),
                phoneField,
                passwordField,
                loginButton,
                message);

        root.setPadding(new Insets(24));
        stage.setScene(new Scene(root, 420, 300));
    }

    private void showAuctionList(User user) {
        detachObservers();
        // Giữ mã của màn hình này cho các callback bất đồng bộ.
        long version = screenVersion;
        ListView<Auction> listView = new ListView<>();

        // Quy định cách hiển thị mỗi phiên trong danh sách.
        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Auction auction, boolean empty) {
                super.updateItem(auction, empty);

                if (empty || auction == null) {
                    setText(null);
                } else {
                    setText(
                            auction.getItem().getName()
                            + " | Giá: "
                            + auction.getCurrentPrice().toPlainString()
                            + " | "
                            + auction.getStatus());
                }
            }
        });

        listView.getItems().setAll(manager.getAllAuction());

        TextField amountField = new TextField();
        amountField.setPromptText("Nhập số tiền đặt giá");

        Label message = new Label();
        // Lớp vô danh cài đặt AuctionObserver: nơi giao diện thật sự nhận sự kiện.
        screenObserver = new AuctionObserver() {
            private void refresh(String text) {
                // Callback có thể đến từ đồng hồ hoặc luồng đặt giá.
                // Chỉ JavaFX Application Thread được thay đổi các control.
                Platform.runLater(() -> {
                    // Đã logout/đổi màn hình thì bỏ thông báo cũ đang chờ.
                    if (version != screenVersion) return;
                    listView.refresh();
                    message.setText(text);
                });
            }
            @Override public void onNewBid(Auction auction, Bid bid) {
                refresh("Có giá mới: " + auction.getItem().getName());
            }
            @Override public void onAuctionStatusChanged(Auction auction,
                    AuctionStatus oldStatus, AuctionStatus newStatus) {
                refresh("Trạng thái phiên: " + newStatus);
            }
            @Override public void onAuctionEnded(Auction auction, Bidder winner) {
                refresh(winner == null ? "Phiên kết thúc không có người thắng"
                        : "Phiên đã kết thúc. Người thắng: " + winner.getName());
            }
        };
        // Runnable là hành động không nhận tham số, không trả về giá trị.
        // Tải lại danh sách cần gỡ đăng ký cũ rồi đăng ký trên danh sách mới.
        Runnable reload = () -> {
            for (Auction auction : observedAuctions) auction.removeObserver(screenObserver);
            observedAuctions.clear();
            observedAuctions.addAll(manager.getAllAuction());
            for (Auction auction : observedAuctions) auction.addObserver(screenObserver);
            listView.getItems().setAll(observedAuctions);
        };
        reload.run();

        Button bidButton = new Button("Đặt giá");

        // Chỉ người đấu giá mới sử dụng nút này.
        bidButton.setDisable(!(user instanceof Bidder));

        bidButton.setOnAction(event -> {
            Auction selected =
                    listView.getSelectionModel().getSelectedItem();

            if (selected == null) {
                message.setText("Hãy chọn một phiên");
                return;
            }

            try {
                BigDecimal amount =
                        new BigDecimal(amountField.getText().trim());

                selected.placeBid((Bidder) user, amount);

                listView.refresh();
                message.setText("Đặt giá thành công");

            } catch (NumberFormatException exception) {
                message.setText("Số tiền không hợp lệ");

            } catch (IllegalArgumentException
                     | IllegalStateException exception) {
                message.setText(exception.getMessage());
            }
        });

        Button refreshButton = new Button("Cập nhật danh sách");

        // Nút này chỉ tải lại dữ liệu; đồng hồ đã tự chuyển trạng thái theo giờ.
        refreshButton.setOnAction(event -> {
            reload.run();
            message.setText("Đã tải lại danh sách");
        });

        Button payButton = new Button("Thanh toán phiên đã chọn");
        payButton.setDisable(!(user instanceof Bidder));

        payButton.setOnAction(event -> {
            Auction selected =
                    listView.getSelectionModel().getSelectedItem();

            if (selected == null) {
                message.setText("Hãy chọn một phiên");
                return;
            }

            try {
                selected.payAuction((Bidder) user);
                listView.refresh();
                message.setText("Thanh toán thành công");

            } catch (IllegalArgumentException
                     | IllegalStateException exception) {
                message.setText(exception.getMessage());
            }
        });

        Button cancelButton = new Button("Hủy phiên đã chọn");
        cancelButton.setDisable(!(user instanceof Seller) && !(user instanceof model.user.Admin));
        cancelButton.setOnAction(event -> {
            Auction selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) { message.setText("Hãy chọn một phiên"); return; }
            try {
                selected.cancelAuction(user);
                listView.refresh();
                message.setText("Đã hủy phiên và hoàn tiền");
            } catch (IllegalArgumentException | IllegalStateException error) {
                message.setText(error.getMessage());
            }
        });

        Button logoutButton = new Button("Đăng xuất");
        logoutButton.setOnAction(event -> showLogin());

        VBox root = new VBox(
                10,
                new Label("Xin chào " + user.getName()),
                listView,
                amountField,
                bidButton,
                payButton,
                refreshButton,
                cancelButton,
                logoutButton,
                message);

        root.setPadding(new Insets(20));
        stage.setScene(new Scene(root, 760, 600));
    }

    public static void main(String[] args) {
        launch(args);
    }
}