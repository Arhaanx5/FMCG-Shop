CREATE TABLE hsn_category_mapping (
    id UUID PRIMARY KEY,
    category_key VARCHAR(150) NOT NULL UNIQUE,
    hsn_code VARCHAR(20) NOT NULL,
    updated_by UUID DEFAULT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
