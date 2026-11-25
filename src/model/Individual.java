package model;

public final class Individual extends Customer {
    private final String firstName;
    private final String surname;
    private final boolean employed;

    public Individual(long id, String firstName, String surname, String address, String phone, boolean employed) {
        super(id, address, phone);
        this.firstName = Account.required(firstName, "First name");
        this.surname = Account.required(surname, "Surname");
        this.employed = employed;
    }

    public String getFirstName() { return firstName; }
    public String getSurname() { return surname; }
    public boolean isEmployed() { return employed; }
    @Override public String getName() { return firstName + " " + surname; }
}
