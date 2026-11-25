package dao;

import model.Company;
import model.Customer;
import model.Individual;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class CustomerDAO {
    private final SqliteDatabase database;
    public CustomerDAO(SqliteDatabase database) { this.database = database; }

    public long insert(Customer customer) throws SQLException {
        try (var connection = database.connect()) { return insert(connection, customer); }
    }

    long insert(Connection connection, Customer customer) throws SQLException {
        try (var statement = connection.prepareStatement("""
                    INSERT INTO customers(first_name,last_name,address,kind,company_name,phone,employed)
                    VALUES(?,?,?,?,?,?,?) RETURNING customer_id
                    """)) {
            bindDetails(statement, customer);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Customer ID unavailable.");
                return result.getLong(1);
            }
        }
    }

    public boolean update(Customer customer) throws SQLException {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("""
                    UPDATE customers SET first_name=?,last_name=?,address=?,kind=?,company_name=?,phone=?,employed=?
                    WHERE customer_id=? AND kind=?
                    """)) {
            bindDetails(statement, customer);
            statement.setLong(8, customer.getCustomerId());
            statement.setString(9, customer instanceof Company ? "COMPANY" : "INDIVIDUAL");
            return statement.executeUpdate() == 1;
        }
    }

    private void bindDetails(PreparedStatement statement, Customer customer) throws SQLException {
        Individual individual = customer instanceof Individual value ? value : null;
        statement.setString(1, individual == null ? "" : individual.getFirstName());
        statement.setString(2, individual == null ? "" : individual.getSurname());
        statement.setString(3, customer.getAddress());
        statement.setString(4, individual == null ? "COMPANY" : "INDIVIDUAL");
        statement.setString(5, individual == null ? customer.getName() : null);
        statement.setString(6, customer.getPhone());
        statement.setBoolean(7, individual != null && individual.isEmployed());
    }

    public boolean deleteUnlinked(long id) throws SQLException {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("""
                    DELETE FROM customers WHERE customer_id=?
                    AND NOT EXISTS (SELECT 1 FROM accounts WHERE customer_id=? )
                    AND NOT EXISTS (SELECT 1 FROM users WHERE customer_id=?)
                    """)) {
            for (int index = 1; index <= 3; index++) statement.setLong(index, id);
            return statement.executeUpdate() == 1;
        }
    }

    public Customer find(long id) throws SQLException {
        try (var connection = database.connect()) { return find(connection, id); }
    }

    Customer find(Connection connection, long id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM customers WHERE customer_id=?")) {
            statement.setLong(1, id);
            try (var row = statement.executeQuery()) { return row.next() ? model(row) : null; }
        }
    }

    public List<Customer> list() throws SQLException {
        try (var connection = database.connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT * FROM customers ORDER BY customer_id")) {
            List<Customer> customers = new ArrayList<>();
            while (rows.next()) customers.add(model(rows));
            return customers;
        }
    }

    private Customer model(ResultSet row) throws SQLException {
        long id = row.getLong("customer_id");
        String address = row.getString("address");
        String phone = row.getString("phone");
        if ("COMPANY".equals(row.getString("kind"))) {
            return new Company(id, row.getString("company_name"), address, phone);
        }
        return new Individual(id, row.getString("first_name"), row.getString("last_name"),
                address, phone, row.getBoolean("employed"));
    }
}
