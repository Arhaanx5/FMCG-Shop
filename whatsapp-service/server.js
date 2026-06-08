import express from 'express';
import pkg from 'whatsapp-web.js';
import qrcode from 'qrcode';
import dotenv from 'dotenv';
import path from 'path';
import fs from 'fs';
import util from 'util';
import { createServer } from 'net';
import { randomUUID } from 'crypto';

dotenv.config();

const { Client, LocalAuth, MessageMedia } = pkg;
const app = express();
const PORT = process.env.PORT || 3000;
const QUEUE_FILE = path.join(process.cwd(), 'queue.json');
const DEAD_LETTER_FILE = path.join(process.cwd(), 'dead_letter.json');
const LOG_FILE = path.join(process.cwd(), 'service.log');

// ═══════════════════════════════════════════════════
//  LOGGING ENGINE (Structured + Rotation)
// ═══════════════════════════════════════════════════
function rotateLogs() {
    try {
        if (fs.existsSync(LOG_FILE) && fs.statSync(LOG_FILE).size > 5 * 1024 * 1024) {
            fs.renameSync(LOG_FILE, LOG_FILE.replace('.log', `_${Date.now()}.log`));
        }
    } catch (_) {}
}
rotateLogs();

const logStream = fs.createWriteStream(LOG_FILE, { flags: 'a' });

function log(level, module, message, data = {}) {
    const entry = {
        ts: new Date().toISOString(),
        level,
        module,
        message,
        ...(Object.keys(data).length > 0 ? { data } : {})
    };
    const line = JSON.stringify(entry) + '\n';
    logStream.write(line);
    const color = level === 'ERROR' ? '\x1b[31m' : level === 'WARN' ? '\x1b[33m' : '\x1b[36m';
    process.stdout.write(`${color}[${entry.ts}] [${level}] [${module}] ${message}\x1b[0m\n`);
}

// ═══════════════════════════════════════════════════
//  STATS TRACKER
// ═══════════════════════════════════════════════════
const stats = {
    sent: 0, failed: 0, queued: 0, retried: 0, dropped: 0,
    uptime: Date.now(), reconnects: 0, lastSentAt: null,
    summary() {
        return {
            sent: this.sent, failed: this.failed, queued: this.queued,
            retried: this.retried, dropped: this.dropped,
            successRate: this.sent + this.failed > 0
                ? ((this.sent / (this.sent + this.failed)) * 100).toFixed(1) + '%' : 'N/A',
            uptimeSeconds: Math.round((Date.now() - this.uptime) / 1000),
            reconnects: this.reconnects,
            lastSentAt: this.lastSentAt
        };
    }
};

// ═══════════════════════════════════════════════════
//  MESSAGE QUEUE ENGINE
// ═══════════════════════════════════════════════════
const SEND_INTERVAL_MS = 1800;    // 1.8s between sends (avoid WhatsApp ban)
const MAX_RETRIES = 3;
const RETRY_DELAYS = [5000, 15000, 45000]; // exponential retry

class MessageQueue {
    constructor() {
        this.queue = [];         // pending messages
        this.deadLetter = [];    // permanently failed
        this.processing = false;
        this.processorTimer = null;
        this.load();
    }

    load() {
        try {
            if (fs.existsSync(QUEUE_FILE)) {
                const data = JSON.parse(fs.readFileSync(QUEUE_FILE, 'utf8'));
                this.queue = Array.isArray(data) ? data : [];
                log('INFO', 'QUEUE', `Loaded ${this.queue.length} pending messages from disk`);
            }
            if (fs.existsSync(DEAD_LETTER_FILE)) {
                const data = JSON.parse(fs.readFileSync(DEAD_LETTER_FILE, 'utf8'));
                this.deadLetter = Array.isArray(data) ? data : [];
            }
        } catch (err) {
            log('WARN', 'QUEUE', 'Failed to load queue from disk', { err: err.message });
            this.queue = [];
        }
    }

