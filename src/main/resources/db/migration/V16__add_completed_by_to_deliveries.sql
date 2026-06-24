ALTER TABLE deliveries ADD COLUMN completed_by_id UUID REFERENCES users(id);
