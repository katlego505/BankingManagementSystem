package model;

public record BankTeller(String username) {
    public Customer registerIndividual(String first, String surname, String address, String phone, boolean employed) {
        return new Individual(0, first, surname, address, phone, employed);
    }

    public Customer registerCompany(String name, String address, String phone) {
        return new Company(0, name, address, phone);
    }
}
