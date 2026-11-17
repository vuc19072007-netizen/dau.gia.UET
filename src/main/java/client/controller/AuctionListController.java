package client.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import client.network.AuctionEventListener;
import client.util.Formats;
import client.view.NavigationManager;
import common.network.payload.AuctionClosedNotificationPayload;
import common.network.payload.AuctionDTO;
import common.network.payload.BidNotificationPayload;
import common.network.payload.UserDTO;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import model.auction.AuctionStatus;

/**
 * Danh sách phiên đấu giá: tìm kiếm, lọc theo loại và trạng thái, mở một phiên.
 * <p>
 * Cập nhật realtime: controller đăng ký AuctionEventListener với client mạng.
 * Sự kiện đến trên luồng mạng nên phải chuyển sang luồng giao diện bằng
 * Platform.runLater() rồi mới sửa bảng.
 */
public class AuctionListController extends BaseController implements AuctionEventListener {
    private static final String ALL_CATEGORIES = "Mọi loại";
    private static final String ALL_STATUSES = "Mọi trạng thái";

    @FXML
    private Label userLabel;
    @FXML
    private Label balanceLabel;
    @FXML
    private Button depositButton;
    @FXML
    private Button sellerButton;
    @FXML
    private Button adminButton;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> categoryBox;
    @FXML
    private ComboBox<String> statusBox;
    @FXML
    private TableView<AuctionDTO> auctionTable;
    @FXML
    private TableColumn<AuctionDTO, String> nameColumn;
    @FXML
    private TableColumn<AuctionDTO, String> categoryColumn;
    @FXML
    private TableColumn<AuctionDTO, String> priceColumn;
    @FXML
    private TableColumn<AuctionDTO, String> leaderColumn;
    @FXML
    private TableColumn<AuctionDTO, String> statusColumn;
    @FXML
    private TableColumn<AuctionDTO, String> endColumn;
    @FXML
    private TableColumn<AuctionDTO, String> sellerColumn;
    @FXML
    private Button openButton;
    @FXML
    private Label messageLabel;

    // Dữ liệu gốc và bản đã lọc. Bảng hiển thị bản đã lọc.
    private final ObservableList<AuctionDTO> auctions = FXCollections.observableArrayList();
    private final FilteredList<AuctionDTO> filtered = new FilteredList<>(auctions);

