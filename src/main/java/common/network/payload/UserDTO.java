package common.network.payload;

import java.io.Serializable;
import java.math.BigDecimal;

import model.user.Bidder;
import model.user.Payable;
import model.user.User;

/**
 * Thông tin tài khoản gửi qua mạng. KHÔNG có mật khẩu: trước đây server gửi
 * nguyên đối tượng User (kèm password) cho client sau khi đăng nhập.
 */
public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String phoneNumber;
    private String address;
    private String role;
    private boolean active;
    // Ví: Bidder và Seller có; Admin để null.
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;

    public static UserDTO fromUser(User user) {
        if (user == null) {
            return null;
        }

        UserDTO dto = new UserDTO();
        dto.id = user.getId();
        dto.name = user.getName();
        dto.phoneNumber = user.getPhoneNumber();
        dto.address = user.getAddress();
        dto.role = user.getRole();
        dto.active = user.isActive();

        // Payable là interface ví chung của Bidder và Seller (đa hình).
        if (user instanceof Payable wallet) {
            dto.balance = wallet.getBalance();
            dto.availableBalance = wallet.getAvailableBalance();
        }

        if (user instanceof Bidder bidder) {
            dto.lockedBalance = bidder.getLockedBalance();
        }

        return dto;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public String getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public BigDecimal getLockedBalance() {
        return lockedBalance;
    }

    public boolean isBidder() {
        return "BIDDER".equals(role);
    }

    public boolean isSeller() {
        return "SELLER".equals(role);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    @Override
    public String toString() {
        return name + " (" + role + ")";
    }
}
