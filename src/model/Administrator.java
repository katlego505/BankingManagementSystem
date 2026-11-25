package model;

public record Administrator(String username) {
    public void validateNewUser(User.Role role, Long customerId) {
        if (role == null || role == User.Role.ADMIN) {
            throw new IllegalArgumentException("Select Teller or Customer.");
        }
        if ((role == User.Role.CUSTOMER) != (customerId != null)) {
            throw new IllegalArgumentException("A customer login must be linked to a registered customer.");
        }
    }

    public void validateDeactivation(User user) {
        if (user.role() == User.Role.ADMIN) throw new IllegalArgumentException("Select a teller or customer.");
    }
}
