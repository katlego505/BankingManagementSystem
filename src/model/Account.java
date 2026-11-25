package model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public abstract class Account {
    private final String accountNumber;
    private final long ownerId;
    private final String branch;
    private final LocalDate dateOpened;
    private BigDecimal balance;

    protected Account(String accountNumber, long ownerId, String branch, BigDecimal balance, LocalDate dateOpened) {
        this.accountNumber = required(accountNumber, "Account number");
        if (ownerId <= 0) throw new IllegalArgumentException("An existing customer is required.");
        this.ownerId = ownerId;
        this.branch = required(branch, "Branch");
        this.balance = money(balance);
        if (balance.signum() < 0) throw new IllegalArgumentException("Opening balance cannot be negative.");
        this.dateOpened = dateOpened;
    }

    public final Transaction deposit(BigDecimal amount) { return deposit(amount, ""); }

    public final Transaction deposit(BigDecimal amount, String note) {
        return credit("Deposit", amount, note);
    }

    public abstract Transaction withdraw(BigDecimal amount);

    protected final Transaction debit(BigDecimal amount) {
        BigDecimal value = positive(amount);
        if (balance.compareTo(value) < 0) throw new IllegalArgumentException("Insufficient funds.");
        balance = balance.subtract(value);
        return Transaction.create("Withdrawal", value, balance, "");
    }

    protected final Transaction credit(String type, BigDecimal amount, String note) {
        BigDecimal value = positive(amount);
        balance = balance.add(value);
        return Transaction.create(type, value, balance, note == null ? "" : note.trim());
    }

    private static BigDecimal positive(BigDecimal amount) {
        BigDecimal value = money(amount);
        if (value.signum() <= 0) throw new IllegalArgumentException("Enter an amount greater than zero.");
        return value;
    }

    private static BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Enter an amount in BWP with at most two decimal places.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }

    public String getAccountNumber() { return accountNumber; }
    public long getOwnerId() { return ownerId; }
    public BigDecimal getBalance() { return balance; }
    public String getBranch() { return branch; }
    public LocalDate getDateOpened() { return dateOpened; }
    public abstract String getAccountType();

    @Override public String toString() {
        return accountNumber + " | " + getAccountType() + " | BWP " + balance;
    }
}
