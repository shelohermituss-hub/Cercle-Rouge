// Page des proches (S3.08) : lit le statut d'une alerte grâce au jeton placé après « # » dans le lien
// (le fragment n'est jamais envoyé à GitHub Pages ni à aucun serveur), interroge l'Edge Function `track` toutes les 5 s,
// et permet « J'ai pris contact », « Alerte fausse » et l'appel au 114. Aucune donnée n'est gardée dans le navigateur.
"use strict";

const API = "https://lvxdpmjyeelrxfbrtvay.supabase.co/functions/v1/track";
const POLL_MS = 5000;

const T = {
  fr: {
    tripActive: (n) => `${n} est en trajet`,
    tripLost: (n) => `Plus de nouvelles de ${n}`,
    tripEnded: (n) => `Le trajet de ${n} est terminé`,
    tripPlanned: (t) => `Arrivée prévue à ${t}`,
    tripLostSub: (t) => `Dernier signal il y a ${t}. Essaie de l'appeler.`,
    tripNoPos: "Dernière position pas encore reçue.",
    mode: { WALK: "À pied", CAR: "En voiture", TAP_TAP: "En tap-tap", TAXI: "En taxi", MOTO: "À moto" },
    battery: (p) => `Batterie ${p} %`,
    devOnline: "Connecté à Internet",
    devOffline: "Sans Internet",
    deviceAge: (a) => `mis à jour il y a ${a}`,
    others: "Ce que les autres proches ont fait",
    otherAct: { TOOK_CONTACT: "a pris contact", FALSE_ALARM: "dit que c'est une fausse alerte", CALLED_EMERGENCY: "a appelé les secours" },
    loading: "Chargement…",
    invalid: "Lien invalide ou expiré",
    invalidSub: "Ce lien ne fonctionne plus. Si tu es inquiet, appelle la personne ou le 114.",
    offline: "Connexion perdue, nouvel essai…",
    alert: (n) => `${n} a besoin d'aide`,
    verify: (n) => `${n} vérifie si tout va bien`,
    closedSafe: (n) => `${n} va bien`,
    closedFalse: (n) => `${n} a signalé une fausse alerte`,
    expired: "Alerte terminée",
    revoked: "Ce lien a été retiré",
    since: (t) => `Depuis ${t}`,
    level: (l) => (l >= 3 ? "Alerte élargie" : l >= 2 ? "Alerte envoyée aux proches" : "Vérification en cours"),
    reason: {
      SOS: "SOS déclenché", DISTRESS_SCREAM: "Cri de détresse détecté", VIOLENCE_SOUND: "Bruit de violence détecté",
      DISTRESS_KEYWORD: "Appel à l'aide détecté", TRIP_DEVIATION: "Écart de trajet", PROLONGED_STOP: "Arrêt prolongé",
      CONTACT_LOST: "Contact perdu", ARRIVAL_OVERDUE: "Arrivée en retard",
    },
    pos: (age, acc) => `Dernière position il y a ${age}${acc ? `, précision ${acc} m` : ""}.`,
    noPos: "Position pas encore disponible.",
    openMap: "Ouvrir dans une application de carte",
    tookContact: "J'ai pris contact",
    call114: "Appeler les secours (114)",
    falseAlarm: "C'est une fausse alerte",
    sent: "Merci, c'est transmis.",
    notSent: "Impossible d'envoyer. Réessaie.",
    timeline: "Chronologie",
    privacy: "Ce lien est personnel et expire tout seul. La position n'est visible que pendant l'alerte.",
    ev: {
      opened: "Alerte ouverte", escalated: "Alerte élargie", closed: "Alerte terminée",
      contact_took_contact: "Un proche a pris contact", contact_false_alarm: "Un proche signale une fausse alerte",
      contact_called_emergency: "Un proche a appelé les secours",
    },
    sec: "s", min: "min", h: "h",
  },
  ht: {
    tripActive: (n) => `${n} nan yon trajè`,
    tripLost: (n) => `Pa gen nouvèl ${n} ankò`,
    tripEnded: (n) => `Trajè ${n} fini`,
    tripPlanned: (t) => `L ap rive a ${t}`,
    tripLostSub: (t) => `Dènye siyal gen ${t}. Eseye rele l.`,
    tripNoPos: "Dènye pozisyon an poko resevwa.",
    mode: { WALK: "A pye", CAR: "Nan machin", TAP_TAP: "Nan tap-tap", TAXI: "Nan taksi", MOTO: "Sou moto" },
    battery: (p) => `Batri ${p} %`,
    devOnline: "Konekte sou Entènèt",
    devOffline: "San Entènèt",
    deviceAge: (a) => `mizajou gen ${a}`,
    others: "Sa lòt moun yo fè",
    otherAct: { TOOK_CONTACT: "pran kontak", FALSE_ALARM: "di se yon fo alèt", CALLED_EMERGENCY: "rele sekou" },
    loading: "N ap chaje…",
    invalid: "Lyen an pa bon oswa li ekspire",
    invalidSub: "Lyen sa a pa mache ankò. Si w enkyè, rele moun nan oswa 114.",
    offline: "Koneksyon an koupe, n ap eseye ankò…",
    alert: (n) => `${n} bezwen èd`,
    verify: (n) => `N ap verifye si ${n} anfòm`,
    closedSafe: (n) => `${n} anfòm`,
    closedFalse: (n) => `${n} di se te yon fo alèt`,
    expired: "Alèt la fini",
    revoked: "Lyen sa a retire",
    since: (t) => `Depi ${t}`,
    level: (l) => (l >= 3 ? "Alèt elaji" : l >= 2 ? "Alèt voye bay moun li yo" : "Verifikasyon an kou"),
    reason: {
      SOS: "SOS deklanche", DISTRESS_SCREAM: "Yo tande yon rèl", VIOLENCE_SOUND: "Yo tande bri vyolans",
      DISTRESS_KEYWORD: "Yo tande yon apèl pou èd", TRIP_DEVIATION: "Wout la chanje", PROLONGED_STOP: "Kanpe twò lontan",
      CONTACT_LOST: "Kontak la pèdi", ARRIVAL_OVERDUE: "Li an reta",
    },
    pos: (age, acc) => `Dènye pozisyon gen ${age}${acc ? `, presizyon ${acc} m` : ""}.`,
    noPos: "Pozisyon an poko disponib.",
    openMap: "Louvri l nan yon aplikasyon kat",
    tookContact: "Mwen pran kontak",
    call114: "Rele sekou (114)",
    falseAlarm: "Se yon fo alèt",
    sent: "Mèsi, sa voye.",
    notSent: "Pa ka voye l. Eseye ankò.",
    timeline: "Kwonoloji",
    privacy: "Lyen sa a pou ou sèlman, li ekspire poukont li. Pozisyon an parèt sèlman pandan alèt la.",
    ev: {
      opened: "Alèt la louvri", escalated: "Alèt la elaji", closed: "Alèt la fini",
      contact_took_contact: "Yon moun pran kontak", contact_false_alarm: "Yon moun di se yon fo alèt",
      contact_called_emergency: "Yon moun rele sekou",
    },
    sec: "s", min: "min", h: "è",
  },
  en: {
    tripActive: (n) => `${n} is on a trip`,
    tripLost: (n) => `No news from ${n}`,
    tripEnded: (n) => `${n}'s trip has ended`,
    tripPlanned: (t) => `Expected arrival at ${t}`,
    tripLostSub: (t) => `Last signal ${t} ago. Try calling them.`,
    tripNoPos: "Last position not received yet.",
    mode: { WALK: "On foot", CAR: "By car", TAP_TAP: "By tap-tap", TAXI: "By taxi", MOTO: "By motorbike" },
    battery: (p) => `Battery ${p}%`,
    devOnline: "Online",
    devOffline: "No Internet",
    deviceAge: (a) => `updated ${a} ago`,
    others: "What the other contacts did",
    otherAct: { TOOK_CONTACT: "made contact", FALSE_ALARM: "says it is a false alarm", CALLED_EMERGENCY: "called emergency services" },
    loading: "Loading…",
    invalid: "Invalid or expired link",
    invalidSub: "This link no longer works. If you are worried, call the person or 114.",
    offline: "Connection lost, retrying…",
    alert: (n) => `${n} needs help`,
    verify: (n) => `${n} is being asked if they are safe`,
    closedSafe: (n) => `${n} is safe`,
    closedFalse: (n) => `${n} reported a false alarm`,
    expired: "Alert ended",
    revoked: "This link was withdrawn",
    since: (t) => `Since ${t}`,
    level: (l) => (l >= 3 ? "Extended alert" : l >= 2 ? "Alert sent to contacts" : "Check in progress"),
    reason: {
      SOS: "SOS triggered", DISTRESS_SCREAM: "Distress scream detected", VIOLENCE_SOUND: "Violence sound detected",
      DISTRESS_KEYWORD: "Call for help detected", TRIP_DEVIATION: "Trip deviation", PROLONGED_STOP: "Prolonged stop",
      CONTACT_LOST: "Contact lost", ARRIVAL_OVERDUE: "Arrival overdue",
    },
    pos: (age, acc) => `Last position ${age} ago${acc ? `, accuracy ${acc} m` : ""}.`,
    noPos: "Position not available yet.",
    openMap: "Open in a map app",
    tookContact: "I made contact",
    call114: "Call emergency services (114)",
    falseAlarm: "It's a false alarm",
    sent: "Thanks, it has been sent.",
    notSent: "Could not send. Try again.",
    timeline: "Timeline",
    privacy: "This link is personal and expires on its own. The location is visible only during the alert.",
    ev: {
      opened: "Alert opened", escalated: "Alert extended", closed: "Alert ended",
      contact_took_contact: "A contact made contact", contact_false_alarm: "A contact reports a false alarm",
      contact_called_emergency: "A contact called emergency services",
    },
    sec: "s", min: "min", h: "h",
  },
};

