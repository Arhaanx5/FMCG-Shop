import express from 'express';
import pkg from 'whatsapp-web.js';
import qrcode from 'qrcode';
import dotenv from 'dotenv';
import path from 'path';

dotenv.config();

const { Client, LocalAuth, MessageMedia } = pkg;
const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json({ limit: '10mb' }));

// Session and QR states
let clientStatus = 'INITIALIZING'; // INITIALIZING, CONNECTED, DISCONNECTED
let currentQr = null;

const client = new Client({
    authStrategy: new LocalAuth({
        dataPath: path.join(process.cwd(), 'session_data')
    }),
    puppeteer: {
        headless: true,
        args: [
            '--no-sandbox',
            '--disable-setuid-sandbox',
            '--disable-dev-shm-usage',
            '--disable-accelerated-2d-canvas',
            '--no-first-run',
            '--no-zygote',
            '--disable-gpu'
        ]
    }
});

// Client Lifecycle Handlers
client.on('qr', async (qr) => {
    console.log('New WhatsApp QR code received');
    clientStatus = 'DISCONNECTED';
    try {
        currentQr = await qrcode.toDataURL(qr);
    } catch (err) {
        console.error('Failed to generate QR data URL', err);
    }
});

client.on('ready', () => {
    console.log('WhatsApp Web Client is READY');
    clientStatus = 'CONNECTED';
    currentQr = null;
});

client.on('authenticated', () => {
    console.log('WhatsApp Web Authenticated successfully');
});

client.on('auth_failure', (msg) => {
    console.error('Authentication failure:', msg);
    clientStatus = 'DISCONNECTED';
    currentQr = null;
});

client.on('disconnected', (reason) => {
    console.log('WhatsApp Client was disconnected:', reason);
    clientStatus = 'DISCONNECTED';
    currentQr = null;
    // Reinitialize to get a fresh QR code
    client.initialize().catch(err => console.error('Error reinitializing:', err));
});

// Initialize client
client.initialize().catch(err => {
    console.error('Initialization error during boot:', err);
    clientStatus = 'DISCONNECTED';
});

// REST Endpoints
app.get('/status', (req, res) => {
    res.json({ status: clientStatus });
});

app.get('/qr', (req, res) => {
    if (clientStatus === 'CONNECTED') {
        return res.json({ qr: null, message: 'Already connected' });
    }
    res.json({ qr: currentQr });
});

app.post('/send', async (req, res) => {
    const { phone, message } = req.body;

    if (!phone || !message) {
        return res.status(400).json({ error: 'Missing phone number or message content' });
    }

    if (clientStatus !== 'CONNECTED') {
        return res.status(503).json({ error: 'WhatsApp service is not authenticated or connected' });
    }

    try {
        // Clean phone number: keep only digits
        let cleanedPhone = phone.replace(/\D/g, '');
        
        // Ensure standard Indian country code prefix 91 if it's a 10 digit number
        if (cleanedPhone.length === 10) {
            cleanedPhone = '91' + cleanedPhone;
        }

        const chatId = cleanedPhone + '@c.us';
        console.log(`Sending automated message to: ${chatId}`);
        
        await client.sendMessage(chatId, message);
        res.json({ success: true, message: 'Message sent successfully' });
    } catch (err) {
        console.error('Failed to send message:', err);
        res.status(500).json({ error: 'Failed to send message: ' + err.message });
    }
});

app.post('/send-media', async (req, res) => {
    const { phone, media, filename, caption } = req.body;

    if (!phone || !media || !filename) {
        return res.status(400).json({ error: 'Missing phone number, media content, or filename' });
    }

    if (clientStatus !== 'CONNECTED') {
        return res.status(503).json({ error: 'WhatsApp service is not authenticated or connected' });
    }

    try {
        // Clean phone number: keep only digits
        let cleanedPhone = phone.replace(/\D/g, '');
        
        // Ensure standard Indian country code prefix 91 if it's a 10 digit number
        if (cleanedPhone.length === 10) {
            cleanedPhone = '91' + cleanedPhone;
        }

        const chatId = cleanedPhone + '@c.us';
        console.log(`Sending automated media message to: ${chatId}, file: ${filename}`);

        // Strip data URL prefix if present in the base64 media string
        let base64Data = media;
        if (base64Data.includes(';base64,')) {
            base64Data = base64Data.split(';base64,')[1];
        }

        const messageMedia = new MessageMedia('application/pdf', base64Data, filename);
        
        await client.sendMessage(chatId, messageMedia, { caption: caption || '' });
        res.json({ success: true, message: 'Media sent successfully' });
    } catch (err) {
        console.error('Failed to send media message:', err);
        res.status(500).json({ error: 'Failed to send media message: ' + err.message });
    }
});

app.post('/logout', async (req, res) => {
    try {
        if (clientStatus === 'CONNECTED') {
            await client.logout();
        }
        clientStatus = 'DISCONNECTED';
        currentQr = null;
        res.json({ success: true, message: 'Successfully logged out and session cleared' });
    } catch (err) {
        console.error('Logout error:', err);
        res.status(500).json({ error: 'Logout failed: ' + err.message });
    }
});

app.listen(PORT, '127.0.0.1', () => {
    console.log(`WhatsApp headless helper service running at http://127.0.0.1:${PORT}`);
});
