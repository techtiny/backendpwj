// Festival greeting cards — SVG rendered to PNG (1080×1350, portrait, looks right as a WhatsApp image) with the Happizo logo.
import { Resvg } from "@resvg/resvg-js";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const dir = path.dirname(fileURLToPath(import.meta.url));
const font = (f) => path.join(dir, "fonts", f);
const LOGO = `data:image/png;base64,${readFileSync(path.join(dir, "assets", "happizo-logo.png")).toString("base64")}`;
const W = 1080, H = 1350;

// Deterministic "random" so a card always looks the same.
const rng = (seed) => () => (seed = (seed * 16807) % 2147483647) / 2147483647;

const star = (x, y, r, fill, op = 1) =>
  `<path d="M${x} ${y - r} Q${x} ${y} ${x + r} ${y} Q${x} ${y} ${x} ${y + r} Q${x} ${y} ${x - r} ${y} Q${x} ${y} ${x} ${y - r}Z" fill="${fill}" opacity="${op}"/>`;

// Fine gold dust — tiny dots and the odd four-point sparkle.
function dust(n, seed) {
  const r = rng(seed), out = [];
  for (let i = 0; i < n; i++) {
    const x = 70 + r() * (W - 140), y = 90 + r() * (H - 180);
    out.push(r() > 0.85 ? star(x, y, 7 + r() * 9, "#F6E7A8", 0.55 + r() * 0.35) : `<circle cx="${x}" cy="${y}" r="${1 + r() * 2.4}" fill="#F6E7A8" opacity="${0.25 + r() * 0.5}"/>`);
  }
  return out.join("");
}

// Fine-line mandala: rings of petals plus dotted circles, all in thin gold.
function mandala(cx, cy) {
  let m = "";
  [[170, 12, 22, 58, 0.3], [250, 16, 20, 66, 0.24], [335, 20, 18, 70, 0.19], [425, 24, 16, 72, 0.14]].forEach(([R, n, rx, ry, op]) => {
    for (let i = 0; i < n; i++) m += `<ellipse cx="${cx}" cy="${cy - R}" rx="${rx}" ry="${ry}" transform="rotate(${(360 / n) * i} ${cx} ${cy})" fill="none" stroke="#D4AF37" stroke-width="1.6" opacity="${op}"/>`;
    m += `<circle cx="${cx}" cy="${cy}" r="${R - ry * 0.5}" fill="none" stroke="#D4AF37" stroke-width="1" opacity="${op * 0.8}"/>`;
  });
  m += `<circle cx="${cx}" cy="${cy}" r="480" fill="none" stroke="#D4AF37" stroke-width="2" stroke-dasharray="2 12" stroke-linecap="round" opacity="0.35"/>`;
  return m;
}

// A diya in gold; (cx, base) is the bottom-centre, w its width.
function diya(cx, base, w, flame = 1) {
  const h = w * 0.34, f = w * 0.6 * flame;
  return `
  <circle cx="${cx}" cy="${base - h - f * 0.45}" r="${w * 0.8}" fill="url(#glow)"/>
  <path d="M${cx} ${base - h - f} C${cx + f * 0.5} ${base - h - f * 0.55} ${cx + f * 0.4} ${base - h - 4} ${cx} ${base - h - 4} C${cx - f * 0.4} ${base - h - 4} ${cx - f * 0.5} ${base - h - f * 0.55} ${cx} ${base - h - f}Z" fill="url(#flame)"/>
  <path d="M${cx} ${base - h - f * 0.5} C${cx + f * 0.16} ${base - h - f * 0.28} ${cx + f * 0.13} ${base - h - 4} ${cx} ${base - h - 4} C${cx - f * 0.13} ${base - h - 4} ${cx - f * 0.16} ${base - h - f * 0.28} ${cx} ${base - h - f * 0.5}Z" fill="#FFF8DC"/>
  <path d="M${cx - w / 2} ${base - h} Q${cx} ${base - h + 12} ${cx + w / 2} ${base - h} Q${cx + w * 0.42} ${base} ${cx} ${base} Q${cx - w * 0.42} ${base} ${cx - w / 2} ${base - h}Z" fill="url(#brass)"/>
  <path d="M${cx - w / 2} ${base - h} Q${cx} ${base - h + 12} ${cx + w / 2} ${base - h}" stroke="#FFF1B8" stroke-width="3" fill="none" stroke-linecap="round"/>
  <path d="M${cx - w * 0.28} ${base - h * 0.5} Q${cx} ${base - h * 0.2} ${cx + w * 0.28} ${base - h * 0.5}" stroke="#7a5600" stroke-width="2" fill="none" opacity="0.55"/>`;
}

// Double gold frame with small diamonds at the corners.
function frame() {
  const d = (x, y) => `<path d="M${x} ${y - 11} L${x + 11} ${y} L${x} ${y + 11} L${x - 11} ${y}Z" fill="url(#gold)"/>`;
  return `<rect x="34" y="34" width="${W - 68}" height="${H - 68}" fill="none" stroke="url(#gold)" stroke-width="3"/>
  <rect x="52" y="52" width="${W - 104}" height="${H - 104}" fill="none" stroke="#D4AF37" stroke-width="1" opacity="0.65"/>
  ${d(34, 34)}${d(W - 34, 34)}${d(34, H - 34)}${d(W - 34, H - 34)}${d(W / 2, 34)}${d(W / 2, H - 34)}`;
}