const $ = (id) => document.getElementById(id);
const token = new URLSearchParams(location.hash.slice(1)).get("t") || "";
let lang = pickLang();
let data = null;
let map = null;
let marker = null;
let circle = null;
let timer = null;
let msgKey = "";

function pickLang() {
  const l = (navigator.language || "fr").slice(0, 2);
  return T[l] ? l : "fr";
}

function t() { return T[lang]; }

function hhmm(iso) {
  return new Date(iso).toLocaleTimeString(lang === "en" ? "en" : "fr", { hour: "2-digit", minute: "2-digit" });
}

function ago(iso) {
  const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (s < 90) return `${s} ${t().sec}`;
  if (s < 5400) return `${Math.round(s / 60)} ${t().min}`;
  return `${Math.round(s / 3600)} ${t().h}`;
}

function applyStatic() {
  document.documentElement.lang = lang;
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    const v = t()[el.dataset.i18n];
    if (typeof v === "string") el.textContent = v;
  });
  document.querySelectorAll(".langs button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.lang === lang)));
}

function banner(kind, title, sub) {
  const b = $("banner");
  b.className = `banner ${kind}`;
  $("title").textContent = title;
  $("subtitle").textContent = sub || "";
}

function deviceLine() {
  if (data.battery == null && data.online == null) return "";
  const parts = [];
  if (data.battery != null) parts.push(t().battery(data.battery));
  if (data.online != null) parts.push(data.online ? t().devOnline : t().devOffline);
  if (data.device_at) parts.push(t().deviceAge(ago(data.device_at)));
  return parts.join(" · ");
}

