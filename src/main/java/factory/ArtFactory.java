package factory;
import model.item.Art;
import model.item.Item;
import java.math.BigDecimal;

public class ArtFactory extends ItemFactory {
    public Item createItem(String name, BigDecimal startingPrice) {
        return new Art(name, startingPrice);
    }
    public Item createItemById(String id, String name, BigDecimal startingPrice) {
        return new Art(id, name, startingPrice);
    }
}