function diwaliSvg() {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#060F24"/><stop offset="0.55" stop-color="#0B2147"/><stop offset="1" stop-color="#143463"/></linearGradient>
    <radialGradient id="halo" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#F4B942" stop-opacity="0.22"/><stop offset="1" stop-color="#F4B942" stop-opacity="0"/></radialGradient>
    <radialGradient id="glow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FFC857" stop-opacity="0.7"/><stop offset="1" stop-color="#FF9F1C" stop-opacity="0"/></radialGradient>
    <linearGradient id="gold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#F9EBB2"/><stop offset="0.5" stop-color="#D4AF37"/><stop offset="1" stop-color="#9C7A1A"/></linearGradient>
    <linearGradient id="goldText" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFF4C4"/><stop offset="0.55" stop-color="#E2B945"/><stop offset="1" stop-color="#B8891B"/></linearGradient>
    <linearGradient id="brass" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F6DE8D"/><stop offset="0.55" stop-color="#C99A2E"/><stop offset="1" stop-color="#7A5600"/></linearGradient>
    <linearGradient id="flame" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#FF8A00"/><stop offset="0.6" stop-color="#FFC233"/><stop offset="1" stop-color="#FFF3B0"/></linearGradient>
    <clipPath id="badge"><rect x="360" y="116" width="360" height="206" rx="30"/></clipPath>
    <filter id="soft" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="12"/></filter>
  </defs>

  <rect width="${W}" height="${H}" fill="url(#bg)"/>
  <circle cx="540" cy="700" r="640" fill="url(#halo)"/>
  ${mandala(540, 700)}
  ${dust(70, 11)}
  ${frame()}

  <!-- Logo -->
  <rect x="354" y="124" width="372" height="214" rx="32" fill="#000" opacity="0.35" filter="url(#soft)"/>
  <rect x="360" y="116" width="360" height="206" rx="30" fill="#fff"/>
  <image href="${LOGO}" x="365" y="64" width="350" height="319" clip-path="url(#badge)" preserveAspectRatio="xMidYMid slice"/>

  <!-- Greeting -->
  <text x="540" y="445" text-anchor="middle" font-family="Cinzel" font-size="30" letter-spacing="12" fill="#E8CC7A">WISHING YOU A</text>
  <text x="540" y="590" text-anchor="middle" font-family="Playfair Display" font-style="italic" font-size="148" fill="#FBF4E2" stroke="#FBF4E2" stroke-width="2">Happy</text>
  <text x="540" y="790" text-anchor="middle" font-family="Playfair Display" font-style="italic" font-size="236" fill="url(#goldText)" stroke="#D4AF37" stroke-width="3">Diwali</text>
  <g transform="translate(540 862)">
    <path d="M-230 0 H-34 M34 0 H230" stroke="url(#gold)" stroke-width="2.5" stroke-linecap="round"/>
    <path d="M0 -13 L13 0 L0 13 L-13 0Z" fill="url(#gold)"/>
  </g>
  <text x="540" y="925" text-anchor="middle" font-family="Poppins" font-weight="500" font-size="34" fill="#E9EEF7">May this festival of lights illuminate your</text>
  <text x="540" y="974" text-anchor="middle" font-family="Poppins" font-weight="500" font-size="34" fill="#E9EEF7">life with prosperity, happiness and success.</text>

  <!-- Diyas -->
  ${diya(345, 1160, 120, 0.9)}${diya(735, 1160, 120, 0.9)}${diya(540, 1168, 170, 1)}

  <!-- Sign-off -->
  <text x="540" y="1232" text-anchor="middle" font-family="Cinzel" font-size="24" letter-spacing="9" fill="#E8CC7A">WITH WARM WISHES</text>
  <text x="540" y="1277" text-anchor="middle" font-family="Cinzel" font-weight="700" font-size="36" letter-spacing="10" fill="#FBF4E2">TEAM HAPPIZO</text>
</svg>`;
}

const THEMES = { diwali: diwaliSvg };
const cache = new Map();

export const cardThemes = () => Object.keys(THEMES);

/** PNG buffer for a theme (rendered once, then cached). Returns null for an unknown theme. */
export function renderCard(theme) {
  const key = String(theme || "").toLowerCase();
  if (!THEMES[key]) return null;
  if (!cache.has(key)) {
    const png = new Resvg(THEMES[key](), {
      fitTo: { mode: "width", value: W },
      font: { fontFiles: ["PlayfairDisplay-Italic.ttf", "Cinzel.ttf", "Poppins-Bold.ttf", "Poppins-Medium.ttf"].map(font), loadSystemFonts: false, defaultFontFamily: "Poppins" },
    }).render().asPng();
    cache.set(key, png);
  }
  return cache.get(key);
}
