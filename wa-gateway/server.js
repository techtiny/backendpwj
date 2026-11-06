// WhatsApp Web gateway — links to one WhatsApp number by QR scan (like WhatsApp Web) and sends messages on request.
// Only the Spring backend talks to this service (shared secret in X-Gateway-Key). The login session is kept in AUTH_DIR,
// so on a host it needs a persistent volume or the QR has to be scanned again after every restart.
import express from "express";
import QRCode from "qrcode";
import pino from "pino";
import { renderCard } from "./cards.js";
import makeWASocket, { useMultiFileAuthState, DisconnectReason, fetchLatestBaileysVersion } from "@whiskeysockets/baileys";

const PORT = process.env.PORT || 3100;
const KEY = process.env.GATEWAY_KEY || "dev-gateway-key";
const AUTH_DIR = process.env.AUTH_DIR || "./auth";
const log = pino({ level: process.env.LOG_LEVEL || "warn" });

// Recently sent messages. WhatsApp asks the sender to re-send a message when the recipient's phone couldn't decrypt it
// the first time; without this store the message stays stuck on a single grey tick and is never delivered.
const sent = new Map();           // message id → message content
const remember = (id, content) => { sent.set(id, content); if (sent.size > 2000) sent.delete(sent.keys().next().value); };
const ACK = { 1: "pending", 2: "sent", 3: "delivered", 4: "read", 5: "played" };
const receipts = new Map();       // message id → latest status (for diagnosing "sent but not delivered")

let sock = null;
let state = { status: "starting", qr: null, phone: null };   // starting | qr | connected | disconnected

async function connect() {
  const { state: auth, saveCreds } = await useMultiFileAuthState(AUTH_DIR);
  const { version } = await fetchLatestBaileysVersion().catch(() => ({ version: undefined }));
  sock = makeWASocket({ version, auth, logger: log, printQRInTerminal: false, browser: ["Happizo CloudDesk", "Chrome", "1.0"], syncFullHistory: false,
    getMessage: async (key) => sent.get(key.id) });
  sock.ev.on("creds.update", saveCreds);
  sock.ev.on("messages.update", (updates) => {
    for (const { key, update } of updates) {
      if (key.fromMe && update.status) { receipts.set(key.id, ACK[update.status] || String(update.status)); console.log(`message ${key.id} → ${ACK[update.status] || update.status}`); }
    }
  });
  sock.ev.on("connection.update", async (u) => {
    const { connection, lastDisconnect, qr } = u;
    if (qr) state = { status: "qr", qr: await QRCode.toDataURL(qr, { margin: 1, width: 280 }), phone: null };
    if (connection === "open") state = { status: "connected", qr: null, phone: (sock.user?.id || "").split(":")[0].split("@")[0] };
    if (connection === "close") {
      const code = lastDisconnect?.error?.output?.statusCode;
      if (code === DisconnectReason.loggedOut) {
        // Unlinked from the phone: drop the saved session so a fresh QR is offered.
        state = { status: "disconnected", qr: null, phone: null };
        await import("node:fs/promises").then(fs => fs.rm(AUTH_DIR, { recursive: true, force: true }));
      } else state = { ...state, status: "starting", qr: null };
      setTimeout(() => connect().catch(e => log.error(e)), 2000);
    }
  });
}

const app = express();
app.use(express.json());
app.use((req, res, next) => (req.get("X-Gateway-Key") === KEY ? next() : res.status(401).json({ error: "unauthorized" })));

app.get("/status", (_req, res) => res.json(state));

// GET /card/diwali — the greeting card as a PNG (used for the preview in the campaign form).
app.get("/card/:theme", (req, res) => {
  const png = renderCard(req.params.theme.replace(/\.png$/, ""));
  if (!png) return res.status(404).json({ error: "unknown card" });
  res.type("png").send(png);
});

// POST /send { phone: "919876543210", message: "...", card?: "diwali" } — phone must already include the country code, digits only.
// With a card, the card image is sent and the message goes along as its caption.
app.post("/send", async (req, res) => {
  const { phone, message, card } = req.body || {};
  if (state.status !== "connected") return res.status(409).json({ error: "WhatsApp is not connected — scan the QR first" });
  if (!/^\d{8,15}$/.test(phone || "") || !message) return res.status(400).json({ error: "phone (digits with country code) and message are required" });
  try {
    const [hit] = await sock.onWhatsApp(phone);
    if (!hit?.exists) return res.status(422).json({ error: "This number is not on WhatsApp" });
    const image = card ? renderCard(card) : null;
    if (card && !image) return res.status(400).json({ error: "unknown card" });
    const content = image ? { image, caption: message, mimetype: "image/png" } : { text: message };
    const sentMsg = await sock.sendMessage(hit.jid, content);
    if (sentMsg?.key?.id) remember(sentMsg.key.id, sentMsg.message);
    res.json({ ok: true, id: sentMsg?.key?.id });
  } catch (e) { res.status(500).json({ error: String(e.message || e) }); }
});

// GET /receipt/:id — latest delivery status of a sent message (sent → delivered → read).
app.get("/receipt/:id", (req, res) => res.json({ status: receipts.get(req.params.id) || "unknown" }));

// POST /logout — unlink this number from the gateway.
app.post("/logout", async (_req, res) => {
  try { await sock?.logout(); } catch { /* already closed */ }
  res.json({ ok: true });
});

const HOST = process.env.HOST || "127.0.0.1";   // 0.0.0.0 when it runs as a separate service on a private network
app.listen(PORT, HOST, () => console.log(`wa-gateway listening on ${HOST}:${PORT}`));
connect().catch(e => { log.error(e); state = { status: "disconnected", qr: null, phone: null }; });
