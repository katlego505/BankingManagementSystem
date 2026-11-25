package model;

import java.util.ArrayList;
import java.util.List;

public abstract class Customer {
    private final long customerId;
    private final String address;
    private final String phone;
    private final List<Account> accounts = new ArrayList<>();

    protected Customer(long customerId, String address, String phone) {
        this.customerId = customerId;
        this.address = Account.required(address, "Address");
        this.phone = Account.required(phone, "Phone");
    }

    public long getCustomerId() { return customerId; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }

    public void addAccount(Account account) {
        if (account.getOwnerId() != customerId) {
            throw new IllegalArgumentException("Account belongs to another customer.");
        }
        accounts.add(account);
    }

    public List<Account> getAccounts() { return List.copyOf(accounts); }
    public abstract String getName();
}
