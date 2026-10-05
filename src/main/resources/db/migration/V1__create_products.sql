CREATE TABLE products (
    id UUID PRIMARY KEY,
    owner_account_id UUID NULL,
    name VARCHAR(200) NOT NULL,
    price NUMERIC(19, 2) NOT NULL CHECK (price >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency IN ('ARS', 'USD')),
    stock INTEGER NULL CHECK (stock >= 0),
    stage VARCHAR(20) NOT NULL DEFAULT 'draft' CHECK (stage IN ('draft', 'published')),
    deleted_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX products_owner_account_id_idx
    ON products (owner_account_id) WHERE deleted_at IS NULL;

CREATE TABLE product_images (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id),
    object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX product_images_product_id_idx ON product_images (product_id);
