package client.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import client.network.AuctionEventListener;
import client.util.Formats;
import client.view.NavigationManager;
import common.network.payload.AuctionClosedNotificationPayload;
import common.network.payload.AuctionDTO;
import common.network.payload.BidDTO;
import common.network.payload.BidNotificationPayload;
import common.network.payload.UserDTO;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import model.auction.AuctionStatus;

/**
 * Màn hình đấu giá trực tiếp của một phiên.
 * <p>
 * - Mở màn hình: xin chi tiết phiên (kèm lịch sử) từ server.
 * - Người khác đặt giá: server phát NEW_BID_NOTIFICATION, màn hình cập nhật giá,
 *   lịch sử và biểu đồ ngay, không cần tải lại.
 * - Đồng hồ đếm ngược chạy bằng Timeline mỗi giây trên luồng giao diện.
 * - Hết giờ hoặc phiên đóng: khóa nút đặt giá, hiện kết quả, người thắng thấy
 *   nút Thanh toán.
 */
public class LiveAuctionController extends BaseController implements AuctionEventListener {
    @FXML
    private Label titleLabel;
    @FXML
    private Label balanceLabel;
    @FXML
    private Label categoryLabel;
    @FXML
    private Label detailsLabel;
    @FXML
    private Label descriptionLabel;
    @FXML
    private Label sellerLabel;
    @FXML
    private Label timeLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label countdownLabel;
    @FXML
    private Label priceLabel;
    @FXML
    private Label leaderLabel;
    @FXML
    private Label minimumLabel;
    @FXML
    private Label antiSnipingLabel;
    @FXML
    private VBox bidBox;
    @FXML
    private TextField amountField;
    @FXML
    private Button bidButton;
    @FXML
    private TextField autoMaxField;
    @FXML
    private TextField autoStepField;
    @FXML
    private Button autoOnButton;
    @FXML
    private Button autoOffButton;
    @FXML
    private Label autoStatusLabel;
    @FXML
    private Button payButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Label messageLabel;
    @FXML
    private LineChart<String, Number> priceChart;
    @FXML
    private TableView<BidDTO> historyTable;
    @FXML
    private TableColumn<BidDTO, String> historyTimeColumn;
    @FXML
    private TableColumn<BidDTO, String> historyBidderColumn;
    @FXML
    private TableColumn<BidDTO, String> historyAmountColumn;

    private String auctionId;
    private AuctionDTO auction;
    private final ObservableList<BidDTO> history = FXCollections.observableArrayList();
    private final XYChart.Series<String, Number> series = new XYChart.Series<>();
    private Timeline clock;

    @FXML
    private void initialize() {
        AuctionListController.bind(historyTimeColumn, bid -> Formats.time(bid.getTime()));
        AuctionListController.bind(historyBidderColumn, BidDTO::getBidderName);
        AuctionListController.bind(historyAmountColumn, bid -> Formats.money(bid.getAmount()));
        historyTable.setItems(history);
        priceChart.getData().add(series);
    }

