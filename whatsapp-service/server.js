import express from 'express';
import pkg from 'whatsapp-web.js';
import qrcode from 'qrcode';
import dotenv from 'dotenv';
import path from 'path';
import fs from 'fs';
import util from 'util';

dotenv.config();

// Redirect console logs to file
const logFilePath = path.join(process.cwd(), 'service.log');
const logFile = fs.createWriteStream(logFilePath, { flags: 'a' });
const logStdout = process.stdout;
const logStderr = process.stderr;

console.log = function () {
    const formatted = `[${new Date().toISOString()}] ` + util.format.apply(null, arguments) + '\n';
    logFile.write(formatted);
    logStdout.write(formatted);
};
console.error = function () {
    const formatted = `[${new Date().toISOString()}] [ERROR] ` + util.format.apply(null, arguments) + '\n';
    logFile.write(formatted);
    logStderr.write(formatted);
};

const { Client, LocalAuth, MessageMedia } = pkg;
const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json({ limit: '10mb' }));

// Session and QR states
let clientStatus = 'INITIALIZING'; // INITIALIZING, CONNECTED, DISCONNECTED
let currentQr = null;
let isInitializing = false;

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

async function safeInitialize() {
    if (isInitializing || clientStatus === 'CONNECTED') {
        console.log(`[WA] Skip initialization (isInitializing=${isInitializing}, status=${clientStatus})`);
        return;
    }
    console.log('[WA] Triggering client.initialize()...');
    isInitializing = true;
    clientStatus = 'INITIALIZING';
    try {
        await client.initialize();
    } catch (err) {
        console.error('[WA] Initialization failed:', err);
        clientStatus = 'DISCONNECTED';
        isInitializing = false;
    }
}

// Client Lifecycle Handlers
client.on('qr', async (qr) => {
    console.log('New WhatsApp QR code received');
    clientStatus = 'DISCONNECTED';
    isInitializing = false;
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
    isInitializing = false;
});

client.on('authenticated', () => {
    console.log('WhatsApp Web Authenticated successfully');
});

client.on('auth_failure', (msg) => {
    console.error('Authentication failure:', msg);
    clientStatus = 'DISCONNECTED';
    currentQr = null;
    isInitializing = false;
});

client.on('disconnected', (reason) => {
    console.log('WhatsApp Client was disconnected:', reason);
    clientStatus = 'DISCONNECTED';
    currentQr = null;
    isInitializing = false;
    // Reinitialize to get a fresh QR code
    setTimeout(() => {
        safeInitialize();
    }, 2000);
});

// Initialize client
safeInitialize();

// REST Endpoints
app.get('/status', (req, res) => {
    res.json({ status: clientStatus });
});

app.get('/qr', (req, res) => {
    if (clientStatus === 'CONNECTED') {
        return res.json({ qr: null, message: 'Already connected' });
    }
    if (clientStatus === 'DISCONNECTED' && !currentQr && !isInitializing) {
        console.log('[WA] GET /qr: Client is disconnected and no QR found. Auto-reinitializing...');
        safeInitialize();
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
        console.log('[WA] Logout requested. Clearing session...');
        if (clientStatus === 'CONNECTED' || client.pupBrowser) {
            try {
                await client.logout();
            } catch (logoutErr) {
                console.warn('[WA] client.logout() failed, destroying client instance:', logoutErr);
                await client.destroy();
            }
        }
        clientStatus = 'DISCONNECTED';
        currentQr = null;
        isInitializing = false;

        // Auto-reinitialize after a brief delay so a fresh QR code is immediately available
        setTimeout(() => {
            console.log('[WA] Reinitializing client after logout...');
            safeInitialize();
        }, 1500);

        res.json({ success: true, message: 'Successfully logged out and session cleared' });
    } catch (err) {
        console.error('Logout error:', err);
        res.status(500).json({ error: 'Logout failed: ' + err.message });
    }
});

app.listen(PORT, '127.0.0.1', () => {
    console.log(`WhatsApp headless helper service running at http://127.0.0.1:${PORT}`);
});
