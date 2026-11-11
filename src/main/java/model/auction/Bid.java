package model.auction;

import java.math.BigDecimal;
import model.Entity;
import model.user.Bidder;

public class Bid extends Entity {
    private final Bidder bidder;
    private final BigDecimal amount;

    public Bid(Bidder bidder, BigDecimal amount) {
        super();

        if (bidder == null) {
            throw new IllegalArgumentException(
                    "Người đặt giá không được null");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Giá đặt phải lớn hơn 0");
        }

        this.bidder = bidder;
        this.amount = amount;
    }

    public Bidder getBidder() {
        return bidder;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}