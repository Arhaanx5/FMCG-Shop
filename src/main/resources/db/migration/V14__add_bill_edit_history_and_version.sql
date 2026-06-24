-- Add version column to bills table for optimistic locking
ALTER TABLE bills ADD COLUMN version INT DEFAULT 0;

-- Create bill_edit_histories table for auditing bill changes
CREATE TABLE bill_edit_histories (
    id UUID PRIMARY KEY,
    bill_id UUID NOT NULL,
    bill_number VARCHAR(255) NOT NULL,
    edited_by VARCHAR(255) NOT NULL,
    edited_at TIMESTAMP NOT NULL,
    old_json TEXT,
    new_json TEXT,
    reason TEXT
);
