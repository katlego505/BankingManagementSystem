package service;

import dao.AccountDAO;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Scheduler implements AutoCloseable {
    private final AccountDAO accounts;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "monthly-interest");
        thread.setDaemon(true);
        return thread;
    });

    public Scheduler(AccountDAO accounts) { this.accounts = accounts; }

    public void start() {
        worker.scheduleWithFixedDelay(() -> {
            try { triggerMonthlyInterest(YearMonth.now(ZoneId.of("Africa/Gaborone")).minusMonths(1)); }
            catch (SQLException exception) { System.err.println("Monthly interest query failed: " + exception.getMessage()); }
        }, 0, 15, TimeUnit.MINUTES);
    }

    public int triggerMonthlyInterest(YearMonth month) throws SQLException {
        int count = 0;
        for (String number : accounts.pendingInterest(month)) {
            try { if (accounts.applyInterest(number, month)) count++; }
            catch (SQLException | RuntimeException exception) {
                System.err.println("Interest will be retried for " + number + ": " + exception.getMessage());
            }
        }
        return count;
    }

    @Override public void close() { worker.shutdownNow(); }
}
