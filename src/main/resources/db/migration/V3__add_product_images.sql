CREATE TABLE product_images (
    product_id UUID PRIMARY KEY REFERENCES products (id) ON DELETE CASCADE,
    asset_id UUID NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    bytes BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    alt VARCHAR(200) NOT NULL,
    uploader VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE product_image_uploads (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    asset_id UUID NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    bytes BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_image_uploads_expires_at ON product_image_uploads (expires_at);
