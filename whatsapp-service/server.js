import express from 'express';
import pkg from 'whatsapp-web.js';
import qrcode from 'qrcode';
import dotenv from 'dotenv';
import path from 'path';
import fs from 'fs';
import util from 'util';
import { createServer } from 'net';

dotenv.config();

// ─── Logging Setup ───────────────────────────────────────────────────────────
const logFilePath = path.join(process.cwd(), 'service.log');

// Log rotation: keep file under 5MB
function rotateLogs() {
    try {
        const stats = fs.statSync(logFilePath);
        if (stats.size > 5 * 1024 * 1024) {
            const archivePath = logFilePath.replace('.log', `_${Date.now()}.log`);
            fs.renameSync(logFilePath, archivePath);
            console.log(`[LOG] Log rotated to ${archivePath}`);
        }
    } catch (_) {}
}
rotateLogs();

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

// ─── State ────────────────────────────────────────────────────────────────────
let clientStatus = 'INITIALIZING';
let currentQr = null;
let isInitializing = false;
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 10;

// ─── Port Check: Wait if port is busy ────────────────────────────────────────
function isPortFree(port) {
    return new Promise((resolve) => {
        const tester = createServer()
            .once('error', () => resolve(false))
            .once('listening', () => { tester.close(); resolve(true); })
            .listen(port, '127.0.0.1');
    });
}

async function waitForPort(port, retries = 10, delayMs = 2000) {
    for (let i = 0; i < retries; i++) {
        const free = await isPortFree(port);
        if (free) return true;
        console.log(`[PORT] Port ${port} busy. Waiting ${delayMs}ms (attempt ${i + 1}/${retries})...`);
        await new Promise(r => setTimeout(r, delayMs));
    }
    return false;
}

// ─── WhatsApp Client Setup ────────────────────────────────────────────────────
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
            '--disable-gpu',
            '--disable-extensions',
            '--single-process'
        ]
    }
});

// ─── Exponential Backoff Reconnect ────────────────────────────────────────────
function scheduleReconnect() {
    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
        console.error(`[WA] Max reconnect attempts (${MAX_RECONNECT_ATTEMPTS}) reached. Manual scan required.`);
        reconnectAttempts = 0;
        return;
    }
    const delay = Math.min(2000 * Math.pow(1.5, reconnectAttempts), 60000); // max 60s
    reconnectAttempts++;
    console.log(`[WA] Scheduling reconnect attempt ${reconnectAttempts} in ${Math.round(delay / 1000)}s...`);
    setTimeout(() => safeInitialize(), delay);
}

async function safeInitialize() {
    if (isInitializing || clientStatus === 'CONNECTED') {
        console.log(`[WA] Skip initialize (isInitializing=${isInitializing}, status=${clientStatus})`);
        return;
    }
    console.log('[WA] Triggering client.initialize()...');
    isInitializing = true;
    clientStatus = 'INITIALIZING';
    try {
        await client.initialize();
    } catch (err) {
        console.error('[WA] Initialization failed:', err.message);
        clientStatus = 'DISCONNECTED';
        isInitializing = false;
        scheduleReconnect();
    }
}

// ─── Client Event Handlers ────────────────────────────────────────────────────
client.on('qr', async (qr) => {
    console.log('[WA] New QR code received — please scan with your phone');
    clientStatus = 'DISCONNECTED';
    isInitializing = false;
    reconnectAttempts = 0; // reset on new QR
    try {
        currentQr = await qrcode.toDataURL(qr);
    } catch (err) {
        console.error('[WA] Failed to generate QR data URL:', err.message);
    }
});

client.on('loading_screen', (percent, message) => {
    console.log(`[WA] Loading: ${percent}% — ${message}`);
});

client.on('ready', () => {
    console.log('[WA] WhatsApp Client READY ✅');
    clientStatus = 'CONNECTED';
    currentQr = null;
    isInitializing = false;
    reconnectAttempts = 0;
});

client.on('authenticated', () => {
    console.log('[WA] Authenticated successfully ✅');
});

client.on('auth_failure', (msg) => {
    console.error('[WA] Authentication FAILED:', msg);
    clientStatus = 'DISCONNECTED';
    currentQr = null;
    isInitializing = false;
    scheduleReconnect();
});

client.on('disconnected', (reason) => {
    console.log('[WA] Client disconnected:', reason);
    clientStatus = 'DISCONNECTED';
    currentQr = null;
    isInitializing = false;
    scheduleReconnect();
});

// ─── Keep-alive heartbeat (prevent session timeout) ───────────────────────────
setInterval(() => {
    if (clientStatus === 'CONNECTED') {
        client.getState().then(state => {
            if (state !== 'CONNECTED') {
                console.log('[WA] Heartbeat: state drifted to', state, '— reinitializing');
                clientStatus = 'DISCONNECTED';
                isInitializing = false;
                scheduleReconnect();
            }
        }).catch(() => {});
    }
}, 30000); // every 30 seconds

