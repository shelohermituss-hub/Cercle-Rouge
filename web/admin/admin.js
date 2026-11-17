// Console d'administration Cercle Rouge (1.21). Aucune dépendance : appels REST à Supabase Auth et aux RPC admin.
// Accès : compte admin (e-mail + mot de passe) ET double authentification TOTP (session aal2), vérifiés par le serveur.
// La session vit seulement en mémoire (aucun stockage) : fermer l'onglet déconnecte.
"use strict";

const { url: URL_BASE, key: KEY } = window.CR_ADMIN;
let session = null; // { access_token, refresh_token }
let factorId = null;
let currentCode = null;

const $ = (id) => document.getElementById(id);
const say = (text) => { $("msg").textContent = text || ""; };
const show = (id) => ["login", "mfa", "console"].forEach((s) => { $(s).hidden = s !== id; });

async function call(path, { method = "POST", body, auth = true } = {}) {
  const headers = { apikey: KEY, "Content-Type": "application/json" };
  if (auth && session) headers.Authorization = `Bearer ${session.access_token}`;
  const res = await fetch(URL_BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error((data && (data.message || data.msg || data.error_description || data.error)) || `HTTP ${res.status}`);
  return data;
}

const rpc = (name, args = {}) => call(`/rest/v1/rpc/${name}`, { body: args });

function cell(tr, value) {
  const td = document.createElement("td");
  td.textContent = value === null || value === undefined ? "—" : typeof value === "object" ? JSON.stringify(value) : String(value);
  tr.appendChild(td);
}

function fill(tableId, rows, keys) {
  const tbody = $(tableId).querySelector("tbody");
  tbody.replaceChildren();
  rows.forEach((r) => {
    const tr = document.createElement("tr");
    keys.forEach((k) => cell(tr, r[k]));
    tbody.appendChild(tr);
  });
  if (rows.length === 0) {
    const tr = document.createElement("tr");
    cell(tr, "Rien sur la période.");
    tbody.appendChild(tr);
  }
}

$("do-login").addEventListener("click", async () => {
  say("");
  try {
    session = await call("/auth/v1/token?grant_type=password", {
      auth: false,
      body: { email: $("email").value.trim(), password: $("password").value },
    });
    $("password").value = "";
    const user = await call("/auth/v1/user", { method: "GET" });
    const totp = (user.factors || []).find((f) => f.factor_type === "totp" && f.status === "verified");
    if (totp) {
      factorId = totp.id;
      $("enroll").hidden = true;
    } else {
      const enrolled = await call("/auth/v1/factors", { body: { factor_type: "totp", friendly_name: `cr-admin-${Date.now()}` } });
      factorId = enrolled.id;
      $("qr").src = enrolled.totp.qr_code;
      $("secret").textContent = enrolled.totp.secret;
      $("enroll").hidden = false;
    }
    show("mfa");
    $("code").focus();
  } catch (e) {
    session = null;
    say(`Connexion refusée : ${e.message}`);
  }
});

$("do-verify").addEventListener("click", async () => {
  say("");
  try {
    const challenge = await call(`/auth/v1/factors/${factorId}/challenge`, { body: {} });
    session = await call(`/auth/v1/factors/${factorId}/verify`, { body: { challenge_id: challenge.id, code: $("code").value.trim() } });
    $("code").value = "";
    $("logout").hidden = false;
    show("console");
    await refresh();
  } catch (e) {
    say(`Code refusé : ${e.message}`);
  }
});

async function refresh() {
  try {
    const days = Number($("days").value);
    fill("health", await rpc("admin_delivery_health", { p_days: days }), ["day", "channel", "source", "status", "n"]);
    fill("abuse", await rpc("admin_abuse", { p_days: Math.min(days, 7) }), ["day", "action", "accounts", "max_count", "top_code"]);
    fill("audit", await rpc("admin_audit", { p_limit: 50 }), ["at", "action", "actor_code", "meta"]);
  } catch (e) {
    say(e.message === "forbidden" ? "Accès refusé : compte non administrateur ou double authentification absente." : e.message);
  }
}

$("days").addEventListener("change", refresh);

$("do-support").addEventListener("click", async () => {
  say("");
  const dl = $("support");
  dl.replaceChildren();
  $("do-reset").hidden = true;
  try {
    const code = $("support-code").value.trim().toUpperCase();
    const rows = await rpc("admin_support", { p_code: code });
    const r = rows[0];
    const labels = {
      created_at: "Créé le", phone_verified: "Numéro vérifié", age_band: "Tranche d'âge", role: "Rôle",
      alerts_30d: "Alertes (30 j)", open_alert: "Alerte en cours", active_session: "Session suivie en cours",
      invites_accepted: "Proches ayant accepté", invites_pending: "Invitations en attente", limits_today: "Plafonds du jour",
    };
    Object.entries(labels).forEach(([k, label]) => {
      const dt = document.createElement("dt");
      dt.textContent = label;
      const dd = document.createElement("dd");
      dd.textContent = typeof r[k] === "object" ? JSON.stringify(r[k]) : String(r[k]);
      dl.append(dt, dd);
    });
    currentCode = code;
    $("do-reset").hidden = false;
    await refresh();
  } catch (e) {
    say(e.message === "not_found" ? "Aucun compte pour ce code." : e.message);
  }
});

$("do-reset").addEventListener("click", async () => {
  if (!currentCode || !window.confirm(`Lever les plafonds du jour du compte ${currentCode} ? L'action est journalisée.`)) return;
  try {
    const n = await rpc("admin_reset_limits", { p_code: currentCode });
    say(`Plafonds levés (${n}).`);
    await refresh();
  } catch (e) {
    say(e.message);
  }
});

$("logout").addEventListener("click", async () => {
  try { await call("/auth/v1/logout", { body: {} }); } catch (_) { /* session déjà expirée */ }
  session = null;
  factorId = null;
  $("logout").hidden = true;
  show("login");
});
