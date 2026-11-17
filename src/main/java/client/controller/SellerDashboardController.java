package client.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import client.network.AuctionEventListener;
import client.util.Formats;
import client.view.NavigationManager;
import common.network.payload.AuctionClosedNotificationPayload;
import common.network.payload.AuctionDTO;
import common.network.payload.BidNotificationPayload;
import common.network.payload.CreateAuctionRequestPayload;
import common.network.payload.ItemDTO;
import common.network.payload.ItemRequestPayload;
import common.network.payload.UserDTO;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import model.auction.AuctionStatus;
import model.item.ItemStatus;

/**
 * Màn hình người bán: thêm/sửa/xóa sản phẩm, tạo phiên cho sản phẩm, theo dõi và
 * hủy phiên của mình. Server kiểm tra quyền chủ sở hữu và sản phẩm có đang trong
 * phiên hay không; controller chỉ gửi yêu cầu và hiển thị kết quả.
 */
public class SellerDashboardController extends BaseController implements AuctionEventListener {
    @FXML
    private Label balanceLabel;
    @FXML
    private TableView<ItemDTO> itemTable;
    @FXML
    private TableColumn<ItemDTO, String> itemNameColumn;
    @FXML
    private TableColumn<ItemDTO, String> itemCategoryColumn;
    @FXML
    private TableColumn<ItemDTO, String> itemPriceColumn;
    @FXML
    private TableColumn<ItemDTO, String> itemStatusColumn;
    @FXML
    private TableView<AuctionDTO> auctionTable;
    @FXML
    private TableColumn<AuctionDTO, String> auctionNameColumn;
    @FXML
    private TableColumn<AuctionDTO, String> auctionPriceColumn;
    @FXML
    private TableColumn<AuctionDTO, String> auctionStatusColumn;
    @FXML
    private TableColumn<AuctionDTO, String> auctionEndColumn;
    @FXML
    private Button viewAuctionButton;
    @FXML
    private Button cancelAuctionButton;
    @FXML
    private Label formTitle;
    @FXML
    private ComboBox<String> categoryBox;
    @FXML
    private TextField nameField;
    @FXML
    private TextField priceField;
    @FXML
    private TextArea descriptionArea;
    @FXML
    private Button saveButton;
    @FXML
    private Button deleteButton;
    @FXML
    private VBox auctionForm;
    @FXML
    private TextField incrementField;
    @FXML
    private TextField startDelayField;
    @FXML
    private TextField durationField;
    @FXML
    private CheckBox antiSnipingBox;
    @FXML
    private Button createAuctionButton;
    @FXML
    private Label messageLabel;

    private final ObservableList<ItemDTO> items = FXCollections.observableArrayList();
    private final ObservableList<AuctionDTO> myAuctions = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        AuctionListController.bind(itemNameColumn, ItemDTO::getName);
        AuctionListController.bind(itemCategoryColumn, ItemDTO::getCategory);
        AuctionListController.bind(itemPriceColumn, item -> Formats.money(item.getStartingPrice()));
        AuctionListController.bind(itemStatusColumn, item -> itemStatus(item.getStatus()));
        itemTable.setItems(items);

        AuctionListController.bind(auctionNameColumn, AuctionDTO::getItemName);
        AuctionListController.bind(auctionPriceColumn, auction -> Formats.money(auction.getCurrentPrice()));
        AuctionListController.bind(auctionStatusColumn, auction -> Formats.status(auction.getStatus()));
        AuctionListController.bind(auctionEndColumn, auction -> Formats.dateTime(auction.getEndTime()));
        auctionTable.setItems(myAuctions);

        categoryBox.setItems(FXCollections.observableArrayList("ELECTRONICS", "ART", "VEHICLE"));
        categoryBox.getSelectionModel().selectFirst();

