package controller;

import dao.CustomerDAO;
import model.*;
import java.sql.SQLException;
import java.util.List;

public final class CustomerController {
    private final CustomerDAO customers;
    private final LoginController login;

    public record CustomerSummary(long id, String name, String first, String surname, String address,
                                  String phone, boolean company, boolean employed) {
        @Override public String toString() { return id + " | " + name; }

        static CustomerSummary from(Customer customer) {
            Individual individual = customer instanceof Individual value ? value : null;
            return new CustomerSummary(customer.getCustomerId(), customer.getName(),
                    individual == null ? "" : individual.getFirstName(),
                    individual == null ? "" : individual.getSurname(), customer.getAddress(),
                    customer.getPhone(), individual == null, individual != null && individual.isEmployed());
        }
    }

    public CustomerController(CustomerDAO customers, LoginController login) {
        this.customers = customers;
        this.login = login;
    }

    public long register(boolean company, String first, String surname, String companyName,
                         String address, String phone, boolean employed) throws SQLException {
        var teller = new BankTeller(login.require(User.Role.TELLER).username());
        return customers.insert(company ? teller.registerCompany(companyName, address, phone)
                : teller.registerIndividual(first, surname, address, phone, employed));
    }

    public void update(long id, boolean company, String first, String surname, String companyName,
                       String address, String phone, boolean employed) throws SQLException {
        login.require(User.Role.TELLER);
        Customer customer = company ? new Company(id, companyName, address, phone)
                : new Individual(id, first, surname, address, phone, employed);
        if (!customers.update(customer)) throw new IllegalArgumentException("Existing customer and type must match.");
    }

    public boolean removeUnlinked(long id) throws SQLException {
        login.require(User.Role.TELLER);
        return customers.deleteUnlinked(id);
    }

    public List<CustomerSummary> list() throws SQLException {
        login.require(User.Role.TELLER, User.Role.ADMIN);
        return customers.list().stream().map(CustomerSummary::from).toList();
    }

}
