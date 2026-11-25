package model;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class InvestmentAccount extends Account implements InterestBearing {
    public static final BigDecimal OPENING_MINIMUM = new BigDecimal("500.00");
    private static final BigDecimal MONTHLY_RATE = new BigDecimal("0.05");

    private InvestmentAccount(String number, long ownerId, String branch, BigDecimal balance, LocalDate opened) {
        super(number, ownerId, branch, balance, opened);
    }

    public static InvestmentAccount open(String number, long ownerId, String branch, BigDecimal balance, LocalDate opened) {
        var account = restore(number, ownerId, branch, balance, opened);
        if (account.getBalance().compareTo(OPENING_MINIMUM) < 0) {
            throw new IllegalArgumentException("Investment accounts require BWP 500.00 to open.");
        }
        return account;
    }

    public static InvestmentAccount restore(String number, long ownerId, String branch, BigDecimal balance,
                                            LocalDate opened) {
        return new InvestmentAccount(number, ownerId, branch, balance, opened);
    }

    @Override public Transaction withdraw(BigDecimal amount) { return debit(amount); }
    @Override public BigDecimal getMonthlyRate() { return MONTHLY_RATE; }
    @Override public String getAccountType() { return "Investment"; }
    @Override public Transaction payInterest(BigDecimal amount, String period) {
        return credit("Interest", amount, period);
    }
}
