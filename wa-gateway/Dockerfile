# wa-gateway — WhatsApp (QR-linked) sender for Sales → Campaigns.
# git is needed: Baileys pulls one dependency straight from GitHub.
FROM node:20-bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY . .
ENV NODE_ENV=production PORT=3100 HOST=:: AUTH_DIR=/data/auth
EXPOSE 3100
CMD ["node", "server.js"]
