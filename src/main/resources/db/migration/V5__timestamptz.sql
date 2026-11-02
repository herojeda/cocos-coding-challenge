ALTER TABLE orders
    ALTER COLUMN datetime TYPE TIMESTAMPTZ
    USING datetime AT TIME ZONE 'America/Argentina/Buenos_Aires';
