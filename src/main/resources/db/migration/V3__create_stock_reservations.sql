CREATE TABLE stock_reservations (
    order_id UUID PRIMARY KEY,
    event_id UUID NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'REJECTED')),
    reason VARCHAR(30) NULL CHECK (reason IN (
        'OUT_OF_STOCK',
        'PRODUCT_NOT_FOUND',
        'PRODUCT_NOT_AVAILABLE',
        'INVALID_QUANTITY',
        'INVALID_ORDER'
    )),
    reserved_at TIMESTAMPTZ NULL,
    rejected_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT stock_reservations_result_check CHECK (
        (status = 'RESERVED' AND reason IS NULL
            AND reserved_at IS NOT NULL AND rejected_at IS NULL)
        OR
        (status = 'REJECTED' AND reason IS NOT NULL
            AND rejected_at IS NOT NULL AND reserved_at IS NULL)
    )
);

CREATE INDEX stock_reservations_event_id_idx
    ON stock_reservations (event_id);
