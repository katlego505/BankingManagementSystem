package model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public interface InterestBearing {
    BigDecimal getBalance();
    BigDecimal getMonthlyRate();
    Transaction payInterest(BigDecimal amount, String period);

    default BigDecimal calculateInterest() { return calculateInterest(getBalance()); }

    default BigDecimal calculateInterest(BigDecimal closingBalance) {
        return closingBalance.multiply(getMonthlyRate()).setScale(2, RoundingMode.HALF_UP);
    }
}
