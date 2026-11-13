package factory;
import model.item.Electronics;
import model.item.Item;
import java.math.BigDecimal;

public class ElectronicsFactory extends ItemFactory {
    public Item createItem(String name, BigDecimal startingPrice) {
        return new Electronics(name, startingPrice);
    }
    public Item createItemById(String id, String name, BigDecimal startingPrice) {
        return new Electronics(id, name, startingPrice);
    }
}
