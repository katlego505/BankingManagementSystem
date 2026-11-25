package dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public final class SqliteDatabase {
    private final Path file;

    public SqliteDatabase(Path file) throws SQLException, IOException {
        this.file = file.toAbsolutePath();
        Files.createDirectories(this.file.getParent());
        try (var input = getClass().getResourceAsStream("/schema.sql")) {
            if (input == null) throw new IOException("Missing schema.sql.");
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            write(connection -> {
                try (var statement = connection.createStatement()) {
                    for (String sql : schema.split(";")) if (!sql.isBlank()) statement.execute(sql);
                    migrate(connection, statement);
                }
                return null;
            });
        }
    }

    private void migrate(Connection connection, Statement statement) throws SQLException {
        addColumn(connection, statement, "customers", "kind", "TEXT NOT NULL DEFAULT 'INDIVIDUAL'");
        addColumn(connection, statement, "customers", "company_name", "TEXT");
        addColumn(connection, statement, "customers", "phone", "TEXT NOT NULL DEFAULT 'Not supplied'");
        addColumn(connection, statement, "customers", "employed", "INTEGER NOT NULL DEFAULT 0");
        if (addColumn(connection, statement, "accounts", "employer_id",
                "INTEGER REFERENCES employer_companies(employer_id)")) {
            statement.executeUpdate("""
                    INSERT OR IGNORE INTO employer_companies(name,address)
                    SELECT DISTINCT employer_name, employer_address FROM accounts WHERE type='Cheque'
                    """);
            statement.executeUpdate("""
                    UPDATE accounts SET employer_id=(SELECT employer_id FROM employer_companies
                        WHERE name=accounts.employer_name AND address=accounts.employer_address) WHERE type='Cheque'
                    """);
        }
        addColumn(connection, statement, "transactions", "reference_id", "TEXT");
        statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS transaction_reference ON transactions(reference_id)");
        addColumn(connection, statement, "transactions", "note", "TEXT NOT NULL DEFAULT ''");
        if (addColumn(connection, statement, "transactions", "new_balance_cents", "INTEGER")) {
            statement.executeUpdate("""
                    UPDATE transactions AS entry SET new_balance_cents =
                        (SELECT balance_cents FROM accounts WHERE account_number = entry.account_number)
                        - COALESCE((SELECT SUM(CASE next.type WHEN 'Withdrawal' THEN -next.amount_cents
                                    ELSE next.amount_cents END) FROM transactions AS next
                             WHERE next.account_number = entry.account_number
                               AND next.transaction_id > entry.transaction_id), 0)
                    """);
        }
    }

    private boolean addColumn(Connection connection, Statement statement, String table, String column,
                              String definition) throws SQLException {
        try (var info = connection.createStatement(); var rows = info.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rows.next()) if (column.equals(rows.getString("name"))) return false;
        }
        statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        return true;
    }

    Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    <T> T write(Work<T> work) throws SQLException {
        try (var connection = connect(); var transaction = connection.createStatement()) {
            transaction.execute("BEGIN IMMEDIATE");
            try {
                T result = work.run(connection);
                transaction.execute("COMMIT");
                return result;
            } catch (SQLException | RuntimeException exception) {
                try { transaction.execute("ROLLBACK"); }
                catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
        }
    }

    @FunctionalInterface
    interface Work<T> { T run(Connection connection) throws SQLException; }
}
