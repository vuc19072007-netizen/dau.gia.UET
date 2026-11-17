package view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import app.AuctionApp;
import client.controller.AdminController;
import client.controller.AuctionListController;
import client.controller.LiveAuctionController;
import client.controller.LoginController;
import client.controller.SellerDashboardController;
import client.network.AuctionClient;
import client.view.NavigationManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.auction.Auction;
import model.item.ItemType;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.network.AuctionServer;
import service.AuctionManager;

/**
 * Test giao diện JavaFX từ đầu tới cuối: server Socket thật chạy trong cùng tiến
 * trình, client JavaFX nối tới nó, test điền form và bấm nút như người dùng.
 * Trên máy không có màn hình (CI) cần chạy dưới xvfb-run.
 */
class AuctionAppTest {
    private static final String PASSWORD = "password1";
    private static final long TIMEOUT_MS = 8000;

    private AuctionServer server;
    private Seller seller;
    private Bidder bidder;
    private Bidder rival;
    private Auction auction;
    private AuctionApp app;
    private Stage stage;

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                ready.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach
    void setUp() throws Exception {
        AuctionManager manager = AuctionManager.getInstance();
        manager.reset();
        seller = new Seller("Người bán", "0914000001", PASSWORD, "HN");
        bidder = new Bidder("Người mua", "0914000002", PASSWORD, "HN");
        rival = new Bidder("Đối thủ", "0914000003", PASSWORD, "HN");
        bidder.deposit(new BigDecimal("10000000"));
        rival.deposit(new BigDecimal("10000000"));
        manager.addUser(seller);
        manager.addUser(bidder);
        manager.addUser(rival);
        manager.addUser(new Admin("Quản trị", "0914000004", PASSWORD, "HN"));

        var item = manager.addItem(seller, ItemType.ELECTRONICS, "Điện thoại mẫu", new BigDecimal("1000000"));
        auction = manager.createAuction(item, seller, new BigDecimal("100000"),
                LocalDateTime.now().minusSeconds(1), LocalDateTime.now().plusMinutes(10));
        auction.startAuction();

        server = new AuctionServer(0);
        server.start();
        System.setProperty("auction.port", String.valueOf(server.getPort()));

        onFx(() -> {
            app = new AuctionApp();
            stage = new Stage();
            app.start(stage);
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        onFx(() -> {
            app.stop();
            stage.hide();
        });
        server.stop();
        AuctionManager.getInstance().reset();
        System.clearProperty("auction.port");
    }

    // ===================== Tiện ích =====================

    // Chạy đoạn code trên luồng JavaFX và chờ xong.
    private static void onFx(ThrowingRunnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            action.run();
            return null;
        });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    private static <T> T onFxGet(Supplier<T> supplier) throws Exception {
        FutureTask<T> task = new FutureTask<>(supplier::get);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    // Chờ tới khi điều kiện (kiểm tra trên luồng JavaFX) đúng, quá giờ thì fail.
    private static void waitFor(String what, Supplier<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (onFxGet(condition)) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Hết thời gian chờ: " + what);
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T find(String id) {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        Node node = stage.getScene().lookup("#" + id);
        if (node == null) {
            throw new AssertionError("Không thấy control #" + id);
        }
        return (T) node;
    }

    private NavigationManager navigation() {
        return app.navigation();
    }

    private void login(String phone, String password) throws Exception {
        onFx(() -> {
            ((TextField) find("phoneField")).setText(phone);
            ((TextField) find("passwordField")).setText(password);
            ((Button) find("loginButton")).fire();
        });
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    // ===================== Test =====================

    @Test
    void wrongPasswordShowsServerReason() throws Exception {
        login(bidder.getPhoneNumber(), "saimatkhau1");
        waitFor("thông báo lỗi", () -> !((Label) find("messageLabel")).getText().startsWith("Đang")
                && !((Label) find("messageLabel")).getText().isBlank());
        assertTrue(onFxGet(() -> navigation().currentController() instanceof LoginController));
    }

    @Test
    void bidderLogsInBidsAndSeesRivalBidLive() throws Exception {
        login(bidder.getPhoneNumber(), PASSWORD);
        waitFor("màn hình danh sách", () -> navigation().currentController() instanceof AuctionListController);

        TableView<?> table = onFxGet(() -> find("auctionTable"));
        waitFor("bảng có phiên", () -> table.getItems().size() == 1);

        onFx(() -> {
            table.getSelectionModel().selectFirst();
            ((Button) find("openButton")).fire();
        });
        waitFor("màn hình đấu giá", () -> navigation().currentController() instanceof LiveAuctionController);
        waitFor("giá hiện tại", () -> ((Label) find("priceLabel")).getText().contains("1.000.000"));

        // Đặt giá 1.100.000 qua giao diện.
        onFx(() -> {
            ((TextField) find("amountField")).setText("1100000");
            ((Button) find("bidButton")).fire();
        });
        waitFor("server nhận giá", () -> auction.getCurrentPrice().compareTo(new BigDecimal("1100000")) == 0);
        assertEquals(bidder.getId(), auction.getHighestBid().getBidder().getId());

        // Đối thủ đặt giá từ một client khác: màn hình phải tự cập nhật.
        AuctionClient other = new AuctionClient("localhost", server.getPort());
        other.connect();
        try {
            assertTrue(other.login(rival.getPhoneNumber(), PASSWORD));
            assertTrue(other.placeBid(auction.getAuctionId(), rival.getId(), new BigDecimal("1300000")).isSuccess());
        } finally {
            other.disconnect();
        }
        waitFor("giá mới hiện realtime", () -> ((Label) find("priceLabel")).getText().contains("1.300.000"));
        TableView<?> history = onFxGet(() -> find("historyTable"));
        waitFor("lịch sử 2 lượt", () -> history.getItems().size() == 2);
        assertTrue(onFxGet(() -> ((Label) find("leaderLabel")).getText().contains(rival.getName())));

        // Đặt giá thấp hơn mức tối thiểu: server từ chối, hiện lý do.
        onFx(() -> {
            ((TextField) find("amountField")).setText("1300000");
            ((Button) find("bidButton")).fire();
        });
        waitFor("lý do bị từ chối", () -> ((Label) find("messageLabel")).getText().contains("tối thiểu"));
        assertEquals(0, auction.getCurrentPrice().compareTo(new BigDecimal("1300000")));
    }

    @Test
    void sellerAddsItemAndCreatesAuction() throws Exception {
        login(seller.getPhoneNumber(), PASSWORD);
        waitFor("màn hình danh sách", () -> navigation().currentController() instanceof AuctionListController);
        assertTrue(onFxGet(() -> find("sellerButton").isVisible()));
        assertFalse(onFxGet(() -> find("depositButton").isVisible()));

        onFx(() -> ((Button) find("sellerButton")).fire());
        waitFor("màn hình Seller", () -> navigation().currentController() instanceof SellerDashboardController);
        TableView<?> items = onFxGet(() -> find("itemTable"));
        waitFor("kho có 1 sản phẩm", () -> items.getItems().size() == 1);

        onFx(() -> {
            @SuppressWarnings("unchecked")
            ComboBox<String> category = (ComboBox<String>) find("categoryBox");
            category.setValue("ART");
            ((TextField) find("nameField")).setText("Tranh test");
            ((TextField) find("priceField")).setText("500000");
            ((TextArea) find("descriptionArea")).setText("Tranh sơn mài");
            ((Button) find("saveButton")).fire();
        });
        waitFor("sản phẩm mới", () -> items.getItems().size() == 2);
        assertEquals(2, AuctionManager.getInstance().getItemsOf(seller.getId()).size());

        // Chọn sản phẩm mới rồi tạo phiên.
        onFx(() -> {
            for (int i = 0; i < items.getItems().size(); i++) {
                if (items.getItems().get(i).toString().equals("Tranh test")) {
                    items.getSelectionModel().select(i);
                }
            }
            ((TextField) find("incrementField")).setText("50000");
            ((TextField) find("durationField")).setText("5");
            ((Button) find("createAuctionButton")).fire();
        });
        waitFor("phiên mới trên server", () -> AuctionManager.getInstance().getAllAuction().size() == 2);
        TableView<?> mine = onFxGet(() -> find("auctionTable"));
        waitFor("bảng phiên của tôi", () -> mine.getItems().size() == 2);
    }

    @Test
    void adminSeesAllUsers() throws Exception {
        login("0914000004", PASSWORD);
        waitFor("màn hình danh sách", () -> navigation().currentController() instanceof AuctionListController);
        onFx(() -> ((Button) find("adminButton")).fire());
        waitFor("màn hình quản trị", () -> navigation().currentController() instanceof AdminController);
        TableView<?> users = onFxGet(() -> find("userTable"));
        waitFor("4 tài khoản", () -> users.getItems().size() == 4);
    }

    @Test
    void logoutReturnsToLogin() throws Exception {
        login(bidder.getPhoneNumber(), PASSWORD);
        waitFor("màn hình danh sách", () -> navigation().currentController() instanceof AuctionListController);
        onFx(() -> stage.getScene().getRoot().lookupAll(".button").stream()
                .map(node -> (Button) node)
                .filter(button -> "Đăng xuất".equals(button.getText()))
                .findFirst().orElseThrow().fire());
        waitFor("quay về đăng nhập", () -> navigation().currentController() instanceof LoginController);
    }
}
