CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users (id),
    cash NUMERIC(18, 2) NOT NULL,
    reserved_cash NUMERIC(18, 2) NOT NULL CHECK (reserved_cash >= 0),
    projection_status VARCHAR(10) NOT NULL CHECK (projection_status IN ('READY', 'PENDING'))
);

CREATE TABLE positions (
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    instrument_id BIGINT NOT NULL REFERENCES instruments (id),
    quantity BIGINT NOT NULL,
    reserved_quantity BIGINT NOT NULL CHECK (reserved_quantity >= 0),
    average_cost NUMERIC(18, 6),
    realized_pnl NUMERIC(18, 2) NOT NULL,
    PRIMARY KEY (account_id, instrument_id)
);

INSERT INTO accounts (account_number, user_id, cash, reserved_cash, projection_status)
SELECT account_number, id, 0, 0, 'PENDING'
FROM users
ORDER BY id;

ALTER TABLE users
    DROP COLUMN account_number;
