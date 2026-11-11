// Page d'accord d'un proche (S3.03) : le jeton est après « # » (jamais envoyé à GitHub Pages). Aucun compte, aucune donnée gardée.
"use strict";
const API = "https://lvxdpmjyeelrxfbrtvay.supabase.co/functions/v1/contact-consent";
const T = {
  fr: {
    loading: "Chargement…",
    ask: (n) => `${n} t'a choisi comme proche de confiance`,
    explain: (n) => `Si un jour ${n} est en danger, tu recevras un SMS avec un lien pour voir sa position pendant l'alerte.`,
    points: ["Aucune application à installer.", "Ta position n'est jamais demandée.", "Tu peux changer d'avis à tout moment."],
    accept: "J'accepte", refuse: "Je refuse", stop: "Ne plus rien recevoir de Cercle Rouge",
    accepted: (n) => `Merci : ${n} pourra te prévenir.`, refused: "C'est noté : tu ne recevras pas ses alertes.",
    stopped: "C'est noté : plus aucun message de Cercle Rouge ne te sera envoyé.",
    expired: "Cette invitation a expiré.", invalid: "Lien invalide ou expiré.", error: "Impossible d'envoyer. Réessaie.",
    privacy: "Confidentialité",
  },
  ht: {
    loading: "N ap chaje…",
    ask: (n) => `${n} chwazi w kòm moun li fè konfyans`,
    explain: (n) => `Si yon jou ${n} an danje, w ap resevwa yon SMS ak yon lyen pou wè kote li ye pandan alèt la.`,
    points: ["Ou pa bezwen enstale okenn aplikasyon.", "Yo pa janm mande pozisyon ou.", "Ou ka chanje lide nenpòt lè."],
    accept: "Mwen dakò", refuse: "Mwen refize", stop: "Pa voye m anyen ankò",
    accepted: (n) => `Mèsi : ${n} ap ka avèti w.`, refused: "Nou note sa : ou p ap resevwa alèt li yo.",
    stopped: "Nou note sa : Cercle Rouge p ap voye w okenn mesaj ankò.",
    expired: "Envitasyon sa a ekspire.", invalid: "Lyen an pa bon oswa li ekspire.", error: "Pa ka voye l. Eseye ankò.",
    privacy: "Konfidansyalite",
  },
  en: {
    loading: "Loading…",
    ask: (n) => `${n} chose you as a trusted contact`,
    explain: (n) => `If ${n} is ever in danger, you will get an SMS with a link to see their location during the alert.`,
    points: ["No app to install.", "Your location is never asked for.", "You can change your mind at any time."],
    accept: "I accept", refuse: "I decline", stop: "Stop all messages from Cercle Rouge",
    accepted: (n) => `Thanks: ${n} will be able to alert you.`, refused: "Noted: you will not receive their alerts.",
    stopped: "Noted: Cercle Rouge will not send you any more messages.",
    expired: "This invitation has expired.", invalid: "Invalid or expired link.", error: "Could not send. Try again.",
    privacy: "Privacy",
  },
};
const $ = (id) => document.getElementById(id);
const token = new URLSearchParams(location.hash.slice(1)).get("i") || "";
let lang = T[(navigator.language || "fr").slice(0, 2)] ? (navigator.language || "fr").slice(0, 2) : "fr";
let data = null;
let done = null;

const t = () => T[lang];
function banner(kind, title, sub) {
  $("banner").className = `banner ${kind}`;
  $("title").textContent = title;
  $("subtitle").textContent = sub || "";
}
function render() {
  document.documentElement.lang = lang;
  document.querySelectorAll(".langs button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.lang === lang)));
  $("privacy").textContent = t().privacy;
  ["accept", "refuse", "stop"].forEach((k) => ($(k).textContent = t()[k]));
  if (!data) return;
  const name = data.name || "?";
  const status = done || data.status;
  $("ask").hidden = status !== "pending";
  if (status === "pending") {
    banner("wait", t().ask(name), "");
    $("explain").textContent = t().explain(name);
    $("points").replaceChildren(...t().points.map((p) => Object.assign(document.createElement("li"), { textContent: p })));
  } else if (status === "accepted") banner("done", t().accepted(name), "");
  else if (status === "refused") banner("wait", t().refused, "");
  else if (status === "stopped") banner("wait", t().stopped, "");
  else banner("wait", t().expired, "");
}
async function call(body) {
  const res = await fetch(API, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body), cache: "no-store", referrerPolicy: "no-referrer" });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(String(res.status));
  return res.json();
}
async function decide(decision, status) {
  document.querySelectorAll("#ask button").forEach((b) => (b.disabled = true));
  try {
    const r = await call({ token, decision });
    if (r && r.ok) {
      done = status;
      render();
      return;
    }
  } catch {}
  $("subtitle").textContent = t().error;
  document.querySelectorAll("#ask button").forEach((b) => (b.disabled = false));
}
$("accept").addEventListener("click", () => decide("accept", "accepted"));
$("refuse").addEventListener("click", () => decide("refuse", "refused"));
$("stop").addEventListener("click", () => decide("stop", "stopped"));
document.querySelectorAll(".langs button").forEach((b) => b.addEventListener("click", () => { lang = b.dataset.lang; render(); }));

render();
banner("wait", t().loading, "");
if (!/^[A-Za-z0-9_-]{32}$/.test(token)) {
  banner("wait", t().invalid, "");
} else {
  call({ token })
    .then((r) => { if (r) { data = r; render(); } else banner("wait", t().invalid, ""); })
    .catch(() => banner("wait", t().error, ""));
}
