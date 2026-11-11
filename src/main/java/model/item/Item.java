package model.item;

import java.math.BigDecimal;
import model.Entity;

public abstract class Item extends Entity {
    // ID và thời gian tạo/cập nhật được kế thừa từ Entity.
    private String name;
    private BigDecimal startingPrice;
    private ItemStatus status;
    private String description;
    private String imageLink;

    // Không truyền ID: Entity tự tạo ID.
    public Item(String name, BigDecimal startingPrice) {
        this(null, name, startingPrice);
    }

    // Có truyền ID: Entity tiếp nhận và xử lý ID.
    public Item(String id, String name, BigDecimal startingPrice) {
        super(id);

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Tên sản phẩm không được để trống");
        }

        if (startingPrice == null
                || startingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Giá khởi điểm phải lớn hơn 0");
        }

        this.name = name.trim();
        this.startingPrice = startingPrice;
        this.status = ItemStatus.AVAILABLE;
    }

    // Các lớp con tự xác định loại sản phẩm.
    public abstract String getCategory();

    public boolean isValid() {
        return name != null
                && !name.trim().isEmpty()
                && startingPrice != null
                && startingPrice.compareTo(BigDecimal.ZERO) > 0
                && status != null;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getImageLink() {
        return imageLink;
    }

    public void setImageLink(String imageLink) {
        this.imageLink = imageLink;
        markUpdated();
    }

    public void setStatus(ItemStatus status) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "Trạng thái sản phẩm không được null");
        }

        this.status = status;
        markUpdated();
    }

    public void setDescription(String description) {
        this.description = description;
        markUpdated();
    }
}