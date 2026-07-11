CREATE TABLE shop_profile (
    id UUID PRIMARY KEY,
    company_name VARCHAR(150) NOT NULL,
    gstin VARCHAR(15) NOT NULL,
    fssai VARCHAR(20) DEFAULT NULL,
    phone VARCHAR(15) DEFAULT NULL,
    address TEXT DEFAULT NULL,
    state_code VARCHAR(2) NOT NULL,
    state_name VARCHAR(50) NOT NULL,
    updated_by UUID DEFAULT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_singleton CHECK (id = 'd3b07384-d113-4ae0-91be-37a113c3d3de')
);

-- Default Lari Traders info
INSERT INTO shop_profile (id, company_name, gstin, fssai, phone, address, state_code, state_name)
VALUES (
    'd3b07384-d113-4ae0-91be-37a113c3d3de',
    'LARI TRADERS',
    '09DIMPA1174G1ZC',
    '22722264000742',
    '9450821033',
    'Gorakhpur, Uttar Pradesh',
    '09',
    'Uttar Pradesh'
);
