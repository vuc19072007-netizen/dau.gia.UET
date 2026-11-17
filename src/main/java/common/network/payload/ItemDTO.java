package common.network.payload;

import java.io.Serializable;
import java.math.BigDecimal;

import model.item.Art;
import model.item.Electronics;
import model.item.Item;
import model.item.ItemStatus;
import model.item.Vehicle;

/**
 * Thông tin sản phẩm gửi qua mạng cho màn hình Seller.
 */
public class ItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String category;
    private String description;
    private BigDecimal startingPrice;
    private ItemStatus status;
    private String ownerId;
    // Một dòng mô tả thuộc tính riêng của lớp con (hãng, họa sĩ, năm sản xuất...).
    private String details;

    public static ItemDTO fromItem(Item item) {
        if (item == null) {
            return null;
        }

        ItemDTO dto = new ItemDTO();
        dto.id = item.getId();
        dto.name = item.getName();
        dto.category = item.getCategory();
        dto.description = item.getDescription();
        dto.startingPrice = item.getStartingPrice();
        dto.status = item.getStatus();
        dto.ownerId = item.getOwnerId();
        dto.details = describe(item);
        return dto;
    }

    // Đọc thuộc tính riêng theo kiểu thật của sản phẩm (pattern matching).
    private static String describe(Item item) {
        if (item instanceof Electronics electronics) {
            return join("Hãng: ", electronics.getBrand(), "Model: ", electronics.getModel());
        }

        if (item instanceof Art art) {
            String year = art.getYearPublished() > 0 ? String.valueOf(art.getYearPublished()) : null;
            return join("Họa sĩ: ", art.getArtist(), "Năm: ", year);
        }

        if (item instanceof Vehicle vehicle) {
            String year = vehicle.getYearMade() > 0 ? String.valueOf(vehicle.getYearMade()) : null;
            return join("Hãng: ", vehicle.getManufacturer(), "Năm SX: ", year);
        }

        return "";
    }

    private static String join(String label1, String value1, String label2, String value2) {
        StringBuilder text = new StringBuilder();

        if (value1 != null && !value1.isBlank()) {
            text.append(label1).append(value1);
        }

        if (value2 != null && !value2.isBlank()) {
            if (!text.isEmpty()) {
                text.append(" · ");
            }
            text.append(label2).append(value2);
        }

        return text.toString();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return name;
    }
}
