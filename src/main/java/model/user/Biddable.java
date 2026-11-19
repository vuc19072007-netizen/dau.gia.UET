package model.user;
import java.math.BigDecimal;
public interface Biddable {
    void lockFunds(BigDecimal amount);

    BigDecimal unlockFunds();

    void confirmPayment(BigDecimal amount);

    boolean canAfford();

    boolean canAfford(BigDecimal amount);
}