        itemTable.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> fillForm(item));
        viewAuctionButton.disableProperty().bind(auctionTable.getSelectionModel().selectedItemProperty().isNull());
        cancelAuctionButton.disableProperty().bind(auctionTable.getSelectionModel().selectedItemProperty().isNull());
        fillForm(null);
    }

    private static String itemStatus(ItemStatus status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case AVAILABLE -> "Sẵn sàng";
            case IN_AUCTION -> "Đang đấu giá";
            case SOLD -> "Đã bán";
            case UNSOLD -> "Không bán được";
            case CANCELED -> "Phiên đã hủy";
        };
    }

    @Override
    public void onShow(Object parameter) {
        client().addEventListener(this);
        reloadAll();
    }

    @Override
    public void onHide() {
        if (client() != null) {
            client().removeEventListener(this);
        }
    }

    private void reloadAll() {
        String selectedId = selectedItem() == null ? null : selectedItem().getId();
        run(() -> client().getMyItems(), list -> {
            items.setAll(list);
            reselect(selectedId);
        }, messageLabel);
        run(() -> client().getAuctions(), this::showMyAuctions, messageLabel);
        request(() -> client().refreshProfile(), payload -> {
            UserDTO user = (UserDTO) payload;
            balanceLabel.setText("Số dư: " + Formats.money(user.getBalance()));
        }, messageLabel);
    }

    private void reselect(String itemId) {
        for (ItemDTO item : items) {
            if (item.getId().equals(itemId)) {
                itemTable.getSelectionModel().select(item);
                return;
            }
        }
        fillForm(null);
    }

    private void showMyAuctions(List<AuctionDTO> all) {
        String me = user().getId();
        myAuctions.setAll(all.stream().filter(auction -> me.equals(auction.getSellerId())).toList());
    }

    private ItemDTO selectedItem() {
        return itemTable.getSelectionModel().getSelectedItem();
    }

    // Có sản phẩm được chọn: form chuyển sang chế độ sửa; không thì chế độ thêm.
    private void fillForm(ItemDTO item) {
        boolean editing = item != null;
        // Sản phẩm đang đấu giá hoặc đã bán thì không sửa, xóa hay tạo phiên mới
        // (server cũng từ chối; khóa nút để người dùng thấy ngay).
        boolean busy = editing && (item.getStatus() == ItemStatus.IN_AUCTION || item.getStatus() == ItemStatus.SOLD);
        formTitle.setText(editing ? (busy ? "Sản phẩm đang đấu giá hoặc đã bán" : "Sửa sản phẩm") : "Thêm sản phẩm");
        saveButton.setText(editing ? "Lưu thay đổi" : "Thêm");
        saveButton.setDisable(busy);
        deleteButton.setDisable(!editing || busy);
        categoryBox.setDisable(editing);
        auctionForm.setDisable(!editing || busy);

        if (editing) {
            categoryBox.setValue(item.getCategory().toUpperCase(Locale.ROOT));
            nameField.setText(item.getName());
            priceField.setText(item.getStartingPrice().toPlainString());
            descriptionArea.setText(item.getDescription() == null ? "" : item.getDescription());
        }
    }

    @FXML
    private void clearForm() {
        itemTable.getSelectionModel().clearSelection();
        nameField.clear();
        priceField.clear();
        descriptionArea.clear();
        fillForm(null);
    }

    @FXML
    private void saveItem() {
        BigDecimal price;
        try {
            price = Formats.parseMoney(priceField.getText());
        } catch (IllegalArgumentException error) {
            showError(messageLabel, error.getMessage());
            return;
        }
        ItemDTO selected = selectedItem();
        ItemRequestPayload payload = new ItemRequestPayload(
                selected == null ? null : selected.getId(),
                categoryBox.getValue(),
                nameField.getText(),
                descriptionArea.getText(),
                price);

        if (selected == null) {
            request(() -> client().addItem(payload), result -> {
                showSuccess(messageLabel, "Đã thêm " + ((ItemDTO) result).getName());
                clearForm();
                reloadAll();
            }, messageLabel);
        } else {
            request(() -> client().updateItem(payload), result -> {
                showSuccess(messageLabel, "Đã lưu thay đổi");
                reloadAll();
            }, messageLabel);
        }
    }

    @FXML
    private void deleteItem() {
        ItemDTO selected = selectedItem();
        if (selected == null || !confirm("Xóa sản phẩm \"" + selected.getName() + "\"?")) {
            return;
        }
        request(() -> client().deleteItem(selected.getId()), result -> {
            showSuccess(messageLabel, "Đã xóa sản phẩm");
            clearForm();
            reloadAll();
        }, messageLabel);
    }

    @FXML
    private void createAuction() {
        ItemDTO selected = selectedItem();
        if (selected == null) {
            showError(messageLabel, "Hãy chọn một sản phẩm");
            return;
        }

        BigDecimal increment;
        long delay;
        long duration;
        try {
            increment = Formats.parseMoney(incrementField.getText());
            delay = Long.parseLong(startDelayField.getText().trim());
            duration = Long.parseLong(durationField.getText().trim());
        } catch (IllegalArgumentException error) {
            showError(messageLabel, error instanceof NumberFormatException
                    ? "Số phút phải là số nguyên" : error.getMessage());
            return;
        }
        if (delay < 0 || duration <= 0) {
            showError(messageLabel, "Số phút bắt đầu phải từ 0, thời lượng phải lớn hơn 0");
            return;
        }

        LocalDateTime start = LocalDateTime.now().plusMinutes(delay);
        CreateAuctionRequestPayload payload = new CreateAuctionRequestPayload();
        payload.setItemId(selected.getId());
        payload.setMinimumIncrement(increment);
        payload.setStartTime(start);
        payload.setEndTime(start.plusMinutes(duration));
        payload.setAntiSnipingEnabled(antiSnipingBox.isSelected());

        createAuctionButton.setDisable(true);
        session.run(() -> client().createAuction(payload), response -> {
            createAuctionButton.setDisable(false);
            if (response.isSuccess()) {
                showSuccess(messageLabel, "Đã tạo phiên cho " + selected.getName());
                reloadAll();
            } else {
                showError(messageLabel, response.getErrorMessage());
            }
        }, error -> {
            createAuctionButton.setDisable(false);
            showError(messageLabel, describe(error));
        });
    }

    @FXML
    private void viewAuction() {
        AuctionDTO selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            navigation.show(NavigationManager.LIVE_AUCTION, selected.getAuctionId());
        }
    }

    @FXML
    private void cancelAuction() {
        AuctionDTO selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (selected.getStatus() == AuctionStatus.PAID || selected.getStatus() == AuctionStatus.CANCELED) {
            showError(messageLabel, "Phiên đã thanh toán hoặc đã hủy");
            return;
        }
        if (!confirm("Hủy phiên \"" + selected.getItemName() + "\"? Tiền đang giữ sẽ được hoàn.")) {
            return;
        }
        request(() -> client().cancelAuction(selected.getAuctionId()), result -> {
            showSuccess(messageLabel, String.valueOf(result));
            reloadAll();
        }, messageLabel);
    }

    @FXML
    private void back() {
        navigation.show(NavigationManager.AUCTION_LIST);
    }

    // ===================== Sự kiện realtime =====================

    @Override
    public void onNewBid(BidNotificationPayload bid) {
        Platform.runLater(() -> {
            for (AuctionDTO auction : myAuctions) {
                if (auction.getAuctionId().equals(bid.getAuctionId())) {
                    auction.setCurrentPrice(bid.getNewCurrentPrice());
                    auctionTable.refresh();
                }
            }
        });
    }

    @Override
    public void onAuctionClosed(AuctionClosedNotificationPayload closed) {
    }

    @Override
    public void onAuctionUpdated(AuctionDTO auction) {
        if (user() != null && user().getId().equals(auction.getSellerId())) {
            Platform.runLater(this::reloadAll);
        }
    }
}
