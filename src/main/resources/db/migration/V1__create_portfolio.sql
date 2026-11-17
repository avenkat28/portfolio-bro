CREATE TABLE portfolio (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT portfolio_name_valid CHECK (char_length(btrim(name)) BETWEEN 1 AND 100 AND name = btrim(name)),
    CONSTRAINT portfolio_currency_usd CHECK (currency = 'USD')
);
CREATE INDEX portfolio_created_id_idx ON portfolio (created_at, id);
