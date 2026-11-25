package dao;

import model.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AccountDAO {
    private static final ZoneId BANK_ZONE = ZoneId.of("Africa/Gaborone");
    private final SqliteDatabase database;
    private final CustomerDAO customers;

    public record AccountRow(String number, String type, String customer, String branch, BigDecimal balance) {
        @Override public String toString() {
            return number + " | " + type + " | " + customer + " | BWP " + balance;
        }
    }

    public record TransactionRow(long id, String account, String type, BigDecimal amount,
                                 String postedAt, BigDecimal newBalance, String note) {
        @Override public String toString() {
            return postedAt + " | " + account + " | " + type + " | BWP " + amount
                    + " | Balance BWP " + newBalance + (note.isBlank() ? "" : " | " + note);
        }
    }

    public AccountDAO(SqliteDatabase database, CustomerDAO customers) {
        this.database = database;
        this.customers = customers;
    }

    public String open(long ownerId, String type, String branch, BigDecimal initialBalance, String employerName, String employerAddress)
            throws SQLException {
        return database.write(connection -> {
            Customer owner = customers.find(connection, ownerId);
            if (owner == null) throw new IllegalArgumentException("Customer does not exist.");
            String number = UUID.randomUUID().toString();
            LocalDate date = LocalDate.now(BANK_ZONE);
            Account account = switch (type) {
                case "Savings" -> new SavingsAccount(number, ownerId, branch, initialBalance, date);
                case "Investment" -> InvestmentAccount.open(number, ownerId, branch, initialBalance, date);
                case "Cheque" -> ChequeAccount.open(number, owner, branch, initialBalance, date,
                        saveEmployer(connection, employerName, employerAddress));
                default -> throw new IllegalArgumentException("Select a valid account type.");
            };
            Company employer = account instanceof ChequeAccount cheque ? cheque.getEmployer() : null;
            owner.addAccount(account);
            try (var statement = connection.prepareStatement("""
                    INSERT INTO accounts(account_number,customer_id,type,branch,balance_cents,created_date,
                                         employer_name,employer_address,employer_id)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """)) {
                statement.setString(1, number);
                statement.setLong(2, ownerId);
                statement.setString(3, account.getAccountType());
                statement.setString(4, account.getBranch());
                statement.setLong(5, cents(account.getBalance()));
                statement.setString(6, date.toString());
                statement.setString(7, employer == null ? null : employer.getName());
                statement.setString(8, employer == null ? null : employer.getAddress());
                statement.setObject(9, employer == null ? null : employer.getCustomerId());
                statement.executeUpdate();
            }
            if (initialBalance.signum() > 0) {
                record(connection, number, Transaction.create("Deposit", initialBalance, account.getBalance(),
                        "Opening deposit"));
            }
            return number;
        });
    }

    public List<AccountRow> list(Long owner) throws SQLException {
        try (var connection = database.connect(); var query = connection.prepareStatement("""
                SELECT a.*, CASE c.kind WHEN 'COMPANY' THEN c.company_name
                         ELSE c.first_name || ' ' || c.last_name END AS customer
                FROM accounts a JOIN customers c USING(customer_id)
                WHERE (? IS NULL OR a.customer_id=?) ORDER BY account_number
                """)) {
            query.setObject(1, owner);
            query.setObject(2, owner);
            try (var rows = query.executeQuery()) {
                List<AccountRow> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(new AccountRow(rows.getString("account_number"), rows.getString("type"),
                            rows.getString("customer"), rows.getString("branch"), money(rows.getLong("balance_cents"))));
                }
                return result;
            }
        }
    }

    public void post(String number, Long owner, BigDecimal amount, boolean withdrawal, String note)
            throws SQLException {
        database.write(connection -> {
            Account account = read(connection, number);
            if (owner != null && owner != account.getOwnerId()) throw new SecurityException("This account is not yours.");
            save(connection, account, withdrawal ? account.withdraw(amount) : account.deposit(amount, note));
            return null;
        });
    }

    public List<TransactionRow> history(String number, Long owner) throws SQLException {
        try (var connection = database.connect();
             var query = connection.prepareStatement("""
                     SELECT t.* FROM transactions t JOIN accounts a USING(account_number)
                     WHERE (? IS NULL OR t.account_number=?) AND (? IS NULL OR a.customer_id=?)
                     ORDER BY t.transaction_id DESC
                     """)) {
            query.setString(1, number);
            query.setString(2, number);
            query.setObject(3, owner);
            query.setObject(4, owner);
            try (var rows = query.executeQuery()) {
                List<TransactionRow> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(new TransactionRow(rows.getLong("transaction_id"), rows.getString("account_number"),
                            rows.getString("type"), money(rows.getLong("amount_cents")), rows.getString("posted_at"),
                            money(rows.getLong("new_balance_cents")), rows.getString("note")));
                }
                return result;
            }
        }
    }

    public List<String> pendingInterest(YearMonth month) throws SQLException {
        try (var connection = database.connect(); var query = connection.prepareStatement("""
                SELECT a.account_number FROM accounts a
                WHERE a.type IN ('Savings','Investment') AND a.created_date<=?
                AND NOT EXISTS (SELECT 1 FROM monthly_interest m WHERE m.account_number=a.account_number
                                AND m.month=? AND m.status='Paid')
                ORDER BY a.account_number
                """)) {
            query.setString(1, month.atEndOfMonth().toString());
            query.setString(2, month.toString());
            try (var rows = query.executeQuery()) {
                List<String> result = new ArrayList<>();
                while (rows.next()) result.add(rows.getString(1));
                return result;
            }
        }
    }

    public boolean applyInterest(String number, YearMonth month) throws SQLException {
        if (!month.isBefore(YearMonth.now(BANK_ZONE))) {
            throw new IllegalArgumentException("Interest is due only after month end.");
        }
        try {
            return database.write(connection -> {
                Account account = read(connection, number);
                if (!(account instanceof InterestBearing interest) || account.getDateOpened().isAfter(month.atEndOfMonth())) {
                    return false;
                }
                if (!setInterestStatus(connection, number, month, "Pending")) return false;
                setInterestStatus(connection, number, month, "Calculating");
                Instant cutoff = month.plusMonths(1).atDay(1).atStartOfDay(BANK_ZONE).toInstant();
                BigDecimal closing = closingBalance(connection, number, cutoff);
                BigDecimal amount = interest.calculateInterest(closing);
                if (amount.signum() > 0) save(connection, account, interest.payInterest(amount, month.toString()));
                setInterestStatus(connection, number, month, "Paid");
                return true;
            });
        } catch (SQLException | RuntimeException exception) {
            try {
                database.write(connection -> setInterestStatus(connection, number, month, "Failed"));
            } catch (SQLException recovery) {
                exception.addSuppressed(recovery);
            }
            throw exception;
        }
    }

    private boolean setInterestStatus(Connection connection, String number, YearMonth month, String status)
            throws SQLException {
        try (var update = connection.prepareStatement("""
                INSERT INTO monthly_interest(account_number,month,status) VALUES(?,?,?)
                ON CONFLICT(account_number,month) DO UPDATE SET status=excluded.status
                WHERE monthly_interest.status<>'Paid'
                """)) {
            update.setString(1, number);
            update.setString(2, month.toString());
            update.setString(3, status);
            return update.executeUpdate() == 1;
        }
    }

    private BigDecimal closingBalance(Connection connection, String number, Instant cutoff) throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT new_balance_cents FROM transactions WHERE account_number=?
                AND julianday(posted_at)<julianday(?) ORDER BY julianday(posted_at) DESC, transaction_id DESC LIMIT 1
                """)) {
            query.setString(1, number);
            query.setString(2, cutoff.toString());
            try (var row = query.executeQuery()) { return row.next() ? money(row.getLong(1)) : BigDecimal.ZERO; }
        }
    }

    private Account read(Connection connection, String number) throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM accounts WHERE account_number=?")) {
            query.setString(1, number);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new IllegalArgumentException("Account number not found.");
                long owner = row.getLong("customer_id");
                String branch = row.getString("branch");
                BigDecimal balance = money(row.getLong("balance_cents"));
                LocalDate date = LocalDate.parse(row.getString("created_date"));
                return switch (row.getString("type")) {
                    case "Savings" -> new SavingsAccount(number, owner, branch, balance, date);
                    case "Investment" -> InvestmentAccount.restore(number, owner, branch, balance, date);
                    case "Cheque" -> ChequeAccount.restore(number, owner, branch, balance, date, employer(row));
                    default -> throw new SQLException("Unknown account type.");
                };
            }
        }
    }

    private Company employer(ResultSet account) throws SQLException {
        long id = account.getLong("employer_id");
        return new Company(id, account.getString("employer_name"), account.getString("employer_address"), "Not supplied");
    }

    private Company saveEmployer(Connection connection, String name, String address) throws SQLException {
        String employer = Account.required(name, "Employer name");
        String location = Account.required(address, "Employer address");
        try (var insert = connection.prepareStatement("""
                INSERT INTO employer_companies(name,address) VALUES(?,?) ON CONFLICT(name,address) DO NOTHING
                """);
             var query = connection.prepareStatement("SELECT employer_id FROM employer_companies WHERE name=? AND address=?")) {
            insert.setString(1, employer);
            insert.setString(2, location);
            insert.executeUpdate();
            query.setString(1, employer);
            query.setString(2, location);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new SQLException("Employer could not be saved.");
                return new Company(row.getLong(1), employer, location, "Not supplied");
            }
        }
    }

    private void save(Connection connection, Account account, Transaction transaction) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE accounts SET balance_cents=? WHERE account_number=?")) {
            statement.setLong(1, cents(account.getBalance()));
            statement.setString(2, account.getAccountNumber());
            if (statement.executeUpdate() != 1) throw new SQLException("Account balance could not be saved.");
        }
        record(connection, account.getAccountNumber(), transaction);
    }

    private void record(Connection connection, String number, Transaction transaction) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO transactions(account_number,type,amount_cents,posted_at,new_balance_cents,note,reference_id)
                VALUES(?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, number);
            statement.setString(2, transaction.type());
            statement.setLong(3, cents(transaction.amount()));
            statement.setString(4, transaction.madeAt().toString());
            statement.setLong(5, cents(transaction.newBalance()));
            statement.setString(6, transaction.note());
            statement.setString(7, transaction.transactionId());
            statement.executeUpdate();
        }
    }

    private static long cents(BigDecimal amount) {
        try { return amount.movePointRight(2).longValueExact(); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("Amount exceeds the supported range.", exception); }
    }

    private static BigDecimal money(long cents) { return BigDecimal.valueOf(cents, 2); }
}
