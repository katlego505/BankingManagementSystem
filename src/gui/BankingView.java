package gui;

import controller.*;
import controller.CustomerController.CustomerSummary;
import controller.AccountController.AccountSummary;
import controller.AdminController.UserSummary;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.ObjectProperty;
import model.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class BankingView {
    private final BorderPane root = new BorderPane();
    private final Label status = new Label();
    private final HBox header = new HBox(14);
    private final LoginController login;
    private final CustomerController customers;
    private final AccountController accounts;
    private final TransactionController transactions;
    private final AdminController admin;
    private final InterestRunner interestRunner;

    public BankingView(LoginController login, CustomerController customers, AccountController accounts,
                       TransactionController transactions, AdminController admin) {
        this(login, customers, accounts, transactions, admin, () -> 0);
    }

    public BankingView(LoginController login, CustomerController customers, AccountController accounts,
                       TransactionController transactions, AdminController admin, InterestRunner interestRunner) {
        this.login = login;
        this.customers = customers;
        this.accounts = accounts;
        this.transactions = transactions;
        this.admin = admin;
        this.interestRunner = interestRunner;
        header.setPadding(new Insets(17));
        header.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #dbe4ed transparent;");
        status.setId("status");
        status.setWrapText(true);
        BorderPane.setMargin(status, new Insets(11, 18, 11, 18));
        root.setTop(header);
        root.setBottom(status);
        run(this::showLogin);
    }

    public Parent root() { return root; }

    private void showLogin() throws Exception {
        boolean setup = login.needsSetup();
        heading(setup ? "Set up administrator" : "Sign in", false);
        ToggleGroup access = new ToggleGroup();
        ToggleButton customerAccess = new ToggleButton("Customer");
        ToggleButton staffAccess = new ToggleButton("Bank Teller / Admin");
        customerAccess.setToggleGroup(access);
        staffAccess.setToggleGroup(access);
        staffAccess.setSelected(true);
        customerAccess.setId("access-customer");
        staffAccess.setId("access-staff");
        HBox accessChoice = new HBox(8, customerAccess, staffAccess);
        accessChoice.setPadding(new Insets(5));
        accessChoice.setStyle("-fx-background-color:#eef3f8;-fx-background-radius:12;");
        styleChoice(customerAccess, false);
        styleChoice(staffAccess, true);
        customerAccess.setOnAction(e -> { styleChoice(customerAccess, true); styleChoice(staffAccess, false); });
        staffAccess.setOnAction(e -> { styleChoice(customerAccess, false); styleChoice(staffAccess, true); });
        var username = field("username", "Username");
        var password = password("password");
        var action = button("sign-in", setup ? "Create administrator" : "Sign in", () -> {
            if (setup) {
                login.setup(username.getText(), password.getText());
                showLogin();
                status.setText("Administrator created. Sign in to continue.");
            } else {
                User.Role role = login.login(username.getText(), password.getText());
                boolean customerSelected = access.getSelectedToggle() == customerAccess;
                if (customerSelected != (role == User.Role.CUSTOMER)) {
                    login.logout();
                    throw new SecurityException("Choose the sign-in type that matches your account.");
                }
                showDashboard(role);
            }
        });
        action.setDefaultButton(true);
        Label title = new Label("Banking Management System");
        title.setStyle("-fx-font-size:28px;-fx-font-weight:bold;-fx-text-fill:#10213c;");
        Label subtitle = new Label(setup ? "Create your secure administrator account" : "Secure Botswana Banking System");
        subtitle.setStyle("-fx-text-fill:#64748b;-fx-font-size:14px;");
        if (!setup) {
            action.setText("Sign In");
            action.setMaxWidth(Double.MAX_VALUE);
            action.setPrefHeight(48);
        }
        VBox form = panel(title, subtitle,
                setup ? new Label("Choose a username and a password of at least eight characters.") : accessChoice,
                username, password, action);
        form.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        form.setMaxWidth(500);
        form.setPadding(new Insets(34));
        form.setStyle("-fx-background-color:white;-fx-background-radius:24;-fx-border-color:#e2e8f0;-fx-border-radius:24;");
        StackPane center = new StackPane(form);
        center.setPadding(new Insets(28));
        center.setStyle("-fx-background-color:linear-gradient(to bottom right,#17233a,#29415a);");
        root.setCenter(center);
    }

    private void heading(String context, boolean signedIn) {
        Label title = new Label("Banking Management System");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#10213c;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().setAll(title, spacer, new Label(context));
        if (signedIn) header.getChildren().add(button("sign-out", "Sign out", () -> {
            login.logout();
            showLogin();
        }));
    }

    private void showDashboard(User.Role role) throws Exception {
        heading(switch (role) {
            case ADMIN -> "Administrator";
            case TELLER -> "Bank teller";
            case CUSTOMER -> "Customer";
        }, true);
        if (role == User.Role.ADMIN) {
            root.setCenter(adminDashboard());
            return;
        }
        boolean teller = role == User.Role.TELLER;
        if (teller) {
            root.setTop(null);
            root.setCenter(tellerDashboard());
            return;
        }
        if (!teller) {
            root.setCenter(customerDashboard());
            return;
        }
        TabPane tabs = new TabPane();
        tabs.getTabs().add(new Tab("Overview", tellerOverview()));
        tabs.getTabs().add(new Tab("Customers", customerPanel()));
        Tab accountTab = new Tab("Accounts");
        Tab transactionTab = new Tab("Transactions");
        tabs.getTabs().addAll(accountTab, transactionTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> run(() -> {
            if (selected == accountTab) accountTab.setContent(accountsPanel(teller));
            if (selected == transactionTab) transactionTab.setContent(transactionsPanel());
        }));
        root.setCenter(tabs);
    }

    private Parent customerDashboard() throws Exception {
        List<AccountSummary> saved = accounts.list();
        BigDecimal total = saved.stream().map(AccountSummary::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        Label totalLabel = new Label("TOTAL NET WORTH");
        totalLabel.setStyle("-fx-text-fill:#65e2b4;-fx-font-weight:bold;-fx-font-size:13px;");
        Label balance = new Label("BWP " + money(total));
        balance.setStyle("-fx-text-fill:white;-fx-font-size:36px;-fx-font-weight:bold;");
        Label count = new Label("Across " + saved.size() + " active account" + (saved.size() == 1 ? "" : "s"));
        count.setStyle("-fx-text-fill:#c2d2e6;-fx-font-size:14px;");
        TitledPane openAccount = new TitledPane("Open a new account", accountsPanel(false));
        openAccount.setExpanded(false);
        Button open = button("customer-open-account", "+ Open New Account", () -> openAccount.setExpanded(true));
        VBox summary = new VBox(10, totalLabel, balance, count, new Separator(), open);
        summary.setPadding(new Insets(24));
        summary.setStyle("-fx-background-color:linear-gradient(to right,#101a30,#063d36);-fx-background-radius:22;");

        Label accountsTitle = sectionTitle("Active Accounts");
        FlowPane cards = new FlowPane(16, 16);
        for (AccountSummary account : saved) cards.getChildren().add(accountCard(account));
        if (saved.isEmpty()) cards.getChildren().add(new Label("No accounts are linked to your profile yet."));
        Label historyTitle = sectionTitle("Transaction History");
        VBox history = panel(historyTitle,
                new Label("Select an account to review activity, deposit, or withdraw."), transactionsPanel());
        history.setStyle("-fx-background-color:white;-fx-background-radius:18;-fx-border-color:#dfe7f0;-fx-border-radius:18;");
        VBox content = new VBox(20, pageTitle("Financial Overview", "Your bank accounts, balances, and recent activity."),
                summary, accountsTitle, cards, openAccount, history);
        content.setPadding(new Insets(26));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:#f1f5f9;-fx-background-color:#f1f5f9;");
        return scroll;
    }

    private Parent tellerOverview() throws Exception {
        List<AccountSummary> saved = accounts.list();
        BigDecimal total = saved.stream().map(AccountSummary::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        VBox cards = new VBox(14,
                metricCard("Accounts", String.valueOf(saved.size()), "All customer accounts"),
                metricCard("Total balance", "BWP " + money(total), "Current bank balances"));
        TextArea records = output();
        records.setId("teller-account-list");
        records.setText(lines(saved));
        VBox content = new VBox(18, pageTitle("Teller Dashboard", "Customer accounts and daily banking activity."),
                new HBox(16, cards), sectionTitle("Accounts"), records);
        content.setPadding(new Insets(24));
        VBox.setVgrow(records, Priority.ALWAYS);
        return content;
    }

    private Parent adminDashboard() throws Exception {
        List<UserSummary> saved = admin.list();
        long tellers = saved.stream().filter(user -> user.role() == User.Role.TELLER).count();
        long customersCount = saved.stream().filter(user -> user.role() == User.Role.CUSTOMER).count();
        long active = saved.stream().filter(UserSummary::active).count();
        FlowPane metrics = new FlowPane(14, 14,
                metricCard("Total users", String.valueOf(saved.size()), "Registered logins"),
                metricCard("Customer logins", String.valueOf(customersCount), "Online banking users"),
                metricCard("Active tellers", String.valueOf(tellers), "Teller accounts"),
                metricCard("Active users", String.valueOf(active), "Enabled access"));
        VBox management = (VBox) adminPanel();
        management.setStyle("-fx-background-color:white;-fx-background-radius:18;-fx-border-color:#dfe7f0;-fx-border-radius:18;");
        VBox content = new VBox(18, pageTitle("Admin Dashboard", "Manage access and review bank system activity."),
                metrics, sectionTitle("User management"), management);
        content.setPadding(new Insets(24));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:#f1f5f9;-fx-background-color:#f1f5f9;");
        return scroll;
    }

    private Parent tellerDashboard() throws Exception {
        List<CustomerSummary> allCustomers = new ArrayList<>(customers.list());
        List<AccountSummary> allAccounts = accounts.list();
        BigDecimal deposits = allAccounts.stream().map(AccountSummary::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        ObjectProperty<CustomerSummary> selectedCustomer = new SimpleObjectProperty<>();

        Label bankName = new Label("Banking Management System");
        bankName.setStyle("-fx-text-fill:white;-fx-font-size:23px;-fx-font-weight:bold;");
        Label terminal = new Label("BWP TELLER TERMINAL");
        terminal.setStyle("-fx-background-color:#fbbf24;-fx-text-fill:#10213c;-fx-font-weight:bold;"+
                "-fx-background-radius:14;-fx-padding:5 12 5 12;");
        HBox brand = new HBox(12, bankName, terminal);
        brand.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label branch = new Label("Francistown Main Branch · Counter #04 · Session Active");
        branch.setStyle("-fx-text-fill:#c7d6e6;");
        Label onDuty = new Label("Teller on Duty  " + login.getCurrentUsername());
        onDuty.setStyle("-fx-text-fill:white;-fx-font-weight:bold;");
        Label interestTotal = new Label("BWP 0.00");
        interestTotal.setStyle("-fx-text-fill:#bbf7d0;-fx-font-weight:bold;");
        Button runInterest = new Button("Run Monthly Interest");
        runInterest.setStyle("-fx-background-color:#10b981;-fx-text-fill:#062b28;-fx-font-weight:bold;"+
                "-fx-background-radius:12;-fx-padding:11 16 11 16;");
        runInterest.setOnAction(e -> run(() -> {
            int credited = interestRunner.run();
            interestTotal.setText(credited + " accounts credited");
            status.setText("Monthly interest applied. Accounts credited: " + credited);
        }));
        Button signOut = button("sign-out", "Sign out", () -> { login.logout(); showLogin(); });
        HBox dutyLine = new HBox(16, onDuty, runInterest, interestTotal, signOut);
        dutyLine.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox masthead = new VBox(8, brand, branch, dutyLine);
        masthead.setPadding(new Insets(20, 26, 20, 26));
        masthead.setStyle("-fx-background-color:#0b2c49;");

        FlowPane metrics = new FlowPane(14, 14,
                metricCard("Registered Customers", String.valueOf(allCustomers.size()), "Individuals & organisations"),
                metricCard("Active Accounts", String.valueOf(allAccounts.size()), "Savings · Investment · Cheque"),
                metricCard("Total Deposits Held", "BWP " + money(deposits), "Across all bank accounts"),
                metricCard("Interest Credited (Session)", interestTotal.getText(), "Investment 5.00% · Savings 0.05%"));
        interestTotal.textProperty().addListener((obs, oldValue, newValue) -> {
            Label metricValue = (Label) ((VBox) metrics.getChildren().get(3)).getChildren().get(1);
            metricValue.setText(newValue.equals("BWP 0.00") ? newValue : newValue);
        });

        TextField search = field("customer-search", "e.g. Mpho, Selebi, Lobatse...");
        VBox results = new VBox(8);
        ScrollPane resultScroll = new ScrollPane(results);
        resultScroll.setFitToWidth(true);
        resultScroll.setPrefHeight(220);
        Label selectedClient = new Label("Select a customer to view accounts and continue.");
        selectedClient.setStyle("-fx-text-fill:#64748b;");
        ComboBox<CustomerSummary> accountOwner = combo("account-owner", "Select customer");
        accountOwner.getItems().setAll(allCustomers);
        accountOwner.valueProperty().addListener((obs, oldValue, value) -> {
            if (value != null && selectedCustomer.get() != value) selectedCustomer.set(value);
        });
        FlowPane accountCards = new FlowPane(12, 12);
        Label selectedAccountsTitle = sectionTitle("Selected Customer Accounts");
        ComboBox<AccountSummary> targetAccount = combo("transaction-account", "Choose account");
        ComboBox<String> operation = combo("transaction-operation", "Operation type");
        operation.getItems().setAll("Deposit", "Withdrawal");
        operation.setValue("Deposit");
        TextField amount = field("transaction-amount", "0.00");
        TextField note = field("transaction-note", "Optional teller reference / narration");
        TextArea ledger = output();
        ledger.setId("transaction-list");
        ledger.setPrefHeight(220);

        Runnable refreshSelected = () -> {
            CustomerSummary customer = selectedCustomer.get();
            if (customer == null) {
                selectedClient.setText("Select a customer to view accounts and continue.");
                accountCards.getChildren().clear();
                targetAccount.getItems().clear();
                ledger.clear();
                return;
            }
            accountOwner.setValue(customer);
            selectedClient.setText(customer.name() + " · CUS-" + customer.id() + " · " + customer.address());
            try {
                List<AccountSummary> customerAccounts = accounts.listForCustomer(customer.id());
                accountCards.getChildren().clear();
                customerAccounts.forEach(account -> accountCards.getChildren().add(accountCard(account)));
                if (customerAccounts.isEmpty()) accountCards.getChildren().add(new Label("No active accounts yet."));
                String previous = targetAccount.getValue() == null ? null : targetAccount.getValue().number();
                targetAccount.getItems().setAll(customerAccounts);
                if (previous != null) targetAccount.getItems().stream().filter(a -> a.number().equals(previous))
                        .findFirst().ifPresent(targetAccount::setValue);
                if (targetAccount.getValue() == null && !customerAccounts.isEmpty()) targetAccount.setValue(customerAccounts.getFirst());
                refreshLedger(targetAccount.getValue(), ledger);
            } catch (Exception exception) { status.setText(exception.getMessage()); }
        };
        selectedCustomer.addListener((obs, previous, selected) -> refreshSelected.run());
        targetAccount.valueProperty().addListener((obs, previous, selected) -> refreshLedger(selected, ledger));

        Runnable refreshResults = () -> {
            String query = search.getText() == null ? "" : search.getText().strip().toLowerCase();
            results.getChildren().clear();
            for (CustomerSummary customer : allCustomers) {
                String text = (customer.name() + " " + customer.id() + " " + customer.address()).toLowerCase();
                if (!query.isEmpty() && !text.contains(query)) continue;
                int accountCount = 0;
                try { accountCount = accounts.listForCustomer(customer.id()).size(); }
                catch (Exception ignored) { }
                Button pick = new Button(customer.name() + "\nCUS-" + customer.id() + " · " +
                        (customer.company() ? "Company / Organisation" : "Individual") + "\n" + customer.address() +
                        "                                      " + accountCount + " accounts");
                pick.setMaxWidth(Double.MAX_VALUE);
                pick.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                pick.setStyle("-fx-background-color:" + (selectedCustomer.get() == customer ? "#eef6ff" : "white") +
                        ";-fx-border-color:" + (selectedCustomer.get() == customer ? "#0b4b78" : "#dbe4ed") +
                        ";-fx-border-radius:12;-fx-background-radius:12;-fx-padding:12;-fx-text-fill:#10213c;");
                pick.setOnAction(e -> selectedCustomer.set(customer));
                results.getChildren().add(pick);
            }
        };
        search.textProperty().addListener((obs, oldValue, newValue) -> refreshResults.run());
        selectedCustomer.addListener((obs, oldValue, newValue) -> refreshResults.run());
        refreshResults.run();
        if (!allCustomers.isEmpty()) selectedCustomer.set(allCustomers.getFirst());

        VBox lookup = sectionCard(sectionTitle("1. Customer Lookup"),
                new Label("Search by name, customer ID, or address"), search, resultScroll, selectedClient);

        ComboBox<String> category = combo("customer-kind", "Customer Category");
        category.getItems().setAll("Individual", "Company / Organisation");
        category.setValue("Individual");
        TextField first = field("first-name", "First name / entity name");
        TextField surname = field("surname", "Surname / registration suffix");
        TextField company = field("company-name", "Company name");
        TextField address = field("address", "Residential / postal address");
        TextField phone = field("phone", "Phone number");
        CheckBox employed = new CheckBox("Employed");
        first.disableProperty().bind(category.valueProperty().isEqualTo("Company / Organisation"));
        surname.disableProperty().bind(first.disableProperty());
        employed.disableProperty().bind(first.disableProperty());
        company.disableProperty().bind(first.disableProperty().not());
        Button register = button("add-customer", "+ Create Customer Profile", () -> {
            boolean companyType = "Company / Organisation".equals(category.getValue());
            long id = customers.register(companyType, first.getText(), surname.getText(), company.getText(),
                    address.getText(), phone.getText(), employed.isSelected());
            CustomerSummary created = customers.list().stream().filter(c -> c.id() == id).findFirst().orElseThrow();
            allCustomers.add(created);
            accountOwner.getItems().setAll(allCustomers);
            selectedCustomer.set(created);
            search.clear();
            refreshResults.run();
            status.setText("Customer profile created. ID " + id);
        });
        register.setMaxWidth(Double.MAX_VALUE);
        VBox registration = sectionCard(sectionTitle("2. Register New Customer"), category,
                row(first, surname, company), row(address, phone, employed), register);

        ComboBox<String> type = combo("account-type", "Account product");
        type.getItems().setAll("Savings", "Investment", "Cheque");
        type.setValue("Savings");
        TextField employer = field("employer", "Employer company name");
        TextField employerAddress = field("employer-address", "Employer address");
        FlowPane employerFields = row(employer, employerAddress);
        employerFields.visibleProperty().bind(type.valueProperty().isEqualTo("Cheque"));
        employerFields.managedProperty().bind(employerFields.visibleProperty());
        TextField branchField = field("branch", "Branch");
        branchField.setText("Francistown Main");
        TextField initial = field("initial-balance", "Initial deposit amount");
        initial.setText("0.00");
        Button openAccount = button("open-account", "Open Account for Selected Customer", () -> {
            CustomerSummary owner = accountOwner.getValue();
            String number = accounts.open(id(owner), type.getValue(), branchField.getText(), initial.getText(),
                    employer.getText(), employerAddress.getText());
            status.setText("Account opened. Number " + number);
            refreshSelected.run();
        });
        openAccount.setMaxWidth(Double.MAX_VALUE);
        VBox openSection = sectionCard(sectionTitle("3. Open New Account"), accountOwner, type,
                employerFields, row(branchField, initial), openAccount);

        Button execute = button("execute-transaction", "Execute Transaction", () -> {
            String accountNumber = number(targetAccount);
            if ("Deposit".equals(operation.getValue())) {
                transactions.deposit(accountNumber, amount.getText(), note.getText());
            } else transactions.withdraw(accountNumber, amount.getText());
            amount.clear();
            note.clear();
            refreshSelected.run();
            status.setText("Transaction completed successfully.");
        });
        Label rules = new Label("Choose the selected customer's account and operation. Savings accounts do not allow withdrawals.");
        rules.setWrapText(true);
        rules.setStyle("-fx-text-fill:#47647d;-fx-background-color:#f1f6fa;-fx-padding:10;-fx-background-radius:9;");
        VBox execution = sectionCard(sectionTitle("4. Counter Transaction Execution"), rules,
                row(targetAccount, operation, amount, execute), note);
        VBox ledgerSection = sectionCard(sectionTitle("Teller Transaction & Interest Ledger"),
                new Label("Deposits, withdrawals, account openings, and monthly interest for the selected customer."), ledger);

        VBox content = new VBox(16, metrics, lookup, registration, openSection,
                selectedAccountsTitle, accountCards, execution, ledgerSection);
        content.setPadding(new Insets(20, 26, 28, 26));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:#f1f5f9;-fx-background-color:#f1f5f9;");
        VBox dashboard = new VBox(masthead, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return dashboard;
    }

    private void refreshLedger(AccountSummary selected, TextArea ledger) {
        if (selected == null) { ledger.setText("Select an account to view its ledger."); return; }
        try { ledger.setText(lines(transactions.history(selected.number()))); }
        catch (Exception exception) { ledger.setText(exception.getMessage()); }
    }

    private static VBox sectionCard(javafx.scene.Node... nodes) {
        VBox card = panel(nodes);
        card.setStyle("-fx-background-color:white;-fx-background-radius:18;-fx-border-color:#e0e8f0;-fx-border-radius:18;");
        return card;
    }

    private static VBox accountCard(AccountSummary account) {
        Label kind = new Label(account.type() + " Account");
        kind.setStyle("-fx-text-fill:#087c67;-fx-font-weight:bold;");
        Label details = new Label(account.number() + " • " + account.branch());
        details.setStyle("-fx-text-fill:#7b8ba2;");
        Label balance = new Label("BWP " + money(account.balance()));
        balance.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-text-fill:#10213c;");
        VBox card = panel(kind, details, balance, new Separator());
        card.setPrefWidth(330);
        card.setStyle("-fx-background-color:white;-fx-background-radius:18;-fx-border-color:#dfe7f0;-fx-border-radius:18;");
        return card;
    }

    private static VBox metricCard(String heading, String value, String note) {
        Label label = new Label(heading);
        label.setStyle("-fx-text-fill:#64748b;-fx-font-weight:bold;");
        Label amount = new Label(value);
        amount.setStyle("-fx-font-size:25px;-fx-font-weight:bold;-fx-text-fill:#10213c;");
        Label caption = new Label(note);
        caption.setStyle("-fx-text-fill:#8291a6;");
        VBox card = panel(label, amount, caption);
        card.setMinWidth(180);
        card.setStyle("-fx-background-color:white;-fx-background-radius:16;-fx-border-color:#dfe7f0;-fx-border-radius:16;");
        return card;
    }

    private static VBox pageTitle(String heading, String caption) {
        Label title = sectionTitle(heading);
        Label subtitle = new Label(caption);
        subtitle.setStyle("-fx-text-fill:#64748b;-fx-font-size:14px;");
        return new VBox(5, title, subtitle);
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#10213c;");
        return label;
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void styleChoice(ToggleButton button, boolean selected) {
        button.setMaxWidth(Double.MAX_VALUE);
        button.setStyle(selected
                ? "-fx-background-color:white;-fx-text-fill:#10213c;-fx-font-weight:bold;-fx-background-radius:10;-fx-pref-height:40;"
                : "-fx-background-color:transparent;-fx-text-fill:#52627a;-fx-background-radius:10;-fx-pref-height:40;");
    }

    private Parent customerPanel() throws Exception {
        ComboBox<CustomerSummary> saved = combo("saved-customer", "Select a customer to edit");
        saved.getItems().setAll(customers.list());
        ComboBox<String> kind = combo("customer-kind", "Customer type");
        kind.getItems().setAll("Individual", "Company");
        kind.setValue("Individual");
        TextField first = field("first-name", "First name");
        TextField surname = field("surname", "Surname");
        TextField company = field("company-name", "Company name");
        TextField address = field("address", "Address");
        TextField phone = field("phone", "Phone");
        CheckBox employed = new CheckBox("Employed");
        employed.setId("employed");

        first.disableProperty().bind(kind.valueProperty().isEqualTo("Company"));
        surname.disableProperty().bind(first.disableProperty());
        employed.disableProperty().bind(first.disableProperty());
        company.disableProperty().bind(first.disableProperty().not());
        TextArea records = output();
        Action refresh = () -> {
            List<CustomerSummary> list = customers.list();
            saved.getItems().setAll(list);
            records.setText(lines(list.stream().map(row -> row + " | " + row.address() + " | " + row.phone()).toList()));
        };
        saved.valueProperty().addListener((observable, previous, selected) -> {
            if (selected == null) return;
            kind.setValue(selected.company() ? "Company" : "Individual");
            first.setText(selected.first());
            surname.setText(selected.surname());
            company.setText(selected.company() ? selected.name() : "");
            address.setText(selected.address());
            phone.setText(selected.phone());
            employed.setSelected(selected.employed());
        });

        Button save = button("add-customer", "Register customer", () -> {
            long id = customers.register("Company".equals(kind.getValue()), first.getText(), surname.getText(),
                    company.getText(), address.getText(), phone.getText(), employed.isSelected());
            refresh.run();
            status.setText("Customer saved. ID " + id);
        });
        Button update = button("update-customer", "Update selected customer", () -> {
            CustomerSummary selected = saved.getValue();
            if (selected == null) throw new IllegalArgumentException("Select a customer to update.");
            customers.update(selected.id(), "Company".equals(kind.getValue()), first.getText(), surname.getText(),
                    company.getText(), address.getText(), phone.getText(), employed.isSelected());
            refresh.run();
            status.setText("Customer details updated.");
        });
        refresh.run();
        return grow(panel(row(saved, kind), row(first, surname, company), row(address, phone, employed),
                row(save, update), new Label("Saved customers"), records), records);
    }

    private Parent accountsPanel(boolean teller) throws Exception {
        ComboBox<CustomerSummary> owner = combo("account-owner", "Select customer");
        if (teller) owner.getItems().setAll(customers.list());
        ComboBox<String> type = combo("account-type", "Account type");
        type.getItems().setAll("Savings", "Investment", "Cheque");
        type.setValue("Savings");
        TextField employer = field("employer", "Employer company name");
        TextField employerAddress = field("employer-address", "Employer address");
        FlowPane employerDetails = row(employer, employerAddress);
        employerDetails.visibleProperty().bind(type.valueProperty().isEqualTo("Cheque"));
        employerDetails.managedProperty().bind(employerDetails.visibleProperty());
        TextField branch = field("branch", "Branch");
        TextField opening = field("initial-balance", "Opening balance in BWP");
        opening.setText("0.00");
        TextArea records = output();
        records.setId("account-list");
        Action refresh = () -> records.setText(lines(accounts.list()));
        refresh.run();
        Button save = button("open-account", "Open account", () -> {
            String number = accounts.open(id(owner.getValue()), type.getValue(), branch.getText(), opening.getText(),
                    employer.getText(), employerAddress.getText());
            refresh.run();
            status.setText("Account opened. Number " + number);
        });
        FlowPane choices = teller ? row(owner, type) : row(type);
        return grow(panel(choices, employerDetails, row(branch, opening, save), new Label("Account balances"), records), records);
    }

    private Parent transactionsPanel() throws Exception {
        ComboBox<AccountSummary> account = combo("transaction-account", "Choose an account");
        account.setMaxWidth(850);
        account.getItems().setAll(accounts.list());
        TextField amount = field("transaction-amount", "Amount in BWP");
        TextField note = field("transaction-note", "Optional deposit note");
        TextArea records = output();
        records.setId("transaction-list");
        Action refresh = () -> records.setText(lines(transactions.history(
                account.getValue() == null ? null : account.getValue().number())));
        account.valueProperty().addListener((observable, previous, selected) -> run(refresh));
        Button deposit = button("deposit", "Deposit", () -> {
            transactions.deposit(number(account), amount.getText(), note.getText());
            refresh.run();
            status.setText("Deposit saved.");
        });
        Button withdraw = button("withdraw", "Withdraw", () -> {
            transactions.withdraw(number(account), amount.getText());
            refresh.run();
            status.setText("Withdrawal saved.");
        });
        Button refreshButton = button("refresh-history", "Refresh history", refresh);
        refresh.run();
        return grow(panel(account, row(amount, note, deposit, withdraw, refreshButton),
                new Label("History with resulting balances (UTC)"), records), records);
    }

    private Parent adminPanel() throws Exception {
        TextField username = field("new-username", "New username");
        PasswordField password = password("new-password");
        ComboBox<User.Role> role = combo("new-role", "Role");
        role.getItems().setAll(User.Role.TELLER, User.Role.CUSTOMER);
        role.setValue(User.Role.TELLER);
        ComboBox<CustomerSummary> customer = combo("new-customer", "Select registered customer");
        customer.getItems().setAll(customers.list());
        customer.disableProperty().bind(role.valueProperty().isNotEqualTo(User.Role.CUSTOMER));
        ComboBox<UserSummary> user = combo("admin-user", "Select existing user");
        TextArea records = output();
        Action refresh = () -> {
            var saved = admin.list();
            user.getItems().setAll(saved);
            records.setText(lines(saved));
        };
        Button create = button("create-user", "Create login", () -> {
            admin.create(username.getText(), password.getText(), role.getValue(),
                    role.getValue() == User.Role.CUSTOMER ? id(customer.getValue()) : null);
            username.clear();
            password.clear();
            refresh.run();
            status.setText("Login created.");
        });
        Button deactivate = button("deactivate-user", "Deactivate", () -> {
            admin.setAccess(userId(user), false);
            refresh.run();
            status.setText("User deactivated.");
        });
        Button activate = button("activate-user", "Activate", () -> {
            admin.setAccess(userId(user), true);
            refresh.run();
            status.setText("User activated.");
        });
        Button unlock = button("unlock-user", "Unlock", () -> {
            admin.unlock(userId(user));
            refresh.run();
            status.setText("User unlocked.");
        });
        refresh.run();
        return grow(panel(row(username, password, role, customer, create),
                row(user, deactivate, activate, unlock), new Label("Saved users"), records), records);
    }

    private static Long id(CustomerSummary customer) { return customer == null ? null : customer.id(); }
    private static long userId(ComboBox<UserSummary> users) {
        if (users.getValue() == null) throw new IllegalArgumentException("Select a user.");
        return users.getValue().id();
    }

    private static String number(ComboBox<AccountSummary> accounts) {
        if (accounts.getValue() == null) throw new IllegalArgumentException("Select an account.");
        return accounts.getValue().number();
    }

    private static String lines(List<?> rows) {
        return rows.isEmpty() ? "No saved records."
                : String.join(System.lineSeparator(), rows.stream().map(Object::toString).toList());
    }

    private static VBox panel(javafx.scene.Node... nodes) {
        VBox result = new VBox(12, nodes);
        result.setPadding(new Insets(18));
        return result;
    }

    private static VBox grow(VBox panel, TextArea records) {
        VBox.setVgrow(records, Priority.ALWAYS);
        return panel;
    }

    private static FlowPane row(javafx.scene.Node... nodes) { return new FlowPane(8, 8, nodes); }

    private static TextField field(String id, String prompt) {
        TextField field = new TextField();
        field.setId(id);
        field.setPromptText(prompt);
        return field;
    }

    private static PasswordField password(String id) {
        PasswordField field = new PasswordField();
        field.setId(id);
        field.setPromptText("Password");
        return field;
    }

    private static <T> ComboBox<T> combo(String id, String prompt) {
        ComboBox<T> result = new ComboBox<>();
        result.setId(id);
        result.setPromptText(prompt);
        return result;
    }

    private static TextArea output() {
        TextArea area = new TextArea();
        area.setEditable(false);
        return area;
    }

    private Button button(String id, String label, Action action) {
        Button button = new Button(label);
        button.setId(id);
        button.setOnAction(event -> run(action));
        return button;
    }

    private void run(Action action) {
        try {
            status.setText("");
            action.run();
        } catch (Exception exception) {
            status.setText(exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface Action { void run() throws Exception; }

    @FunctionalInterface
    public interface InterestRunner { int run() throws Exception; }
}
