-- 1. Identifiers as BIGINT (columns and sequences)
ALTER TABLE users
    ALTER COLUMN id TYPE BIGINT;
ALTER TABLE instruments
    ALTER COLUMN id TYPE BIGINT;
ALTER TABLE orders
    ALTER COLUMN id TYPE BIGINT,
    ALTER COLUMN user_id TYPE BIGINT,
    ALTER COLUMN instrument_id TYPE BIGINT;
ALTER TABLE market_data
    ALTER COLUMN id TYPE BIGINT,
    ALTER COLUMN instrument_id TYPE BIGINT;

ALTER SEQUENCE users_id_seq AS BIGINT;
ALTER SEQUENCE instruments_id_seq AS BIGINT;
ALTER SEQUENCE orders_id_seq AS BIGINT;
ALTER SEQUENCE market_data_id_seq AS BIGINT;

-- 2. Order size as NUMERIC with business checks
ALTER TABLE orders
    ALTER COLUMN size TYPE NUMERIC(18, 2);

ALTER TABLE orders
    ADD CONSTRAINT orders_size_positive CHECK (size > 0),
    ADD CONSTRAINT orders_size_whole_except_cash
        CHECK (side IN ('CASH_IN', 'CASH_OUT') OR size = trunc(size));

-- 3. Normalize instruments.type to English enum values
UPDATE instruments
SET type = 'STOCK'
WHERE type = 'ACCIONES';

UPDATE instruments
SET type = 'CURRENCY'
WHERE type = 'MONEDA';

-- 4. NOT NULL where the seed and ADRs require it
ALTER TABLE users
    ALTER COLUMN email SET NOT NULL;

ALTER TABLE instruments
    ALTER COLUMN ticker SET NOT NULL,
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN type SET NOT NULL;

ALTER TABLE orders
    ALTER COLUMN instrument_id SET NOT NULL,
    ALTER COLUMN user_id SET NOT NULL,
    ALTER COLUMN size SET NOT NULL,
    ALTER COLUMN type SET NOT NULL,
    ALTER COLUMN side SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN datetime SET NOT NULL;

ALTER TABLE market_data
    ALTER COLUMN instrument_id SET NOT NULL,
    ALTER COLUMN date SET NOT NULL,
    ALTER COLUMN close SET NOT NULL,
    ALTER COLUMN previous_close SET NOT NULL;

-- 5. Enumerated value checks
ALTER TABLE instruments
    ADD CONSTRAINT instruments_type_known CHECK (type IN ('STOCK', 'CURRENCY'));

ALTER TABLE orders
    ADD CONSTRAINT orders_side_known CHECK (side IN ('BUY', 'SELL', 'CASH_IN', 'CASH_OUT')),
    ADD CONSTRAINT orders_type_known CHECK (type IN ('MARKET', 'LIMIT')),
    ADD CONSTRAINT orders_status_known CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED'));

-- 6. Uniqueness
ALTER TABLE instruments
    ADD CONSTRAINT instruments_ticker_key UNIQUE (ticker);

CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));

CREATE UNIQUE INDEX market_data_instrument_id_date_key
    ON market_data (instrument_id, date DESC);

-- 7. Rejection reason
ALTER TABLE orders
    ADD COLUMN rejection_reason VARCHAR(32);

ALTER TABLE orders
    ADD CONSTRAINT orders_rejection_reason_known
        CHECK (
            rejection_reason IS NULL
            OR rejection_reason IN (
                'INSUFFICIENT_CASH',
                'INSUFFICIENT_HOLDINGS',
                'NO_MARKET_PRICE'
            )
        ),
    ADD CONSTRAINT orders_rejection_reason_only_when_rejected
        CHECK (rejection_reason IS NULL OR status = 'REJECTED');

-- 8. Idempotency columns
ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(64),
    ADD COLUMN request_hash CHAR(64),
    ADD CONSTRAINT orders_idempotency_key_unique UNIQUE (user_id, idempotency_key),
    ADD CONSTRAINT orders_idempotency_complete
        CHECK ((idempotency_key IS NULL) = (request_hash IS NULL));

-- 9. Chronological reconstruction index
CREATE INDEX orders_user_id_datetime_id_idx ON orders (user_id, datetime, id);

-- 10. Accent-insensitive search support
CREATE EXTENSION IF NOT EXISTS unaccent;
