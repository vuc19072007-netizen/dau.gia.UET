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
    // Mã (id) của người bán sở hữu sản phẩm. Lưu mã thay vì lưu cả đối tượng Seller
    // để Item không phụ thuộc vào gói user, và để so sánh quyền theo mã người dùng
    // (cùng cách với payAuction()/cancelAuction() trong Auction).
    // null nghĩa là sản phẩm chưa được gán chủ.
    private String ownerId;

    // Không truyền ID: Entity tự tạo ID.
    public Item(String name, BigDecimal startingPrice) {
        this(null, name, startingPrice);
    }

    // Có truyền ID: Entity tiếp nhận và xử lý ID.
    public Item(String id, String name, BigDecimal startingPrice) {
        super(id);

        // Hai hàm kiểm tra được tách riêng để updateDetails() dùng lại đúng
        // cùng một luật, không phải chép lại điều kiện ở hai nơi.
        validateName(name);
        validateStartingPrice(startingPrice);

        this.name = name.trim();
        this.startingPrice = startingPrice;
        this.status = ItemStatus.AVAILABLE;
    }

    // static: hàm chỉ kiểm tra tham số, không đọc dữ liệu của đối tượng nào.
    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Tên sản phẩm không được để trống");
        }
    }

    private static void validateStartingPrice(BigDecimal startingPrice) {
        if (startingPrice == null
                || startingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Giá khởi điểm phải lớn hơn 0");
        }
    }

    /*
     * Sửa thông tin sản phẩm (chức năng "sửa" của màn hình Seller).
     * Kiểm tra TẤT CẢ dữ liệu trước rồi mới gán: nếu tên đúng mà giá sai thì
     * không trường nào bị đổi, tránh sản phẩm bị sửa dở dang.
     * Hàm này không kiểm tra quyền; AuctionManager.updateItem() kiểm tra người
     * gọi có phải chủ sở hữu và sản phẩm có đang nằm trong phiên nào không.
     */
    public void updateDetails(
            String name,
            BigDecimal startingPrice,
            String description) {
        validateName(name);
        validateStartingPrice(startingPrice);

        this.name = name.trim();
        this.startingPrice = startingPrice;
        this.description = description;
        markUpdated();
    }

    public String getOwnerId() {
        return ownerId;
    }

    /*
     * Gán chủ sở hữu. Chỉ gán được một lần: sản phẩm đã có chủ thì không ai
     * đổi được sang người khác (gán lại đúng mã cũ thì không sao).
     */
    public void setOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Mã chủ sở hữu không được để trống");
        }

        if (this.ownerId != null && !this.ownerId.equals(ownerId)) {
            throw new IllegalStateException(
                    "Sản phẩm đã có chủ sở hữu");
        }

        this.ownerId = ownerId;
        markUpdated();
    }

    // true khi userId đúng là mã của chủ sở hữu.
    // Viết ownerId.equals(userId) sau khi kiểm tra ownerId != null để không bị
    // NullPointerException với sản phẩm chưa có chủ.
    public boolean isOwnedBy(String userId) {
        return ownerId != null && ownerId.equals(userId);
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