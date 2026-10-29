ALTER TABLE marketdata RENAME TO market_data;

ALTER TABLE market_data RENAME COLUMN instrumentid TO instrument_id;
ALTER TABLE market_data RENAME COLUMN previousclose TO previous_close;

ALTER TABLE orders RENAME COLUMN instrumentid TO instrument_id;
ALTER TABLE orders RENAME COLUMN userid TO user_id;

ALTER TABLE users RENAME COLUMN accountnumber TO account_number;

ALTER SEQUENCE marketdata_id_seq RENAME TO market_data_id_seq;
