package factory;
import model.item.Item;
import model.item.ItemType;
import java.math.BigDecimal;

public abstract class ItemFactory {
    public abstract Item createItem(String name, BigDecimal startingPrice);
    public abstract Item createItemById(String id, String name, BigDecimal startingPrice);
    public static ItemFactory getFactory(ItemType type) {
        if (type == null) {
            throw new IllegalArgumentException(
                    "Loại sản phẩm không được null");
        }
        switch (type) {
            case VEHICLE: return new VehicleFactory();
            case ART: return new ArtFactory();
            case ELECTRONICS: return new ElectronicsFactory();
            default: throw new IllegalArgumentException("Unsupported item type");
        }
    }
}
