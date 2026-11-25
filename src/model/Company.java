package model;

public final class Company extends Customer {
    private final String companyName;

    public Company(long id, String name, String address, String phone) {
        super(id, address, phone);
        companyName = Account.required(name, "Company name");
    }

    @Override public String getName() { return companyName; }
}
