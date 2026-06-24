CREATE UNIQUE INDEX idx_product_name_lower ON products(LOWER(TRIM(name)));
