package model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Transaction(String transactionId, String type, BigDecimal amount,
                          Instant madeAt, BigDecimal newBalance, String note) {
    public static Transaction create(String type, BigDecimal amount, BigDecimal balance, String note) {
        return new Transaction(UUID.randomUUID().toString(), type, amount, Instant.now(), balance, note);
    }
}
