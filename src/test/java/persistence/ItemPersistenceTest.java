package persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;

import model.auction.Auction;
import model.item.Item;
import model.item.ItemType;
import model.user.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import service.AuctionManager;

/**
 * Kho sản phẩm phải được lưu cùng người dùng và phiên: sản phẩm chưa lên phiên
 * không bị mất, sản phẩm đã lên phiên vẫn là cùng một đối tượng sau khi tải.
 */
class ItemPersistenceTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void cleanUp() {
        AuctionManager.getInstance().reset();
    }

    @Test
    void itemsSurviveRestart() {
        AuctionManager manager = AuctionManager.getInstance();
        manager.reset();
        Seller seller = new Seller("Seller", "0913000001", "password1", "HN");
        manager.addUser(seller);

        Item stored = manager.addItem(seller, ItemType.ART, "Chưa lên phiên", BigDecimal.TEN);
        Item listed = manager.addItem(seller, ItemType.ART, "Đã lên phiên", BigDecimal.TEN);
        manager.createAuction(listed, seller, BigDecimal.ONE,
                LocalDateTime.now().plusMinutes(1), LocalDateTime.now().plusMinutes(5));

        DataStore store = new DataStore(tempDir.resolve("data.ser").toString());
        manager.saveTo(store);
        manager.reset();
        assertTrue(manager.loadFrom(store));

        Seller reloaded = (Seller) manager.getUser(seller.getId());
        assertEquals(2, manager.getItemsOf(reloaded.getId()).size());
        assertEquals("Chưa lên phiên", manager.getItem(stored.getId()).getName());

        Auction auction = manager.getAllAuction().get(0);
        assertSame(auction.getItem(), manager.getItem(listed.getId()),
                "sản phẩm trong phiên và trong kho phải là cùng một đối tượng");
    }
}
