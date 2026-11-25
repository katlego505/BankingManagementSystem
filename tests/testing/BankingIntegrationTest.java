package testing;

import controller.*;
import dao.*;
import gui.BankingView;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import model.*;
import service.Scheduler;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class BankingIntegrationTest {
    private static final String PASSWORD = "Testing-pass-2026";

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("kabzas-part-b-");
        Path file = directory.resolve("test.db");
        Platform.startup(() -> Platform.setImplicitExit(false));
        var test = new FutureTask<Void>(() -> {
            testFlows(file);
            return null;
        });
        try {
            Platform.runLater(test);
            test.get(60, TimeUnit.SECONDS);
            System.out.println("PASS: JavaFX sign-in and deposits, roles, lockout, customer CRUD, account rules, rollback, and monthly interest.");
        } finally {
            Platform.exit();
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static void testFlows(Path file) throws Exception {
        var db = new SqliteDatabase(file);
        var customerDAO = new CustomerDAO(db);
        var accountDAO = new AccountDAO(db, customerDAO);
        var userDAO = new UserDAO(db);
        var login = new LoginController(userDAO);
        var customers = new CustomerController(customerDAO, login);
        var accounts = new AccountController(accountDAO, login);
        var transactions = new TransactionController(accountDAO, login);
        var admin = new AdminController(userDAO, login);
        Stage stage = new Stage();

        try {
            Parent root = new BankingView(login, customers, accounts, transactions, admin).root();
            stage.setScene(new Scene(root, 980, 670));
            stage.show();
            signIn(root, "admin");
            check(!login.needsSetup(), "First administrator is stored");
            signIn(root, "admin");
            text(root, "new-username", "teller");
            text(root, "new-password", PASSWORD);
            click(root, "create-user");
            check(userDAO.findByName("teller") != null, "Admin created teller through JavaFX");
            click(root, "sign-out");

            signIn(root, "teller");
            text(root, "first-name", "Sam");
            text(root, "surname", "Customer");
            text(root, "address", "Gaborone");
            text(root, "phone", "70000001");
            click(root, "add-customer");
            long customer = customers.list().getFirst().id();
            long employer = customers.register(true, "", "", "Test Company", "Francistown", "70000002", false);
            long employee = customers.register(false, "Employed", "Tester", "", "Main", "70000003", true);
            long temporary = customers.register(false, "Temporary", "Tester", "", "Main", "70000004", false);
            customers.update(temporary, false, "Temporary", "Tester", "", "Updated", "70000005", false);
            check(customerDAO.find(temporary).getPhone().equals("70000005"), "Updated telephone persisted");
            check(customers.removeUnlinked(temporary) && customerDAO.find(temporary) == null, "Customer delete verified");
            rejects(IllegalArgumentException.class,
                    () -> accounts.open(customer, "Cheque", "Main", "0", "Test Company", "Francistown"));
            rejects(IllegalArgumentException.class,
                    () -> accounts.open(employer, "Cheque", "Main", "0", "Test Company", "Francistown"));
            rejects(IllegalArgumentException.class,
                    () -> accounts.open(customer, "Investment", "Main", "499.99", "", ""));

            layout(root);
            ((ComboBox<?>) root.lookup("#account-owner")).getSelectionModel().selectFirst();
            select(root, "account-type", "Investment");
            text(root, "branch", "Gaborone");
            text(root, "initial-balance", "500.00");
            click(root, "open-account");
            String investment = accountDAO.list(customer).getFirst().number();
            check(!customers.removeUnlinked(customer), "Cannot delete an account owner");
            rejects(IllegalArgumentException.class, () -> accounts.open(employee, "Cheque", "Main", "0", "", ""));
            String cheque = accounts.open(employee, "Cheque", "Main", "200", "Employer Outside Bank", "Gaborone");
            String savings = accounts.open(employee, "Savings", "Main", "1000", "", "");
            rejects(IllegalArgumentException.class, () -> transactions.withdraw(savings, "1"));
            rejects(IllegalArgumentException.class, () -> transactions.withdraw(cheque, "-50"));
            rejects(IllegalArgumentException.class, () -> transactions.deposit(cheque, "NaN", ""));
            click(root, "sign-out");

            signIn(root, "admin");
            admin.create("sam", PASSWORD, User.Role.CUSTOMER, customer);
            admin.create("locked", PASSWORD, User.Role.CUSTOMER, employee);
            admin.create("disabled", PASSWORD, User.Role.TELLER, null);
            admin.setAccess(userDAO.findByName("disabled").id(), false);
            click(root, "sign-out");
            for (int attempt = 0; attempt < 3; attempt++) {
                rejects(SecurityException.class, () -> login.login("locked", "incorrect"));
            }
            var reopenedUsers = new UserDAO(new SqliteDatabase(file));
            check(reopenedUsers.findByName("locked").failedAttempts() == 3
                    && reopenedUsers.authenticate("locked", PASSWORD) == null, "Lockout persists across reopen");
            rejects(SecurityException.class, () -> login.login("disabled", PASSWORD));
            rejects(SecurityException.class, () -> customers.list());

            signIn(root, "sam");
            rejects(SecurityException.class, () -> transactions.deposit(cheque, "1", ""));
            rejects(SecurityException.class, customers::list);
            rejects(SecurityException.class, admin::list);
            check(accounts.list().size() == 1, "Customer sees only owned accounts");
            layout(root);
            ((ComboBox<?>) root.lookup("#transaction-account")).getSelectionModel().selectFirst();
            text(root, "transaction-amount", "25.50");
            text(root, "transaction-note", "Test deposit");
            click(root, "deposit");
            text(root, "transaction-amount", "30.50");
            click(root, "withdraw");
            var reopened = new AccountDAO(new SqliteDatabase(file), customerDAO);
            check(reopened.list(customer).getFirst().balance().equals(new BigDecimal("495.00")), "Balance persisted");
            var history = reopened.history(investment, customer);
            check(history.size() == 3 && history.getFirst().newBalance().equals(new BigDecimal("495.00"))
                    && history.get(1).note().equals("Test deposit"), "Transaction history and resulting balances persisted");
            check(transactions.history(cheque).isEmpty(), "No other customer's history");

            sql(file, """
                    CREATE TRIGGER fail_history BEFORE INSERT ON transactions
                    BEGIN SELECT RAISE(ABORT, 'simulated failure'); END
                    """);
            rejects(SQLException.class, () -> transactions.deposit(investment, "10", ""));
            check(reopened.list(customer).getFirst().balance().equals(new BigDecimal("495.00"))
                    && reopened.history(investment, customer).size() == 3, "Failed history write rolls back balance");

            YearMonth past = YearMonth.now(ZoneId.of("Africa/Gaborone")).minusMonths(1);
            String date = past.atDay(1).toString();
            sql(file, "UPDATE accounts SET created_date='" + date + "'");
            sql(file, "UPDATE transactions SET posted_at='" + date + "T12:00:00Z'");
            rejects(SQLException.class, () -> reopened.applyInterest(investment, past));
            sql(file, "DROP TRIGGER fail_history");
            try (var scheduler = new Scheduler(reopened)) {
                check(scheduler.triggerMonthlyInterest(past) == 2, "Failed interest retried and eligible accounts credited");
                check(scheduler.triggerMonthlyInterest(past) == 0, "Interest does not post twice");
            }
            check(reopened.history(investment, customer).getFirst().amount().equals(new BigDecimal("24.75")),
                    "Investment earns five percent");
            check(reopened.history(savings, employee).getFirst().amount().equals(new BigDecimal("0.50")),
                    "Savings earns 0.05 percent");
            check(reopened.history(cheque, employee).size() == 1, "Cheque earns no interest");

            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT password_hash FROM users")) {
                while (rows.next()) check(!rows.getString(1).contains(PASSWORD), "Password hashes only");
            }
        } finally {
            stage.close();
        }
    }

    private static void sql(Path file, String sql) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static void signIn(Parent root, String username) {
        layout(root);
        if ("sam".equals(username)) ((ToggleButton) root.lookup("#access-customer")).fire();
        text(root, "username", username);
        text(root, "password", PASSWORD);
        click(root, "sign-in");
        layout(root);
    }

    @SuppressWarnings("unchecked")
    private static void select(Parent root, String id, String value) {
        ((ComboBox<String>) root.lookup("#" + id)).setValue(value);
    }

    private static void layout(Parent root) { root.applyCss(); root.layout(); }
    private static void text(Parent root, String id, String value) {
        ((TextField) root.lookup("#" + id)).setText(value);
    }

    private static void click(Parent root, String id) { ((Button) root.lookup("#" + id)).fire(); }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private static void rejects(Class<? extends Exception> type, Action action) throws Exception {
        try { action.run(); }
        catch (Exception e) {
            if (type.isInstance(e)) return;
            throw e;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    @FunctionalInterface
    private interface Action { void run() throws Exception; }
}
