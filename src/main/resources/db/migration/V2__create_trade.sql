CREATE TABLE trade (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES portfolio(id) ON DELETE RESTRICT,
    sequence_number BIGINT NOT NULL CHECK (sequence_number > 0),
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    ticker VARCHAR(15) NOT NULL CHECK (ticker ~ '^[A-Z][A-Z0-9.-]{0,14}$'),
    shares NUMERIC(19,8) NOT NULL CHECK (shares > 0),
    price_per_share NUMERIC(19,6) NOT NULL CHECK (price_per_share > 0),
    fees NUMERIC(19,6) NOT NULL CHECK (fees >= 0),
    executed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    idempotency_key UUID NOT NULL,
    CONSTRAINT trade_portfolio_sequence UNIQUE (portfolio_id, sequence_number),
    CONSTRAINT trade_portfolio_idempotency UNIQUE (portfolio_id, idempotency_key)
);
CREATE INDEX trade_portfolio_ticker_sequence_idx ON trade (portfolio_id, ticker, sequence_number);
