package model.user;

import java.math.BigDecimal;

public class Seller extends User implements Payable {
    private BigDecimal balance;

    public Seller(String name, String phoneNumber, String password, String address) {
        super(name, phoneNumber, password, address, "SELLER");
        this.balance = BigDecimal.ZERO;
    }

    public Seller(String id, String name, String phoneNumber, String password, String address) {
        super(id, name, phoneNumber, password, address, "SELLER");
        this.balance = BigDecimal.ZERO; // Bổ sung khởi tạo để tránh lỗi NullPointerException
    }

    @Override
    public synchronized void deposit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền giao dịch phải lớn hơn 0");
        }
        this.balance = this.balance.add(amount);
        markUpdated();
    }

    @Override
    public synchronized void withdraw(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền rút phải lớn hơn 0!");
        }
        if (amount.compareTo(this.balance) > 0) {
            throw new IllegalArgumentException(
                    "Số tiền rút (" + amount + ") vượt quá số dư hiện có (" + this.balance + ")!");
        }
        this.balance = this.balance.subtract(amount);
        markUpdated();
    }

    @Override
    public synchronized BigDecimal getBalance() {
        return balance;
    }

    @Override
    public synchronized BigDecimal getAvailableBalance() {
        return balance;
    }
}