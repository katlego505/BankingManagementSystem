package model;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class SavingsAccount extends Account implements InterestBearing {
    private static final BigDecimal MONTHLY_RATE = new BigDecimal("0.0005");
    public SavingsAccount(String number, long ownerId, String branch, BigDecimal balance, LocalDate opened) {
        super(number, ownerId, branch, balance, opened);
    }
    @Override public Transaction withdraw(BigDecimal amount) {
        throw new IllegalArgumentException("Savings accounts do not allow withdrawals.");
    }
    @Override public BigDecimal getMonthlyRate() { return MONTHLY_RATE; }
    @Override public String getAccountType() { return "Savings"; }
    @Override public Transaction payInterest(BigDecimal amount, String period) {
        return credit("Interest", amount, period);
    }
}