    save() {
        try {
            fs.writeFileSync(QUEUE_FILE, JSON.stringify(this.queue, null, 2));
            if (this.deadLetter.length > 0)
                fs.writeFileSync(DEAD_LETTER_FILE, JSON.stringify(this.deadLetter, null, 2));
        } catch (err) {
            log('ERROR', 'QUEUE', 'Failed to persist queue', { err: err.message });
        }
    }

    enqueue(msg, priority = 'normal') {
        const item = {
            id: randomUUID(),
            priority,           // 'urgent' | 'normal'
            type: msg.type,     // 'text' | 'media'
            phone: msg.phone,
            message: msg.message,
            media: msg.media || null,
            filename: msg.filename || null,
            caption: msg.caption || null,
            retries: 0,
            nextRetryAt: Date.now(),
            enqueuedAt: new Date().toISOString()
        };
        // Urgent messages go to front
        if (priority === 'urgent') {
            this.queue.unshift(item);
        } else {
            this.queue.push(item);
        }
        stats.queued++;
        this.save();
        log('INFO', 'QUEUE', `Enqueued ${priority} message`, { id: item.id, phone: item.phone });
        return item.id;
    }

    enqueueAll(messages, priority = 'normal') {
        const ids = messages.map(m => this.enqueue(m, priority));
        return ids;
    }

    startProcessor() {
        if (this.processorTimer) return;
        this.processorTimer = setInterval(() => this.processNext(), SEND_INTERVAL_MS);
        log('INFO', 'QUEUE', 'Message processor started');
    }

    stopProcessor() {
        if (this.processorTimer) {
            clearInterval(this.processorTimer);
            this.processorTimer = null;
        }
    }

    async processNext() {
        if (this.processing || clientStatus !== 'CONNECTED') return;
        const now = Date.now();
        // Find next message ready to send (retry delay respected)
        const idx = this.queue.findIndex(m => m.nextRetryAt <= now);
        if (idx === -1) return;

        this.processing = true;
        const msg = this.queue[idx];

        try {
            await sendWhatsAppMessage(msg);
            this.queue.splice(idx, 1);
            stats.sent++;
            stats.lastSentAt = new Date().toISOString();
            log('INFO', 'QUEUE', `✅ Message sent`, { id: msg.id, phone: msg.phone, retries: msg.retries });
        } catch (err) {
            msg.retries++;
            stats.retried++;
            if (msg.retries >= MAX_RETRIES) {
                // Move to dead letter queue
                msg.failedAt = new Date().toISOString();
                msg.lastError = err.message;
                this.deadLetter.push(msg);
                this.queue.splice(idx, 1);
                stats.failed++;
                stats.dropped++;
                log('ERROR', 'QUEUE', `❌ Message permanently failed (moved to dead letter)`, { id: msg.id, phone: msg.phone });
            } else {
                const delay = RETRY_DELAYS[msg.retries - 1] || 60000;
                msg.nextRetryAt = Date.now() + delay;
                log('WARN', 'QUEUE', `⚠️ Message failed, retry ${msg.retries}/${MAX_RETRIES} in ${delay / 1000}s`, { id: msg.id, err: err.message });
            }
        }

        this.save();
        this.processing = false;
    }

    getStats() {
        return {
            pending: this.queue.length,
            deadLetter: this.deadLetter.length,
            urgentCount: this.queue.filter(m => m.priority === 'urgent').length,
            normalCount: this.queue.filter(m => m.priority === 'normal').length,
            nextProcessIn: `${SEND_INTERVAL_MS}ms`
        };
    }

    clear(type = 'pending') {
        if (type === 'dead') {
            const count = this.deadLetter.length;
            this.deadLetter = [];
            this.save();
            return count;
        } else {
            const count = this.queue.length;
            this.queue = [];
            this.save();
            return count;
        }
    }
}

