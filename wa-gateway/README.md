# wa-gateway

Small Node service that links **one WhatsApp number** by QR scan (WhatsApp Web / "Linked devices") and sends the Sales → Campaigns
messages. Only the Spring backend calls it.

```
cd wa-gateway
npm install
npm start          # listens on 127.0.0.1:3100
```

Then open **Sales → Campaigns** in the app: the QR code appears there — scan it from WhatsApp → Linked devices → Link a device.

| env var | default | meaning |
|---|---|---|
| `PORT` | 3100 | port |
| `HOST` | 127.0.0.1 | use `0.0.0.0` when it runs as its own service on a private network |
| `GATEWAY_KEY` | dev-gateway-key | shared secret; must equal the backend's `pwj.whatsapp.gateway.key` |
| `AUTH_DIR` | ./auth | where the linked session is stored — mount a persistent volume in production, or the QR must be re-scanned after every restart |

Backend settings: `pwj.whatsapp.gateway.url` (default `http://localhost:3100`) and `pwj.whatsapp.gateway.key`.

This uses the unofficial WhatsApp Web protocol (Baileys). WhatsApp can restrict a number that sends bulk or unwanted messages —
the backend sends slowly (8–20 s apart, 250 contacts per campaign max); use a dedicated business number and message only people who expect it.