function renderTrip() {
  const name = data.name || "?";
  const open = data.status === "active" || data.status === "lost";
  if (data.status === "active") {
    banner("verify", t().tripActive(name), [t().mode[data.mode] || "", t().tripPlanned(hhmm(data.planned_arrival))].filter(Boolean).join(" · "));
  } else if (data.status === "lost") {
    banner("alert", t().tripLost(name), t().tripLostSub(ago(data.last_heartbeat_at)));
  } else if (data.status === "ended") {
    banner("done", t().tripEnded(name), data.ended_at ? hhmm(data.ended_at) : "");
  } else {
    banner("wait", t().revoked, "");
  }
  $("live").hidden = !open;
  $("actions").hidden = true;
  $("act-msg").textContent = "";
  $("device").textContent = data.battery != null ? t().battery(data.battery) + " · " + t().deviceAge(ago(data.last_heartbeat_at)) : "";
  if (open) renderPosition(true);
  $("history").hidden = true;
  $("others").hidden = true;
}

function render() {
  applyStatic();
  if (!data) return;
  if (data.kind === "trip") return renderTrip();
  $("actions").hidden = false;
  const name = data.name || "?";
  const open = data.status === "open";
  if (open) {
    const kind = data.level >= 2 ? "alert" : "verify";
    const title = data.level >= 2 ? t().alert(name) : t().verify(name);
    const reason = t().reason[data.reason] || "";
    banner(kind, title, [t().level(data.level), reason, t().since(hhmm(data.opened_at))].filter(Boolean).join(" · "));
  } else if (data.status === "closed") {
    banner("done", data.closure === "FALSE_ALARM" ? t().closedFalse(name) : t().closedSafe(name), data.closed_at ? hhmm(data.closed_at) : "");
  } else if (data.status === "revoked") {
    banner("wait", t().revoked, "");
  } else {
    banner("wait", t().expired, "");
  }

  $("live").hidden = !open;
  $("device").textContent = open ? deviceLine() : "";
  if (open) renderPosition(false);
  $("act-msg").textContent = msgKey ? t()[msgKey] : "";
  const actions = data.actions || [];
  $("act-contact").disabled = actions.includes("TOOK_CONTACT");
  $("act-false").disabled = actions.includes("FALSE_ALARM");

  const others = data.others || [];
  $("others").hidden = others.length === 0 || !open;
  $("others-list").replaceChildren(...others.map((o) => {
    const li = document.createElement("li");
    const time = document.createElement("time");
    time.dateTime = o.at;
    time.textContent = hhmm(o.at);
    li.append(time, document.createTextNode(`${o.label || "?"} ${t().otherAct[o.action] || o.action}`));
    return li;
  }));

  const events = data.events || [];
  $("history").hidden = events.length === 0;
  $("events").replaceChildren(...events.map((e) => {
    const li = document.createElement("li");
    const time = document.createElement("time");
    time.dateTime = e.at;
    time.textContent = hhmm(e.at);
    li.append(time, document.createTextNode(t().ev[e.type] || e.type));
    return li;
  }));
}

