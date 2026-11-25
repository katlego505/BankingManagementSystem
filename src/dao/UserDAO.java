package dao;

import model.User;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class UserDAO {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 210_000;
    private final SqliteDatabase database;

    public UserDAO(SqliteDatabase database) { this.database = database; }

    public boolean needsAdministrator() throws SQLException {
        try (var connection = database.connect()) { return needsAdministrator(connection); }
    }

    private boolean needsAdministrator(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT 1 FROM users LIMIT 1")) {
            return !rows.next();
        }
    }

    public void bootstrap(String username, String password) throws SQLException {
        String hash = hash(password);
        database.write(connection -> {
            if (!needsAdministrator(connection)) throw new SecurityException("Administrator setup already completed.");
            insert(connection, username, hash, User.Role.ADMIN, null);
            return null;
        });
    }

    public void create(String username, String password, User.Role role, Long customerId) throws SQLException {
        try (var connection = database.connect()) { insert(connection, username, hash(password), role, customerId); }
    }

    private void insert(Connection connection, String username, String hash, User.Role role, Long customerId)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO users(username,password_hash,role,customer_id) VALUES(?,?,?,?)
                """)) {
            statement.setString(1, username);
            statement.setString(2, hash);
            statement.setString(3, role.name());
            statement.setObject(4, customerId);
            statement.executeUpdate();
        }
    }

    public User authenticate(String username, String password) throws SQLException {
        return database.write(connection -> {
            User user;
            String saved;
            try (var statement = connection.prepareStatement("SELECT * FROM users WHERE username=?")) {
                statement.setString(1, username);
                try (var row = statement.executeQuery()) {
                    if (!row.next()) return null;
                    user = model(row);
                    saved = row.getString("password_hash");
                }
            }
            if (!user.active() || user.failedAttempts() >= 3) return null;
            String[] fields = saved.split(":");
            boolean valid = MessageDigest.isEqual(Base64.getDecoder().decode(fields[1]),
                    derive(password, Base64.getDecoder().decode(fields[0])));
            try (var statement = connection.prepareStatement("UPDATE users SET failed_attempts=? WHERE user_id=?")) {
                statement.setInt(1, valid ? 0 : user.failedAttempts() + 1);
                statement.setLong(2, user.id());
                statement.executeUpdate();
            }
            return valid ? new User(user.id(), user.username(), user.role(), user.customerId(), true, 0) : null;
        });
    }

    public User find(long id) throws SQLException {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("SELECT * FROM users WHERE user_id=?")) {
            statement.setLong(1, id);
            try (var row = statement.executeQuery()) { return row.next() ? model(row) : null; }
        }
    }

    public User findByName(String username) throws SQLException {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("SELECT * FROM users WHERE username=?")) {
            statement.setString(1, username);
            try (var row = statement.executeQuery()) { return row.next() ? model(row) : null; }
        }
    }

    public List<User> list() throws SQLException {
        try (var connection = database.connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT * FROM users ORDER BY username")) {
            List<User> result = new ArrayList<>();
            while (rows.next()) result.add(model(rows));
            return result;
        }
    }

    public void setAccess(long target, boolean enabled) throws SQLException {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("""
                    UPDATE users SET active=?, failed_attempts=0 WHERE user_id=? AND role<>'ADMIN'
                    """)) {
            statement.setBoolean(1, enabled);
            statement.setLong(2, target);
            if (statement.executeUpdate() != 1) throw new IllegalArgumentException("Select a teller or customer.");
        }
    }

    public void unlock(long target) throws SQLException {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("UPDATE users SET failed_attempts=0 WHERE user_id=? AND active=1")) {
            statement.setLong(1, target);
            if (statement.executeUpdate() != 1) throw new IllegalArgumentException("Select an active user.");
        }
    }

    private static User model(ResultSet row) throws SQLException {
        long customerId = row.getLong("customer_id");
        Long owner = row.wasNull() ? null : customerId;
        return new User(row.getLong("user_id"), row.getString("username"),
                User.Role.valueOf(row.getString("role")), owner, row.getBoolean("active"),
                row.getInt("failed_attempts"));
    }

    private static String hash(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Use a password with at least eight characters.");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        var encoder = Base64.getEncoder();
        return encoder.encodeToString(salt) + ":" + encoder.encodeToString(derive(password, salt));
    }

    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable.", exception);
        } finally {
            spec.clearPassword();
        }
    }
}