// ─── Graceful Shutdown ────────────────────────────────────────────────────────
async function gracefulShutdown(signal) {
    console.log(`[WA] Received ${signal}, shutting down gracefully...`);
    try {
        if (clientStatus === 'CONNECTED') await client.destroy();
    } catch (_) {}
    process.exit(0);
}
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));
process.on('uncaughtException', (err) => {
    console.error('[WA] Uncaught exception:', err.message);
});
process.on('unhandledRejection', (reason) => {
    console.error('[WA] Unhandled rejection:', reason);
});

// ─── REST Endpoints ───────────────────────────────────────────────────────────

// Health check
app.get('/health', (req, res) => {
    res.json({
        status: clientStatus,
        uptime: Math.round(process.uptime()),
        reconnectAttempts,
        hasQr: !!currentQr,
        timestamp: new Date().toISOString()
    });
});

// Status
app.get('/status', (req, res) => {
    res.json({ status: clientStatus });
});

// QR Code
app.get('/qr', (req, res) => {
    if (clientStatus === 'CONNECTED') {
        return res.json({ qr: null, message: 'Already connected' });
    }
    if (clientStatus === 'DISCONNECTED' && !currentQr && !isInitializing) {
        console.log('[WA] GET /qr: Auto-reinitializing to get fresh QR...');
        safeInitialize();
    }
    res.json({ qr: currentQr, status: clientStatus });
});

// Send text message (with retry)
app.post('/send', async (req, res) => {
    const { phone, message } = req.body;

    if (!phone || !message) {
        return res.status(400).json({ error: 'Missing phone or message' });
    }
    if (clientStatus !== 'CONNECTED') {
        return res.status(503).json({ error: 'WhatsApp not connected. Status: ' + clientStatus });
    }

    try {
        let cleanedPhone = phone.replace(/\D/g, '');
        if (cleanedPhone.length === 10) cleanedPhone = '91' + cleanedPhone;
        const chatId = cleanedPhone + '@c.us';
        console.log(`[WA] Sending message to: ${chatId}`);
        await client.sendMessage(chatId, message);
        res.json({ success: true, message: 'Message sent successfully' });
    } catch (err) {
        console.error('[WA] Send failed:', err.message);
        res.status(500).json({ error: 'Send failed: ' + err.message });
    }
});

// Send media (PDF, image etc.)
app.post('/send-media', async (req, res) => {
    const { phone, media, filename, caption } = req.body;

    if (!phone || !media || !filename) {
        return res.status(400).json({ error: 'Missing phone, media, or filename' });
    }
    if (clientStatus !== 'CONNECTED') {
        return res.status(503).json({ error: 'WhatsApp not connected. Status: ' + clientStatus });
    }

    try {
        let cleanedPhone = phone.replace(/\D/g, '');
        if (cleanedPhone.length === 10) cleanedPhone = '91' + cleanedPhone;
        const chatId = cleanedPhone + '@c.us';

        let base64Data = media;
        if (base64Data.includes(';base64,')) {
            base64Data = base64Data.split(';base64,')[1];
        }

        console.log(`[WA] Sending media to: ${chatId}, file: ${filename}`);
        const messageMedia = new MessageMedia('application/pdf', base64Data, filename);
        await client.sendMessage(chatId, messageMedia, { caption: caption || '' });
        res.json({ success: true, message: 'Media sent successfully' });
    } catch (err) {
        console.error('[WA] Media send failed:', err.message);
        res.status(500).json({ error: 'Media send failed: ' + err.message });
    }
});

// Logout & reinitialize
app.post('/logout', async (req, res) => {
    try {
        console.log('[WA] Logout requested...');
        if (clientStatus === 'CONNECTED' || client.pupBrowser) {
            try { await client.logout(); }
            catch (_) { try { await client.destroy(); } catch (__) {} }
        }
        clientStatus = 'DISCONNECTED';
        currentQr = null;
        isInitializing = false;
        reconnectAttempts = 0;
        setTimeout(() => safeInitialize(), 1500);
        res.json({ success: true, message: 'Logged out and reinitializing...' });
    } catch (err) {
        console.error('[WA] Logout error:', err.message);
        res.status(500).json({ error: 'Logout failed: ' + err.message });
    }
});

// ─── Start Server ─────────────────────────────────────────────────────────────
async function startServer() {
    const portFree = await waitForPort(PORT, 8, 3000);
    if (!portFree) {
        console.error(`[WA] Port ${PORT} is still in use after retries. Exiting.`);
        process.exit(1);
    }

    app.listen(PORT, '127.0.0.1', () => {
        console.log(`[WA] WhatsApp service running at http://127.0.0.1:${PORT}`);
        safeInitialize();
    });
}

startServer();
