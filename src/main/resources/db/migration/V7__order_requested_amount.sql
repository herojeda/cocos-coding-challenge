ALTER TABLE orders
    ADD COLUMN amount NUMERIC(18, 2),
    ALTER COLUMN size DROP NOT NULL,
    ADD CONSTRAINT orders_amount_positive CHECK (amount IS NULL OR amount > 0),
    ADD CONSTRAINT orders_amount_only_for_trades CHECK (amount IS NULL OR side IN ('BUY', 'SELL')),
    ADD CONSTRAINT orders_size_or_unpriced_amount CHECK (
        size IS NOT NULL
        OR (amount IS NOT NULL AND status = 'REJECTED' AND rejection_reason = 'NO_MARKET_PRICE'));
