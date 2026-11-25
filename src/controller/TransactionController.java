package controller;

import dao.AccountDAO;
import model.Account;
import model.User;
import java.sql.SQLException;
import java.util.List;

public final class TransactionController {
    private final AccountDAO accounts;
    private final LoginController login;

    public TransactionController(AccountDAO accounts, LoginController login) {
        this.accounts = accounts;
        this.login = login;
    }

    public void deposit(String number, String amount, String note) throws SQLException {
        User user = login.require(User.Role.CUSTOMER, User.Role.TELLER);
        accounts.post(Account.required(number, "Account"), user.customerId(),
                AccountController.amount(amount), false, note);
    }

    public void withdraw(String number, String amount) throws SQLException {
        User user = login.require(User.Role.CUSTOMER, User.Role.TELLER);
        accounts.post(Account.required(number, "Account"), user.customerId(),
                AccountController.amount(amount), true, "");
    }

    public List<String> history(String number) throws SQLException {
        User user = login.require(User.Role.CUSTOMER, User.Role.TELLER);
        return accounts.history(number, user.customerId()).stream().map(Object::toString).toList();
    }
}
