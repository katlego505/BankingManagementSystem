package controller;

import dao.UserDAO;
import model.*;
import java.sql.SQLException;
import java.util.List;

public final class AdminController {
    private final UserDAO users;
    private final LoginController login;

    public record UserSummary(long id, String username, User.Role role, boolean active, int failedAttempts) {
        @Override public String toString() {
            return username + " | " + role + " | " + (!active ? "Inactive" : failedAttempts >= 3 ? "Locked" : "Active");
        }
    }

    public AdminController(UserDAO users, LoginController login) {
        this.users = users;
        this.login = login;
    }

    public List<UserSummary> list() throws SQLException {
        login.require(User.Role.ADMIN);
        return users.list().stream().map(u ->
                new UserSummary(u.id(), u.username(), u.role(), u.active(), u.failedAttempts())).toList();
    }

    public void create(String username, String password, User.Role role, Long customerId) throws SQLException {
        var admin = new Administrator(login.require(User.Role.ADMIN).username());
        admin.validateNewUser(role, customerId);
        users.create(Account.required(username, "Username"), password, role, customerId);
    }

    public void setAccess(long target, boolean enabled) throws SQLException {
        var admin = new Administrator(login.require(User.Role.ADMIN).username());
        User user = users.find(target);
        if (user == null) throw new IllegalArgumentException("User does not exist.");
        admin.validateDeactivation(user);
        users.setAccess(target, enabled);
    }

    public void unlock(long target) throws SQLException {
        login.require(User.Role.ADMIN);
        users.unlock(target);
    }
}
