package common.network.payload;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Yêu cầu thêm hoặc sửa sản phẩm. Thêm mới thì itemId = null.
 * category chỉ dùng khi thêm mới: "ART", "ELECTRONICS", "VEHICLE".
 */
public class ItemRequestPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String itemId;
    private final String category;
    private final String name;
    private final String description;
    private final BigDecimal startingPrice;

    public ItemRequestPayload(
            String itemId,
            String category,
            String name,
            String description,
            BigDecimal startingPrice) {
        this.itemId = itemId;
        this.category = category;
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
    }

    public String getItemId() {
        return itemId;
    }

    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }
}
