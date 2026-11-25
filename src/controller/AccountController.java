package controller;

import dao.AccountDAO;
import model.Account;
import model.User;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public final class AccountController {
    private final AccountDAO accounts;
    private final LoginController login;

    public record AccountSummary(String number, String type, String branch, BigDecimal balance, String display) {
        @Override public String toString() { return display; }
    }

    public AccountController(AccountDAO accounts, LoginController login) {
        this.accounts = accounts;
        this.login = login;
    }

    public String open(Long selectedOwner, String type, String branch, String initial, String employer, String address)
            throws SQLException {
        User user = login.require(User.Role.CUSTOMER, User.Role.TELLER);
        Long owner = user.role() == User.Role.CUSTOMER ? user.customerId() : selectedOwner;
        if (owner == null) throw new IllegalArgumentException("Select an existing customer.");
        if (user.role() == User.Role.CUSTOMER && selectedOwner != null && !owner.equals(selectedOwner)) {
            throw new SecurityException("You can open accounts only for yourself.");
        }
        return accounts.open(owner, Account.required(type, "Account type"), branch, amount(initial), employer, address);
    }

    public List<AccountSummary> list() throws SQLException {
        User user = login.require(User.Role.CUSTOMER, User.Role.TELLER);
        return accounts.list(user.customerId()).stream()
                .map(account -> new AccountSummary(account.number(), account.type(), account.branch(),
                        account.balance(), account + " | " + account.branch())).toList();
    }

    public List<AccountSummary> listForCustomer(long customerId) throws SQLException {
        login.require(User.Role.TELLER);
        return accounts.list(customerId).stream()
                .map(account -> new AccountSummary(account.number(), account.type(), account.branch(),
                        account.balance(), account + " | " + account.branch())).toList();
    }

    static BigDecimal amount(String input) {
        BigDecimal value;
        try { value = new BigDecimal(input.strip()); }
        catch (NumberFormatException | NullPointerException exception) {
            throw new IllegalArgumentException("Enter an amount in BWP.");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Use at most two decimal places.");
        }
        return value;
    }
}
