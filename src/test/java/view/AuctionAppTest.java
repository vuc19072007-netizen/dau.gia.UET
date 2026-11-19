package view;

import app.AuctionApp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.auction.Auction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuctionAppTest {
    @BeforeAll static void startToolkit() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.countDown();
        });
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @AfterAll static void stopToolkit() { Platform.exit(); }

    @Test void loginAndPlaceBidThroughExistingInterface() throws Exception {
        FutureTask<Void> test = new FutureTask<>(() -> {
            AuctionApp app = new AuctionApp();
            Stage stage = new Stage();
            try {
                app.start(stage);
                VBox login = (VBox) stage.getScene().getRoot();
                ((TextField) login.getChildren().get(1)).setText("0987654321");
                PasswordField password = (PasswordField) login.getChildren().get(2);
                Button loginButton = (Button) login.getChildren().get(3);
                password.setText("wrong");
                loginButton.fire();
                assertFalse(((Label) login.getChildren().get(4)).getText().isBlank());
                password.setText("password1");
                loginButton.fire();
                VBox screen = (VBox) stage.getScene().getRoot();
                ListView<?> list = (ListView<?>) screen.lookup(".list-view");
                assertNotNull(list);
                assertEquals(1, list.getItems().size());
                list.getSelectionModel().selectFirst();
                Auction auction = (Auction) list.getSelectionModel().getSelectedItem();
                TextField amount = (TextField) screen.lookup(".text-field");
                Button placeBid = screen.getChildren().stream()
                        .filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> "Đặt giá".equals(button.getText()))
                        .findFirst().orElseThrow();
                amount.setText("1000000");
                placeBid.fire();
                assertTrue(auction.getBidHistory().isEmpty());
                amount.setText("1100000");
                placeBid.fire();
                assertEquals(1, auction.getBidHistory().size());
                assertEquals("1100000", auction.getCurrentPrice().toPlainString());
            } finally {
                app.stop();
                stage.hide();
            }
            return null;
        });
        Platform.runLater(test);
        test.get(10, TimeUnit.SECONDS);
    }
}