// ═══════════════════════════════════════════════════
//  WHATSAPP CLIENT MANAGER
// ═══════════════════════════════════════════════════
let clientStatus = 'INITIALIZING';
let currentQr = null;
let isInitializing = false;
let reconnectAttempts = 0;
const MAX_RECONNECT = 10;
const messageQueue = new MessageQueue();

const client = new Client({
    authStrategy: new LocalAuth({ dataPath: path.join(process.cwd(), 'session_data') }),
    puppeteer: {
        headless: true,
        args: [
            '--no-sandbox', '--disable-setuid-sandbox',
            '--disable-dev-shm-usage', '--disable-accelerated-2d-canvas',
            '--no-first-run', '--no-zygote', '--disable-gpu',
            '--disable-extensions', '--single-process',
            '--memory-pressure-off'
        ]
    }
});

function scheduleReconnect() {
    if (reconnectAttempts >= MAX_RECONNECT) {
        log('ERROR', 'WA', `Max reconnect attempts reached. Manual QR scan required.`);
        reconnectAttempts = 0;
        return;
    }
    const delay = Math.min(3000 * Math.pow(1.6, reconnectAttempts), 90000);
    reconnectAttempts++;
    stats.reconnects++;
    log('INFO', 'WA', `Reconnect attempt ${reconnectAttempts} scheduled in ${Math.round(delay / 1000)}s`);
    setTimeout(() => safeInitialize(), delay);
}

async function safeInitialize() {
    if (isInitializing || clientStatus === 'CONNECTED') return;
    log('INFO', 'WA', 'Initializing WhatsApp client...');
    isInitializing = true;
    clientStatus = 'INITIALIZING';
    try {
        await client.initialize();
    } catch (err) {
        log('ERROR', 'WA', 'Initialization failed', { err: err.message });
        clientStatus = 'DISCONNECTED';
        isInitializing = false;
        scheduleReconnect();
    }
}

client.on('qr', async (qr) => {
    log('INFO', 'WA', 'QR code ready — scan with WhatsApp phone app');
    clientStatus = 'DISCONNECTED';
    isInitializing = false;
    reconnectAttempts = 0;
    messageQueue.stopProcessor();
    try { currentQr = await qrcode.toDataURL(qr); }
    catch (err) { log('ERROR', 'WA', 'QR generation failed', { err: err.message }); }
});

client.on('loading_screen', (percent, message) => {
    log('INFO', 'WA', `Loading ${percent}%: ${message}`);
});

client.on('authenticated', () => {
    log('INFO', 'WA', 'Authenticated ✅');
});

client.on('ready', () => {
    log('INFO', 'WA', 'WhatsApp READY ✅ — Queue processor started');
    clientStatus = 'CONNECTED';
    currentQr = null;
    isInitializing = false;
    reconnectAttempts = 0;
    messageQueue.startProcessor();
});

client.on('auth_failure', (msg) => {
    log('ERROR', 'WA', 'Auth failure', { msg });
    clientStatus = 'DISCONNECTED';
    currentQr = null;
    isInitializing = false;
    messageQueue.stopProcessor();
    scheduleReconnect();
});

client.on('disconnected', (reason) => {
    log('WARN', 'WA', 'Disconnected', { reason });
    clientStatus = 'DISCONNECTED';
    currentQr = null;
    isInitializing = false;
    messageQueue.stopProcessor();
    scheduleReconnect();
});

// Keep-alive heartbeat
setInterval(async () => {
    if (clientStatus !== 'CONNECTED') return;
    try {
        const state = await client.getState();
        if (state !== 'CONNECTED') {
            log('WARN', 'WA', 'Heartbeat: state drifted', { state });
            clientStatus = 'DISCONNECTED';
            isInitializing = false;
            messageQueue.stopProcessor();
            scheduleReconnect();
        }
    } catch (_) {}
}, 30000);

// ═══════════════════════════════════════════════════
//  MESSAGE SENDER
// ═══════════════════════════════════════════════════
function formatPhone(phone) {
    let cleaned = phone.replace(/\D/g, '');
    if (cleaned.length === 10) cleaned = '91' + cleaned;
    return cleaned + '@c.us';
}

