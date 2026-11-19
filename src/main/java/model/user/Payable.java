package model.user;
import java.math.BigDecimal;
public interface Payable {
    void deposit(BigDecimal amount);
    void withdraw(BigDecimal amount);
    BigDecimal getBalance();
    BigDecimal getAvailableBalance();
}
