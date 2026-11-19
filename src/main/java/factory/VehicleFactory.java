package factory;
import model.item.Vehicle;
import model.item.Item;
import java.math.BigDecimal;

public class VehicleFactory extends ItemFactory {
    public Item createItem(String name, BigDecimal startingPrice) {
        return new Vehicle(name, startingPrice);
    }
    public Item createItemById(String id, String name, BigDecimal startingPrice) {
        return new Vehicle(id, name, startingPrice);
    }
}