async function sendWhatsAppMessage(msg) {
    const chatId = formatPhone(msg.phone);
    if (msg.type === 'media' && msg.media) {
        let base64Data = msg.media;
        if (base64Data.includes(';base64,')) base64Data = base64Data.split(';base64,')[1];
        const media = new MessageMedia('application/pdf', base64Data, msg.filename || 'document.pdf');
        await client.sendMessage(chatId, media, { caption: msg.caption || '' });
    } else {
        await client.sendMessage(chatId, msg.message);
    }
}

// ═══════════════════════════════════════════════════
//  PORT CHECK
// ═══════════════════════════════════════════════════
function isPortFree(port) {
    return new Promise((resolve) => {
        const tester = createServer()
            .once('error', () => resolve(false))
            .once('listening', () => { tester.close(); resolve(true); })
            .listen(port, '127.0.0.1');
    });
}

async function waitForPort(port, retries = 10, delayMs = 3000) {
    for (let i = 0; i < retries; i++) {
        if (await isPortFree(port)) return true;
        log('WARN', 'PORT', `Port ${port} busy, retrying in ${delayMs}ms (${i + 1}/${retries})`);
        await new Promise(r => setTimeout(r, delayMs));
    }
    return false;
}

// ═══════════════════════════════════════════════════
//  EXPRESS REST API
// ═══════════════════════════════════════════════════
app.use(express.json({ limit: '20mb' }));

// Request logger middleware
app.use((req, _res, next) => {
    log('INFO', 'API', `${req.method} ${req.path}`);
    next();
});

// ── Health Check ─────────────────────────────────
app.get('/health', (_req, res) => {
    res.json({
        status: clientStatus,
        queue: messageQueue.getStats(),
        stats: stats.summary(),
        version: '2.0.0'
    });
});

// ── Status ───────────────────────────────────────
app.get('/status', (_req, res) => {
    res.json({ status: clientStatus });
});

// ── QR Code ──────────────────────────────────────
app.get('/qr', (_req, res) => {
    if (clientStatus === 'CONNECTED') return res.json({ qr: null, message: 'Already connected' });
    if (!currentQr && !isInitializing) {
        log('INFO', 'API', 'Auto-reinitializing for QR...');
        safeInitialize();
    }
    res.json({ qr: currentQr, status: clientStatus });
});

// ── Send Single Text ─────────────────────────────
app.post('/send', (req, res) => {
    const { phone, message, priority = 'normal' } = req.body;
    if (!phone || !message) return res.status(400).json({ error: 'Missing phone or message' });

    const id = messageQueue.enqueue({ type: 'text', phone, message }, priority);
    res.json({ success: true, messageId: id, queued: true, queueSize: messageQueue.queue.length });
});

// ── Send Single Media ────────────────────────────
app.post('/send-media', (req, res) => {
    const { phone, media, filename, caption, priority = 'normal' } = req.body;
    if (!phone || !media || !filename) return res.status(400).json({ error: 'Missing phone, media, or filename' });

    const id = messageQueue.enqueue({ type: 'media', phone, media, filename, caption }, priority);
    res.json({ success: true, messageId: id, queued: true, queueSize: messageQueue.queue.length });
});

// ── Bulk Send ─────────────────────────────────────
app.post('/send-bulk', (req, res) => {
    const { messages, priority = 'normal' } = req.body;
    if (!Array.isArray(messages) || messages.length === 0) {
        return res.status(400).json({ error: 'messages must be a non-empty array' });
    }
    const valid = messages.filter(m => m.phone && (m.message || (m.media && m.filename)));
    if (valid.length === 0) return res.status(400).json({ error: 'No valid messages in batch' });

    const ids = messageQueue.enqueueAll(valid.map(m => ({
        type: m.media ? 'media' : 'text', ...m
    })), priority);

    const estimatedSeconds = Math.round((ids.length * SEND_INTERVAL_MS) / 1000);
    res.json({
        success: true,
        queued: ids.length,
        skipped: messages.length - valid.length,
        messageIds: ids,
        estimatedDeliverySeconds: estimatedSeconds
    });
});

