package dao;

import model.Company;
import model.Customer;
import model.Individual;
import java.sql.SQLException;
import java.util.List;

/** Local assignment fixture. It creates customer records without creating logins. */
public final class PresentationCustomers {
    private PresentationCustomers() {}

    public static int insertMissing(SqliteDatabase database) throws SQLException {
        var customers = new CustomerDAO(database);
        List<Customer> sample = List.of(
                new Individual(0, "Kabelo", "Moleleki", "Gaborone", "70009001", true),
                new Individual(0, "Naledi", "Mothibi", "Francistown", "70009002", true),
                new Individual(0, "Tumisang", "Kebone", "Maun", "70009003", false),
                new Individual(0, "Boitumelo", "Lesedi", "Molepolole", "70009004", true),
                new Individual(0, "Onalenna", "Motsamai", "Kanye", "70009005", true),
                new Individual(0, "Lorato", "Mogorosi", "Palapye", "70009006", false),
                new Individual(0, "Kagiso", "Motswedi", "Serowe", "70009007", true),
                new Individual(0, "Lesego", "Tlhagale", "Lobatse", "70009008", false),
                new Company(0, "Okavango Office Supplies", "Maun", "70009009"),
                new Company(0, "Kgalagadi Print Studio", "Gaborone", "70009010")
        );

        return database.write(connection -> {
            int added = 0;
            try (var query = connection.prepareStatement("""
                    SELECT 1 FROM customers
                    WHERE phone=? AND CASE kind WHEN 'COMPANY' THEN company_name
                         ELSE first_name || ' ' || last_name END = ?
                    LIMIT 1
                    """)) {
                for (Customer customer : sample) {
                    query.setString(1, customer.getPhone());
                    query.setString(2, customer.getName());
                    boolean exists;
                    try (var result = query.executeQuery()) { exists = result.next(); }
                    if (!exists) {
                        customers.insert(connection, customer);
                        added++;
                    }
                }
            }
            return added;
        });
    }
}
