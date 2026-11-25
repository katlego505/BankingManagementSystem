package gui;

import controller.*;
import dao.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import service.Scheduler;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.ZoneId;

public final class BankingSystemGUI extends Application {
    private Scheduler scheduler;

    @Override
    public void start(Stage stage) throws Exception {
        var database = new SqliteDatabase(Path.of(System.getProperty("bank.database", "data/banking.db")));
        var customers = new CustomerDAO(database);
        var accounts = new AccountDAO(database, customers);
        var users = new UserDAO(database);
        var login = new LoginController(users);
        scheduler = new Scheduler(accounts);
        var view = new BankingView(login, new CustomerController(customers, login),
                new AccountController(accounts, login), new TransactionController(accounts, login),
                new AdminController(users, login),
                () -> scheduler.triggerMonthlyInterest(YearMonth.now(ZoneId.of("Africa/Gaborone")).minusMonths(1)));
        scheduler.start();

        stage.setTitle("Banking Management System");
        stage.setScene(new Scene(view.root(), 980, 670));
        stage.setMinWidth(760);
        stage.setMinHeight(550);
        stage.show();
    }

    @Override public void stop() { if (scheduler != null) scheduler.close(); }
}
