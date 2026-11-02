ALTER TABLE market_data
    ADD CONSTRAINT market_data_prices_positive CHECK (close > 0 AND previous_close > 0);
