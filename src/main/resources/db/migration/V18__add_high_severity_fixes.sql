-- Bug #21: WhatsApp cooldown tracking
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS last_whatsapp_alert_sent TIMESTAMP WITHOUT TIME ZONE DEFAULT NULL;