// ── Queue Stats ───────────────────────────────────
app.get('/queue/stats', (_req, res) => {
    res.json({ ...messageQueue.getStats(), stats: stats.summary() });
});

// ── Clear Queue ───────────────────────────────────
app.delete('/queue', (req, res) => {
    const type = req.query.type || 'pending';
    const cleared = messageQueue.clear(type);
    log('WARN', 'API', `Queue cleared`, { type, cleared });
    res.json({ success: true, cleared, type });
});

// ── View Dead Letters ─────────────────────────────
app.get('/queue/dead', (_req, res) => {
    res.json({ count: messageQueue.deadLetter.length, messages: messageQueue.deadLetter.slice(-50) });
});

// ── Retry Dead Letters ────────────────────────────
app.post('/queue/retry-dead', (_req, res) => {
    if (messageQueue.deadLetter.length === 0) return res.json({ success: true, retried: 0 });
    const revived = messageQueue.deadLetter.map(m => ({
        type: m.type, phone: m.phone, message: m.message,
        media: m.media, filename: m.filename, caption: m.caption
    }));
    messageQueue.deadLetter = [];
    const ids = messageQueue.enqueueAll(revived, 'normal');
    messageQueue.save();
    res.json({ success: true, retried: ids.length });
});

// ── Logout & Reinitialize ─────────────────────────
app.post('/logout', async (_req, res) => {
    try {
        log('INFO', 'API', 'Logout requested');
        if (clientStatus === 'CONNECTED' || client.pupBrowser) {
            try { await client.logout(); }
            catch (_) { try { await client.destroy(); } catch (__) {} }
        }
        clientStatus = 'DISCONNECTED';
        currentQr = null;
        isInitializing = false;
        reconnectAttempts = 0;
        messageQueue.stopProcessor();
        setTimeout(() => safeInitialize(), 1500);
        res.json({ success: true, message: 'Logged out. Reinitializing...' });
    } catch (err) {
        log('ERROR', 'API', 'Logout failed', { err: err.message });
        res.status(500).json({ error: 'Logout failed: ' + err.message });
    }
});

// ── 404 Handler ───────────────────────────────────
app.use((_req, res) => {
    res.status(404).json({ error: 'Endpoint not found' });
});

// ═══════════════════════════════════════════════════
//  GRACEFUL SHUTDOWN
// ═══════════════════════════════════════════════════
async function shutdown(signal) {
    log('INFO', 'SYSTEM', `Received ${signal} — shutting down`);
    messageQueue.stopProcessor();
    messageQueue.save();
    try { if (clientStatus === 'CONNECTED') await client.destroy(); } catch (_) {}
    logStream.end();
    process.exit(0);
}
process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
process.on('uncaughtException', (err) => log('ERROR', 'SYSTEM', 'Uncaught exception', { err: err.message }));
process.on('unhandledRejection', (r) => log('ERROR', 'SYSTEM', 'Unhandled rejection', { reason: String(r) }));

// ═══════════════════════════════════════════════════
//  BOOT
// ═══════════════════════════════════════════════════
async function boot() {
    log('INFO', 'SYSTEM', '🚀 Lari Traders WhatsApp Service v2.0 starting...');
    const portFree = await waitForPort(PORT, 10, 3000);
    if (!portFree) {
        log('ERROR', 'SYSTEM', `Port ${PORT} still occupied after retries. Exiting.`);
        process.exit(1);
    }
    app.listen(PORT, '127.0.0.1', () => {
        log('INFO', 'SYSTEM', `✅ HTTP server listening on http://127.0.0.1:${PORT}`);
        log('INFO', 'SYSTEM', `📋 Endpoints: /health /status /qr /send /send-bulk /send-media /queue/stats`);
        safeInitialize();
    });
}

boot();