    @Override
    public void onShow(Object parameter) {
        auctionId = (String) parameter;
        client().addEventListener(this);

        // Đồng hồ đếm ngược: mỗi giây vẽ lại thời gian còn lại và trạng thái nút.
        clock = new Timeline(new KeyFrame(Duration.seconds(1), event -> tick()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        loadDetail();
        refreshProfile();
    }

    @Override
    public void onHide() {
        if (clock != null) {
            clock.stop();
        }
        if (client() != null) {
            client().removeEventListener(this);
        }
    }

    private void loadDetail() {
        request(() -> client().getAuctionDetail(auctionId), payload -> render((AuctionDTO) payload), messageLabel);
    }

    private void refreshProfile() {
        UserDTO user = user();
        if (user == null || user.getBalance() == null) {
            balanceLabel.setVisible(false);
            return;
        }
        request(() -> client().refreshProfile(), payload -> {
            UserDTO fresh = (UserDTO) payload;
            balanceLabel.setText("Khả dụng: " + Formats.money(fresh.getAvailableBalance())
                    + " · Đang giữ: " + Formats.money(fresh.getLockedBalance()));
        }, messageLabel);
    }

    // Đổ toàn bộ dữ liệu phiên lên giao diện.
    private void render(AuctionDTO detail) {
        this.auction = detail;
        titleLabel.setText(detail.getItemName());
        categoryLabel.setText(detail.getItemCategory());
        detailsLabel.setText(detail.getItemDetails() == null ? "" : detail.getItemDetails());
        descriptionLabel.setText(detail.getItemDescription() == null ? "" : detail.getItemDescription());
        sellerLabel.setText("Người bán: " + detail.getSellerName());
        timeLabel.setText("Mở: " + Formats.dateTime(detail.getStartTime())
                + "  ·  Đóng: " + Formats.dateTime(detail.getEndTime()));
        antiSnipingLabel.setText(detail.isAntiSnipingEnabled()
                ? "Chống bid phút chót: đặt giá trong 30 giây cuối sẽ gia hạn thêm 30 giây."
                : "");

        history.setAll(detail.getBidHistory());
        FXCollections.reverse(history);
        rebuildChart();

        BigDecimal autoMax = detail.getMyAutoBidMax();
        autoStatusLabel.setText(autoMax == null ? "Đang tắt" : "Đang bật, tối đa " + Formats.money(autoMax));
        if (autoStepField.getText() == null || autoStepField.getText().isBlank()) {
            autoStepField.setText(detail.getMinimumIncrement().toPlainString());
        }

        renderPrice();
        updateControls();
    }

    private void renderPrice() {
        priceLabel.setText(Formats.money(auction.getCurrentPrice()));
        leaderLabel.setText(auction.getHighestBidderName() == null
                ? "Chưa có ai đặt giá"
                : "Dẫn đầu: " + auction.getHighestBidderName() + (isMeLeading() ? " (bạn)" : ""));
        minimumLabel.setText("Bước giá " + Formats.money(auction.getMinimumIncrement())
                + " · Giá tối thiểu tiếp theo " + Formats.money(auction.getMinimumNextBid()));
        amountField.setText(auction.getMinimumNextBid().toPlainString());
    }

    // Biểu đồ: điểm đầu là giá khởi điểm, mỗi lượt đặt thêm một điểm.
    private void rebuildChart() {
        series.getData().clear();
        series.getData().add(new XYChart.Data<>("Khởi điểm", auction.getStartingPrice()));
        for (int i = history.size() - 1; i >= 0; i--) {
            addChartPoint(history.get(i));
        }
    }

    private void addChartPoint(BidDTO bid) {
        // Thêm số thứ tự để hai lượt cùng một giây vẫn là hai điểm riêng.
        String label = Formats.time(bid.getTime()) + " #" + series.getData().size();
        series.getData().add(new XYChart.Data<>(label, bid.getAmount()));
    }

    private boolean isMeLeading() {
        UserDTO user = user();
        return user != null && auction != null && user.getId().equals(auction.getHighestBidderId());
    }

    private boolean timeLeft() {
        return auction.getEndTime() != null && LocalDateTime.now().isBefore(auction.getEndTime());
    }

    private void tick() {
        if (auction == null) {
            return;
        }
        countdownLabel.setText(auction.getStatus() == AuctionStatus.OPEN
                ? "Mở sau " + Formats.countdown(auction.getStartTime())
                : Formats.countdown(auction.getEndTime()));
        updateControls();
    }

    // Bật/tắt nút theo vai trò, trạng thái phiên và thời gian còn lại.
    private void updateControls() {
        UserDTO user = user();
        AuctionStatus status = auction.getStatus();
        statusLabel.setText(Formats.status(status));

        boolean bidder = user != null && user.isBidder();
        boolean canBid = bidder && status == AuctionStatus.RUNNING && timeLeft();
        bidBox.setVisible(bidder);
        bidBox.setManaged(bidder);
        bidButton.setDisable(!canBid);
        amountField.setDisable(!canBid);
        autoOnButton.setDisable(!canBid);
        autoOffButton.setDisable(!canBid || auction.getMyAutoBidMax() == null);

        boolean canPay = bidder && status == AuctionStatus.FINISHED && isMeLeading();
        payButton.setVisible(canPay);
        payButton.setManaged(canPay);

        boolean owner = user != null && user.isSeller() && user.getId().equals(auction.getSellerId());
        boolean admin = user != null && user.isAdmin();
        boolean cancellable = status == AuctionStatus.OPEN || status == AuctionStatus.RUNNING
                || (status == AuctionStatus.FINISHED && auction.getHighestBidderId() != null);
        boolean canCancel = (owner || admin) && cancellable;
        cancelButton.setVisible(canCancel);
        cancelButton.setManaged(canCancel);
    }

    @FXML
    private void placeBid() {
        BigDecimal amount;
        try {
            amount = Formats.parseMoney(amountField.getText());
        } catch (IllegalArgumentException error) {
            showError(messageLabel, error.getMessage());
            return;
        }
        bidButton.setDisable(true);
        request(() -> client().placeBid(auctionId, user().getId(), amount), payload -> {
            showSuccess(messageLabel, "Đặt giá " + Formats.money(amount) + " thành công");
            refreshProfile();
        }, messageLabel);
    }

    @FXML
    private void enableAutoBid() {
        BigDecimal max;
        BigDecimal step;
        try {
            max = Formats.parseMoney(autoMaxField.getText());
            step = Formats.parseMoney(autoStepField.getText());
        } catch (IllegalArgumentException error) {
            showError(messageLabel, error.getMessage());
            return;
        }
        request(() -> client().setAutoBid(auctionId, max, step), payload -> {
            showSuccess(messageLabel, String.valueOf(payload));
            loadDetail();
            refreshProfile();
        }, messageLabel);
    }

    @FXML
    private void disableAutoBid() {
        request(() -> client().cancelAutoBid(auctionId), payload -> {
            showSuccess(messageLabel, String.valueOf(payload));
            loadDetail();
        }, messageLabel);
    }

    @FXML
    private void pay() {
        request(() -> client().payAuction(auctionId), payload -> {
            showSuccess(messageLabel, String.valueOf(payload));
            loadDetail();
            refreshProfile();
        }, messageLabel);
    }

    @FXML
    private void cancelAuction() {
        if (!confirm("Hủy phiên này? Tiền đang giữ sẽ được hoàn cho người đặt giá.")) {
            return;
        }
        request(() -> client().cancelAuction(auctionId), payload -> {
            showSuccess(messageLabel, String.valueOf(payload));
            loadDetail();
        }, messageLabel);
    }

    @FXML
    private void back() {
        navigation.show(NavigationManager.AUCTION_LIST);
    }

    // ===================== Sự kiện realtime (luồng mạng) =====================

    @Override
    public void onNewBid(BidNotificationPayload bid) {
        if (!bid.getAuctionId().equals(auctionId)) {
            return;
        }
        Platform.runLater(() -> {
            if (auction == null) {
                return;
            }
            BidDTO row = new BidDTO(bid.getBidderId(), bid.getBidderName(), bid.getAmount(), bid.getTimestamp());
            history.add(0, row);
            addChartPoint(row);

            auction.setCurrentPrice(bid.getNewCurrentPrice());
            auction.setHighestBidderId(bid.getBidderId());
            auction.setHighestBidderName(bid.getBidderName());
            if (bid.getEndTime() != null && !bid.getEndTime().equals(auction.getEndTime())) {
                auction.setEndTime(bid.getEndTime());
                timeLabel.setText("Mở: " + Formats.dateTime(auction.getStartTime())
                        + "  ·  Đóng: " + Formats.dateTime(auction.getEndTime()) + " (đã gia hạn)");
            }
            renderPrice();
            updateControls();
            refreshProfile();
        });
    }

    @Override
    public void onAuctionClosed(AuctionClosedNotificationPayload closed) {
        if (!closed.getAuctionId().equals(auctionId)) {
            return;
        }
        Platform.runLater(() -> {
            String result = "CANCELED".equals(closed.getStatus())
                    ? "Phiên đã bị hủy."
                    : closed.getWinnerId() == null
                        ? "Phiên kết thúc, không có người thắng."
                        : "Phiên kết thúc. Người thắng: " + closed.getWinnerName()
                            + " với giá " + Formats.money(closed.getFinalPrice());
            showSuccess(messageLabel, result);
            refreshProfile();
        });
    }

    @Override
    public void onAuctionUpdated(AuctionDTO updated) {
        if (updated.getAuctionId().equals(auctionId)) {
            Platform.runLater(this::loadDetail);
        }
    }
}
