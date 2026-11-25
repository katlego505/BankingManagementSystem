package model;

public record User(long id, String username, Role role, Long customerId, boolean active, int failedAttempts) {
    public enum Role { CUSTOMER, TELLER, ADMIN }
}
