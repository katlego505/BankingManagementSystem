package controller;

import dao.UserDAO;
import model.Account;
import model.User;
import java.sql.SQLException;
import java.util.Arrays;

public final class LoginController {
    private final UserDAO users;
    private User current;

    public LoginController(UserDAO users) { this.users = users; }

    public boolean needsSetup() throws SQLException { return users.needsAdministrator(); }

    public void setup(String username, String password) throws SQLException {
        users.bootstrap(Account.required(username, "Username"), password);
    }

    public User.Role login(String username, String password) throws SQLException {
        current = null;
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("Enter your password.");
        current = users.authenticate(Account.required(username, "Username"), password);
        if (current == null) throw new SecurityException("Sign-in failed. An account locks after three failed attempts.");
        return current.role();
    }

    public void logout() { current = null; }

    public String getCurrentUsername() {
        return current == null ? "" : current.username();
    }

    User require(User.Role... roles) throws SQLException {
        if (current == null) throw new SecurityException("Sign in first.");
        User stored = users.find(current.id());
        if (stored == null || !stored.active() || stored.failedAttempts() >= 3) {
            logout();
            throw new SecurityException("Your account is inactive or locked.");
        }
        if (!Arrays.asList(roles).contains(stored.role())) {
            throw new SecurityException("This function is unavailable for your role.");
        }
        return stored;
    }
}
