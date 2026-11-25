CREATE TABLE IF NOT EXISTS customers (
    customer_id INTEGER PRIMARY KEY,
    first_name TEXT NOT NULL DEFAULT '',
    last_name TEXT NOT NULL DEFAULT '',
    address TEXT NOT NULL,
    kind TEXT NOT NULL DEFAULT 'INDIVIDUAL' CHECK(kind IN ('INDIVIDUAL','COMPANY')),
    company_name TEXT,
    phone TEXT NOT NULL,
    employed INTEGER NOT NULL DEFAULT 0 CHECK(employed IN (0,1))
);

CREATE TABLE IF NOT EXISTS employer_companies (
    employer_id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    address TEXT NOT NULL,
    UNIQUE(name,address)
);

CREATE TABLE IF NOT EXISTS accounts (
    account_number TEXT PRIMARY KEY,
    customer_id INTEGER NOT NULL REFERENCES customers(customer_id),
    type TEXT NOT NULL CHECK(type IN ('Savings','Investment','Cheque')),
    branch TEXT NOT NULL,
    balance_cents INTEGER NOT NULL CHECK(balance_cents >= 0),
    created_date TEXT NOT NULL,
    employer_name TEXT,
    employer_address TEXT,
    employer_id INTEGER REFERENCES employer_companies(employer_id),
    CHECK(type <> 'Cheque' OR (employer_name IS NOT NULL AND employer_address IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id INTEGER PRIMARY KEY,
    account_number TEXT NOT NULL REFERENCES accounts(account_number),
    type TEXT NOT NULL CHECK(type IN ('Deposit','Withdrawal','Interest')),
    amount_cents INTEGER NOT NULL CHECK(amount_cents > 0),
    posted_at TEXT NOT NULL,
    new_balance_cents INTEGER NOT NULL CHECK(new_balance_cents >= 0),
    note TEXT NOT NULL DEFAULT '',
    reference_id TEXT UNIQUE
);

CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY,
    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('CUSTOMER','TELLER','ADMIN')),
    customer_id INTEGER UNIQUE REFERENCES customers(customer_id),
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)),
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK(failed_attempts BETWEEN 0 AND 3),
    CHECK((role = 'CUSTOMER') = (customer_id IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS monthly_interest (
    account_number TEXT NOT NULL REFERENCES accounts(account_number),
    month TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('Pending','Calculating','Paid','Failed')),
    PRIMARY KEY(account_number, month)
);

CREATE INDEX IF NOT EXISTS accounts_owner ON accounts(customer_id);
CREATE INDEX IF NOT EXISTS transactions_account ON transactions(account_number, transaction_id);