    @FXML
    private void initialize() {
        bind(nameColumn, AuctionDTO::getItemName);
        bind(categoryColumn, AuctionDTO::getItemCategory);
        bind(priceColumn, auction -> Formats.money(auction.getCurrentPrice()));
        bind(leaderColumn, auction -> auction.getHighestBidderName() == null ? "-" : auction.getHighestBidderName());
        bind(statusColumn, auction -> Formats.status(auction.getStatus()));
        bind(endColumn, auction -> Formats.dateTime(auction.getEndTime()));
        bind(sellerColumn, AuctionDTO::getSellerName);

        auctionTable.setItems(filtered);
        auctionTable.setRowFactory(table -> {
            TableRow<AuctionDTO> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    open(row.getItem());
                }
            });
            return row;
        });
        openButton.disableProperty().bind(auctionTable.getSelectionModel().selectedItemProperty().isNull());

        categoryBox.setItems(FXCollections.observableArrayList(ALL_CATEGORIES, "Art", "Electronics", "Vehicle"));
        categoryBox.getSelectionModel().selectFirst();
        statusBox.getItems().add(ALL_STATUSES);
        for (AuctionStatus status : AuctionStatus.values()) {
            statusBox.getItems().add(Formats.status(status));
        }
        statusBox.getSelectionModel().selectFirst();

        searchField.textProperty().addListener((obs, old, value) -> applyFilter());
        categoryBox.valueProperty().addListener((obs, old, value) -> applyFilter());
        statusBox.valueProperty().addListener((obs, old, value) -> applyFilter());
    }

    // Gắn một cột với hàm lấy chữ hiển thị từ AuctionDTO.
    static <T> void bind(TableColumn<T, String> column, Function<T, String> text) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
    }

    @Override
    public void onShow(Object parameter) {
        UserDTO user = user();
        userLabel.setText(user.getName() + " · " + Formats.role(user.getRole()));
        depositButton.setVisible(user.isBidder());
        depositButton.setManaged(user.isBidder());
        sellerButton.setVisible(user.isSeller());
        sellerButton.setManaged(user.isSeller());
        adminButton.setVisible(user.isAdmin());
        adminButton.setManaged(user.isAdmin());
        showBalance(user);

        client().addEventListener(this);
        reload();
        refreshProfile();
    }

    @Override
    public void onHide() {
        if (client() != null) {
            client().removeEventListener(this);
        }
    }

    private void showBalance(UserDTO user) {
        boolean hasWallet = user.getBalance() != null;
        balanceLabel.setVisible(hasWallet);
        balanceLabel.setManaged(hasWallet);
        if (hasWallet) {
            balanceLabel.setText("Khả dụng: " + Formats.money(user.getAvailableBalance()));
        }
    }

    private void refreshProfile() {
        request(() -> client().refreshProfile(), payload -> showBalance((UserDTO) payload), messageLabel);
    }

    @FXML
    private void reload() {
        run(() -> client().getAuctions(), this::setAuctions, messageLabel);
    }

    private void setAuctions(List<AuctionDTO> list) {
        auctions.setAll(list);
        messageLabel.setText(list.size() + " phiên");
    }

    private void applyFilter() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String category = categoryBox.getValue();
        String status = statusBox.getValue();

        filtered.setPredicate(auction -> {
            boolean nameOk = keyword.isEmpty()
                    || (auction.getItemName() != null
                        && auction.getItemName().toLowerCase(Locale.ROOT).contains(keyword));
            boolean categoryOk = category == null || ALL_CATEGORIES.equals(category)
                    || category.equals(auction.getItemCategory());
            boolean statusOk = status == null || ALL_STATUSES.equals(status)
                    || status.equals(Formats.status(auction.getStatus()));
            return nameOk && categoryOk && statusOk;
        });
    }

    @FXML
    private void openSelected() {
        AuctionDTO selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            open(selected);
        }
    }

    private void open(AuctionDTO auction) {
        navigation.show(NavigationManager.LIVE_AUCTION, auction.getAuctionId());
    }

    @FXML
    private void deposit() {
        TextInputDialog dialog = new TextInputDialog("1000000");
        dialog.setTitle("Nạp tiền");
        dialog.setHeaderText("Nhập số tiền muốn nạp vào ví");
        dialog.setContentText("Số tiền (đ):");
        dialog.showAndWait().ifPresent(text -> {
            BigDecimal amount;
            try {
                amount = Formats.parseMoney(text);
            } catch (IllegalArgumentException error) {
                showError(messageLabel, error.getMessage());
                return;
            }
            request(() -> client().deposit(amount), payload -> {
                showBalance((UserDTO) payload);
                showSuccess(messageLabel, "Đã nạp " + Formats.money(amount));
            }, messageLabel);
        });
    }

    @FXML
    private void openSeller() {
        navigation.show(NavigationManager.SELLER_DASHBOARD);
    }

    @FXML
    private void openAdmin() {
        navigation.show(NavigationManager.ADMIN);
    }

    @FXML
    private void logout() {
        session.run(() -> client().logout(), response -> navigation.show(NavigationManager.LOGIN),
                error -> navigation.show(NavigationManager.LOGIN));
    }

    // ===================== Sự kiện realtime (luồng mạng) =====================

    @Override
    public void onNewBid(BidNotificationPayload bid) {
        Platform.runLater(() -> {
            AuctionDTO auction = find(bid.getAuctionId());
            if (auction == null) {
                return;
            }
            auction.setCurrentPrice(bid.getNewCurrentPrice());
            auction.setHighestBidderId(bid.getBidderId());
            auction.setHighestBidderName(bid.getBidderName());
            auction.setBidCount(auction.getBidCount() + 1);
            if (bid.getEndTime() != null) {
                auction.setEndTime(bid.getEndTime());
            }
            auctionTable.refresh();
            // Có thể mình vừa bị vượt giá: số dư khả dụng đổi.
            if (user() != null && user().isBidder()) {
                refreshProfile();
            }
        });
    }

    @Override
    public void onAuctionClosed(AuctionClosedNotificationPayload closed) {
        // Trạng thái mới đến qua onAuctionUpdated; ở đây chỉ cập nhật số dư.
        Platform.runLater(() -> {
            if (user() != null && user().getBalance() != null) {
                refreshProfile();
            }
        });
    }

    @Override
    public void onAuctionUpdated(AuctionDTO updated) {
        Platform.runLater(() -> {
            AuctionDTO existing = find(updated.getAuctionId());
            if (existing == null) {
                auctions.add(0, updated);
            } else {
                auctions.set(auctions.indexOf(existing), updated);
            }
        });
    }

    private AuctionDTO find(String auctionId) {
        for (AuctionDTO auction : auctions) {
            if (auction.getAuctionId().equals(auctionId)) {
                return auction;
            }
        }
        return null;
    }
}
