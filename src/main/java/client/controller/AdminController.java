package client.controller;

import client.util.Formats;
import client.view.NavigationManager;
import common.network.payload.AuctionDTO;
import common.network.payload.UserDTO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import model.auction.AuctionStatus;

/**
 * Màn hình quản trị: xem mọi tài khoản, khóa/mở khóa, hủy phiên đấu giá.
 * Server chỉ chấp nhận các yêu cầu này từ tài khoản Admin.
 */
public class AdminController extends BaseController {
    @FXML
    private TableView<UserDTO> userTable;
    @FXML
    private TableColumn<UserDTO, String> userNameColumn;
    @FXML
    private TableColumn<UserDTO, String> userPhoneColumn;
    @FXML
    private TableColumn<UserDTO, String> userRoleColumn;
    @FXML
    private TableColumn<UserDTO, String> userStatusColumn;
    @FXML
    private TableColumn<UserDTO, String> userBalanceColumn;
    @FXML
    private Button banButton;
    @FXML
    private Button unbanButton;
    @FXML
    private TableView<AuctionDTO> auctionTable;
    @FXML
    private TableColumn<AuctionDTO, String> auctionNameColumn;
    @FXML
    private TableColumn<AuctionDTO, String> auctionSellerColumn;
    @FXML
    private TableColumn<AuctionDTO, String> auctionPriceColumn;
    @FXML
    private TableColumn<AuctionDTO, String> auctionStatusColumn;
    @FXML
    private Button cancelAuctionButton;
    @FXML
    private Label messageLabel;

    private final ObservableList<UserDTO> users = FXCollections.observableArrayList();
    private final ObservableList<AuctionDTO> auctions = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        AuctionListController.bind(userNameColumn, UserDTO::getName);
        AuctionListController.bind(userPhoneColumn, UserDTO::getPhoneNumber);
        AuctionListController.bind(userRoleColumn, user -> Formats.role(user.getRole()));
        AuctionListController.bind(userStatusColumn, user -> user.isActive() ? "Hoạt động" : "Bị khóa");
        AuctionListController.bind(userBalanceColumn, user -> Formats.money(user.getBalance()));
        userTable.setItems(users);

        AuctionListController.bind(auctionNameColumn, AuctionDTO::getItemName);
        AuctionListController.bind(auctionSellerColumn, AuctionDTO::getSellerName);
        AuctionListController.bind(auctionPriceColumn, auction -> Formats.money(auction.getCurrentPrice()));
        AuctionListController.bind(auctionStatusColumn, auction -> Formats.status(auction.getStatus()));
        auctionTable.setItems(auctions);

        // Nút chỉ bật khi lựa chọn phù hợp: khóa tài khoản đang hoạt động (không phải admin)...
        userTable.getSelectionModel().selectedItemProperty().addListener((obs, old, user) -> {
            banButton.setDisable(user == null || !user.isActive() || user.isAdmin());
            unbanButton.setDisable(user == null || user.isActive());
        });
        auctionTable.getSelectionModel().selectedItemProperty().addListener((obs, old, auction) ->
                cancelAuctionButton.setDisable(auction == null
                        || auction.getStatus() == AuctionStatus.PAID
                        || auction.getStatus() == AuctionStatus.CANCELED));
        banButton.setDisable(true);
        unbanButton.setDisable(true);
        cancelAuctionButton.setDisable(true);
    }

    @Override
    public void onShow(Object parameter) {
        reload();
    }

    @FXML
    private void reload() {
        run(() -> client().getUsers(), users::setAll, messageLabel);
        run(() -> client().getAuctions(), auctions::setAll, messageLabel);
    }

    @FXML
    private void ban() {
        UserDTO selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null || !confirm("Khóa tài khoản " + selected.getName() + "?")) {
            return;
        }
        request(() -> client().banUser(selected.getId()), payload -> {
            showSuccess(messageLabel, "Đã khóa " + selected.getName());
            reload();
        }, messageLabel);
    }

    @FXML
    private void unban() {
        UserDTO selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        request(() -> client().unbanUser(selected.getId()), payload -> {
            showSuccess(messageLabel, "Đã mở khóa " + selected.getName());
            reload();
        }, messageLabel);
    }

    @FXML
    private void cancelAuction() {
        AuctionDTO selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null || !confirm("Hủy phiên \"" + selected.getItemName() + "\"?")) {
            return;
        }
        request(() -> client().cancelAuction(selected.getAuctionId()), payload -> {
            showSuccess(messageLabel, String.valueOf(payload));
            reload();
        }, messageLabel);
    }

    @FXML
    private void back() {
        navigation.show(NavigationManager.AUCTION_LIST);
    }
}
