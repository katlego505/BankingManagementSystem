package model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class ChequeAccount extends Account {
    private final Company employer;

    private ChequeAccount(String number, long ownerId, String branch, BigDecimal balance,
                          LocalDate opened, Company employer) {
        super(number, ownerId, branch, balance, opened);
        this.employer = Objects.requireNonNull(employer);
    }

    public static ChequeAccount open(String number, Customer owner, String branch, BigDecimal balance,
                                    LocalDate opened, Company employer) {
        if (!(owner instanceof Individual individual) || !individual.isEmployed()) {
            throw new IllegalArgumentException("A Cheque account requires an employed individual.");
        }
        if (employer == null) throw new IllegalArgumentException("Select the employer company.");
        return restore(number, owner.getCustomerId(), branch, balance, opened, employer);
    }

    public static ChequeAccount restore(String number, long ownerId, String branch, BigDecimal balance,
                                       LocalDate opened, Company employer) {
        return new ChequeAccount(number, ownerId, branch, balance, opened, employer);
    }

    @Override public Transaction withdraw(BigDecimal amount) { return debit(amount); }
    @Override public String getAccountType() { return "Cheque"; }
    public Company getEmployer() { return employer; }
}