function renderPosition(trip) {
  const p = (data.positions || [])[0];
  if (!p) {
    $("pos-text").textContent = trip ? t().tripNoPos : t().noPos;
    $("map").hidden = true;
    $("open-map").hidden = true;
    return;
  }
  $("map").hidden = false;
  $("open-map").hidden = false;
  $("pos-text").textContent = t().pos(ago(p.ts), p.acc ? Math.round(p.acc) : null);
  $("open-map").href = `geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}`;
  const ll = [p.lat, p.lon];
  if (!map) {
    map = L.map("map", { zoomControl: true, attributionControl: true }).setView(ll, 16);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      referrerPolicy: "strict-origin",
      attribution: "© OpenStreetMap",
    }).addTo(map);
    marker = L.circleMarker(ll, { radius: 9, color: "#0e1012", weight: 3, fillColor: "#ff6b5a", fillOpacity: 1 }).addTo(map);
    circle = L.circle(ll, { radius: p.acc || 0, color: "#ff6b5a", weight: 1, fillOpacity: 0.12 }).addTo(map);
  } else {
    marker.setLatLng(ll);
    circle.setLatLng(ll).setRadius(p.acc || 0);
    if (!map.getBounds().contains(ll)) map.panTo(ll);
  }
}

async function call(body) {
  const res = await fetch(API, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
    cache: "no-store",
    referrerPolicy: "no-referrer",
  });
  if (res.status === 404) return { notFound: true };
  if (!res.ok) throw new Error(String(res.status));
  return res.json();
}

async function poll() {
  clearTimeout(timer);
  try {
    const r = await call({ token });
    if (r.notFound) {
      data = null;
      banner("wait", t().invalid, t().invalidSub);
      $("live").hidden = true;
      return; // plus de requêtes : le lien ne marchera plus
    }
    data = r;
    render();
    if (data.status === "open" || data.status === "active" || data.status === "lost") timer = setTimeout(poll, POLL_MS);
  } catch {
    if (!data) banner("wait", t().offline, "");
    else $("pos-text").textContent = t().offline;
    timer = setTimeout(poll, POLL_MS * 2);
  }
}

async function act(action, button) {
  button.disabled = true;
  try {
    const r = await call({ token, action });
    msgKey = r.ok ? "sent" : "notSent";
    $("act-msg").textContent = t()[msgKey];
    if (!r.ok) button.disabled = false;
    poll();
  } catch {
    msgKey = "notSent";
    $("act-msg").textContent = t().notSent;
    button.disabled = false;
  }
}

$("act-contact").addEventListener("click", (e) => act("TOOK_CONTACT", e.currentTarget));
$("act-false").addEventListener("click", (e) => act("FALSE_ALARM", e.currentTarget));
$("act-114").addEventListener("click", () => { call({ token, action: "CALLED_EMERGENCY" }).catch(() => {}); });
document.querySelectorAll(".langs button").forEach((b) => b.addEventListener("click", () => {
  lang = b.dataset.lang;
  render();
  if (!data) applyStatic();
}));
document.addEventListener("visibilitychange", () => { if (!document.hidden && data && ["open", "active", "lost"].includes(data.status)) poll(); });

applyStatic();
if (/^[A-Za-z0-9_-]{32}$/.test(token)) {
  poll();
} else {
  banner("wait", t().invalid, t().invalidSub);
}
